package Capstone.CSmart.global.service.cache;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final AiResponseRepository aiResponseRepository;
    private final MessageRepository messageRepository;
    private final SemanticCacheService semanticCacheService;

    /**
     * 승인된 AI 응답들을 캐시로 이관
     * 배치 처리로 메모리 효율적으로 처리
     */
    @Transactional
    public CacheWarmupResult warmupCacheFromApprovedResponses() {
        log.info("승인된 AI 응답들을 캐시로 이관 시작...");
        
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        int batchSize = 50;
        int pageNumber = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            
            // 승인된 AI 응답들을 배치로 조회
            Page<AiResponse> approvedResponses = aiResponseRepository
                .findByStatusOrderByGeneratedAtDesc(AiResponseStatus.SENT, pageable);
            
            if (approvedResponses.isEmpty()) {
                break;
            }

            log.info("배치 처리 중: {}/{}", pageNumber * batchSize, approvedResponses.getTotalElements());
            
            for (AiResponse aiResponse : approvedResponses.getContent()) {
                try {
                    totalProcessed.incrementAndGet();
                    
                    // 이미 캐시에 있는지 확인
                    Optional<SemanticCache> existingCache = semanticCacheService
                        .getCacheRepository().findByOriginalResponseId(aiResponse.getResponseId());
                    
                    if (existingCache.isPresent()) {
                        log.debug("이미 캐시에 존재: responseId={}", aiResponse.getResponseId());
                        skipCount.incrementAndGet();
                        continue;
                    }

                    // 원본 메시지 조회
                    Optional<Message> messageOpt = messageRepository.findById(aiResponse.getMessageId());
                    if (messageOpt.isEmpty()) {
                        log.warn("메시지를 찾을 수 없음: messageId={}", aiResponse.getMessageId());
                        errorCount.incrementAndGet();
                        continue;
                    }

                    Message message = messageOpt.get();
                    
                    // 응답이 유효한지 확인
                    if (aiResponse.getFinalResponse() == null || aiResponse.getFinalResponse().trim().isEmpty()) {
                        log.warn("빈 응답으로 캐시 저장 생략: responseId={}", aiResponse.getResponseId());
                        errorCount.incrementAndGet();
                        continue;
                    }

                    // 기본 신뢰도 계산 (승인된 응답이므로 0.8 시작)
                    double confidenceScore = calculateBasicConfidenceScore(aiResponse);

                    // 캐시에 저장
                    semanticCacheService.saveToCache(
                        message.getContent(),
                        aiResponse.getFinalResponse(),
                        aiResponse.getResponseId(),
                        confidenceScore
                    );

                    successCount.incrementAndGet();
                    log.debug("캐시 저장 완료: responseId={}, confidence={:.2f}", 
                        aiResponse.getResponseId(), confidenceScore);

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("캐시 저장 실패: responseId={}, error={}", 
                        aiResponse.getResponseId(), e.getMessage(), e);
                }
            }

            pageNumber++;
            
            // 진행 상황 로깅
            if (pageNumber % 10 == 0) {
                log.info("진행 상황: 처리완료={}, 성공={}, 스킵={}, 실패={}", 
                    totalProcessed.get(), successCount.get(), skipCount.get(), errorCount.get());
            }
        }

        CacheWarmupResult result = CacheWarmupResult.builder()
            .totalProcessed(totalProcessed.get())
            .successCount(successCount.get())
            .skipCount(skipCount.get())
            .errorCount(errorCount.get())
            .build();

        log.info("캐시 워밍업 완료: {}", result);
        return result;
    }

    /**
     * 특정 기간의 승인된 응답만 캐시로 이관
     */
    @Transactional
    public CacheWarmupResult warmupRecentApprovedResponses(int recentDays) {
        log.info("최근 {}일간의 승인된 AI 응답들을 캐시로 이관 시작...", recentDays);
        
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 최근 응답들만 조회
        java.time.OffsetDateTime cutoffDate = java.time.OffsetDateTime.now().minusDays(recentDays);
        
        aiResponseRepository.findAll().stream()
            .filter(response -> response.getStatus() == AiResponseStatus.SENT)
            .filter(response -> response.getGeneratedAt().isAfter(cutoffDate))
            .forEach(aiResponse -> {
                try {
                    totalProcessed.incrementAndGet();
                    
                    // 캐시 존재 여부 확인
                    Optional<SemanticCache> existingCache = semanticCacheService
                        .getCacheRepository().findByOriginalResponseId(aiResponse.getResponseId());
                    
                    if (existingCache.isPresent()) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    // 메시지 조회
                    Optional<Message> messageOpt = messageRepository.findById(aiResponse.getMessageId());
                    if (messageOpt.isEmpty()) {
                        errorCount.incrementAndGet();
                        return;
                    }

                    Message message = messageOpt.get();
                    
                    if (aiResponse.getFinalResponse() == null || aiResponse.getFinalResponse().trim().isEmpty()) {
                        errorCount.incrementAndGet();
                        return;
                    }

                    double confidenceScore = calculateBasicConfidenceScore(aiResponse);

                    semanticCacheService.saveToCache(
                        message.getContent(),
                        aiResponse.getFinalResponse(),
                        aiResponse.getResponseId(),
                        confidenceScore
                    );

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("최근 응답 캐시 저장 실패: responseId={}", aiResponse.getResponseId(), e);
                }
            });

        CacheWarmupResult result = CacheWarmupResult.builder()
            .totalProcessed(totalProcessed.get())
            .successCount(successCount.get())
            .skipCount(skipCount.get())
            .errorCount(errorCount.get())
            .build();

        log.info("최근 응답 캐시 워밍업 완료: {}", result);
        return result;
    }

    /**
     * 승인된 응답의 기본 신뢰도 점수 계산
     * 추후 ConfidenceScoreService에서 더 정교한 계산으로 업데이트
     */
    private double calculateBasicConfidenceScore(AiResponse aiResponse) {
        double baseScore = 0.8; // 승인된 응답 기본 점수

        // 수정 없이 승인됐으면 점수 증가
        if (aiResponse.getRecommendedResponse().equals(aiResponse.getFinalResponse())) {
            baseScore += 0.1;
        }

        // 응답 길이가 적절하면 점수 증가
        int responseLength = aiResponse.getFinalResponse().length();
        if (responseLength > 50 && responseLength < 1000) {
            baseScore += 0.05;
        }

        return Math.min(1.0, baseScore);
    }

    /**
     * 캐시 워밍업 결과
     */
    public static record CacheWarmupResult(
        int totalProcessed,
        int successCount,
        int skipCount,
        int errorCount
    ) {
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return String.format(
                "CacheWarmupResult{총처리=%d, 성공=%d, 스킵=%d, 실패=%d, 성공률=%.1f%%}",
                totalProcessed, successCount, skipCount, errorCount,
                totalProcessed > 0 ? (successCount * 100.0 / totalProcessed) : 0.0
            );
        }

        public static class Builder {
            private int totalProcessed;
            private int successCount;
            private int skipCount;
            private int errorCount;

            public Builder totalProcessed(int totalProcessed) {
                this.totalProcessed = totalProcessed;
                return this;
            }

            public Builder successCount(int successCount) {
                this.successCount = successCount;
                return this;
            }

            public Builder skipCount(int skipCount) {
                this.skipCount = skipCount;
                return this;
            }

            public Builder errorCount(int errorCount) {
                this.errorCount = errorCount;
                return this;
            }

            public CacheWarmupResult build() {
                return new CacheWarmupResult(totalProcessed, successCount, skipCount, errorCount);
            }
        }
    }
}