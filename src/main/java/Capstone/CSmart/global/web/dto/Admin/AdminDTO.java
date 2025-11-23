package Capstone.CSmart.global.web.dto.Admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDTO {

    private Long adminId;
    private String name;
    private String status; // ACTIVE, INACTIVE
    private String kakaoId;
}


