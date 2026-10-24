package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.demo.security.AppUserPrincipal;
import com.example.demo.security.CurrentUser;

/** ส่งข้อมูลที่ทุกหน้าใช้ร่วมกัน: ผู้ใช้ที่ล็อกอินอยู่ (แสดงชื่อ ปุ่มออกจากระบบ เมนูของครู) และชื่อเว็บ */
@ControllerAdvice
public class GlobalModelAdvice {

    private final String brand;

    public GlobalModelAdvice(@Value("${foodlen.brand-name:NutriKid}") String brand) {
        this.brand = brand;
    }

    @ModelAttribute("currentUser")
    public AppUserPrincipal currentUser() {
        return CurrentUser.get();
    }

    @ModelAttribute("brand")
    public String brand() {
        return brand;
    }
}
