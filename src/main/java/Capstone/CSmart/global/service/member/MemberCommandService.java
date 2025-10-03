package Capstone.CSmart.global.service.member;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import Capstone.CSmart.global.domain.entity.Member;
import Capstone.CSmart.global.repository.MemberRepository;
import Capstone.CSmart.global.security.provider.JwtTokenProvider;
import Capstone.CSmart.global.web.Auth.AuthRequestDTO;
import Capstone.CSmart.global.web.Auth.AuthResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponseDTO.SignUpResponse signUp(AuthRequestDTO.SignUpRequest request) {
        // 이메일 중복 체크
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new AuthException(ErrorStatus.MEMBER_EMAIL_ALREADY_EXISTS);
        }

        // 닉네임 중복 체크 (닉네임이 제공된 경우에만)
        if (request.getNickname() != null && memberRepository.existsByNickname(request.getNickname())) {
            throw new AuthException(ErrorStatus.MEMBER_NICKNAME_ALREADY_EXISTS);
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 회원 생성
        Member member = Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .name(request.getName())
                .nickname(request.getNickname())
                .build();

        Member savedMember = memberRepository.save(member);

        return AuthResponseDTO.SignUpResponse.builder()
                .memberId(savedMember.getId())
                .email(savedMember.getEmail())
                .name(savedMember.getName())
                .nickname(savedMember.getNickname())
                .createdAt(savedMember.getCreatedAt())
                .build();
    }

    public AuthResponseDTO.LoginResponse login(AuthRequestDTO.LoginRequest request) {
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_LOGIN_FAIL));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new AuthException(ErrorStatus.MEMBER_LOGIN_FAIL);
        }

        // 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        // 리프레시 토큰 저장
        member.updateRefreshToken(refreshToken);

        return AuthResponseDTO.LoginResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .name(member.getName())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponseDTO.RefreshTokenResponse refreshToken(AuthRequestDTO.RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 토큰 유효성 검증
        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
            throw new AuthException(ErrorStatus.AUTH_INVALID_TOKEN);
        }

        // 토큰에서 회원 ID 추출
        Long memberId = jwtTokenProvider.getId(refreshToken);

        // 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_NOT_FOUND));

        // 저장된 리프레시 토큰과 비교
        if (!refreshToken.equals(member.getRefreshToken())) {
            throw new AuthException(ErrorStatus.NOT_EQUAL_TOKEN);
        }

        // 새로운 토큰 생성
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);

        // 새로운 리프레시 토큰 저장
        member.updateRefreshToken(newRefreshToken);

        return AuthResponseDTO.RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void logout(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_NOT_FOUND));

        // 리프레시 토큰 제거
        member.clearRefreshToken();
    }
}
