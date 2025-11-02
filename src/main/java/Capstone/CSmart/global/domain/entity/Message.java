package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_student", columnList = "studentId"),
        @Index(name = "idx_messages_sent_at", columnList = "sentAt")
})
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    private Long studentId;

    private Long teacherId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String messageType; // text/image/file

    @Column(length = 20)
    private String senderType; // student/teacher/system/ai

    private OffsetDateTime sentAt;
}


