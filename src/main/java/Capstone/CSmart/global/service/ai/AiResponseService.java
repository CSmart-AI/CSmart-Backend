package Capstone.CSmart.global.service.ai;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiResponseService {

    private final AiResponseRepository aiResponseRepository;
    private final MessageRepository messageRepository;
    private final StudentRepository studentRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${langgraph.url}")
    private String langGraphUrl;

    @Value("${kakao.webhook.url}")
    private String kakaoWebhookUrl;

    @Transactional
    public AiResponse generateResponse(Long messageId) {
        // 이미 AI 응답이 있는지 확인
        Optional<AiResponse> existingResponse = aiResponseRepository.findByMessageId(messageId);
        if (existingResponse.isPresent()) {
            log.info("AI Response already exists for messageId: {}", messageId);
            return existingResponse.get();
        }

        try {
            // 메시지 조회
            final Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

            // 학생 정보 조회
            final Student student = studentRepository.findById(message.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found: " + message.getStudentId()));

            // 최근 대화 이력 수집 (최대 10개)
            List<Message> recentMessages = messageRepository
                    .findByStudentIdOrderBySentAtDesc(message.getStudentId(), PageRequest.of(0, 10));

            // LangGraph 요청 데이터 구성
            Map<String, Object> langGraphRequest = new HashMap<>();
            langGraphRequest.put("question", message.getContent());

            // 학생 프로필 구성
            Map<String, String> studentProfile = new HashMap<>();
            studentProfile.put("target_university", student.getTargetUniversity() != null ? student.getTargetUniversity() : "미지정");
            studentProfile.put("track", "계열 미지정"); // 기본값
            langGraphRequest.put("student_profile", studentProfile);

            // 최근 대화 이력 구성
            List<Map<String, String>> recentDialogues = new ArrayList<>();
            for (Message msg : recentMessages) {
                Map<String, String> dialogue = new HashMap<>();
                dialogue.put("role", msg.getSenderType());
                dialogue.put("message", msg.getContent());
                recentDialogues.add(dialogue);
            }
            langGraphRequest.put("recent_dialogues", recentDialogues);

            // LangGraph API 호출
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(langGraphRequest, headers);

            String langGraphEndpoint = langGraphUrl + "/api/chat";
            log.info("Calling LangGraph API: {} for messageId: {}", langGraphEndpoint, messageId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    langGraphEndpoint, HttpMethod.POST, request, Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("LangGraph API returned null response");
            }

            // 응답 파싱
            String finalAnswer = (String) responseBody.get("final_answer");
            
            if (finalAnswer == null || finalAnswer.trim().isEmpty()) {
                throw new RuntimeException("LangGraph API returned empty answer");
            }

            log.info("LangGraph 응답 생성 완료: messageId={}, response length={}", 
                    messageId, finalAnswer.length());

            // AiResponse 엔티티 생성 및 저장
            AiResponse aiResponse = AiResponse.builder()
                    .messageId(messageId)
                    .studentId(message.getStudentId())
                    .teacherId(student.getAssignedTeacherId())
                    .recommendedResponse(finalAnswer)
                    .status(AiResponseStatus.PENDING_REVIEW) // 모든 응답은 검수 필요
                    .generatedAt(OffsetDateTime.now())
                    .build();

            AiResponse savedResponse = aiResponseRepository.save(aiResponse);
            log.info("AI Response saved with ID: {} for messageId: {}", savedResponse.getResponseId(), messageId);

            return savedResponse;

        } catch (Exception e) {
            log.error("AI Response generation failed for messageId: {}, error: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("AI Response generation failed: " + e.getMessage(), e);
        }
    }

    public List<AiResponse> getPendingResponsesForTeacher(Long teacherId) {
        return aiResponseRepository.findByTeacherIdAndStatus(teacherId, AiResponseStatus.PENDING_REVIEW);
    }

    public List<AiResponse> getAllPendingResponses() {
        return aiResponseRepository.findByStatusOrderByGeneratedAtDesc(AiResponseStatus.PENDING_REVIEW);
    }

    @Transactional
    public void approveAndSend(Long responseId) {
        try {
            AiResponse aiResponse = aiResponseRepository.findById(responseId)
                    .orElseThrow(() -> new RuntimeException("AI Response not found: " + responseId));

            // 상태 업데이트
            aiResponse.setStatus(AiResponseStatus.APPROVED);
            aiResponse.setReviewedAt(OffsetDateTime.now());
            aiResponse.setFinalResponse(aiResponse.getRecommendedResponse());

            aiResponseRepository.save(aiResponse);

            // 카카오톡 웹훅으로 메시지 전송
            sendToKakao(aiResponse);

            // 전송 완료 상태로 업데이트
            aiResponse.setStatus(AiResponseStatus.SENT);
            aiResponse.setSentAt(OffsetDateTime.now());
            aiResponseRepository.save(aiResponse);

            log.info("AI Response {} approved and sent", responseId);

        } catch (Exception e) {
            log.error("Failed to approve and send AI Response: {}", responseId, e);
            throw new RuntimeException("Failed to approve and send: " + e.getMessage());
        }
    }

    @Transactional
    public void editAndSend(Long responseId, String editedContent) {
        try {
            AiResponse aiResponse = aiResponseRepository.findById(responseId)
                    .orElseThrow(() -> new RuntimeException("AI Response not found: " + responseId));

            // 수정된 내용으로 업데이트
            aiResponse.setStatus(AiResponseStatus.APPROVED);
            aiResponse.setReviewedAt(OffsetDateTime.now());
            aiResponse.setFinalResponse(editedContent);

            aiResponseRepository.save(aiResponse);

            // 카카오톡 웹훅으로 메시지 전송
            sendToKakao(aiResponse);

            // 전송 완료 상태로 업데이트
            aiResponse.setStatus(AiResponseStatus.SENT);
            aiResponse.setSentAt(OffsetDateTime.now());
            aiResponseRepository.save(aiResponse);

            log.info("AI Response {} edited and sent", responseId);

        } catch (Exception e) {
            log.error("Failed to edit and send AI Response: {}", responseId, e);
            throw new RuntimeException("Failed to edit and send: " + e.getMessage());
        }
    }

    private void sendToKakao(AiResponse aiResponse) {
        try {
            // 학생 정보 조회
            Student student = studentRepository.findById(aiResponse.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // 카카오톡 웹훅 API 호출
            Map<String, Object> kakaoRequest = new HashMap<>();
            kakaoRequest.put("recipient", student.getName() != null ? student.getName() : "학생");
            kakaoRequest.put("message", aiResponse.getFinalResponse());
            kakaoRequest.put("messageType", "text");
            kakaoRequest.put("chatId", "student-" + student.getStudentId()); // 임시 chatId

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(kakaoRequest, headers);

            String kakaoEndpoint = kakaoWebhookUrl + "/api/message/send";
            ResponseEntity<String> response = restTemplate.exchange(
                    kakaoEndpoint, HttpMethod.POST, request, String.class);

            log.info("Message sent to Kakao: {}", response.getBody());

        } catch (Exception e) {
            log.error("Failed to send message to Kakao", e);
            throw new RuntimeException("Failed to send to Kakao: " + e.getMessage());
        }
    }
}
