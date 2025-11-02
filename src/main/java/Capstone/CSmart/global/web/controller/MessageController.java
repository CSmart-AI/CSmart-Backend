package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.Message;
import Capstone.CSmart.global.repository.MessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "메시지 관리 API", description = "메시지 이력 조회 및 관리")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageRepository messageRepository;

    @Operation(summary = "전체 메시지 조회", description = "전체 메시지 이력 조회 (관리자용)")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAllMessages(
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("Get all messages request: limit={}", limit);
        
        try {
            List<Message> messages = messageRepository.findAll(PageRequest.of(0, limit)).getContent();
            
            List<Map<String, Object>> messageDTOs = messages.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.MESSAGE_OK, messageDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get all messages", e);
            return ApiResponse.onFailure("GET_MESSAGES_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "메시지 상세 조회", description = "특정 메시지의 상세 정보 조회")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getMessage(@PathVariable Long id) {
        
        log.info("Get message request: id={}", id);
        
        try {
            Message message = messageRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Message not found: " + id));
            
            Map<String, Object> messageDTO = convertToMap(message);
            return ApiResponse.onSuccess(SuccessStatus.MESSAGE_OK, messageDTO);
            
        } catch (Exception e) {
            log.error("Failed to get message: {}", id, e);
            return ApiResponse.onFailure("GET_MESSAGE_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "학생별 메시지 조회", description = "특정 학생의 메시지 이력 조회")
    @GetMapping("/student/{studentId}")
    public ApiResponse<List<Map<String, Object>>> getMessagesByStudent(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Get messages by student request: studentId={}, limit={}", studentId, limit);
        
        try {
            List<Message> messages = messageRepository.findByStudentIdOrderBySentAtDesc(
                    studentId, PageRequest.of(0, limit));
            
            List<Map<String, Object>> messageDTOs = messages.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.MESSAGE_OK, messageDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get messages by student: {}", studentId, e);
            return ApiResponse.onFailure("GET_MESSAGES_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "선생님별 메시지 조회", description = "특정 선생님이 관여한 메시지 이력 조회")
    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<List<Map<String, Object>>> getMessagesByTeacher(
            @PathVariable Long teacherId,
            @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Get messages by teacher request: teacherId={}, limit={}", teacherId, limit);
        
        try {
            // 선생님이 보낸 메시지나 선생님이 배정된 학생의 메시지
            List<Message> messages = messageRepository.findAll().stream()
                    .filter(m -> teacherId.equals(m.getTeacherId()) || 
                            (m.getTeacherId() != null && teacherId.equals(m.getTeacherId())))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            List<Map<String, Object>> messageDTOs = messages.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.MESSAGE_OK, messageDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get messages by teacher: {}", teacherId, e);
            return ApiResponse.onFailure("GET_MESSAGES_FAILED", e.getMessage(), null);
        }
    }

    // DTO 변환 메서드
    private Map<String, Object> convertToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageId", message.getMessageId());
        map.put("studentId", message.getStudentId());
        map.put("teacherId", message.getTeacherId());
        map.put("content", message.getContent());
        map.put("messageType", message.getMessageType());
        map.put("senderType", message.getSenderType());
        map.put("sentAt", message.getSentAt());
        map.put("createdAt", message.getCreatedAt());
        return map;
    }
}




