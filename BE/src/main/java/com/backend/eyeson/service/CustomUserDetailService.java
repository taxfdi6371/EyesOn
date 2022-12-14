package com.backend.eyeson.service;

import com.backend.eyeson.entity.UserEntity;
import com.backend.eyeson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Component("userDetailsService")
public class CustomUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;


    @Transactional
    @Override
    public UserDetails loadUserByUsername(String email)  {
        // login 시에 db에서 유저정보와, 권한정보를 같이 가져옴

        // 1. 매니저 테이블에서 찾고
        if(userRepository.findByUserEmail(email).isPresent()){
            return userRepository.findByUserEmail(email)
                    .map(user -> createUser(email,user))
                    .get();
        }

        else{
            return null;
        }
    }

    private org.springframework.security.core.userdetails.User createUser(String email, UserEntity user) {
//        List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream()
//                .map(authority -> new SimpleGrantedAuthority(authority.getAuthorityName()))
//                .collect(Collectors.toList());
        // 1의 정보를 기반으로 userdetails.User객체를 생성해서 return
        GrantedAuthority grantedAuthority = new SimpleGrantedAuthority(user.getAuthority().toString());
        return new org.springframework.security.core.userdetails.User(user.getUserEmail(),
                user.getUserPass(),
                Collections.singleton(grantedAuthority));
    }
}
