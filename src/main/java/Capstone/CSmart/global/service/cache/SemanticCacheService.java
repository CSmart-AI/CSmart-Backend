package Capstone.CSmart.global.service.cache;

import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.repository.SemanticCacheRepository;
import Capstone.CSmart.global.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticCacheService {

    private final SemanticCacheRepository cacheRepository;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${semantic-cache.similarity-threshold:0.85}")
    private double similarityThreshold;

    @Value("${semantic-cache.cache-ttl:604800}") // 7일
    private long cacheTtl;

    private static final String REDIS_KEY_PREFIX = "semantic_cache:";
    private static final String REDIS_STATS_KEY = "semantic_cache_stats";

    /**
     * 시멘틱 캐시에서 유사한 답변 검색
     * 성능 최적화: 고신뢰도 캐시만 조회 + 유사도 1회만 계산
     */
    public Optional<SemanticCache> findSimilarAnswer(String question) {
        try {
            log.debug("Searching semantic cache for question: {}", question.substring(0, Math.min(question.length(), 100)));

            // 1. 질문을 임베딩으로 변환
            List<Double> questionEmbedding = embeddingService.generateEmbedding(question);

            // 2. 신뢰도 높은 캐시만 조회 (성능 최적화)
            // 신뢰도 0.7 이상의 캐시만 검색 대상으로 함
            List<SemanticCache> highQualityCaches = cacheRepository
                .findByConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescHitCountDesc(
                    0.7,
                    org.springframework.data.domain.PageRequest.of(0, 200) // 최대 200개만
                );

            if (highQualityCaches.isEmpty()) {
                log.debug("No high-quality cache entries found");
                return Optional.empty();
            }

            log.debug("Searching {} high-quality cache entries", highQualityCaches.size());

            // 3. 유사도 계산 결과를 Map에 저장 (1회만 계산)
            java.util.Map<SemanticCache, Double> similarityMap = new java.util.HashMap<>();

            for (SemanticCache cache : highQualityCaches) {
                try {
                    List<Double> cacheEmbedding = embeddingService.jsonToVector(cache.getEmbeddingJson());
                    double similarity = embeddingService.cosineSimilarity(questionEmbedding, cacheEmbedding);

                    if (similarity >= similarityThreshold) {
                        similarityMap.put(cache, similarity);
                        log.debug("Cache ID: {}, Similarity: {:.4f}", cache.getCacheId(), similarity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate similarity for cacheId: {}", cache.getCacheId(), e);
                }
            }

            if (similarityMap.isEmpty()) {
                log.debug("No cache entries above similarity threshold {}", similarityThreshold);
                return Optional.empty();
            }

            // 4. 가장 유사도가 높은 캐시 선택
            var bestMatch = similarityMap.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey);

            // 5. 캐시 히트 시 통계 업데이트
            if (bestMatch.isPresent()) {
                SemanticCache cache = bestMatch.get();
                double similarity = similarityMap.get(cache);

                log.info("🎯 캐시 히트! Cache ID: {}, Similarity: {:.4f}, Hit Count: {}",
                    cache.getCacheId(), similarity, cache.getHitCount());

                // 비동기로 히트 카운트 업데이트 (성능을 위해)
                updateCacheHitAsync(cache.getCacheId());

                // Redis에서 빠른 접근용 저장
                String redisKey = REDIS_KEY_PREFIX + cache.getCacheId();
                redisTemplate.opsForValue().set(redisKey, cache.getAnswer(), cacheTtl, TimeUnit.SECONDS);

                return bestMatch;
            }

            log.debug("No similar cache found above threshold {}", similarityThreshold);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to search semantic cache", e);
            return Optional.empty();
        }
    }

    /**
     * 새로운 답변을 캐시에 저장
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SemanticCache saveToCache(String question, String answer, Long responseId, double confidenceScore) {
        try {
            log.info("Saving to semantic cache: question={}, responseId={}, confidenceScore={}", 
                question.substring(0, Math.min(question.length(), 50)), responseId, confidenceScore);

            // 1. 이미 저장된 응답인지 확인
            Optional<SemanticCache> existing = cacheRepository.findByOriginalResponseId(responseId);
            if (existing.isPresent()) {
                log.warn("Cache already exists for responseId: {}", responseId);
                return existing.get();
            }

            // 2. 임베딩 생성
            List<Double> embedding = embeddingService.generateEmbedding(question);
            String embeddingJson = embeddingService.vectorToJson(embedding);

            // 3. 캐시 키 생성
            String cacheKey = generateCacheKey(question);

            // 4. 캐시 엔트티 생성
            SemanticCache cache = SemanticCache.builder()
                .question(question)
                .answer(answer)
                .embeddingJson(embeddingJson)
                .confidenceScore(confidenceScore)
                .hitCount(0)
                .lastHitAt(OffsetDateTime.now())
                .originalResponseId(responseId)
                .cacheKey(cacheKey)
                .embeddingModel(embeddingService.getEmbeddingModel())
                .build();

            // 5. DB에 저장
            SemanticCache savedCache = cacheRepository.save(cache);

            // 6. Redis에도 저장 (빠른 접근용)
            String redisKey = REDIS_KEY_PREFIX + savedCache.getCacheId();
            redisTemplate.opsForValue().set(redisKey, answer, cacheTtl, TimeUnit.SECONDS);

            // 7. 통계 업데이트
            updateCacheStatsAsync();

            log.info("✅ Saved to semantic cache: cacheId={}", savedCache.getCacheId());
            return savedCache;

        } catch (Exception e) {
            log.error("Failed to save to semantic cache", e);
            throw new RuntimeException("Failed to save to semantic cache", e);
        }
    }

    /**
     * 캐시 키 생성 (해시 기반)
     */
    private String generateCacheKey(String question) {
        return "q_" + Math.abs(question.hashCode()) + "_" + System.currentTimeMillis();
    }

    /**
     * 비동기로 캐시 히트 카운트 업데이트
     * public으로 변경 (Spring AOP 프록시를 위해 필요)
     */
    @org.springframework.scheduling.annotation.Async("cacheTaskExecutor")
    @org.springframework.transaction.annotation.Transactional
    public void updateCacheHitAsync(Long cacheId) {
        try {
            Optional<SemanticCache> cacheOpt = cacheRepository.findById(cacheId);
            if (cacheOpt.isPresent()) {
                SemanticCache cache = cacheOpt.get();
                cache.incrementHitCount();
                cacheRepository.save(cache);
                log.debug("비동기 캐시 히트 카운트 업데이트 완료: cacheId={}", cacheId);
            }
        } catch (Exception e) {
            log.error("Failed to update cache hit count for cacheId: {}", cacheId, e);
        }
    }

    /**
     * 비동기로 전체 캐시 통계 업데이트
     */
    @org.springframework.scheduling.annotation.Async("cacheTaskExecutor")
    public void updateCacheStatsAsync() {
        try {
            long totalCaches = cacheRepository.count();
            redisTemplate.opsForHash().put(REDIS_STATS_KEY, "total_caches", String.valueOf(totalCaches));
            redisTemplate.opsForHash().put(REDIS_STATS_KEY, "last_updated", String.valueOf(System.currentTimeMillis()));
            log.debug("비동기 캐시 통계 업데이트 완료: totalCaches={}", totalCaches);
        } catch (Exception e) {
            log.error("Failed to update cache stats", e);
        }
    }

    /**
     * 캐시 통계 조회
     */
    public CacheStatistics getCacheStatistics() {
        try {
            Object[] stats = cacheRepository.getCacheStatistics();
            
            long totalCount = stats != null && stats.length > 0 ? ((Number) stats[0]).longValue() : 0;
            long totalHits = stats != null && stats.length > 1 && stats[1] != null ? ((Number) stats[1]).longValue() : 0;
            double avgConfidence = stats != null && stats.length > 2 && stats[2] != null ? ((Number) stats[2]).doubleValue() : 0.0;

            double hitRate = totalCount > 0 ? (double) totalHits / (totalHits + totalCount) * 100 : 0.0;
            
            // 예상 비용 절감 계산 (캐시 히트당 $0.02 절약)
            double estimatedSavings = totalHits * 0.02;

            return CacheStatistics.builder()
                .totalCacheCount(totalCount)
                .totalHitCount(totalHits)
                .hitRate(hitRate)
                .averageConfidenceScore(avgConfidence)
                .estimatedCostSavings(estimatedSavings)
                .similarityThreshold(similarityThreshold)
                .build();

        } catch (Exception e) {
            log.error("Failed to get cache statistics", e);
            return CacheStatistics.builder()
                .totalCacheCount(0)
                .totalHitCount(0)
                .hitRate(0.0)
                .averageConfidenceScore(0.0)
                .estimatedCostSavings(0.0)
                .similarityThreshold(similarityThreshold)
                .build();
        }
    }

    /**
     * 신뢰도 높은 캐시 조회
     */
    public List<SemanticCache> getHighConfidenceCaches(double minConfidence, int limit) {
        return cacheRepository.findByConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescHitCountDesc(
            minConfidence, 
            org.springframework.data.domain.PageRequest.of(0, limit)
        );
    }

    /**
     * 캐시 삭제 (낮은 신뢰도 또는 오래된 캐시 정리)
     */
    @Transactional
    public void cleanupLowQualityCaches(double minConfidence, int maxAge) {
        try {
            java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(maxAge);
            
            // 낮은 신뢰도 또는 오래된 캐시 조회
            List<SemanticCache> cachesToDelete = cacheRepository.findAll().stream()
                .filter(cache -> 
                    cache.getConfidenceScore() < minConfidence || 
                    cache.getCreatedAt().isBefore(cutoffDate)
                )
                .toList();

            for (SemanticCache cache : cachesToDelete) {
                // Redis에서도 삭제
                String redisKey = REDIS_KEY_PREFIX + cache.getCacheId();
                redisTemplate.delete(redisKey);
            }

            // DB에서 삭제
            cacheRepository.deleteAll(cachesToDelete);
            
            log.info("Cleaned up {} low quality cache entries", cachesToDelete.size());

        } catch (Exception e) {
            log.error("Failed to cleanup low quality caches", e);
        }
    }

    /**
     * Repository 접근 (CacheWarmupService용)
     */
    public SemanticCacheRepository getCacheRepository() {
        return cacheRepository;
    }

    /**
     * 캐시 통계 DTO
     */
    public static record CacheStatistics(
        long totalCacheCount,
        long totalHitCount,
        double hitRate,
        double averageConfidenceScore,
        double estimatedCostSavings,
        double similarityThreshold
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private long totalCacheCount;
            private long totalHitCount;
            private double hitRate;
            private double averageConfidenceScore;
            private double estimatedCostSavings;
            private double similarityThreshold;

            public Builder totalCacheCount(long totalCacheCount) {
                this.totalCacheCount = totalCacheCount;
                return this;
            }

            public Builder totalHitCount(long totalHitCount) {
                this.totalHitCount = totalHitCount;
                return this;
            }

            public Builder hitRate(double hitRate) {
                this.hitRate = hitRate;
                return this;
            }

            public Builder averageConfidenceScore(double averageConfidenceScore) {
                this.averageConfidenceScore = averageConfidenceScore;
                return this;
            }

            public Builder estimatedCostSavings(double estimatedCostSavings) {
                this.estimatedCostSavings = estimatedCostSavings;
                return this;
            }

            public Builder similarityThreshold(double similarityThreshold) {
                this.similarityThreshold = similarityThreshold;
                return this;
            }

            public CacheStatistics build() {
                return new CacheStatistics(
                    totalCacheCount,
                    totalHitCount,
                    hitRate,
                    averageConfidenceScore,
                    estimatedCostSavings,
                    similarityThreshold
                );
            }
        }
    }
}