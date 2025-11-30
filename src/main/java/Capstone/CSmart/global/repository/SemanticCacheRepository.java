package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.SemanticCache;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SemanticCacheRepository extends JpaRepository<SemanticCache, Long> {

    /**
     * 신뢰도 점수가 임계값 이상인 캐시 조회
     */
    @Query("SELECT sc FROM SemanticCache sc WHERE sc.confidenceScore >= :minConfidence ORDER BY sc.confidenceScore DESC, sc.hitCount DESC")
    List<SemanticCache> findByConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescHitCountDesc(
            @Param("minConfidence") Double minConfidence, 
            Pageable pageable);

    /**
     * 특정 기간 이후 생성된 캐시 조회
     */
    @Query("SELECT sc FROM SemanticCache sc WHERE sc.createdAt >= :since ORDER BY sc.createdAt DESC")
    List<SemanticCache> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("since") LocalDateTime since);

    /**
     * 히트 수가 많은 캐시 조회 (인기 캐시)
     */
    @Query("SELECT sc FROM SemanticCache sc WHERE sc.hitCount >= :minHitCount ORDER BY sc.hitCount DESC")
    List<SemanticCache> findByHitCountGreaterThanEqualOrderByHitCountDesc(
            @Param("minHitCount") Integer minHitCount, 
            Pageable pageable);

    /**
     * 원본 응답 ID로 캐시 조회
     */
    Optional<SemanticCache> findByOriginalResponseId(Long originalResponseId);

    /**
     * 캐시 키로 캐시 조회
     */
    Optional<SemanticCache> findByCacheKey(String cacheKey);

    /**
     * 전체 캐시 통계
     */
    @Query("SELECT " +
           "COUNT(sc) as totalCount, " +
           "SUM(sc.hitCount) as totalHits, " +
           "AVG(sc.confidenceScore) as avgConfidence " +
           "FROM SemanticCache sc")
    Object[] getCacheStatistics();

    /**
     * 신뢰도 점수별 캐시 개수
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN sc.confidenceScore >= 0.9 THEN 'EXCELLENT' " +
           "  WHEN sc.confidenceScore >= 0.8 THEN 'GOOD' " +
           "  WHEN sc.confidenceScore >= 0.7 THEN 'FAIR' " +
           "  ELSE 'POOR' " +
           "END as confidenceLevel, " +
           "COUNT(sc) as count " +
           "FROM SemanticCache sc " +
           "GROUP BY " +
           "CASE " +
           "  WHEN sc.confidenceScore >= 0.9 THEN 'EXCELLENT' " +
           "  WHEN sc.confidenceScore >= 0.8 THEN 'GOOD' " +
           "  WHEN sc.confidenceScore >= 0.7 THEN 'FAIR' " +
           "  ELSE 'POOR' " +
           "END")
    List<Object[]> getConfidenceLevelDistribution();

    /**
     * 최근 N일간 캐시 히트 수 합계
     */
    @Query("SELECT SUM(sc.hitCount) FROM SemanticCache sc WHERE sc.lastHitAt >= :since")
    Long getTotalHitsSince(@Param("since") OffsetDateTime since);

    /**
     * 임베딩 모델별 캐시 개수
     */
    @Query("SELECT sc.embeddingModel, COUNT(sc) FROM SemanticCache sc GROUP BY sc.embeddingModel")
    List<Object[]> getCountByEmbeddingModel();
}