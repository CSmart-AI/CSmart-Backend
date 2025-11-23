package Capstone.CSmart.global.web.controller;

import Capstone.CSmart.global.apiPayload.ApiResponse;
import Capstone.CSmart.global.apiPayload.code.status.SuccessStatus;
import Capstone.CSmart.global.domain.entity.Admin;
import Capstone.CSmart.global.repository.AdminRepository;
import Capstone.CSmart.global.web.dto.Admin.AdminDTO;
import Capstone.CSmart.global.web.dto.Admin.CreateAdminRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "관리자 API", description = "관리자 계정 관리 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "관리자 생성", description = "새로운 관리자 계정 생성")
    @PostMapping("/")
    public ApiResponse<AdminDTO> createAdmin(
            @Valid @RequestBody CreateAdminRequestDTO request) {
        
        log.info("Create admin request: name={}, kakaoId={}", 
            request.getName(), request.getKakaoId());
        
        try {
            // 카카오 계정 중복 확인
            if (adminRepository.findByKakaoId(request.getKakaoId()).isPresent()) {
                return ApiResponse.onFailure("KAKAO_ID_ALREADY_EXISTS", 
                    "이미 사용 중인 카카오 계정 아이디입니다.", null);
            }
            
            // 카카오 비밀번호 암호화
            String encodedKakaoPassword = passwordEncoder.encode(request.getKakaoPassword());
            
            Admin admin = Admin.builder()
                    .name(request.getName())
                    .kakaoId(request.getKakaoId())
                    .kakaoPasswordEncrypted(encodedKakaoPassword)
                    .status("ACTIVE") // 기본 상태 ACTIVE
                    .build();
            
            Admin savedAdmin = adminRepository.save(admin);
            AdminDTO adminDTO = convertToDTO(savedAdmin);
            
            log.info("Admin created successfully: adminId={}", savedAdmin.getAdminId());
            
            return ApiResponse.onSuccess(SuccessStatus.ADMIN_OK, adminDTO);
            
        } catch (Exception e) {
            log.error("Failed to create admin", e);
            return ApiResponse.onFailure("CREATE_ADMIN_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "전체 관리자 목록 조회", description = "활성 상태인 모든 관리자 목록 조회")
    @GetMapping("/")
    public ApiResponse<List<AdminDTO>> getAllAdmins() {
        
        log.info("Get all admins request");
        
        try {
            List<Admin> admins = adminRepository.findAll();
            
            List<AdminDTO> adminDTOs = admins.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ApiResponse.onSuccess(SuccessStatus.ADMIN_OK, adminDTOs);
            
        } catch (Exception e) {
            log.error("Failed to get all admins", e);
            return ApiResponse.onFailure("GET_ADMINS_FAILED", e.getMessage(), null);
        }
    }

    @Operation(summary = "관리자 조회", description = "특정 관리자 정보 조회")
    @GetMapping("/{id}")
    public ApiResponse<AdminDTO> getAdmin(@PathVariable Long id) {
        
        log.info("Get admin request: id={}", id);
        
        try {
            Admin admin = adminRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Admin not found: " + id));
            
            AdminDTO adminDTO = convertToDTO(admin);
            return ApiResponse.onSuccess(SuccessStatus.ADMIN_OK, adminDTO);
            
        } catch (Exception e) {
            log.error("Failed to get admin: {}", id, e);
            return ApiResponse.onFailure("GET_ADMIN_FAILED", e.getMessage(), null);
        }
    }

    private AdminDTO convertToDTO(Admin admin) {
        return AdminDTO.builder()
                .adminId(admin.getAdminId())
                .name(admin.getName())
                .status(admin.getStatus())
                .kakaoId(admin.getKakaoId())
                .build();
    }
}

