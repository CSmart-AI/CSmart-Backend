package Capstone.CSmart.global.service.ai;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.SemanticCache;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import Capstone.CSmart.global.repository.AiResponseRepository;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.cache.SemanticCacheService;
import Capstone.CSmart.global.service.circuitbreaker.CircuitBreakerService;
import Capstone.CSmart.global.service.confidence.ConfidenceScoreService;
import Capstone.CSmart.global.service.gemini.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiResponseService {

    private final AiResponseRepository aiResponseRepository;
    private final MessageRepository messageRepository;
    private final StudentRepository studentRepository;
    private final SemanticCacheService semanticCacheService;
    private final ConfidenceScoreService confidenceScoreService;
    private final GeminiService geminiService;
    private final CircuitBreakerService circuitBreakerService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${langgraph.url}")
    private String langGraphUrl;
    
    private static final String AI_PROCESSING_LOCK_PREFIX = "ai_processing_lock:";
    private static final long LOCK_TTL_SECONDS = 300; // 5분 (AI 응답 생성 최대 시간)
    private static final String LANGGRAPH_CIRCUIT_BREAKER = "langgraph-api";

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

        // Redis 분산 락으로 중복 호출 방지
        String lockKey = AI_PROCESSING_LOCK_PREFIX + messageId;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "processing", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.FALSE.equals(lockAcquired)) {
            // 이미 처리 중인 경우, 잠시 대기 후 기존 응답 확인
            log.warn("AI Response generation already in progress for messageId: {}, waiting...", messageId);
            
            // 최대 3초 대기 (다른 요청이 완료될 때까지)
            for (int i = 0; i < 30; i++) {
                try {
                    Thread.sleep(100); // 100ms 대기
                    Optional<AiResponse> response = aiResponseRepository.findTopByMessageIdOrderByGeneratedAtDesc(messageId);
                    if (response.isPresent()) {
                        log.info("AI Response created by another request for messageId: {}", messageId);
                        return response.get();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // 대기 후에도 없으면 예외 발생
            throw new RuntimeException("AI Response generation is already in progress for messageId: " + messageId);
        }

        try {
            // 락 획득 후 다시 한 번 기존 응답 확인 (락 획득 전과 후 사이에 다른 요청이 완료되었을 수 있음)
            Optional<AiResponse> doubleCheckResponse = aiResponseRepository.findTopByMessageIdOrderByGeneratedAtDesc(messageId);
            if (doubleCheckResponse.isPresent()) {
                log.info("AI Response already exists (double check) for messageId: {}", messageId);
                return doubleCheckResponse.get();
            }
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

            // ✅ Transfer 전/후 구분 및 메시지 생성 시점 확인
            String registrationStatus = student.getRegistrationStatus();
            Long assignedTeacherId = student.getAssignedTeacherId();
            
            // Transfer 여부 확인: registrationStatus가 "TRANSFERRED_TO_TEACHER"이고 assignedTeacherId가 null이 아닌 경우만 Transfer 후로 간주
            boolean isTransferred = "TRANSFERRED_TO_TEACHER".equals(registrationStatus) 
                    && assignedTeacherId != null;
            
            // 메시지가 Transfer 이후에 생성된 메시지인지 확인
            // Transfer 시점은 student.getUpdatedAt()으로 확인 (Transfer 시 updatedAt이 갱신됨)
            // LocalDateTime을 OffsetDateTime으로 변환하여 비교
            boolean isMessageAfterTransfer = false;
            if (isTransferred && message.getSentAt() != null && student.getUpdatedAt() != null) {
                // LocalDateTime을 OffsetDateTime으로 변환 (시스템 기본 시간대 사용)
                java.time.OffsetDateTime transferTime = student.getUpdatedAt()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toOffsetDateTime();
                // 메시지가 Transfer 이후에 보낸 메시지인지 확인 (Transfer 시점 이후 1초 여유를 둠)
                isMessageAfterTransfer = message.getSentAt().isAfter(transferTime.minusSeconds(1));
            }
            
            log.info("학생 상태 확인: studentId={}, registrationStatus={}, assignedTeacherId={}, isTransferred={}, messageSentAt={}, studentUpdatedAt={}, isMessageAfterTransfer={}", 
                    student.getStudentId(), registrationStatus, assignedTeacherId, isTransferred, 
                    message.getSentAt(), student.getUpdatedAt(), isMessageAfterTransfer);
            
            // Transfer 전이거나, Transfer 이후지만 기존 메시지(Transfer 이전에 보낸 메시지)인 경우 Gemini만 사용
            if (!isTransferred || !isMessageAfterTransfer) {
                // Transfer 전 또는 Transfer 이전에 보낸 기존 메시지: Gemini API로만 응답 생성 (LangGraph 사용 안 함)
                log.info("✅ Transfer 전 또는 기존 메시지: Gemini API로만 응답 생성 (LangGraph 호출 안 함). messageId={}, studentId={}, registrationStatus={}, isTransferred={}, isMessageAfterTransfer={}", 
                        messageId, student.getStudentId(), registrationStatus, isTransferred, isMessageAfterTransfer);
                return generateResponseWithGemini(message, student);
            }

            // Transfer 이후에 생성된 새로운 메시지만 LangGraph 사용
            log.info("✅ Transfer 이후 새로운 메시지: LangGraph로 응답 생성. messageId={}, studentId={}, teacherId={}, messageSentAt={}", 
                    messageId, student.getStudentId(), assignedTeacherId, message.getSentAt());

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

            // LangGraph API 호출 (Circuit Breaker로 보호)
            ResponseEntity<Map> response = circuitBreakerService.execute(
                    LANGGRAPH_CIRCUIT_BREAKER,
                    () -> restTemplate.exchange(langGraphEndpoint, HttpMethod.POST, request, Map.class)
            );

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
        } finally {
            // 처리 완료 후 락 해제
            redisTemplate.delete(lockKey);
            log.debug("AI processing lock released for messageId: {}", messageId);
        }
    }

    /**
     * Transfer 전: Gemini API로 간단한 상담 응답 생성
     */
    private AiResponse generateResponseWithGemini(Message message, Student student) {
        try {
            String question = message.getContent();
            log.info("Gemini API로 상담 응답 생성: messageId={}, question length={}", 
                    message.getMessageId(), question.length());

            // Gemini API 호출
            String geminiAnswer = geminiService.generateChatResponse(question);

            if (geminiAnswer == null || geminiAnswer.trim().isEmpty()) {
                throw new RuntimeException("Gemini API가 빈 응답을 반환했습니다");
            }

            // AiResponse 엔티티 생성 및 저장
            AiResponse aiResponse = AiResponse.builder()
                    .messageId(message.getMessageId())
                    .studentId(message.getStudentId())
                    .teacherId(null) // Transfer 전에는 선생님 배정 안 됨
                    .recommendedResponse(geminiAnswer)
                    .status(AiResponseStatus.PENDING_REVIEW)
                    .generatedAt(OffsetDateTime.now())
                    .build();

            AiResponse savedResponse = aiResponseRepository.save(aiResponse);
            log.info("Gemini 기반 AI Response 생성 완료: responseId={}, answer length={}", 
                    savedResponse.getResponseId(), geminiAnswer.length());

            return savedResponse;

        } catch (Exception e) {
            log.error("Gemini 응답 생성 실패: messageId={}, error: {}", 
                    message.getMessageId(), e.getMessage(), e);
            throw new RuntimeException("Gemini 응답 생성 실패: " + e.getMessage(), e);
        }
    }

    public List<AiResponse> getPendingResponsesForTeacher(Long teacherId) {
        List<AiResponse> allResponses = aiResponseRepository.findByTeacherIdAndStatus(teacherId, AiResponseStatus.PENDING_REVIEW);
        
        // 같은 messageId에 대해 여러 AiResponse가 있으면, 가장 최신 것만 반환
        return allResponses.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    AiResponse::getMessageId,
                    java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        list -> list.stream()
                            .max(Comparator.comparing(AiResponse::getGeneratedAt))
                            .orElse(null)
                    )
                ))
                .values()
                .stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AiResponse::getGeneratedAt).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<AiResponse> getAllPendingResponses() {
        List<AiResponse> allResponses = aiResponseRepository.findByStatusOrderByGeneratedAtDesc(AiResponseStatus.PENDING_REVIEW);
        
        // 같은 messageId에 대해 여러 AiResponse가 있으면, 가장 최신 것만 반환
        return allResponses.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    AiResponse::getMessageId,
                    java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        list -> list.stream()
                            .max(Comparator.comparing(AiResponse::getGeneratedAt))
                            .orElse(null)
                    )
                ))
                .values()
                .stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AiResponse::getGeneratedAt).reversed())
                .collect(java.util.stream.Collectors.toList());
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
}
