package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ทุกหน้าต้องล็อกอิน ยกเว้นหน้าล็อกอิน/สมัครสมาชิกและไฟล์ static
 * หน้า /staff/** เฉพาะครู/นักโภชนาการ ส่วนสิทธิ์ดูข้อมูลเด็กแต่ละคนตรวจใน ChildService
 * (ผู้ปกครองเห็นเฉพาะเด็กที่ตัวเองลงทะเบียน) CSRF เปิดตามค่าเริ่มต้นของ Spring Security
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/staff/**").hasRole("STAFF")
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
