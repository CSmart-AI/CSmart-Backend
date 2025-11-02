package Capstone.CSmart.global.web.dto.AiResponse;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditResponseRequestDTO {

    @NotBlank
    private String editedContent;
}






