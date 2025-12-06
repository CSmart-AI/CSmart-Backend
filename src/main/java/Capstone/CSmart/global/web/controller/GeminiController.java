package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.repository.StudentRepository;
import Capstone.CSmart.global.service.gemini.GeminiService;
import Capstone.CSmart.global.service.student.StudentInfoUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Gemini API", description = "초기 상담 정보 추출 (프론트엔드 전용)")
@RestController
@RequestMapping("/api/gemini")
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;
    private final StudentRepository studentRepository;
    private final StudentInfoUpdateService studentInfoUpdateService;

    @Operation(
        summary = "학생 정보 추출 (정식 서비스 가입용)", 
        description = "학생이 정식으로 서비스를 사용하기로 결정했을 때 호출합니다. " +
                     "Gemini가 채팅 기록을 분석해 학생 정보(이름, 나이, 전적대학, 목표대학 등)를 추출하고 Student 테이블에 저장합니다."
    )
    @PostMapping("/extract-student-info/{studentId}")
    public ApiResponse<Map<String, Object>> extractStudentInfo(@PathVariable Long studentId) {
        
        log.info("Gemini 학생 정보 추출 요청: studentId={}", studentId);
        
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
            
            if ("REGISTERED".equals(student.getRegistrationStatus()) || 
                "INFO_COLLECTED".equals(student.getRegistrationStatus())) {
                log.warn("학생 정보가 이미 추출되었습니다: studentId={}, status={}", 
                        studentId, student.getRegistrationStatus());
                return ApiResponse.onFailure("ALREADY_REGISTERED", 
                        "이미 정보가 추출된 학생입니다.", null);
            }
            
            Map<String, Object> extractedInfo = geminiService.extractUserInfoFromChat(studentId);
            
            if (extractedInfo.isEmpty()) {
                log.warn("Gemini 정보 추출 실패: studentId={}", studentId);
                return ApiResponse.onFailure("EXTRACTION_FAILED", 
                        "정보 추출에 실패했습니다. 채팅 기록이 충분한지 확인해주세요.", null);
            }
            
            log.info("Gemini 정보 추출 완료: studentId={}", studentId);
            
            return ApiResponse.onSuccess(SuccessStatus.CHATBOT_INFO_EXTRACTED, extractedInfo);
            
        } catch (Exception e) {
            log.error("Gemini 정보 추출 중 오류 발생: studentId={}", studentId, e);
            return ApiResponse.onFailure("EXTRACTION_ERROR", e.getMessage(), null);
        }
    }
    
    @Operation(
        summary = "학생 정보 추출 및 저장 (정식 서비스 가입 완료)", 
        description = "학생이 정식으로 서비스를 사용하기로 결정했을 때 호출합니다. " +
                     "Gemini가 채팅 기록에서 학생 정보를 추출하고, 즉시 Student 테이블에 저장합니다. " +
                     "상태가 CHATTING에서 REGISTERED로 변경됩니다."
    )
    @PostMapping("/extract-and-update/{studentId}")
    public ApiResponse<Student> extractAndUpdateStudentInfo(@PathVariable Long studentId) {
        
        log.info("Gemini 학생 정보 추출 및 저장 요청: studentId={}", studentId);
        
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
            
            if ("REGISTERED".equals(student.getRegistrationStatus()) || 
                "INFO_COLLECTED".equals(student.getRegistrationStatus())) {
                log.warn("학생 정보가 이미 저장되었습니다: studentId={}, status={}", 
                        studentId, student.getRegistrationStatus());
                return ApiResponse.onFailure("ALREADY_REGISTERED", 
                        "이미 정보가 저장된 학생입니다.", student);
            }
            
            Map<String, Object> extractedInfo = geminiService.extractUserInfoFromChat(studentId);
            
            if (extractedInfo.isEmpty()) {
                log.warn("Gemini 정보 추출 실패: studentId={}", studentId);
                return ApiResponse.onFailure("EXTRACTION_FAILED", 
                        "정보 추출에 실패했습니다. 채팅 기록이 충분한지 확인해주세요.", null);
            }
            
            // 공통 서비스를 사용하여 학생 정보 업데이트
            Student updatedStudent = studentInfoUpdateService.updateStudentInfo(student, extractedInfo);
            
            // 등록 상태 업데이트: CHATTING → REGISTERED
            if (!"REGISTERED".equals(updatedStudent.getRegistrationStatus()) && 
                !"INFO_COLLECTED".equals(updatedStudent.getRegistrationStatus())) {
                updatedStudent.setRegistrationStatus("REGISTERED");
            }
            
            updatedStudent = studentRepository.save(updatedStudent);
            
            log.info("Gemini 정보 추출 및 저장 완료: studentId={}", studentId);
            
            return ApiResponse.onSuccess(SuccessStatus.CHATBOT_INFO_EXTRACTED, updatedStudent);
            
        } catch (Exception e) {
            log.error("Gemini 정보 추출/저장 중 오류 발생: studentId={}", studentId, e);
            return ApiResponse.onFailure("EXTRACTION_ERROR", e.getMessage(), null);
        }
    }
    

    @Operation(
        summary = "학생 정보 수동 저장", 
        description = "프론트엔드에서 수동으로 입력한 학생 정보를 저장합니다. " +
                     "Gemini API를 사용하지 않고 직접 입력된 정보를 Student 테이블에 저장합니다."
    )
    @PostMapping("/save-student-info/{studentId}")
    public ApiResponse<Student> saveStudentInfoManually(
            @PathVariable Long studentId,
            @RequestBody Map<String, Object> studentInfo) {
        
        log.info("학생 정보 수동 저장 요청: studentId={}", studentId);
        
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
            
            // 공통 서비스를 사용하여 학생 정보 업데이트
            Student updatedStudent = studentInfoUpdateService.updateStudentInfo(student, studentInfo);
            
            // 등록 상태 업데이트 (수동 저장 시에도 REGISTERED로 변경)
            if (!"REGISTERED".equals(updatedStudent.getRegistrationStatus()) && 
                !"INFO_COLLECTED".equals(updatedStudent.getRegistrationStatus())) {
                updatedStudent.setRegistrationStatus("REGISTERED");
            }
            
            updatedStudent = studentRepository.save(updatedStudent);
            
            log.info("학생 정보 수동 저장 완료: studentId={}", studentId);
            
            return ApiResponse.onSuccess(SuccessStatus.CHATBOT_INFO_EXTRACTED, updatedStudent);
            
        } catch (Exception e) {
            log.error("학생 정보 수동 저장 중 오류 발생: studentId={}", studentId, e);
            return ApiResponse.onFailure("SAVE_ERROR", e.getMessage(), null);
        }
    }
}

