package Capstone.CSmart.global.service.student;

import Capstone.CSmart.global.domain.entity.Conversation;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.ChannelType;
import Capstone.CSmart.global.domain.enums.StudentStatus;
import Capstone.CSmart.global.repository.ConversationRepository;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.web.dto.Kakao.KakaoMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 학생 관련 서비스
 * 학생 정보 생성/조회/업데이트 및 대화 기록 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final ConversationRepository conversationRepository;

    /**
     * 카카오 userId로 학생 조회 또는 생성
     * 없으면 자동으로 생성합니다.
     */
    @Transactional
    public Student getOrCreateStudent(String kakaoUserId, String botId, String botName) {
        return studentRepository.findByKakaoUserId(kakaoUserId)
                .orElseGet(() -> {
                    log.info("새로운 학생 생성: kakaoUserId={}", kakaoUserId);
                    Student newStudent = Student.builder()
                            .kakaoUserId(kakaoUserId)
                            .kakaoChannelId(botId)
                            .status(StudentStatus.INITIAL) // 초기 상태
                            .build();
                    return studentRepository.save(newStudent);
                });
    }

    /**
     * 대화 기록 저장 (사용자 메시지)
     */
    @Transactional
    public Conversation saveUserMessage(Student student, KakaoMessageDTO message) {
        Conversation conversation = Conversation.builder()
                .student(student)
                .channelType(message.getChannelType())
                .senderType("USER")
                .message(message.getUtterance())
                .botId(message.getBotId())
                .botName(message.getBotName())
                .build();
        
        Conversation saved = conversationRepository.save(conversation);
        log.info("사용자 메시지 저장 완료: conversationId={}, studentId={}, channelType={}", 
                 saved.getConversationId(), student.getStudentId(), message.getChannelType());
        return saved;
    }

    /**
     * 대화 기록 저장 (AI 응답)
     */
    @Transactional
    public Conversation saveAiMessage(Student student, String aiResponse, ChannelType channelType, String botId, String botName) {
        Conversation conversation = Conversation.builder()
                .student(student)
                .channelType(channelType)
                .senderType("AI")
                .message(aiResponse)
                .botId(botId)
                .botName(botName)
                .build();
        
        Conversation saved = conversationRepository.save(conversation);
        log.info("AI 응답 저장 완료: conversationId={}, studentId={}, channelType={}", 
                 saved.getConversationId(), student.getStudentId(), channelType);
        return saved;
    }

    /**
     * 특정 학생의 모든 대화 기록 조회
     */
    @Transactional(readOnly = true)
    public List<Conversation> getConversationHistory(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        return conversationRepository.findByStudentOrderByCreatedAtAsc(student);
    }

    /**
     * 특정 학생의 특정 채널 대화 기록 조회
     */
    @Transactional(readOnly = true)
    public List<Conversation> getConversationHistoryByChannel(Long studentId, ChannelType channelType) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        return conversationRepository.findByStudentAndChannelTypeOrderByCreatedAtAsc(student, channelType);
    }

    /**
     * 학생 정보 업데이트
     */
    @Transactional
    public Student updateStudent(Student student) {
        return studentRepository.save(student);
    }

    /**
     * 학생 조회
     */
    @Transactional(readOnly = true)
    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
    }
}

