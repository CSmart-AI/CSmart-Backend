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
    Page<AiResponse> findByStatusOrderByGeneratedAtDesc(AiResponseStatus status, Pageable pageable);
    List<AiResponse> findByTeacherIdAndStatus(Long teacherId, AiResponseStatus status);
    List<AiResponse> findByStudentIdOrderByGeneratedAtDesc(Long studentId);
    Optional<AiResponse> findByMessageId(Long messageId);
    // 가장 최근 생성된 응답만 가져오기 (중복 방지)
    Optional<AiResponse> findTopByMessageIdOrderByGeneratedAtDesc(Long messageId);
    // 같은 messageId를 가진 모든 AiResponse 조회
    List<AiResponse> findAllByMessageId(Long messageId);
    // 같은 messageId와 상태를 가진 모든 AiResponse 조회
    List<AiResponse> findAllByMessageIdAndStatus(Long messageId, AiResponseStatus status);
    Page<AiResponse> findByTeacherIdAndStatusOrderByGeneratedAtDesc(Long teacherId, AiResponseStatus status, Pageable pageable);
    // 선생님별 AI 응답 조회 (모든 상태)
    Page<AiResponse> findByTeacherIdOrderByGeneratedAtDesc(Long teacherId, Pageable pageable);
}



