package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.service.ai.AiScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 테스트", description = "스케줄러 수동 트리거")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTestController {

    private final AiScheduler aiScheduler;

    @Operation(summary = "스케줄러 수동 트리거", description = "10분 주기 작업을 즉시 한 번 실행")
    @PostMapping("/trigger-once")
    public ApiResponse<String> triggerOnce() {
        aiScheduler.processPendingMessages();
        return ApiResponse.onSuccess(SuccessStatus.AI_SCHEDULER_TRIGGERED, "triggered");
    }
}


