package Capstone.CSmart.global.domain.entity;

import Capstone.CSmart.global.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "semantic_cache", indexes = {
    @Index(name = "idx_semantic_cache_created_at", columnList = "createdAt"),
    @Index(name = "idx_semantic_cache_hit_count", columnList = "hitCount"),
    @Index(name = "idx_semantic_cache_confidence_score", columnList = "confidenceScore")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCache extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cache_id")
    private Long cacheId;

    @Column(nullable = false, columnDefinition = "TEXT", name = "question")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT", name = "answer")
    private String answer;

    @Column(columnDefinition = "JSON", name = "embedding_json")
    private String embeddingJson;

    @Column(nullable = false, name = "confidence_score")
    private Double confidenceScore;

    @Column(nullable = false, name = "hit_count")
    @Builder.Default
    private Integer hitCount = 0;

    @Column(name = "last_hit_at")
    private OffsetDateTime lastHitAt;

    @Column(name = "original_response_id")
    private Long originalResponseId;

    @Column(name = "cache_key")
    private String cacheKey;

    @Column(name = "embedding_model")
    private String embeddingModel;

    public void incrementHitCount() {
        this.hitCount++;
        this.lastHitAt = OffsetDateTime.now();
    }
}