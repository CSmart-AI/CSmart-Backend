package Capstone.CSmart.global.security.handler.resolver;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.AuthException;
import Capstone.CSmart.global.security.annotation.AuthRole;
import Capstone.CSmart.global.security.principal.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthRoleArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthRole.class) 
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName().equals("anonymousUser")) {
            throw new AuthException(ErrorStatus._UNAUTHORIZED);
        }

        if (authentication.getPrincipal() instanceof PrincipalDetails principalDetails) {
            return principalDetails.getRole();
        }

        throw new AuthException(ErrorStatus.MEMBER_NOT_FOUND);
    }
}




