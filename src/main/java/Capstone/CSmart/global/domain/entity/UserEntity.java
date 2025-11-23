package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.enums.UserRole;

/**
 * Admin, Teacher 등 인증이 필요한 엔티티의 공통 인터페이스
 */
public interface UserEntity {
    Long getUserId();
    String getName();
    String getKakaoId();
    String getKakaoPasswordEncrypted();
    UserRole getUserRole();
}




