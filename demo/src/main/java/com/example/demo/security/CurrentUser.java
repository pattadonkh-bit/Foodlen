package com.example.demo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** อ่านผู้ใช้ที่ล็อกอินอยู่จาก Spring Security (null ถ้ายังไม่ล็อกอิน) */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUserPrincipal get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && a.getPrincipal() instanceof AppUserPrincipal p) {
            return p;
        }
        return null;
    }
}
