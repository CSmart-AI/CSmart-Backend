package Capstone.CSmart.global.service.gemini;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final MessageRepository messageRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.url}")
    private String geminiUrl;

    public Map<String, Object> extractUserInfoFromChat(Long studentId) {
        try {
            // 최근 채팅 기록 조회 (최대 20개)
            List<Message> recentMessages = messageRepository
                    .findByStudentIdOrderBySentAtDesc(studentId, PageRequest.of(0, 20));

            if (recentMessages.isEmpty()) {
                log.warn("Student {} has no messages", studentId);
                return new HashMap<>();
            }

            // 채팅 기록을 문자열로 변환
            StringBuilder chatHistory = new StringBuilder();
            for (Message message : recentMessages) {
                chatHistory.append(message.getSenderType())
                        .append(": ")
                        .append(message.getContent())
                        .append("\n");
            }

            // Gemini API 프롬프트 구성
            String prompt = String.format(
                    "다음 대화 기록을 분석해서 학생의 정보를 JSON 형식으로 추출해주세요.\n\n" +
                    "대화 기록:\n%s\n\n" +
                    "다음 형식의 JSON으로 답변해주세요:\n" +
                    "{\n" +
                    "  \"name\": \"학생 이름\",\n" +
                    "  \"age\": 숫자,\n" +
                    "  \"previousSchool\": \"전적대학교\",\n" +
                    "  \"targetUniversity\": \"목표대학교\",\n" +
                    "  \"phoneNumber\": \"전화번호\",\n" +
                    "  \"track\": \"문과/이과/특성화고/예체능/기타\",\n" +
                    "  \"consultationData\": \"상담에서 언급된 기타 정보\"\n" +
                    "}\n\n" +
                    "정보를 찾을 수 없는 필드는 null로 설정해주세요. JSON만 답변해주세요.",
                    chatHistory.toString()
            );

            // Gemini API 요청 구성
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", geminiApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Gemini API 호출
            String url = geminiUrl + "?key=" + geminiApiKey;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            // 응답 파싱
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> content2 = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content2.get("parts");
                    
                    if (!parts.isEmpty()) {
                        String text = (String) parts.get(0).get("text");
                        log.info("Gemini API 응답: {}", text);
                        
                        // JSON 파싱
                        return objectMapper.readValue(text, Map.class);
                    }
                }
            }

            log.warn("Gemini API 응답에서 텍스트를 찾을 수 없음");
            return new HashMap<>();

        } catch (Exception e) {
            log.error("Gemini API 호출 중 오류 발생", e);
            return new HashMap<>();
        }
    }
}






