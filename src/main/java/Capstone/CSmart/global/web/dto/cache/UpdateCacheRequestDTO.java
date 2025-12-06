package Capstone.CSmart.global.web.dto.cache;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "캐시 답변 수정 요청 DTO")
public class UpdateCacheRequestDTO {

    @NotBlank(message = "답변 내용은 필수입니다.")
    @Schema(
        description = "수정할 답변 내용",
        example = "중앙대학교 편입 시험은 매년 12월에 실시됩니다."
    )
    private String answer;
}



