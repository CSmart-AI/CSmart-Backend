package Capstone.CSmart.global.service.gemini;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.service.circuitbreaker.CircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final MessageRepository messageRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CircuitBreakerService circuitBreakerService;
    
    private static final String GEMINI_CIRCUIT_BREAKER = "gemini-api";

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.url}")
    private String geminiUrl;

    public Map<String, Object> extractUserInfoFromChat(Long studentId) {
        return extractUserInfoFromChatWithRetry(studentId, 3);
    }

    /**
     * 재시도 로직이 포함된 학생 정보 추출
     */
    private Map<String, Object> extractUserInfoFromChatWithRetry(Long studentId, int maxRetries) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
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

                // Gemini API 프롬프트 구성 (14개 질문 형식에 맞춰 추출)
                String prompt = String.format(
                    "다음 대화 기록을 분석해서 학생의 정보를 JSON 형식으로 추출해주세요.\n\n" +
                    "대화 기록:\n%s\n\n" +
                    "학생이 아래 14개 질문에 대해 답변한 형식으로 정보를 추출해주세요:\n" +
                    "1. 성함\n" +
                    "2. 나이\n" +
                    "3. 일반/학사\n" +
                    "4. 전적대/학과/학점은행제\n" +
                    "5. 목표대학/목표학과\n" +
                    "6. 문과/이과/특성화고/예체능/기타\n" +
                    "7. 수능 수학/영어등급 (수능 미응시 시 모의고사등급 or 내신등급)\n" +
                    "8. 수강했던 편입인강 or 학원과 진도\n" +
                    "9. 편입재수, 수능재수 여부\n" +
                    "10. 토익 취득 여부\n" +
                    "11. 알바 유무\n" +
                    "12. 통화 가능 시간\n" +
                    "13. 꼭 하고 싶은 말\n" +
                    "14. 유입경로(인스타/블로그)\n\n" +
                    "다음 형식의 JSON으로 답변해주세요 (맞춤법과 띄어쓰기를 정규화하세요):\n" +
                    "{\n" +
                    "  \"name\": \"학생 이름 (1번 질문 답변, 없으면 null)\",\n" +
                    "  \"age\": 숫자 (2번 질문 답변, 없으면 null),\n" +
                    "  \"type\": \"일반 또는 학사 (3번 질문 답변, 없으면 null)\",\n" +
                    "  \"previousSchool\": \"전적대/학과/학점은행제 (4번 질문 답변, 예: 단국대 천안캠퍼스 수학과, 없으면 null)\",\n" +
                    "  \"targetUniversity\": \"목표대학 (5번 질문 답변, 예: 단국대 죽전, 없으면 null)\",\n" +
                    "  \"desiredMajor\": \"목표학과 (5번 질문 답변, 예: 수학교육과, 없으면 null)\",\n" +
                    "  \"track\": \"문과/이과/특성화고/예체능/기타 (6번 질문 답변, 없으면 null)\",\n" +
                    "  \"mathGrade\": \"수학 등급 (7번 질문 답변, 예: 1등급(선택과목 기하), 없으면 null)\",\n" +
                    "  \"englishGrade\": \"영어 등급 (7번 질문 답변, 예: 3등급, 없으면 null)\",\n" +
                    "  \"previousCourse\": \"수강했던 편입인강 or 학원과 진도 (8번 질문 답변, 예: 장황수학 일변수 미적분, 없으면 null)\",\n" +
                    "  \"isRetaking\": true/false (9번 질문 답변 - 편입재수 여부, 없으면 null),\n" +
                    "  \"isSunungRetaking\": true/false (9번 질문 답변 - 수능재수 여부, 없으면 null),\n" +
                    "  \"hasToeic\": true/false (10번 질문 답변, 없으면 null),\n" +
                    "  \"hasPartTimeJob\": true/false (11번 질문 답변, 없으면 null),\n" +
                    "  \"availableCallTime\": \"통화 가능 시간 (12번 질문 답변, 없으면 null)\",\n" +
                    "  \"message\": \"꼭 하고 싶은 말 (13번 질문 답변, 없으면 null)\",\n" +
                    "  \"source\": \"유입경로 (14번 질문 답변, 예: 인스타, 없으면 null)\"\n" +
                    "}\n\n" +
                    "중요 사항:\n" +
                    "1. 학생이 \"1. 이성재\", \"2. 22\" 같은 형식으로 답변한 경우, 번호를 제거하고 내용만 추출하세요.\n" +
                    "2. 맞춤법과 띄어쓰기를 정규화하세요 (예: \"학 습\" -> \"학습\", \"채찍질해주시기\" -> \"채찍질해 주시기\").\n" +
                    "3. 여러 채팅이 있어도 14개 질문에 대한 답변을 찾아서 추출하세요.\n" +
                    "4. 불리언 값은 \"예\", \"있음\", \"있습니다\" 등은 true, \"아니오\", \"없음\", \"없습니다\" 등은 false로 변환하세요.\n" +
                    "5. 모든 필드명은 정확히 위의 형식과 일치해야 합니다 (대소문자 구분).\n" +
                    "6. 정보를 찾을 수 없는 필드는 반드시 null로 설정해주세요.\n" +
                    "7. age는 숫자만 추출해주세요 (문자열이 아닌 숫자 타입).\n" +
                    "8. JSON만 답변해주세요. 다른 설명은 포함하지 마세요.",
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

                // Gemini API 호출 (Circuit Breaker로 보호)
                String url = geminiUrl + "?key=" + geminiApiKey;
                ResponseEntity<Map> response = circuitBreakerService.execute(
                        GEMINI_CIRCUIT_BREAKER,
                        () -> restTemplate.exchange(url, HttpMethod.POST, request, Map.class)
                );

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
                            Map<String, Object> result = objectMapper.readValue(text, Map.class);
                            
                            // ✅ track 필드를 항상 "이과"로 고정
                            result.put("track", "이과");
                            log.info("track 필드를 '이과'로 고정: studentId={}", studentId);
                            
                            log.info("Gemini API 정보 추출 성공: studentId={}, 추출된 필드 수={}", 
                                    studentId, result.size());
                            return result;
                        }
                    }
                }

                log.warn("Gemini API 응답에서 텍스트를 찾을 수 없음: studentId={}, retry={}/{}", 
                        studentId, retryCount + 1, maxRetries);
                
                // 재시도
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
                return new HashMap<>();

            } catch (HttpClientErrorException e) {
                lastException = e;
                
                // 429 Too Many Requests 에러 처리
                if (e.getStatusCode().value() == 429) {
                    long retryDelaySeconds = extractRetryDelayFromError(e);
                    log.warn("Gemini API Rate Limit 초과: studentId={}, retry={}/{}, {}초 후 재시도 예정", 
                            studentId, retryCount + 1, maxRetries, retryDelaySeconds);
                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            // Retry-After 시간만큼 대기 (최소 5초, 최대 60초)
                            long delayMs = Math.max(5000, Math.min(retryDelaySeconds * 1000, 60000));
                            log.info("Rate Limit 대기 중: studentId={}, {}ms", studentId, delayMs);
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                } else {
                    // 다른 HTTP 에러
                    log.error("Gemini API HTTP 에러: studentId={}, status={}, retry={}/{}", 
                            studentId, e.getStatusCode(), retryCount + 1, maxRetries, e);
                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(1000 * retryCount); // 지수 백오프
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                }
                
            } catch (RestClientException e) {
                lastException = e;
                log.error("Gemini API 호출 중 네트워크 오류: studentId={}, retry={}/{}", 
                        studentId, retryCount + 1, maxRetries, e);
                
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
            } catch (Exception e) {
                lastException = e;
                log.error("Gemini API 호출 중 오류 발생: studentId={}, retry={}/{}", 
                        studentId, retryCount + 1, maxRetries, e);
                
                // 재시도
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            }
        }
        
        // 모든 재시도 실패
        log.error("Gemini API 호출 최종 실패: studentId={}, 총 재시도 횟수={}", 
                studentId, maxRetries, lastException);
        return new HashMap<>();
    }

    /**
     * 첫 메시지를 요약하여 학생 정보 추출
     * 웰컴블록으로 학생정보 요청 메시지를 받은 후 첫 메시지에 대해 요약 수행
     */
    public Map<String, Object> summarizeFirstMessage(String firstMessage) {
        return summarizeFirstMessageWithRetry(firstMessage, 3);
    }

    /**
     * 재시도 로직이 포함된 첫 메시지 요약
     */
    private Map<String, Object> summarizeFirstMessageWithRetry(String firstMessage, int maxRetries) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                log.info("첫 메시지 요약 시작: message length={}, retry={}/{}", 
                        firstMessage.length(), retryCount + 1, maxRetries);

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
                    "  \"phoneNumber\": \"전화번호 (010-1234-5678 형식으로 정규화, 없으면 null)\",\n" +
                    "  \"desiredMajor\": \"희망 전공명 (예: 수학교육과, 컴퓨터공학과 등, 없으면 null)\",\n" +
                    "  \"currentGrade\": \"현재 학년 (예: 1학년, 2학년, 3학년, 4학년 형식으로 통일, 없으면 null)\",\n" +
                    "  \"desiredSemester\": \"희망 입학 학기 (예: 2025년 1학기, 2025년 2학기, 없으면 null)\"\n" +
                    "}\n\n" +
                    "중요 사항:\n" +
                    "1. name 필드는 반드시 추출해주세요. 메시지에서 이름이 언급되었으면 그대로 추출하고, 없으면 null로 설정하세요.\n" +
                    "2. 모든 필드명은 정확히 위의 형식과 일치해야 합니다 (대소문자 구분).\n" +
                    "3. 정보를 찾을 수 없는 필드는 반드시 null로 설정해주세요.\n" +
                    "4. phoneNumber는 010-1234-5678 형식으로 정규화해주세요.\n" +
                    "5. currentGrade는 반드시 '숫자학년' 형식으로 통일해주세요 (예: 1학년, 2학년).\n" +
                    "6. age는 숫자만 추출해주세요 (문자열이 아닌 숫자 타입).\n" +
                    "7. JSON만 답변해주세요. 다른 설명은 포함하지 마세요.",
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

                // Gemini API 호출 (Circuit Breaker로 보호)
                String url = geminiUrl + "?key=" + geminiApiKey;
                ResponseEntity<Map> response = circuitBreakerService.execute(
                        GEMINI_CIRCUIT_BREAKER,
                        () -> restTemplate.exchange(url, HttpMethod.POST, request, Map.class)
                );

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

                log.warn("첫 메시지 요약 실패: Gemini API 응답에서 텍스트를 찾을 수 없음, retry={}/{}", 
                        retryCount + 1, maxRetries);
                
                // 재시도
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
                return new HashMap<>();

            } catch (HttpClientErrorException e) {
                lastException = e;
                
                // 429 Too Many Requests 에러 처리
                if (e.getStatusCode().value() == 429) {
                    long retryDelaySeconds = extractRetryDelayFromError(e);
                    log.warn("Gemini API Rate Limit 초과: retry={}/{}, {}초 후 재시도 예정", 
                            retryCount + 1, maxRetries, retryDelaySeconds);
                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            // Retry-After 시간만큼 대기 (최소 5초, 최대 60초)
                            long delayMs = Math.max(5000, Math.min(retryDelaySeconds * 1000, 60000));
                            log.info("Rate Limit 대기 중: {}ms", delayMs);
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                } else {
                    // 다른 HTTP 에러
                    log.error("Gemini API HTTP 에러: status={}, retry={}/{}", 
                            e.getStatusCode(), retryCount + 1, maxRetries, e);
                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(1000 * retryCount); // 지수 백오프
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                }
                
            } catch (RestClientException e) {
                lastException = e;
                log.error("Gemini API 호출 중 네트워크 오류: retry={}/{}", 
                        retryCount + 1, maxRetries, e);
                
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
            } catch (Exception e) {
                lastException = e;
                log.error("첫 메시지 요약 중 오류 발생: retry={}/{}", 
                        retryCount + 1, maxRetries, e);
                
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            }
        }
        
        // 모든 재시도 실패
        log.error("첫 메시지 요약 최종 실패: 총 재시도 횟수={}", maxRetries, lastException);
        return new HashMap<>();
    }

    /**
     * 429 에러 응답에서 재시도 대기 시간 추출
     * 에러 메시지에서 "Please retry in X.XXs" 패턴을 찾아서 초 단위로 반환
     */
    private long extractRetryDelayFromError(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null) {
                // "Please retry in 44.051894455s" 패턴 찾기
                Pattern pattern = Pattern.compile("Please retry in (\\d+(?:\\.\\d+)?)s");
                Matcher matcher = pattern.matcher(responseBody);
                if (matcher.find()) {
                    double seconds = Double.parseDouble(matcher.group(1));
                    return (long) Math.ceil(seconds);
                }
            }
        } catch (Exception ex) {
            log.warn("Retry delay 추출 실패", ex);
        }
        
        // 기본값: 45초 (에러 메시지에서 추출 실패 시)
        return 45L;
    }

    /**
     * Gemini API로 간단한 채팅 응답 생성 (Transfer 전 상담용)
     * @param question 학생 질문
     * @return AI 응답 텍스트
     */
    public String generateChatResponse(String question) {
        try {
            // Gemini API 프롬프트 구성
            String prompt = String.format(
                "다음은 편입 상담을 받는 학생의 질문입니다. 친절하고 간단하게 답변해주세요.\n\n" +
                "질문: %s\n\n" +
                "답변:",
                question
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

            // Gemini API 호출 (Circuit Breaker로 보호)
            String url = geminiUrl + "?key=" + geminiApiKey;
            ResponseEntity<Map> response = circuitBreakerService.execute(
                    GEMINI_CIRCUIT_BREAKER,
                    () -> restTemplate.exchange(url, HttpMethod.POST, request, Map.class)
            );

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
                        log.info("Gemini 채팅 응답 생성 완료: 응답 길이={}", text.length());
                        return text.trim();
                    }
                }
            }

            log.warn("Gemini API 응답에서 텍스트를 찾을 수 없음");
            return "죄송합니다. 응답을 생성하는데 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";

        } catch (Exception e) {
            log.error("Gemini 채팅 응답 생성 실패: {}", e.getMessage(), e);
            return "죄송합니다. 응답을 생성하는데 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
    }
}

