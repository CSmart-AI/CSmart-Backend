package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByStudentIdAndSentAtAfterAndSenderType(Long studentId, OffsetDateTime since, String senderType);
    List<Message> findByStudentIdAndSenderTypeOrderBySentAtDesc(Long studentId, String senderType, Pageable pageable);
    List<Message> findByStudentIdOrderBySentAtDesc(Long studentId, Pageable pageable);
    long countByStudentId(Long studentId);
    long countByStudentIdAndSenderType(Long studentId, String senderType);
    
    // 선생님이 보낸 메시지 조회 (senderType이 "teacher"이고 teacherId가 일치)
    List<Message> findByTeacherIdAndSenderTypeOrderBySentAtDesc(Long teacherId, String senderType, Pageable pageable);
    
    // 선생님이 배정된 학생의 메시지 조회 (teacherId가 일치)
    List<Message> findByTeacherIdOrderBySentAtDesc(Long teacherId, Pageable pageable);
}


