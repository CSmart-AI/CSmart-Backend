package Capstone.CSmart.global.apiPayload.code.status;


import Capstone.CSmart.global.apiPayload.code.BaseCode;
import Capstone.CSmart.global.apiPayload.code.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

    // 공통
    OK(HttpStatus.OK, "COMMON_2000", "요청이 성공적으로 처리되었습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_2001", "리소스가 성공적으로 생성되었습니다."),
    
    // 회원 관련
    MEMBER_OK(HttpStatus.OK, "MEMBER_2000", "회원 처리가 완료되었습니다."),
    
    // 카카오톡 챗봇 관련
    CHATBOT_MESSAGE_RECEIVED(HttpStatus.OK, "CHATBOT_2000", "메시지가 수신되었습니다."),
    CHATBOT_INFO_EXTRACTED(HttpStatus.OK, "CHATBOT_2001", "학생 정보가 추출되었습니다."),
    
    // 학생 관련
    STUDENT_OK(HttpStatus.OK, "STUDENT_2000", "학생 처리가 완료되었습니다."),
    STUDENT_DELETED(HttpStatus.OK, "STUDENT_2001", "학생이 삭제되었습니다."),
    
    // 선생님 관련
    TEACHER_OK(HttpStatus.OK, "TEACHER_2000", "선생님 처리가 완료되었습니다."),
    TEACHER_DELETED(HttpStatus.OK, "TEACHER_2001", "선생님이 삭제되었습니다."),
    
    // 메시지 관련
    MESSAGE_OK(HttpStatus.OK, "MESSAGE_2000", "메시지 처리가 완료되었습니다."),
    
    // AI 응답 관련
    AI_RESPONSE_OK(HttpStatus.OK, "AI_2000", "AI 응답 처리가 완료되었습니다."),
    AI_RESPONSE_APPROVED(HttpStatus.OK, "AI_2001", "AI 응답이 승인되고 전송되었습니다."),
    AI_SCHEDULER_TRIGGERED(HttpStatus.OK, "AI_2002", "AI 스케줄러가 실행되었습니다."),
    
    // 카카오 서비스 관련
    KAKAO_OK(HttpStatus.OK, "KAKAO_2000", "카카오 서비스 요청이 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getReason() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .build();
    }

    @Override
    public ReasonDTO getReasonHttpStatus() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .httpStatus(httpStatus)
                .build()
                ;
    }
}

