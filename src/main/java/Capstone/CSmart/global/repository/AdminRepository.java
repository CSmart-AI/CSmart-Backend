package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByKakaoId(String kakaoId);
}






