package Capstone.CSmart.global.service.ai;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import Capstone.CSmart.global.domain.enums.ChannelType;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.cache.SemanticCacheService;
import Capstone.CSmart.global.service.confidence.ConfidenceScoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiResponseService {

    private final AiResponseRepository aiResponseRepository;
    private final MessageRepository messageRepository;
    private final StudentRepository studentRepository;
    private final SemanticCacheService semanticCacheService;
    private final ConfidenceScoreService confidenceScoreService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${langgraph.url}")
    private String langGraphUrl;

    @Value("${kakao.webhook.url.admin}")
    private String adminWebhookUrl;

    @Value("${kakao.webhook.url.teacher}")
    private String teacherWebhookUrl;

    /**
     * 상담폼인지 확인 (긴 메시지, 번호 목록 등)
     */
    private boolean isConsultationForm(String content) {
        if (content == null) return false;

        // 200자 이상 또는 줄바꿈 5개 이상
        boolean isLong = content.length() > 200 || content.split("\n").length > 5;

        // 번호 목록 (1. 2. 3. 패턴)
        boolean hasNumberedList = content.contains("1.") && content.contains("2.") && content.contains("3.");

        return isLong || hasNumberedList;
    }

    @Transactional
    public AiResponse generateResponse(Long messageId) {
        // 이미 AI 응답이 있는지 확인 (가장 최근 것만)
        Optional<AiResponse> existingResponse = aiResponseRepository.findTopByMessageIdOrderByGeneratedAtDesc(messageId);
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

            // ✅ 상담폼은 AI 응답 생성하지 않음
            if (isConsultationForm(message.getContent())) {
                log.info("상담폼 메시지 감지, AI 응답 생성 건너뜀: messageId={}, content length={}",
                        messageId, message.getContent().length());
                throw new RuntimeException("상담폼은 AI 응답을 생성하지 않습니다");
            }

            // 🆕 1. 시멘틱 캐시에서 유사한 답변 검색
            Optional<SemanticCache> cachedAnswer = semanticCacheService.findSimilarAnswer(message.getContent());

            if (cachedAnswer.isPresent()) {
                log.info("✅ 캐시 히트! LangGraph 호출 생략. messageId={}, cacheId={}",
                    messageId, cachedAnswer.get().getCacheId());
                log.info("📝 현재 메시지: {}", message.getContent());
                log.info("💾 캐시된 질문: {}", cachedAnswer.get().getQuestion());

                // 캐시된 답변으로 AiResponse 생성
                AiResponse aiResponse = AiResponse.builder()
                    .messageId(messageId)
                    .studentId(message.getStudentId())
                    .teacherId(student.getAssignedTeacherId())
                    .recommendedResponse(cachedAnswer.get().getAnswer())
                    .status(AiResponseStatus.PENDING_REVIEW)
                    .generatedAt(OffsetDateTime.now())
                    .build();

                AiResponse savedResponse = aiResponseRepository.save(aiResponse);
                log.info("캐시 기반 AI Response 생성 완료: responseId={}", savedResponse.getResponseId());
                return savedResponse;
            }

            // 🆕 2. 캐시 미스 → LangGraph 호출
            log.info("❌ 캐시 미스. LangGraph 호출 필요. messageId={}", messageId);

            // ✅ 개별 메시지 처리 (메시지 결합 제거)
            String question = message.getContent();
            log.info("개별 메시지 처리: messageId={}, question length={}", messageId, question.length());

            // LangGraph 요청 데이터 구성
            Map<String, Object> langGraphRequest = new HashMap<>();
            langGraphRequest.put("question", question);

            // 학생 프로필 구성
            Map<String, String> studentProfile = new HashMap<>();
            studentProfile.put("target_university", student.getTargetUniversity() != null ? student.getTargetUniversity() : "미지정");
            studentProfile.put("track", "계열 미지정");
            langGraphRequest.put("student_profile", studentProfile);

            log.info("LangGraph 요청 구성: question length={}", question.length());

            // LangGraph API 호출
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(langGraphRequest, headers);

            String langGraphEndpoint = langGraphUrl + "/api/chat";
            log.info("Calling LangGraph API: {}", langGraphEndpoint);

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

            // ✅ 무의미한 답변 필터링 (너무 짧은 답변만)
            if (finalAnswer.trim().length() < 10) {
                log.warn("너무 짧은 AI 응답, 저장하지 않음: messageId={}, answer={}", messageId, finalAnswer);
                throw new RuntimeException("AI가 너무 짧은 답변을 생성했습니다");
            }

            log.info("LangGraph 응답 생성 완료: messageId={}, response length={}",
                    messageId, finalAnswer.length());

            // AiResponse 엔티티 생성 및 저장
            AiResponse aiResponse = AiResponse.builder()
                    .messageId(messageId)
                    .studentId(message.getStudentId())
                    .teacherId(student.getAssignedTeacherId())
                    .recommendedResponse(finalAnswer)
                    .status(AiResponseStatus.PENDING_REVIEW)
                    .generatedAt(OffsetDateTime.now())
                    .build();

            AiResponse savedResponse = aiResponseRepository.save(aiResponse);
            log.info("AI Response saved: responseId={}", savedResponse.getResponseId());

            // 🆕 3. 새로 생성된 응답을 캐시에 저장
            try {
                semanticCacheService.saveToCache(
                    question,
                    finalAnswer,
                    savedResponse.getResponseId(),
                    0.5 // 기본 신뢰도
                );
                log.info("캐시 저장 완료: responseId={}", savedResponse.getResponseId());
            } catch (Exception cacheError) {
                log.warn("캐시 저장 실패 (서비스는 정상 진행): {}", cacheError.getMessage());
            }

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

            // 최종 응답 설정
            aiResponse.setFinalResponse(aiResponse.getRecommendedResponse());

            // 전송 성공 후 상태 업데이트
            aiResponse.setStatus(AiResponseStatus.SENT);
            aiResponse.setReviewedAt(OffsetDateTime.now());
            aiResponse.setSentAt(OffsetDateTime.now());

            aiResponseRepository.save(aiResponse);

            // 승인 후 캐시 신뢰도 업데이트
            try {
                double newConfidenceScore = confidenceScoreService.calculateConfidenceScore(aiResponse);

                semanticCacheService.getCacheRepository()
                    .findByOriginalResponseId(responseId)
                    .ifPresent(cache -> {
                        cache.setConfidenceScore(newConfidenceScore);
                        semanticCacheService.getCacheRepository().save(cache);
                        log.info("✅ 승인 후 캐시 신뢰도 업데이트: responseId={}, 신뢰도: {}",
                            responseId, newConfidenceScore);
                    });

            } catch (Exception e) {
                log.warn("승인 후 캐시 신뢰도 업데이트 실패: responseId={}", responseId, e);
            }

            log.info("AI Response {} approved and marked as SENT", responseId);

        } catch (Exception e) {
            log.error("Failed to approve AI Response: {}", responseId, e);
            throw new RuntimeException("Failed to approve and mark as sent: " + e.getMessage());
        }
    }

    @Transactional
    public void editAndSend(Long responseId, String editedContent) {
        try {
            AiResponse aiResponse = aiResponseRepository.findById(responseId)
                    .orElseThrow(() -> new RuntimeException("AI Response not found: " + responseId));

            // 수정된 내용으로 최종 응답 설정
            aiResponse.setFinalResponse(editedContent);

            // 전송 성공 후 상태 업데이트
            aiResponse.setStatus(AiResponseStatus.SENT);
            aiResponse.setReviewedAt(OffsetDateTime.now());
            aiResponse.setSentAt(OffsetDateTime.now());

            aiResponseRepository.save(aiResponse);

            // 수정 및 승인 후 캐시 신뢰도 업데이트
            try {
                double newConfidenceScore = confidenceScoreService.calculateConfidenceScore(aiResponse);

                semanticCacheService.getCacheRepository()
                    .findByOriginalResponseId(responseId)
                    .ifPresent(cache -> {
                        cache.setConfidenceScore(newConfidenceScore);
                        cache.setAnswer(editedContent);
                        semanticCacheService.getCacheRepository().save(cache);
                        log.info("✅ 수정 후 캐시 업데이트: responseId={}, 신뢰도: {}",
                            responseId, newConfidenceScore);
                    });

            } catch (Exception e) {
                log.warn("수정 후 캐시 업데이트 실패: responseId={}", responseId, e);
            }

            log.info("AI Response {} edited and marked as SENT", responseId);

        } catch (Exception e) {
            log.error("Failed to edit AI Response: {}", responseId, e);
            throw new RuntimeException("Failed to edit and mark as sent: " + e.getMessage());
        }
    }

    private void sendToKakao(AiResponse aiResponse) {
        try {
            // 학생 정보 조회
            Student student = studentRepository.findById(aiResponse.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // 학생에게 선생님이 배정되어 있으면 TEACHER 채널, 아니면 ADMIN 채널 사용
            ChannelType channelType = (student.getAssignedTeacherId() != null)
                    ? ChannelType.TEACHER
                    : ChannelType.ADMIN;

            String webhookUrl = getWebhookUrlByChannelType(channelType);

            // 카카오톡 웹훅 API 호출
            Map<String, Object> kakaoRequest = new HashMap<>();
            kakaoRequest.put("recipient", student.getName() != null ? student.getName() : "학생");
            kakaoRequest.put("message", aiResponse.getFinalResponse());
            kakaoRequest.put("messageType", "text");
            kakaoRequest.put("chatId", "student-" + student.getStudentId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(kakaoRequest, headers);

            String kakaoEndpoint = webhookUrl + "/api/message/send";
            log.info("Sending message to Kakao channel: {}, url: {}", channelType, kakaoEndpoint);

            ResponseEntity<String> response = restTemplate.exchange(
                    kakaoEndpoint, HttpMethod.POST, request, String.class);

            log.info("Message sent to Kakao channel {}: {}", channelType, response.getBody());

        } catch (Exception e) {
            log.error("Failed to send message to Kakao", e);
            throw new RuntimeException("Failed to send to Kakao: " + e.getMessage());
        }
    }

    /**
     * ChannelType에 맞는 웹훅 서버 URL 반환
     */
    private String getWebhookUrlByChannelType(ChannelType channelType) {
        return switch (channelType) {
            case ADMIN -> adminWebhookUrl;
            case TEACHER -> teacherWebhookUrl;
        };
    }
}
