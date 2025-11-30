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
}


