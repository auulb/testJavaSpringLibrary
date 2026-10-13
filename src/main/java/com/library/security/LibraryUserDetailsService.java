package com.library.security;

import com.library.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从 users.json 加载登录用户。
 */
@Service
public class LibraryUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LibraryUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(LibraryUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
    }
}
