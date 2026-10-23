package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.AppUserRepository;
import com.example.demo.domain.Role;

/**
 * สร้างบัญชีเริ่มต้นตอนเปิดระบบ (ไม่ทับบัญชีที่มีอยู่แล้ว)
 * <ul>
 *   <li>บัญชีครู/นักโภชนาการจริง: ตั้ง foodlen.bootstrap.staff-username และ foodlen.bootstrap.staff-password
 *       (แนะนำส่งรหัสผ่านผ่านตัวแปรสภาพแวดล้อม เช่น FOODLEN_BOOTSTRAP_STAFF_PASSWORD อย่าเขียนลงไฟล์ที่ commit)</li>
 *   <li>บัญชีสาธิต: เมื่อ foodlen.seed-demo=true จะสร้าง teacher@foodlen.demo / parent1@foodlen.demo / parent2@foodlen.demo รหัสผ่านตาม {@link #DEMO_PASSWORD}
 *       (ใช้เพื่อสาธิตเท่านั้น ห้ามเปิดใช้บนระบบจริง)</li>
 * </ul>
 */
@Component
@Order(1)
public class AccountBootstrap implements CommandLineRunner {

    /** รหัสผ่านของบัญชีสาธิต (ข้อมูลจำลองเท่านั้น) */
    public static final String DEMO_PASSWORD = "Foodlen#2569";
    /** อีเมลของบัญชีสาธิต (โดเมน .demo ไม่มีอยู่จริง) */
    public static final String DEMO_TEACHER = "teacher@foodlen.demo";
    public static final String DEMO_PARENT1 = "parent1@foodlen.demo";
    public static final String DEMO_PARENT2 = "parent2@foodlen.demo";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String staffUsername;
    private final String staffPassword;
    private final String staffName;
    private final boolean seedDemo;

    public AccountBootstrap(AppUserRepository users, PasswordEncoder encoder,
                            @Value("${foodlen.bootstrap.staff-username:}") String staffUsername,
                            @Value("${foodlen.bootstrap.staff-password:}") String staffPassword,
                            @Value("${foodlen.bootstrap.staff-display-name:ครู/นักโภชนาการ}") String staffName,
                            @Value("${foodlen.seed-demo:false}") boolean seedDemo) {
        this.users = users;
        this.encoder = encoder;
        this.staffUsername = staffUsername;
        this.staffPassword = staffPassword;
        this.staffName = staffName;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(String... args) {
        if (!staffUsername.isBlank() && !staffPassword.isBlank()) {
            ensure(staffUsername.trim().toLowerCase(java.util.Locale.ROOT), staffPassword, staffName, Role.STAFF);
        }
        if (seedDemo) {
            ensure(DEMO_TEACHER, DEMO_PASSWORD, "ครูตัวอย่าง", Role.STAFF);
            ensure(DEMO_PARENT1, DEMO_PASSWORD, "ผู้ปกครองตัวอย่าง 1", Role.PARENT);
            ensure(DEMO_PARENT2, DEMO_PASSWORD, "ผู้ปกครองตัวอย่าง 2", Role.PARENT);
        }
    }

    private void ensure(String username, String password, String displayName, Role role) {
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, encoder.encode(password), displayName, role));
        }
    }
}
