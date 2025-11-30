package Capstone.CSmart.global.service.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${semantic-cache.embedding.model:text-embedding-004}")
    private String embeddingModel;

    @Value("${semantic-cache.embedding.dimensions:768}")
    private Integer embeddingDimensions;

    /**
     * 텍스트를 벡터 임베딩으로 변환
     * Google AI Embeddings API 사용
     */
    public List<Double> generateEmbedding(String text) {
        try {
            // Google AI Embeddings API URL
            String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                embeddingModel, apiKey
            );

            // 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 요청 본문 구성
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> parts = new HashMap<>();
            parts.put("text", text);
            content.put("parts", List.of(parts));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("content", content);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Generating embedding for text length: {} with model: {}", text.length(), embeddingModel);

            // API 호출
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Google Embeddings API returned null response");
            }

            // 응답 파싱
            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> embedding = (Map<String, Object>) responseBody.get("embedding");
            
            if (embedding == null) {
                throw new RuntimeException("No embedding found in API response");
            }

            List<Double> values = (List<Double>) embedding.get("values");
            
            if (values == null || values.isEmpty()) {
                throw new RuntimeException("Empty embedding values in API response");
            }

            log.debug("Generated embedding with {} dimensions", values.size());
            return values;

        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", text.substring(0, Math.min(text.length(), 100)), e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 두 벡터의 코사인 유사도 계산
     */
    public double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size()) {
            throw new IllegalArgumentException(
                String.format("Vector dimensions must match: %d vs %d", vec1.size(), vec2.size())
            );
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            double v1 = vec1.get(i);
            double v2 = vec2.get(i);
            
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        double norm1Sqrt = Math.sqrt(norm1);
        double norm2Sqrt = Math.sqrt(norm2);

        if (norm1Sqrt == 0.0 || norm2Sqrt == 0.0) {
            return 0.0;
        }

        return dotProduct / (norm1Sqrt * norm2Sqrt);
    }

    /**
     * 벡터 리스트를 JSON 문자열로 변환
     */
    public String vectorToJson(List<Double> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("Failed to convert vector to JSON", e);
            throw new RuntimeException("Vector serialization failed", e);
        }
    }

    /**
     * JSON 문자열을 벡터 리스트로 변환
     */
    public List<Double> jsonToVector(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("Failed to parse JSON to vector: {}", json, e);
            throw new RuntimeException("Vector deserialization failed", e);
        }
    }

    /**
     * 여러 텍스트를 배치로 임베딩 생성 (향후 확장용)
     */
    public Map<String, List<Double>> generateEmbeddingsBatch(List<String> texts) {
        Map<String, List<Double>> results = new HashMap<>();
        
        for (String text : texts) {
            try {
                List<Double> embedding = generateEmbedding(text);
                results.put(text, embedding);
            } catch (Exception e) {
                log.warn("Failed to generate embedding for text in batch: {}", 
                    text.substring(0, Math.min(text.length(), 100)), e);
            }
        }
        
        return results;
    }

    /**
     * 임베딩 모델 정보 반환
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * 임베딩 차원 수 반환
     */
    public Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }
}