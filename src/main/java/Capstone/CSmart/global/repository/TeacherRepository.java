package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByKakaoChannelId(String channelId);
    List<Teacher> findByStatus(String status);
    Optional<Teacher> findByKakaoId(String kakaoId);
    Optional<Teacher> findByEmail(String email);
    List<Teacher> findByCreatedByAdminId(Long adminId);
}






