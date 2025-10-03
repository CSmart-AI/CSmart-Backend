package Capstone.CSmart.global.security.provider;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

   private final SecretKey secretKey;
   private final Long accessTokenValidityMilliseconds;
   private final Long refreshTokenValidityMilliseconds;

   public JwtTokenProvider(
           @Value("${jwt.secret}") final String secretKey,
           @Value("${jwt.access-token-validity}") final Long accessTokenValidityMilliseconds,
           @Value("${jwt.refresh-token-validity}") final Long refreshTokenValidityMilliseconds) {
       this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
       this.accessTokenValidityMilliseconds = accessTokenValidityMilliseconds;
       this.refreshTokenValidityMilliseconds = refreshTokenValidityMilliseconds;
   }

   public String createAccessToken(Long memberId) {
       return createToken(memberId, accessTokenValidityMilliseconds);
   }

   public String createRefreshToken(Long memberId) {
       return createToken(memberId, refreshTokenValidityMilliseconds);
   }

    private String createToken(Long memberId, Long validityMilliseconds) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime tokenValidity = now.plusSeconds(validityMilliseconds / 1000);

        return Jwts.builder()
                .claim("id", memberId)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(tokenValidity.toInstant()))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getId(String token) {
        return getClaims(token).getPayload().get("id", Long.class);
    }

    public boolean isTokenValid(String token) {
        try {
            Jws<Claims> claims = getClaims(token);
            Date expiredDate = claims.getPayload().getExpiration();
            Date now = new Date();
            return expiredDate.after(now);
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorStatus.AUTH_EXPIRED_TOKEN);
        } catch (SecurityException
                 | MalformedJwtException
                 | UnsupportedJwtException
                 | IllegalArgumentException e) {
            throw new AuthException(ErrorStatus.AUTH_INVALID_TOKEN);
        }
    }

    private Jws<Claims> getClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
    }
}
