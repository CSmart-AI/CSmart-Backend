package Capstone.CSmart.global.security.filter;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import Capstone.CSmart.global.domain.entity.Admin;
import Capstone.CSmart.global.domain.entity.Teacher;
import Capstone.CSmart.global.domain.entity.UserEntity;
import Capstone.CSmart.global.repository.AdminRepository;
import Capstone.CSmart.global.repository.TeacherRepository;
import Capstone.CSmart.global.security.principal.PrincipalDetails;
import Capstone.CSmart.global.security.provider.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminRepository adminRepository;
    private final TeacherRepository teacherRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        
        // 카카오톡 웹훅 및 인증 불필요 경로는 JWT 검증 스킵
        if (shouldNotFilter(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String authorizationHeader = request.getHeader("Authorization");

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);

                if (jwtTokenProvider.isTokenValid(token)) {
                    Long userId = jwtTokenProvider.getId(token);
                    String role = jwtTokenProvider.getRole(token);
                    
                    // Role에 따라 다른 Repository에서 사용자 조회
                    UserEntity user = loadUserByRole(userId, role);

                    if (user != null) {
                        PrincipalDetails principalDetails = new PrincipalDetails(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        principalDetails, "", principalDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        throw new AuthException(ErrorStatus.MEMBER_NOT_FOUND);
                    }
                } else {
                    throw new AuthException(ErrorStatus.AUTH_INVALID_TOKEN);
                }
            }
            filterChain.doFilter(request, response);
        } catch (AuthException ex) {
            setJsonResponse(response, ex.getErrorReasonHttpStatus().getHttpStatus().value(),
                    ex.getErrorReason().getCode(),
                    ex.getErrorReason().getMessage());
        } catch (Exception ex) {
            setJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_SERVER_ERROR",
                    "예기치 않은 오류가 발생했습니다.");
        }
    }
    
    /**
     * Role에 따라 다른 Repository에서 사용자 조회
     */
    private UserEntity loadUserByRole(Long userId, String role) {
        return switch (role) {
            case "ADMIN" -> adminRepository.findById(userId)
                    .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_NOT_FOUND));
            case "TEACHER" -> teacherRepository.findById(userId)
                    .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_NOT_FOUND));
            default -> throw new AuthException(ErrorStatus.MEMBER_NOT_FOUND);
        };
    }
    
    /**
     * JWT 필터를 건너뛸 경로 확인
     */
    private boolean shouldNotFilter(String requestURI) {
        return requestURI.startsWith("/api/kakao/messages") ||
               requestURI.startsWith("/auth/") ||
               requestURI.startsWith("/api/gemini/") ||
               requestURI.startsWith("/swagger-ui") ||
               requestURI.startsWith("/v3/api-docs") ||
               requestURI.startsWith("/actuator");
    }

    // JSON 응답 설정
    private void setJsonResponse(HttpServletResponse response, int statusCode, String code, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");
        String jsonResponse = String.format("{\"isSuccess\": false, \"code\": \"%s\", \"message\": \"%s\"}", code, message);
        response.getWriter().write(jsonResponse);
    }
}