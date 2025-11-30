package Capstone.CSmart.global.web.dto.cache;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "캐시 워밍업 요청 DTO")
public class CacheWarmupRequestDTO {

    @Schema(
        description = "최근 며칠간의 응답을 캐시에 추가할지 설정 (null이면 전체)", 
        example = "7"
    )
    private Integer recentDays;
}