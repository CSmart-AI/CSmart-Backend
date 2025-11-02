package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "students")
public class Student extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @Column(length = 100)
    private String name;

    private Integer age;

    @Column(length = 255)
    private String previousSchool;

    @Column(length = 255)
    private String targetUniversity;

    @Column(length = 30)
    private String phoneNumber;

    @Column(length = 100)
    private String kakaoChannelId;

    @Column(length = 100, unique = true)
    private String kakaoUserId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String consultationFormDataJson;

    private Long assignedTeacherId;

    @Column(length = 50)
    private String registrationStatus;
}


