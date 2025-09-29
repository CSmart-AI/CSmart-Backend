//package Capstone.FOSSistant.global.security.principal;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@Transactional(readOnly = true)
//@RequiredArgsConstructor
//public class PrincipalDetailsService implements UserDetailsService {
//
//    private final MemberRepository memberRepository;
//
//    @Override
//    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {
//        Member member = memberRepository.findById(Long.parseLong(memberId))
//                .orElseThrow(() -> new AuthException(ErrorStatus.MEMBER_NOT_FOUND));
//
//        return new PrincipalDetails(member);
//    }
//}
