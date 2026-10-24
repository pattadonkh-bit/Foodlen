package com.example.demo.controller;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.AppUserRepository;
import com.example.demo.domain.Role;
import com.example.demo.security.CurrentUser;

/** หน้าล็อกอิน (Spring Security จัดการการตรวจรหัสผ่าน) และสมัครสมาชิกสำหรับผู้ปกครอง */
@Controller
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final int MIN_PASSWORD = 8;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String registered,
                        Model model) {
        if (CurrentUser.get() != null) {
            return "redirect:/";
        }
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("registered", registered != null);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return CurrentUser.get() != null ? "redirect:/" : "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String displayName,
                           @RequestParam String password,
                           @RequestParam String confirm,
                           Model model, RedirectAttributes redirect) {
        // อีเมลคือชื่อสำหรับล็อกอิน เก็บเป็นตัวพิมพ์เล็กเพื่อไม่ให้ซ้ำเพราะตัวพิมพ์ใหญ่-เล็ก
        String u = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String d = displayName == null ? "" : displayName.trim();
        String error = null;
        if (u.length() > 100 || !EMAIL.matcher(u).matches()) {
            error = "กรุณากรอกอีเมลให้ถูกต้อง เช่น name@example.com";
        } else if (d.isEmpty() || d.length() > 100) {
            error = "กรุณากรอกชื่อที่แสดง (ไม่เกิน 100 ตัวอักษร)";
        } else if (password == null || password.length() < MIN_PASSWORD) {
            error = "รหัสผ่านต้องยาวอย่างน้อย " + MIN_PASSWORD + " ตัวอักษร";
        } else if (password.length() > 72) {
            error = "รหัสผ่านยาวเกินไป (ไม่เกิน 72 ตัวอักษร)";
        } else if (!password.equals(confirm)) {
            error = "รหัสผ่านและการยืนยันไม่ตรงกัน";
        } else if (users.existsByUsername(u)) {
            error = "อีเมลนี้สมัครไว้แล้ว";
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("email", u);
            model.addAttribute("displayName", d);
            return "register";
        }
        // สมัครเองได้เฉพาะบทบาทผู้ปกครอง บัญชีครู/นักโภชนาการสร้างโดยผู้ดูแลระบบ (ดู README)
        try {
            users.save(new AppUser(u, encoder.encode(password), d, Role.PARENT));
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error", "อีเมลนี้สมัครไว้แล้ว");
            model.addAttribute("email", u);
            model.addAttribute("displayName", d);
            return "register";
        }
        redirect.addAttribute("registered", "1");
        return "redirect:/login";
    }
}
