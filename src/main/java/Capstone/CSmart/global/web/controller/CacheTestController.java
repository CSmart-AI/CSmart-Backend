package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.service.embedding.EmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cache/test")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "캐시 테스트", description = "시멘틱 캐시 시스템 테스트 API")
public class CacheTestController {

    private final EmbeddingService embeddingService;

    /**
     * 임베딩 생성 테스트
     */
    @PostMapping("/embedding")
    @Operation(
        summary = "임베딩 생성 테스트",
        description = "텍스트를 임베딩으로 변환하는 테스트를 수행합니다."
    )
    public ApiResponse<Map<String, Object>> testEmbedding(
        @Parameter(description = "임베딩으로 변환할 텍스트")
        @RequestParam String text
    ) {
        try {
            long startTime = System.currentTimeMillis();
            
            List<Double> embedding = embeddingService.generateEmbedding(text);
            
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = Map.of(
                "text", text,
                "embeddingSize", embedding.size(),
                "embeddingModel", embeddingService.getEmbeddingModel(),
                "durationMs", duration,
                "firstFewValues", embedding.subList(0, Math.min(5, embedding.size()))
            );

            log.info("임베딩 생성 테스트 완료: 텍스트길이={}, 임베딩크기={}, 소요시간={}ms", 
                text.length(), embedding.size(), duration);

            return ApiResponse.onSuccess(SuccessStatus.OK, result);

        } catch (Exception e) {
            log.error("임베딩 생성 테스트 실패", e);
            throw new RuntimeException("임베딩 생성에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 유사도 계산 테스트
     */
    @PostMapping("/similarity")
    @Operation(
        summary = "유사도 계산 테스트",
        description = "두 텍스트 간의 코사인 유사도를 계산합니다."
    )
    public ApiResponse<Map<String, Object>> testSimilarity(
        @Parameter(description = "첫 번째 텍스트")
        @RequestParam String text1,
        
        @Parameter(description = "두 번째 텍스트")
        @RequestParam String text2
    ) {
        try {
            long startTime = System.currentTimeMillis();
            
            List<Double> embedding1 = embeddingService.generateEmbedding(text1);
            List<Double> embedding2 = embeddingService.generateEmbedding(text2);
            
            double similarity = embeddingService.cosineSimilarity(embedding1, embedding2);
            
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = Map.of(
                "text1", text1,
                "text2", text2,
                "similarity", similarity,
                "similarityPercentage", String.format("%.1f%%", similarity * 100),
                "durationMs", duration,
                "isHighSimilarity", similarity >= 0.85
            );

            log.info("유사도 계산 완료: similarity={:.4f}, duration={}ms", similarity, duration);

            return ApiResponse.onSuccess(SuccessStatus.OK, result);

        } catch (Exception e) {
            log.error("유사도 계산 실패", e);
            throw new RuntimeException("유사도 계산에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 시스템 상태 확인
     */
    @GetMapping("/health")
    @Operation(
        summary = "캐시 시스템 상태 확인",
        description = "시멘틱 캐시 시스템의 전체 상태를 확인합니다."
    )
    public ApiResponse<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = Map.of(
                "embeddingService", "OK",
                "embeddingModel", embeddingService.getEmbeddingModel(),
                "embeddingDimensions", embeddingService.getEmbeddingDimensions(),
                "timestamp", java.time.OffsetDateTime.now(),
                "status", "HEALTHY"
            );

            return ApiResponse.onSuccess(SuccessStatus.OK, health);

        } catch (Exception e) {
            log.error("시스템 상태 확인 실패", e);
            
            Map<String, Object> health = Map.of(
                "status", "UNHEALTHY",
                "error", e.getMessage(),
                "timestamp", java.time.OffsetDateTime.now()
            );
            
            return ApiResponse.onSuccess(SuccessStatus.OK, health);
        }
    }
}