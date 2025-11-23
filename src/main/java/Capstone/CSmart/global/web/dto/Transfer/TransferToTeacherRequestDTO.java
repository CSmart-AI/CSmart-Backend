package Capstone.CSmart.global.web.dto.Transfer;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 선생님 채널 전환 요청 DTO
 * studentId로 자동으로 대화 히스토리를 조회하여 Gemini로 분석
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferToTeacherRequestDTO {

    @NotNull(message = "학생 ID는 필수입니다.")
    private Long studentId;

    @NotNull(message = "선생님 ID는 필수입니다.")
    private Long teacherId;
}











