package Capstone.CSmart.global.service.kakao;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.web.dto.Kakao.KakaoMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            // 1. 학생 조회 또는 생성
            Student student = studentRepository.findByKakaoUserId(message.getUserId())
                    .orElseGet(() -> {
                        log.info("새로운 학생 생성: kakaoUserId={}", message.getUserId());
                        Student newStudent = Student.builder()
                                .kakaoUserId(message.getUserId())
                                .name(message.getUserName())
                                .kakaoChannelId(message.getBotId())
                                .registrationStatus("CHATTING") // 초기 상태
                                .build();
                        return studentRepository.save(newStudent);
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
            
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: userId={}, error={}", 
                     message.getUserId(), e.getMessage(), e);
        }
    }
}


