package com.example.demo.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.demo.security.AppUserPrincipal;
import com.example.demo.security.CurrentUser;

/** ส่งผู้ใช้ที่ล็อกอินอยู่ให้ทุกหน้า (ใช้แสดงชื่อ ปุ่มออกจากระบบ และเมนูของครู) */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("currentUser")
    public AppUserPrincipal currentUser() {
        return CurrentUser.get();
    }
}
