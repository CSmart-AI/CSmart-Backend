package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import Capstone.CSmart.global.domain.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;

/**
 * 대화 기록 엔티티
 * Admin 채널과 Teacher 채널의 모든 대화를 저장합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channelType; // ADMIN 또는 TEACHER

    @Column(nullable = false, length = 20)
    private String senderType; // USER 또는 AI

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(length = 100)
    private String botId;

    @Column(length = 100)
    private String botName;
}

