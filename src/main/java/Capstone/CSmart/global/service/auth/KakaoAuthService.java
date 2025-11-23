package Capstone.CSmart.global.service.auth;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import Capstone.CSmart.global.domain.entity.Admin;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.domain.enums.Status;
import Capstone.CSmart.global.domain.enums.UserRole;
import Capstone.CSmart.global.repository.AdminRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.security.provider.JwtTokenProvider;
import Capstone.CSmart.global.service.kakao.KakaoService;
import Capstone.CSmart.global.web.Auth.AuthResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class KakaoAuthService {

    private final AdminRepository adminRepository;
    private final TeacherRepository teacherRepository;
    private final KakaoService kakaoService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 카카오 계정으로 로그인
     *
     * @param kakaoId 카카오 계정 아이디
     * @param kakaoPassword 카카오 계정 비밀번호
     * @return 로그인 응답 (JWT 토큰 포함)
     */
    public AuthResponseDTO.KakaoLoginResponse kakaoLogin(String kakaoId, String kakaoPassword) {
        log.info("카카오 로그인 시도: kakaoId={}", kakaoId);

        // 1. Admin인지 확인
        Admin admin = adminRepository.findByKakaoId(kakaoId).orElse(null);
        if (admin != null && "ACTIVE".equals(admin.getStatus())) {
            // 카카오 비밀번호 검증
            if (verifyKakaoPassword(kakaoPassword, admin.getKakaoPasswordEncrypted())) {
                // 카카오톡 웹훅 서버에 로그인 (DB 검증 통과 후 바로 실제 로그인)
                boolean loginSuccess = kakaoService.loginToKakaoWebhook(kakaoId, kakaoPassword, UserRole.ADMIN);
                if (!loginSuccess) {
                    log.warn("카카오톡 웹훅 서버 로그인 실패: kakaoId={}", kakaoId);
                    throw new AuthException(ErrorStatus.KAKAO_LOGIN_FAIL);
                }

                // JWT 토큰 발급
                String accessToken = jwtTokenProvider.createAccessToken(admin.getAdminId(), "ADMIN");
                String refreshToken = jwtTokenProvider.createRefreshToken(admin.getAdminId(), "ADMIN");

                log.info("Admin 로그인 성공: adminId={}", admin.getAdminId());

                return AuthResponseDTO.KakaoLoginResponse.builder()
                        .userId(admin.getAdminId())
                        .role("ADMIN")
                        .name(admin.getName())
                        .email(admin.getKakaoId())
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build();
            } else {
                log.warn("카카오 비밀번호 불일치: kakaoId={}", kakaoId);
                throw new AuthException(ErrorStatus.KAKAO_LOGIN_FAIL);
            }
        }

        // 2. Teacher인지 확인
        Teacher teacher = teacherRepository.findByKakaoId(kakaoId).orElse(null);
        if (teacher != null && "ACTIVE".equals(teacher.getStatus())) {
            // Admin이 생성한 Teacher인지 확인
            if (teacher.getCreatedByAdminId() == null) {
                log.warn("인증되지 않은 Teacher 계정: kakaoId={}", kakaoId);
                throw new AuthException(ErrorStatus.KAKAO_ACCOUNT_NOT_AUTHORIZED);
            }

            // 카카오 비밀번호 검증
            if (verifyKakaoPassword(kakaoPassword, teacher.getKakaoPasswordEncrypted())) {
                // 카카오톡 웹훅 서버에 로그인 (DB 검증 통과 후 바로 실제 로그인)
                boolean loginSuccess = kakaoService.loginToKakaoWebhook(kakaoId, kakaoPassword, UserRole.TEACHER);
                if (!loginSuccess) {
                    log.warn("카카오톡 웹훅 서버 로그인 실패: kakaoId={}", kakaoId);
                    throw new AuthException(ErrorStatus.KAKAO_LOGIN_FAIL);
                }

                // JWT 토큰 발급
                String accessToken = jwtTokenProvider.createAccessToken(teacher.getTeacherId(), "TEACHER");
                String refreshToken = jwtTokenProvider.createRefreshToken(teacher.getTeacherId(), "TEACHER");

                log.info("Teacher 로그인 성공: teacherId={}", teacher.getTeacherId());

                return AuthResponseDTO.KakaoLoginResponse.builder()
                        .userId(teacher.getTeacherId())
                        .role("TEACHER")
                        .name(teacher.getName())
                        .email(teacher.getKakaoId())
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build();
            } else {
                log.warn("카카오 비밀번호 불일치: kakaoId={}", kakaoId);
                throw new AuthException(ErrorStatus.KAKAO_LOGIN_FAIL);
            }
        }

        // 3. 둘 다 아니면 → 인증되지 않은 계정
        log.warn("인증되지 않은 카카오 계정: kakaoId={}", kakaoId);
        throw new AuthException(ErrorStatus.KAKAO_ACCOUNT_NOT_AUTHORIZED);
    }

    /**
     * 카카오 비밀번호 검증
     *
     * @param rawPassword 평문 비밀번호
     * @param encodedPassword 암호화된 비밀번호
     * @return 검증 성공 여부
     */
    private boolean verifyKakaoPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}






