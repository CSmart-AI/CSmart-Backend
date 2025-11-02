package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByKakaoUserId(String kakaoUserId);
    List<Student> findByAssignedTeacherId(Long teacherId);
    List<Student> findByRegistrationStatus(String status);
    List<Student> findByAssignedTeacherIdAndRegistrationStatus(Long teacherId, String status);
}


