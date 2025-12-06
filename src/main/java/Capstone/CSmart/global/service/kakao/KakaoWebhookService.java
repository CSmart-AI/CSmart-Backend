package Capstone.CSmart.global.service.kakao;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.StudentStatus;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import Capstone.CSmart.global.service.student.StudentInfoUpdateService;
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
    private final StudentInfoUpdateService studentInfoUpdateService;
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

            // 3. 첫 메시지 요약 기능 제거 (transfer 시에만 정보 요약)
            // 첫 메시지 오자마자 요약하는 기능은 제거되었습니다.
            // 학생 정보 요약은 transferToTeacher API 호출 시에만 수행됩니다.
            
            /* 첫 메시지 요약 기능 (주석 처리)
            long messageCount = messageRepository.countByStudentIdAndSenderType(
                    student.getStudentId(), "student");
            
            if (messageCount == 1) {
                log.info("첫 메시지 감지: studentId={}, 첫 메시지 요약 시작", student.getStudentId());
                
                // 비동기로 첫 메시지 요약 수행 (웹훅 응답 지연 방지)
                summarizeFirstMessageAsync(student, message.getUtterance());
            }
            */
            
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: userId={}, error={}", 
                     message.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * 첫 메시지를 비동기로 요약하여 학생 정보 추출
     * 웰컴블록으로 학생정보 요청 메시지를 받은 후 첫 메시지에 대해 Gemini API로 요약
     * 추출된 정보를 Student 엔티티의 실제 필드에 직접 저장
     * 
     * [주석 처리됨] 첫 메시지 오자마자 요약하는 기능은 제거되었습니다.
     * 학생 정보 요약은 transferToTeacher API 호출 시에만 수행됩니다.
     */
    /*
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
            
            // 공통 서비스를 사용하여 학생 정보 업데이트
            Student updatedStudent = studentInfoUpdateService.updateStudentInfo(student, extractedInfo);
            
            // 업데이트된 경우 저장
            if (updatedStudent != null) {
                studentRepository.save(updatedStudent);
                log.info("첫 메시지 요약 완료 및 Student 필드 저장: studentId={}, 추출된 필드 수={}", 
                        updatedStudent.getStudentId(), extractedInfo.size());
            } else {
                log.info("첫 메시지 요약 완료: studentId={}, 업데이트할 필드 없음", 
                        student.getStudentId());
            }
            
        } catch (Exception e) {
            log.error("첫 메시지 요약 중 오류 발생: studentId={}", 
                    student.getStudentId(), e);
        }
    }
    */
}


