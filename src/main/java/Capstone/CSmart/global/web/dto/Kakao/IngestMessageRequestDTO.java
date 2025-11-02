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
public class IngestMessageRequestDTO {
    @NotBlank
    private String channelUuid;

    @NotBlank
    private String kakaoUserId;

    @NotBlank
    private String chatId;

    @NotBlank
    private String content;

    @NotBlank
    private String sentAt; // ISO8601 string

    private String meta;
}


