package com.example.demo.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.demo.domain.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ชื่อผู้ใช้ (อีเมล) เก็บเป็นตัวพิมพ์เล็ก จึงไม่สนตัวพิมพ์ใหญ่-เล็กตอนล็อกอิน
        return users.findByUsername(username == null ? "" : username.trim().toLowerCase(Locale.ROOT))
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("ไม่พบผู้ใช้"));
    }
}
