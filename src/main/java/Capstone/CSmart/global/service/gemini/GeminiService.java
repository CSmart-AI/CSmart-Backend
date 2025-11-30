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
                        
                        // 마크다운 코드 블록 제거 (```json ... ``` 형식)
                        text = text.trim();
                        if (text.startsWith("```")) {
                            // 첫 번째 ``` 제거
                            int startIndex = text.indexOf("```");
                            if (startIndex != -1) {
                                text = text.substring(startIndex + 3);
                                // 언어 지정자 제거 (json, JSON 등)
                                text = text.replaceFirst("^(json|JSON)\\s*", "");
                            }
                            // 마지막 ``` 제거
                            int endIndex = text.lastIndexOf("```");
                            if (endIndex != -1) {
                                text = text.substring(0, endIndex);
                            }
                            text = text.trim();
                        }
                        
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

    /**
     * 첫 메시지를 요약하여 학생 정보 추출
     * 웰컴블록으로 학생정보 요청 메시지를 받은 후 첫 메시지에 대해 요약 수행
     */
    public Map<String, Object> summarizeFirstMessage(String firstMessage) {
        try {
            log.info("첫 메시지 요약 시작: message length={}", firstMessage.length());

            // Gemini API 프롬프트 구성
            // Student 엔티티의 실제 필드와 정확히 매칭되는 JSON 형식으로 요청
            String prompt = String.format(
                    "다음은 학생이 처음 보낸 메시지입니다. 이 메시지를 분석해서 학생의 기본 정보를 JSON 형식으로 추출해주세요.\n\n" +
                    "첫 메시지:\n%s\n\n" +
                    "다음 형식의 JSON으로 답변해주세요 (Student 엔티티 필드와 정확히 일치해야 합니다):\n" +
                    "{\n" +
                    "  \"name\": \"학생 이름 (반드시 추출 - 메시지에서 이름을 찾아주세요. 없으면 null)\",\n" +
                    "  \"age\": 숫자 (나이를 숫자로만 추출, 없으면 null),\n" +
                    "  \"previousSchool\": \"전적대학교명 (예: 서울대학교, 단국대학교 등, 없으면 null)\",\n" +
                    "  \"targetUniversity\": \"목표대학교명 (예: 서울대학교, 연세대학교 등, 없으면 null)\",\n" +
                    "  \"phoneNumber\": \"전화번호 (010-1234-5678 형식, 없으면 null)\",\n" +
                    "  \"desiredMajor\": \"희망 전공명 (예: 수학교육과, 컴퓨터공학과 등, 없으면 null)\",\n" +
                    "  \"currentGrade\": \"현재 학년 (예: 1학년, 2학년, 3학년, 4학년, 없으면 null)\",\n" +
                    "  \"desiredSemester\": \"희망 입학 학기 (예: 2025년 1학기, 2025년 2학기, 없으면 null)\"\n" +
                    "}\n\n" +
                    "중요 사항:\n" +
                    "1. name 필드는 반드시 추출해주세요. 메시지에서 이름이 언급되었으면 그대로 추출하고, 없으면 null로 설정하세요.\n" +
                    "2. 모든 필드명은 정확히 위의 형식과 일치해야 합니다 (대소문자 구분).\n" +
                    "3. 정보를 찾을 수 없는 필드는 반드시 null로 설정해주세요.\n" +
                    "4. JSON만 답변해주세요. 다른 설명은 포함하지 마세요.",
                    firstMessage
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
                        log.info("첫 메시지 요약 결과: {}", text);
                        
                        // 마크다운 코드 블록 제거 (```json ... ``` 형식)
                        text = text.trim();
                        if (text.startsWith("```")) {
                            int startIndex = text.indexOf("```");
                            if (startIndex != -1) {
                                text = text.substring(startIndex + 3);
                                text = text.replaceFirst("^(json|JSON)\\s*", "");
                            }
                            int endIndex = text.lastIndexOf("```");
                            if (endIndex != -1) {
                                text = text.substring(0, endIndex);
                            }
                            text = text.trim();
                        }
                        
                        // JSON 파싱
                        Map<String, Object> result = objectMapper.readValue(text, Map.class);
                        log.info("첫 메시지 요약 완료: 추출된 필드 수={}", result.size());
                        return result;
                    }
                }
            }

            log.warn("첫 메시지 요약 실패: Gemini API 응답에서 텍스트를 찾을 수 없음");
            return new HashMap<>();

        } catch (Exception e) {
            log.error("첫 메시지 요약 중 오류 발생", e);
            return new HashMap<>();
        }
    }
}






