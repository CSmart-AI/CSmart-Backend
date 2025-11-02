package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.AiResponse;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface AiResponseRepository extends JpaRepository<AiResponse, Long> {
    List<AiResponse> findByStatusOrderByGeneratedAtDesc(AiResponseStatus status);
    List<AiResponse> findByTeacherIdAndStatus(Long teacherId, AiResponseStatus status);
    List<AiResponse> findByStudentIdOrderByGeneratedAtDesc(Long studentId);
    Optional<AiResponse> findByMessageId(Long messageId);
    Page<AiResponse> findByTeacherIdAndStatusOrderByGeneratedAtDesc(Long teacherId, AiResponseStatus status, Pageable pageable);
}



