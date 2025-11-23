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
@Table(name = "teachers")
public class Teacher extends BaseEntity implements UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teacherId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(length = 30)
    private String phoneNumber;

    @Column(length = 100)
    private String kakaoChannelId;

    @Column(length = 255)
    private String specialization;

    @Column(length = 20)
    private String status; // ACTIVE, INACTIVE (String)

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole userRole = UserRole.TEACHER;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @Column(length = 255, unique = true)
    private String kakaoId;

    @Column(length = 255)
    private String kakaoPasswordEncrypted;

    @Override
    public Long getUserId() {
        return teacherId;
    }
}






