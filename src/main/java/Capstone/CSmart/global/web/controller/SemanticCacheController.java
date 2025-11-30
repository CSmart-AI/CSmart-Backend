package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.service.cache.CacheWarmupService;
import Capstone.CSmart.global.service.cache.SemanticCacheService;
import Capstone.CSmart.global.service.confidence.ConfidenceScoreService;
import Capstone.CSmart.global.service.scheduler.CacheSchedulerService;
import Capstone.CSmart.global.web.dto.cache.CacheStatsResponseDTO;
import Capstone.CSmart.global.web.dto.cache.CacheWarmupRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cache/semantic")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "시멘틱 캐시", description = "시멘틱 캐시 관리 및 모니터링 API")
public class SemanticCacheController {

    private final SemanticCacheService semanticCacheService;
    private final CacheWarmupService cacheWarmupService;
    private final ConfidenceScoreService confidenceScoreService;
    private final CacheSchedulerService cacheSchedulerService;

    /**
     * 캐시 통계 조회
     */
    @GetMapping("/stats")
    @Operation(
        summary = "캐시 통계 조회",
        description = "시멘틱 캐시의 전체 통계 정보를 조회합니다."
    )
    public ApiResponse<CacheStatsResponseDTO> getCacheStats() {
        try {
            SemanticCacheService.CacheStatistics stats = semanticCacheService.getCacheStatistics();
            
            CacheStatsResponseDTO response = CacheStatsResponseDTO.builder()
                .totalCacheCount(stats.totalCacheCount())
                .totalHitCount(stats.totalHitCount())
                .hitRate(stats.hitRate())
                .averageConfidenceScore(stats.averageConfidenceScore())
                .estimatedCostSavings(stats.estimatedCostSavings())
                .similarityThreshold(stats.similarityThreshold())
                .build();

            return ApiResponse.onSuccess(SuccessStatus.OK, response);

        } catch (Exception e) {
            log.error("캐시 통계 조회 실패", e);
            throw new RuntimeException("캐시 통계 조회에 실패했습니다.");
        }
    }

    /**
     * 캐시 워밍업 실행 (관리자만)
     */
    @PostMapping("/warmup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "캐시 워밍업 실행",
        description = "승인된 AI 응답들을 시멘틱 캐시에 추가합니다. (관리자 전용)"
    )
    public ApiResponse<CacheWarmupService.CacheWarmupResult> warmupCache(
        @RequestBody CacheWarmupRequestDTO request
    ) {
        try {
            log.info("캐시 워밍업 실행 요청: recentDays={}", request.getRecentDays());
            
            CacheWarmupService.CacheWarmupResult result;
            
            if (request.getRecentDays() != null && request.getRecentDays() > 0) {
                result = cacheWarmupService.warmupRecentApprovedResponses(request.getRecentDays());
            } else {
                result = cacheWarmupService.warmupCacheFromApprovedResponses();
            }

            log.info("캐시 워밍업 완료: {}", result);
            return ApiResponse.onSuccess(SuccessStatus.OK, result);

        } catch (Exception e) {
            log.error("캐시 워밍업 실패", e);
            throw new RuntimeException("캐시 워밍업에 실패했습니다.");
        }
    }

    /**
     * 신뢰도 점수 재계산 (관리자만)
     */
    @PostMapping("/recalculate-confidence")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "신뢰도 점수 재계산",
        description = "모든 캐시의 신뢰도 점수를 재계산합니다. (관리자 전용)"
    )
    public ApiResponse<ConfidenceScoreService.ConfidenceUpdateResult> recalculateConfidence() {
        try {
            log.info("신뢰도 점수 재계산 실행");
            
            ConfidenceScoreService.ConfidenceUpdateResult result = 
                confidenceScoreService.recalculateAllCacheConfidenceScores();

            log.info("신뢰도 점수 재계산 완료: {}", result);
            return ApiResponse.onSuccess(SuccessStatus.OK, result);

        } catch (Exception e) {
            log.error("신뢰도 점수 재계산 실패", e);
            throw new RuntimeException("신뢰도 점수 재계산에 실패했습니다.");
        }
    }

    /**
     * 전체 캐시 재구축 (관리자만)
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "전체 캐시 재구축",
        description = "낮은 품질의 캐시를 정리하고 전체 캐시를 재구축합니다. (관리자 전용)"
    )
    public ApiResponse<CacheSchedulerService.CacheRebuildResult> rebuildCache() {
        try {
            log.info("전체 캐시 재구축 실행");
            
            CacheSchedulerService.CacheRebuildResult result = cacheSchedulerService.rebuildAllCache();

            log.info("전체 캐시 재구축 완료: {}", result);
            return ApiResponse.onSuccess(SuccessStatus.OK, result);

        } catch (Exception e) {
            log.error("전체 캐시 재구축 실패", e);
            throw new RuntimeException("전체 캐시 재구축에 실패했습니다.");
        }
    }

    /**
     * 낮은 품질 캐시 정리 (관리자만)
     */
    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "낮은 품질 캐시 정리",
        description = "신뢰도가 낮거나 오래된 캐시를 삭제합니다. (관리자 전용)"
    )
    public ApiResponse<String> cleanupLowQualityCache(
        @Parameter(description = "최소 신뢰도 (0.0 ~ 1.0)")
        @RequestParam(defaultValue = "0.6") double minConfidence,
        
        @Parameter(description = "최대 보관 일수")
        @RequestParam(defaultValue = "30") int maxAge
    ) {
        try {
            log.info("낮은 품질 캐시 정리 실행: minConfidence={}, maxAge={}", minConfidence, maxAge);
            
            semanticCacheService.cleanupLowQualityCaches(minConfidence, maxAge);

            String message = String.format("신뢰도 %.1f 미만, %d일 이상된 캐시를 정리했습니다.", minConfidence, maxAge);
            log.info("낮은 품질 캐시 정리 완료");
            
            return ApiResponse.onSuccess(SuccessStatus.OK, message);

        } catch (Exception e) {
            log.error("낮은 품질 캐시 정리 실패", e);
            throw new RuntimeException("낮은 품질 캐시 정리에 실패했습니다.");
        }
    }

    /**
     * 캐시 히트 테스트
     */
    @GetMapping("/test-hit")
    @Operation(
        summary = "캐시 히트 테스트",
        description = "특정 질문으로 캐시 히트를 테스트합니다."
    )
    public ApiResponse<Object> testCacheHit(
        @Parameter(description = "테스트할 질문")
        @RequestParam String question
    ) {
        try {
            log.info("캐시 히트 테스트: question={}", question.substring(0, Math.min(question.length(), 100)));
            
            var cachedAnswer = semanticCacheService.findSimilarAnswer(question);
            
            if (cachedAnswer.isPresent()) {
                var cache = cachedAnswer.get();
                return ApiResponse.onSuccess(SuccessStatus.OK, java.util.Map.of(
                    "hit", true,
                    "cacheId", cache.getCacheId(),
                    "answer", cache.getAnswer(),
                    "confidenceScore", cache.getConfidenceScore(),
                    "hitCount", cache.getHitCount()
                ));
            } else {
                return ApiResponse.onSuccess(SuccessStatus.OK, java.util.Map.of(
                    "hit", false,
                    "message", "캐시에서 유사한 답변을 찾을 수 없습니다."
                ));
            }

        } catch (Exception e) {
            log.error("캐시 히트 테스트 실패", e);
            throw new RuntimeException("캐시 히트 테스트에 실패했습니다.");
        }
    }

    /**
     * 고품질 캐시 목록 조회
     */
    @GetMapping("/high-quality")
    @Operation(
        summary = "고품질 캐시 목록 조회",
        description = "신뢰도가 높은 캐시 목록을 조회합니다."
    )
    public ApiResponse<Object> getHighQualityCaches(
        @Parameter(description = "최소 신뢰도")
        @RequestParam(defaultValue = "0.8") double minConfidence,
        
        @Parameter(description = "조회할 개수")
        @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            var highQualityCaches = semanticCacheService.getHighConfidenceCaches(minConfidence, limit);
            
            var result = highQualityCaches.stream().map(cache -> java.util.Map.of(
                "cacheId", cache.getCacheId(),
                "question", cache.getQuestion().substring(0, Math.min(cache.getQuestion().length(), 100)) + "...",
                "confidenceScore", cache.getConfidenceScore(),
                "hitCount", cache.getHitCount(),
                "createdAt", cache.getCreatedAt()
            )).toList();
            
            return ApiResponse.onSuccess(SuccessStatus.OK, java.util.Map.of(
                "totalCount", result.size(),
                "minConfidence", minConfidence,
                "caches", result
            ));

        } catch (Exception e) {
            log.error("고품질 캐시 목록 조회 실패", e);
            throw new RuntimeException("고품질 캐시 목록 조회에 실패했습니다.");
        }
    }
}