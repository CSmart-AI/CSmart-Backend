package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import Capstone.CSmart.global.domain.enums.StudentStatus;
import jakarta.persistence.*;
import lombok.*;

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
    private String desiredMajor;

    @Column(length = 50)
    private String currentGrade;

    @Column(length = 50)
    private String desiredSemester;

    // 14개 질문에 대한 추가 필드
    @Column(length = 20)
    private String type; // 일반/학사

    @Column(length = 50)
    private String track; // 문과/이과/특성화고/예체능/기타

    @Column(length = 50)
    private String mathGrade; // 수학 등급 (예: 1등급(선택과목 기하))

    @Column(length = 50)
    private String englishGrade; // 영어 등급 (예: 3등급)

    @Column(length = 255)
    private String previousCourse; // 수강했던 편입인강 or 학원과 진도

    private Boolean isRetaking; // 편입재수 여부

    private Boolean isSunungRetaking; // 수능재수 여부

    private Boolean hasToeic; // 토익 취득 여부

    private Boolean hasPartTimeJob; // 알바 유무

    @Column(length = 100)
    private String availableCallTime; // 통화 가능 시간

    @Lob
    @Column(columnDefinition = "TEXT")
    private String message; // 꼭 하고 싶은 말

    @Column(length = 100)
    private String source; // 유입경로(인스타/블로그)

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StudentStatus status;
}


