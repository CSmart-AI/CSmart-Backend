package Capstone.CSmart.global.service.confidence;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.SemanticCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfidenceScoreService {

    private final AiResponseRepository aiResponseRepository;
    private final SemanticCacheRepository semanticCacheRepository;

    /**
     * AI 응답의 신뢰도 점수 계산 (0.0 ~ 1.0)
     * 
     * 평가 기준:
     * - 승인 여부 (40%)
     * - 수정 여부 (30%) 
     * - 사용 빈도 (20%)
     * - 응답 품질 (10%)
     */
    public double calculateConfidenceScore(AiResponse response) {
        try {
            double totalScore = 0.0;

            // 1. 승인 여부 점수 (40% 가중치)
            double approvalScore = calculateApprovalScore(response);
            totalScore += approvalScore * 0.4;

            // 2. 수정 여부 점수 (30% 가중치)
            double modificationScore = calculateModificationScore(response);
            totalScore += modificationScore * 0.3;

            // 3. 사용 빈도 점수 (20% 가중치)
            double frequencyScore = calculateFrequencyScore(response);
            totalScore += frequencyScore * 0.2;

            // 4. 응답 품질 점수 (10% 가중치)
            double qualityScore = calculateQualityScore(response);
            totalScore += qualityScore * 0.1;

            // 0.0 ~ 1.0 범위로 정규화
            double finalScore = Math.max(0.0, Math.min(1.0, totalScore));

            log.debug("신뢰도 계산 완료: responseId={}, approval={:.2f}, modification={:.2f}, frequency={:.2f}, quality={:.2f}, final={:.2f}",
                response.getResponseId(), approvalScore, modificationScore, frequencyScore, qualityScore, finalScore);

            return finalScore;

        } catch (Exception e) {
            log.error("신뢰도 계산 실패: responseId={}", response.getResponseId(), e);
            return 0.5; // 기본값
        }
    }

    /**
     * 승인 여부에 따른 점수 계산
     */
    private double calculateApprovalScore(AiResponse response) {
        return switch (response.getStatus()) {
            case SENT -> 1.0;           // 최종 승인 및 전송
            case APPROVED -> 0.9;       // 승인됨
            case PENDING_REVIEW -> 0.5; // 검토 중
            case REJECTED -> 0.1;       // 거절됨
        };
    }

    /**
     * 수정 여부에 따른 점수 계산
     */
    private double calculateModificationScore(AiResponse response) {
        if (response.getRecommendedResponse() == null || response.getFinalResponse() == null) {
            return 0.5;
        }

        // 수정 없이 그대로 사용했으면 높은 점수
        if (response.getRecommendedResponse().equals(response.getFinalResponse())) {
            return 1.0;
        }

        // 약간의 수정만 있었다면 중간 점수
        double similarityRatio = calculateTextSimilarity(
            response.getRecommendedResponse(), 
            response.getFinalResponse()
        );

        return Math.max(0.3, similarityRatio);
    }

    /**
     * 사용 빈도에 따른 점수 계산
     */
    private double calculateFrequencyScore(AiResponse response) {
        try {
            // 해당 응답의 캐시 엔트리 조회
            Optional<SemanticCache> cacheOpt = semanticCacheRepository
                .findByOriginalResponseId(response.getResponseId());
            
            if (cacheOpt.isEmpty()) {
                return 0.5; // 캐시에 없으면 기본값
            }

            SemanticCache cache = cacheOpt.get();
            int hitCount = cache.getHitCount();

            // 히트 수에 따른 점수 계산
            if (hitCount >= 20) return 1.0;      // 20회 이상
            if (hitCount >= 10) return 0.8;      // 10~19회
            if (hitCount >= 5) return 0.6;       // 5~9회
            if (hitCount >= 2) return 0.4;       // 2~4회
            if (hitCount >= 1) return 0.3;       // 1회
            return 0.2;                           // 0회

        } catch (Exception e) {
            log.warn("빈도 점수 계산 실패: responseId={}", response.getResponseId(), e);
            return 0.5;
        }
    }

    /**
     * 응답 품질에 따른 점수 계산
     */
    private double calculateQualityScore(AiResponse response) {
        if (response.getFinalResponse() == null) {
            return 0.0;
        }

        String answer = response.getFinalResponse();
        double score = 0.5; // 기본 점수

        // 적절한 길이 체크
        int length = answer.length();
        if (length >= 100 && length <= 1000) {
            score += 0.2;
        } else if (length >= 50 && length < 100) {
            score += 0.1;
        } else if (length > 1000) {
            score -= 0.1; // 너무 긴 응답은 감점
        }

        // 구조화된 답변인지 체크 (불릿 포인트, 번호 등)
        if (answer.contains("1.") || answer.contains("-") || answer.contains("•")) {
            score += 0.1;
        }

        // 편입 관련 키워드 체크
        String[] transferKeywords = {"편입", "모집요강", "시험", "학점", "지원", "입학", "대학"};
        long keywordCount = java.util.Arrays.stream(transferKeywords)
            .mapToLong(keyword -> answer.length() - answer.replace(keyword, "").length())
            .sum() / 2; // 대략적인 키워드 개수

        if (keywordCount >= 3) {
            score += 0.1;
        }

        // 부적절한 내용 체크
        if (answer.contains("죄송") || answer.contains("모르겠") || answer.contains("확실하지 않")) {
            score -= 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 두 텍스트 간의 유사도 계산 (간단한 버전)
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        // 레벤슈타인 거리 기반 유사도
        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int editDistance = levenshteinDistance(text1, text2);
        return 1.0 - ((double) editDistance / maxLength);
    }

    /**
     * 레벤슈타인 거리 계산
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[m][n];
    }

    /**
     * 캐시의 신뢰도 점수 업데이트
     */
    public void updateCacheConfidenceScore(Long cacheId) {
        try {
            Optional<SemanticCache> cacheOpt = semanticCacheRepository.findById(cacheId);
            if (cacheOpt.isEmpty()) {
                log.warn("캐시를 찾을 수 없음: cacheId={}", cacheId);
                return;
            }

            SemanticCache cache = cacheOpt.get();
            
            // 원본 응답 조회
            if (cache.getOriginalResponseId() == null) {
                log.warn("원본 응답 ID가 없음: cacheId={}", cacheId);
                return;
            }

            Optional<AiResponse> responseOpt = aiResponseRepository.findById(cache.getOriginalResponseId());
            if (responseOpt.isEmpty()) {
                log.warn("원본 응답을 찾을 수 없음: responseId={}", cache.getOriginalResponseId());
                return;
            }

            AiResponse response = responseOpt.get();
            double newConfidenceScore = calculateConfidenceScore(response);

            // 캐시 신뢰도 업데이트
            cache.setConfidenceScore(newConfidenceScore);
            semanticCacheRepository.save(cache);

            log.info("캐시 신뢰도 업데이트 완료: cacheId={}, newScore={:.3f}", cacheId, newConfidenceScore);

        } catch (Exception e) {
            log.error("캐시 신뢰도 업데이트 실패: cacheId={}", cacheId, e);
        }
    }

    /**
     * 전체 캐시의 신뢰도 점수 재계산
     */
    public ConfidenceUpdateResult recalculateAllCacheConfidenceScores() {
        log.info("전체 캐시 신뢰도 재계산 시작...");

        List<SemanticCache> allCaches = semanticCacheRepository.findAll();
        int totalCount = allCaches.size();
        int successCount = 0;
        int errorCount = 0;

        for (SemanticCache cache : allCaches) {
            try {
                updateCacheConfidenceScore(cache.getCacheId());
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("캐시 신뢰도 재계산 실패: cacheId={}", cache.getCacheId(), e);
            }
        }

        ConfidenceUpdateResult result = new ConfidenceUpdateResult(totalCount, successCount, errorCount);
        log.info("전체 캐시 신뢰도 재계산 완료: {}", result);

        return result;
    }

    /**
     * 신뢰도 업데이트 결과
     */
    public record ConfidenceUpdateResult(int totalCount, int successCount, int errorCount) {
        @Override
        public String toString() {
            return String.format(
                "ConfidenceUpdateResult{총개수=%d, 성공=%d, 실패=%d, 성공률=%.1f%%}",
                totalCount, successCount, errorCount,
                totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0
            );
        }
    }
}