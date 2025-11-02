package Capstone.CSmart.global.service.ingest;

import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.repository.MessageRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.ai.AiResponseService;
import Capstone.CSmart.global.web.dto.Kakao.ChatbotRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageIngestService {

    private final StudentRepository studentRepository;
    private final MessageRepository messageRepository;
    private final AiResponseService aiResponseService;

    /**
     * 카카오톡 웹훅에서 받은 메시지 저장
     * AI 응답 생성은 즉시 하지 않고, 10분 주기 스케줄러에서 배치 처리
     */
    @Transactional
    public void ingestAndGenerateResponse(ChatbotRequestDTO dto) {
        // 학생 조회 또는 생성
        Student student = studentRepository.findByKakaoUserId(dto.getUserId())
                .orElseGet(() -> {
                    Student newStudent = Student.builder()
                            .kakaoUserId(dto.getUserId())
                            .name(dto.getUserName())
                            .kakaoChannelId(dto.getBotId())
                            .registrationStatus("CHATTING")  // 초기 상태: 단순 채팅 중
                            .build();
                    return studentRepository.save(newStudent);
                });

        // 메시지 저장만 수행 (AI 응답은 스케줄러에서 배치 처리)
        Message message = Message.builder()
                .studentId(student.getStudentId())
                .teacherId(null)
                .content(dto.getUtterance())
                .messageType("text")
                .senderType("student")
                .sentAt(OffsetDateTime.parse(dto.getTimestamp()))
                .build();

        Message savedMessage = messageRepository.save(message);
        log.info("메시지 저장 완료: messageId={}, studentId={}", 
                savedMessage.getMessageId(), student.getStudentId());
    }
}


