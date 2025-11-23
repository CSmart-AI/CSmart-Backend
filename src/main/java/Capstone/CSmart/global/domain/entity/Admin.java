package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import Capstone.CSmart.global.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "admins")
public class Admin extends BaseEntity implements UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole userRole = UserRole.ADMIN;

    @Column(length = 20)
    private String status; // ACTIVE, INACTIVE (String으로 통일)

    @Column(length = 255, unique = true)
    private String kakaoId; // 카카오 계정 아이디

    @Column(length = 255)
    private String kakaoPasswordEncrypted; // 암호화된 카카오 계정 비밀번호

    @Override
    public Long getUserId() {
        return adminId;
    }
}






