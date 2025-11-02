package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import Capstone.CSmart.global.domain.enums.AiResponseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_responses", indexes = {
        @Index(name = "idx_ai_responses_status", columnList = "status"),
        @Index(name = "idx_ai_responses_teacher", columnList = "teacherId"),
        @Index(name = "idx_ai_responses_message", columnList = "messageId")
})
public class AiResponse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long responseId;

    @Column(nullable = false)
    private Long messageId; // FK to Message

    @Column(nullable = false)
    private Long studentId; // FK to Student

    private Long teacherId; // FK to Teacher (담당 선생님)

    @Column(columnDefinition = "TEXT")
    private String recommendedResponse; // AI가 생성한 추천 응답

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiResponseStatus status; // 응답 상태

    private Long reviewedByTeacherId; // 검수한 선생님 ID

    private Long reviewedByAdminId; // 검수한 관리자 ID

    @Column(columnDefinition = "TEXT")
    private String finalResponse; // 최종 전송될 응답 (수정 가능)

    private OffsetDateTime generatedAt; // AI 응답 생성 시간

    private OffsetDateTime reviewedAt; // 검수 시간

    private OffsetDateTime sentAt; // 전송 시간
}






