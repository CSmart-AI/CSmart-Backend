package Capstone.CSmart.global.service.kakao;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.StudentStatus;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import Capstone.CSmart.global.web.dto.Kakao.KakaoMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오톡 웹훅 서비스
 * 메시지를 Message 엔티티에 저장만 함
 * AI 응답은 AiScheduler가 배치로 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoWebhookService {

    private final StudentRepository studentRepository;
    private final MessageRepository messageRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 수신한 메시지를 비동기로 처리합니다.
     * Message 엔티티에 저장만 하고, AI 응답은 스케줄러가 처리
     */
    @Async
    @Transactional
    public void handleIncomingMessage(KakaoMessageDTO message) {
        log.info("메시지 수신: channelType={}, userId={}, message={}", 
                 message.getChannelType(), message.getUserId(), message.getUtterance());
        
        try {
            // 1. 학생 조회 또는 생성 (동시성 문제 해결)
            Student student = studentRepository.findByKakaoUserId(message.getUserId())
                    .orElseGet(() -> {
                        log.info("새로운 학생 생성 시도: kakaoUserId={}", message.getUserId());
                        try {
                            Student newStudent = Student.builder()
                                    .kakaoUserId(message.getUserId())
                                    .name(message.getUserName())
                                    .kakaoChannelId(message.getBotId())
                                    .registrationStatus("CHATTING") // 초기 상태
                                    .status(StudentStatus.INITIAL)
                                    .build();
                            return studentRepository.save(newStudent);
                        } catch (DataIntegrityViolationException e) {
                            // 동시 요청으로 인한 중복 생성 시도 시, 다시 조회
                            log.warn("학생 생성 중 중복 감지, 재조회: kakaoUserId={}", message.getUserId());
                            return studentRepository.findByKakaoUserId(message.getUserId())
                                    .orElseThrow(() -> new RuntimeException("학생 생성 및 조회 실패: " + message.getUserId()));
                        }
                    });

            // 2. Message 엔티티에 저장 (AI 응답은 스케줄러가 처리)
            Message savedMessage = Message.builder()
                    .studentId(student.getStudentId())
                    .content(message.getUtterance())
                    .senderType("student") // AiScheduler가 "student"로 필터링함
                    .messageType("TEXT")
                    .sentAt(java.time.OffsetDateTime.now()) // 전송 시간 설정 (필수!)
                    .build();
            
            messageRepository.save(savedMessage);
            
            log.info("메시지 저장 완료: studentId={}, messageId={}", 
                     student.getStudentId(), savedMessage.getMessageId());

            // 3. 첫 메시지인지 확인 (웰컴블록으로 학생정보 요청 후 첫 응답)
            long messageCount = messageRepository.countByStudentIdAndSenderType(
                    student.getStudentId(), "student");
            
            if (messageCount == 1) {
                log.info("첫 메시지 감지: studentId={}, 첫 메시지 요약 시작", student.getStudentId());
                
                // 비동기로 첫 메시지 요약 수행 (웹훅 응답 지연 방지)
                summarizeFirstMessageAsync(student, message.getUtterance());
            }
            
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: userId={}, error={}", 
                     message.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * 첫 메시지를 비동기로 요약하여 학생 정보 추출
     * 웰컴블록으로 학생정보 요청 메시지를 받은 후 첫 메시지에 대해 Gemini API로 요약
     * 추출된 정보를 Student 엔티티의 실제 필드에 직접 저장
     */
    @Async
    @Transactional
    public void summarizeFirstMessageAsync(Student student, String firstMessage) {
        try {
            log.info("첫 메시지 요약 시작: studentId={}", student.getStudentId());
            
            // Gemini API로 첫 메시지 요약
            java.util.Map<String, Object> extractedInfo = geminiService.summarizeFirstMessage(firstMessage);
            
            if (extractedInfo.isEmpty()) {
                log.warn("첫 메시지 요약 실패: studentId={}", student.getStudentId());
                return;
            }
            
            // 추출된 정보를 Student 엔티티의 실제 필드에 직접 저장
            boolean updated = false;
            
            // 이름 업데이트 (항상 업데이트 - Gemini가 추출한 이름이 더 정확할 수 있음)
            if (extractedInfo.containsKey("name") && extractedInfo.get("name") != null) {
                String extractedName = ((String) extractedInfo.get("name")).trim();
                if (!extractedName.isEmpty()) {
                    // 기존 이름과 다르거나 비어있으면 업데이트
                    if (student.getName() == null || student.getName().isEmpty() || 
                        !student.getName().equals(extractedName)) {
                        student.setName(extractedName);
                        updated = true;
                        log.info("학생 이름 업데이트: studentId={}, 기존={}, 신규={}", 
                                student.getStudentId(), student.getName(), extractedName);
                    }
                }
            }
            
            // 나이 업데이트
            if (extractedInfo.containsKey("age") && extractedInfo.get("age") != null) {
                try {
                    Integer extractedAge = ((Number) extractedInfo.get("age")).intValue();
                    if (extractedAge > 0 && extractedAge < 150) { // 유효한 나이 범위 체크
                        if (student.getAge() == null) {
                            student.setAge(extractedAge);
                            updated = true;
                            log.info("학생 나이 업데이트: studentId={}, age={}", 
                                    student.getStudentId(), extractedAge);
                        }
                    }
                } catch (Exception e) {
                    log.warn("나이 파싱 실패: studentId={}, age={}", 
                            student.getStudentId(), extractedInfo.get("age"), e);
                }
            }
            
            // 전적 대학 업데이트
            if (extractedInfo.containsKey("previousSchool") && extractedInfo.get("previousSchool") != null) {
                String previousSchool = ((String) extractedInfo.get("previousSchool")).trim();
                if (!previousSchool.isEmpty() && !"null".equalsIgnoreCase(previousSchool)) {
                    if (student.getPreviousSchool() == null || student.getPreviousSchool().isEmpty()) {
                        student.setPreviousSchool(previousSchool);
                        updated = true;
                        log.info("전적 대학 업데이트: studentId={}, previousSchool={}", 
                                student.getStudentId(), previousSchool);
                    }
                }
            }
            
            // 목표 대학 업데이트
            if (extractedInfo.containsKey("targetUniversity") && extractedInfo.get("targetUniversity") != null) {
                String targetUniversity = ((String) extractedInfo.get("targetUniversity")).trim();
                if (!targetUniversity.isEmpty() && !"null".equalsIgnoreCase(targetUniversity)) {
                    if (student.getTargetUniversity() == null || student.getTargetUniversity().isEmpty()) {
                        student.setTargetUniversity(targetUniversity);
                        updated = true;
                        log.info("목표 대학 업데이트: studentId={}, targetUniversity={}", 
                                student.getStudentId(), targetUniversity);
                    }
                }
            }
            
            // 전화번호 업데이트
            if (extractedInfo.containsKey("phoneNumber") && extractedInfo.get("phoneNumber") != null) {
                String phoneNumber = ((String) extractedInfo.get("phoneNumber")).trim();
                if (!phoneNumber.isEmpty() && !"null".equalsIgnoreCase(phoneNumber)) {
                    if (student.getPhoneNumber() == null || student.getPhoneNumber().isEmpty()) {
                        student.setPhoneNumber(phoneNumber);
                        updated = true;
                        log.info("전화번호 업데이트: studentId={}, phoneNumber={}", 
                                student.getStudentId(), phoneNumber);
                    }
                }
            }
            
            // 희망 전공 업데이트
            if (extractedInfo.containsKey("desiredMajor") && extractedInfo.get("desiredMajor") != null) {
                String desiredMajor = ((String) extractedInfo.get("desiredMajor")).trim();
                if (!desiredMajor.isEmpty() && !"null".equalsIgnoreCase(desiredMajor)) {
                    if (student.getDesiredMajor() == null || student.getDesiredMajor().isEmpty()) {
                        student.setDesiredMajor(desiredMajor);
                        updated = true;
                        log.info("희망 전공 업데이트: studentId={}, desiredMajor={}", 
                                student.getStudentId(), desiredMajor);
                    }
                }
            }
            
            // 현재 학년 업데이트
            if (extractedInfo.containsKey("currentGrade") && extractedInfo.get("currentGrade") != null) {
                String currentGrade = ((String) extractedInfo.get("currentGrade")).trim();
                if (!currentGrade.isEmpty() && !"null".equalsIgnoreCase(currentGrade)) {
                    if (student.getCurrentGrade() == null || student.getCurrentGrade().isEmpty()) {
                        student.setCurrentGrade(currentGrade);
                        updated = true;
                        log.info("현재 학년 업데이트: studentId={}, currentGrade={}", 
                                student.getStudentId(), currentGrade);
                    }
                }
            }
            
            // 희망 입학 학기 업데이트
            if (extractedInfo.containsKey("desiredSemester") && extractedInfo.get("desiredSemester") != null) {
                String desiredSemester = ((String) extractedInfo.get("desiredSemester")).trim();
                if (!desiredSemester.isEmpty() && !"null".equalsIgnoreCase(desiredSemester)) {
                    if (student.getDesiredSemester() == null || student.getDesiredSemester().isEmpty()) {
                        student.setDesiredSemester(desiredSemester);
                        updated = true;
                        log.info("희망 입학 학기 업데이트: studentId={}, desiredSemester={}", 
                                student.getStudentId(), desiredSemester);
                    }
                }
            }
            
            // 업데이트된 필드가 있으면 저장
            if (updated) {
                studentRepository.save(student);
                log.info("첫 메시지 요약 완료 및 Student 필드 저장: studentId={}, 추출된 필드 수={}", 
                        student.getStudentId(), extractedInfo.size());
            } else {
                log.info("첫 메시지 요약 완료: studentId={}, 업데이트할 필드 없음", 
                        student.getStudentId());
            }
            
        } catch (Exception e) {
            log.error("첫 메시지 요약 중 오류 발생: studentId={}", 
                    student.getStudentId(), e);
        }
    }
}


