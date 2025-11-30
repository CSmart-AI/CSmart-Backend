package Capstone.CSmart.global.web.dto.AiResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseDTO {

    private Long responseId;
    private Long messageId;
    private Long studentId;
    private Long teacherId;
    private String recommendedResponse;
    private String status;
    private OffsetDateTime generatedAt;
    private String lastMessage; // 학생의 마지막 메시지
}












