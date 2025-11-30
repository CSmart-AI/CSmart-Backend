package Capstone.CSmart.global.service.scheduler;

import Capstone.CSmart.global.service.cache.CacheWarmupService;
import Capstone.CSmart.global.service.cache.SemanticCacheService;
import Capstone.CSmart.global.service.confidence.ConfidenceScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheSchedulerService {

    private final CacheWarmupService cacheWarmupService;
    private final ConfidenceScoreService confidenceScoreService;
    private final SemanticCacheService semanticCacheService;

    /**
     * 애플리케이션 시작 시 초기 캐시 워밍업 (선택사항)
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initializeCache() {
        log.info("🚀 애플리케이션 시작 - 시멘틱 캐시 시스템 초기화");
        
        try {
            // 최근 7일간의 승인된 응답들로 캐시 초기화
            CacheWarmupService.CacheWarmupResult result = cacheWarmupService.warmupRecentApprovedResponses(7);
            log.info("초기 캐시 워밍업 완료: {}", result);
            
        } catch (Exception e) {
            log.error("초기 캐시 워밍업 실패", e);
        }
    }

    /**
     * 매일 새벽 2시 - 신뢰도 높은 응답들을 캐시에 추가
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    public void buildHighConfidenceCache() {
        log.info("🔄 정기 캐시 구축 작업 시작 - {}", 
            OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        try {
            // 1. 최근 1일간 승인된 응답들을 캐시에 추가
            CacheWarmupService.CacheWarmupResult warmupResult = cacheWarmupService.warmupRecentApprovedResponses(1);
            log.info("일일 캐시 워밍업 완료: {}", warmupResult);
            
            // 2. 모든 캐시의 신뢰도 점수 재계산
            ConfidenceScoreService.ConfidenceUpdateResult confidenceResult = confidenceScoreService.recalculateAllCacheConfidenceScores();
            log.info("신뢰도 점수 재계산 완료: {}", confidenceResult);
            
            // 3. 캐시 통계 출력
            SemanticCacheService.CacheStatistics stats = semanticCacheService.getCacheStatistics();
            log.info("캐시 통계: 총 캐시 수={}, 총 히트 수={}, 히트율={:.1f}%, 예상 절감액=${:.2f}", 
                stats.totalCacheCount(), stats.totalHitCount(), stats.hitRate(), stats.estimatedCostSavings());
                
        } catch (Exception e) {
            log.error("정기 캐시 구축 작업 실패", e);
        }
    }

    /**
     * 매주 일요일 새벽 3시 - 낮은 품질의 캐시 정리
     */
    @Scheduled(cron = "0 0 3 * * SUN") // 매주 일요일 새벽 3시
    public void cleanupLowQualityCache() {
        log.info("🧹 낮은 품질 캐시 정리 작업 시작");
        
        try {
            // 신뢰도 0.6 미만, 30일 이상된 캐시 정리
            semanticCacheService.cleanupLowQualityCaches(0.6, 30);
            log.info("낮은 품질 캐시 정리 완료");
            
            // 정리 후 통계 출력
            SemanticCacheService.CacheStatistics stats = semanticCacheService.getCacheStatistics();
            log.info("정리 후 캐시 통계: 총 캐시 수={}, 평균 신뢰도={:.3f}", 
                stats.totalCacheCount(), stats.averageConfidenceScore());
                
        } catch (Exception e) {
            log.error("캐시 정리 작업 실패", e);
        }
    }

    /**
     * 매시간 - 캐시 상태 모니터링 (간단한 헬스체크)
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3600000ms
    public void monitorCacheHealth() {
        try {
            SemanticCacheService.CacheStatistics stats = semanticCacheService.getCacheStatistics();
            
            // 캐시 히트율이 너무 낮으면 경고
            if (stats.hitRate() < 30.0 && stats.totalCacheCount() > 10) {
                log.warn("⚠️ 캐시 히트율이 낮습니다: {:.1f}% (캐시 수: {})", 
                    stats.hitRate(), stats.totalCacheCount());
            }
            
            // 캐시가 너무 많으면 알림
            if (stats.totalCacheCount() > 10000) {
                log.warn("⚠️ 캐시 개수가 많습니다: {} (정리 고려 필요)", stats.totalCacheCount());
            }
            
            log.debug("캐시 상태 모니터링: 캐시수={}, 히트율={:.1f}%", 
                stats.totalCacheCount(), stats.hitRate());
                
        } catch (Exception e) {
            log.error("캐시 상태 모니터링 실패", e);
        }
    }

    /**
     * 수동 실행용 - 전체 캐시 재구축
     */
    public CacheRebuildResult rebuildAllCache() {
        log.info("🔨 전체 캐시 재구축 시작");
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 기존 캐시 정리 (모든 캐시 삭제는 위험하므로 낮은 품질만)
            semanticCacheService.cleanupLowQualityCaches(0.3, 7);
            
            // 2. 전체 승인된 응답 재구축
            CacheWarmupService.CacheWarmupResult warmupResult = cacheWarmupService.warmupCacheFromApprovedResponses();
            
            // 3. 신뢰도 재계산
            ConfidenceScoreService.ConfidenceUpdateResult confidenceResult = confidenceScoreService.recalculateAllCacheConfidenceScores();
            
            long duration = System.currentTimeMillis() - startTime;
            CacheRebuildResult result = new CacheRebuildResult(
                true, 
                warmupResult.successCount(), 
                confidenceResult.successCount(), 
                duration, 
                null
            );
            
            log.info("전체 캐시 재구축 완료: {}", result);
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            CacheRebuildResult result = new CacheRebuildResult(false, 0, 0, duration, e.getMessage());
            log.error("전체 캐시 재구축 실패: {}", result, e);
            return result;
        }
    }

    /**
     * 캐시 재구축 결과
     */
    public record CacheRebuildResult(
        boolean success,
        int cacheCount,
        int confidenceUpdateCount,
        long durationMs,
        String errorMessage
    ) {
        @Override
        public String toString() {
            if (success) {
                return String.format(
                    "CacheRebuildResult{성공=true, 캐시수=%d, 신뢰도업데이트=%d, 소요시간=%dms}",
                    cacheCount, confidenceUpdateCount, durationMs
                );
            } else {
                return String.format(
                    "CacheRebuildResult{성공=false, 소요시간=%dms, 오류=%s}",
                    durationMs, errorMessage
                );
            }
        }
    }
}