package Capstone.CSmart.global.web.dto.Kakao;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDTO {

    @NotBlank
    private String userId;

    private String userName;

    @NotBlank
    private String userType;

    @NotBlank
    private String utterance;

    @NotBlank
    private String timestamp;

    @NotBlank
    private String botId;

    @NotBlank
    private String botName;

    @NotBlank
    private String actionName;
}






