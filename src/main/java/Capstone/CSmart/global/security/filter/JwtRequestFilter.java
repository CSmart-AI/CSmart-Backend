package Capstone.CSmart.global.security.filter;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import Capstone.CSmart.global.security.principal.PrincipalDetailsService;
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
    private final PrincipalDetailsService principalDetailsService;

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
                    UserDetails userDetails =
                            principalDetailsService.loadUserByUsername(userId.toString());

                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, "", userDetails.getAuthorities());
                        SecurityContextHolder.getContext()
                                .setAuthentication(usernamePasswordAuthenticationToken);
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