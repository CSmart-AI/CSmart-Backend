package Capstone.CSmart.global.web.dto.cache;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "캐시 통계 응답 DTO")
public class CacheStatsResponseDTO {

    @Schema(description = "총 캐시 개수", example = "1250")
    private final long totalCacheCount;

    @Schema(description = "총 히트 수", example = "3420")
    private final long totalHitCount;

    @Schema(description = "캐시 히트율 (%)", example = "73.2")
    private final double hitRate;

    @Schema(description = "평균 신뢰도 점수", example = "0.823")
    private final double averageConfidenceScore;

    @Schema(description = "예상 비용 절감액 ($)", example = "68.40")
    private final double estimatedCostSavings;

    @Schema(description = "유사도 임계값", example = "0.85")
    private final double similarityThreshold;
}