package com.example.demo.config;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.demo.advice.MealType;
import com.example.demo.domain.AppUser;
import com.example.demo.domain.AppUserRepository;
import com.example.demo.domain.Child;
import com.example.demo.domain.MealSource;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;
import com.example.demo.nutrition.Sex;
import com.example.demo.security.AppUserPrincipal;
import com.example.demo.service.ChildService;
import com.example.demo.service.MealService;

/**
 * สร้างข้อมูลตัวอย่าง (เด็กสมมติ 4 คน + มื้ออาหาร 14 วัน) สำหรับสาธิตกราฟและสถิติ
 * เปิดใช้ด้วย foodlen.seed-demo=true และทำงานเฉพาะเมื่อฐานข้อมูลยังไม่มีเด็ก
 * (ชื่อทุกคนมี "(ตัวอย่าง)" ต่อท้าย; ข้อมูลทั้งหมดเป็นข้อมูลจำลอง ไม่ใช่เด็กจริง)
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "foodlen.seed-demo", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private record Kid(String name, Sex sex, LocalDate birth, String classroom, String owner,
                       double[][] growth, String[] favourites) {
    }

    private final ChildService children;
    private final MealService meals;
    private final MenuCatalog menus;
    private final AppUserRepository users;

    public DemoDataSeeder(ChildService children, MealService meals, MenuCatalog menus, AppUserRepository users) {
        this.children = children;
        this.meals = meals;
        this.menus = menus;
        this.users = users;
    }

    @Override
    public void run(String... args) {
        // ทำงานในนามครูตัวอย่าง เพื่อให้ผ่านการตรวจสิทธิ์ใน ChildService (ข้อมูลที่สร้างผูกกับผู้ปกครองตัวอย่างแต่ละคน)
        AppUser teacher = users.findByUsername(AccountBootstrap.DEMO_TEACHER).orElse(null);
        if (teacher == null) {
            return;
        }
        AppUserPrincipal asTeacher = new AppUserPrincipal(teacher);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(asTeacher, null, asTeacher.getAuthorities()));
        try {
            seed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seed() {
        if (!children.listOverviews().isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        // growth: {เดือนก่อนวันนี้, น้ำหนัก, ส่วนสูง}
        List<Kid> kids = List.of(
                new Kid("น้องมะลิ (ตัวอย่าง)", Sex.F, today.minusYears(9).minusMonths(7), "ป.4/1", AccountBootstrap.DEMO_PARENT1,
                        new double[][] {{18, 31, 122}, {12, 34.5, 126}, {6, 37, 129}, {0, 40, 132}},
                        new String[] {"KhaoManKai", "KaiThot", "KhaoKhaMu", "PhatThai", "KhaoNiewMaMuang"}),
                new Kid("น้องต้นกล้า (ตัวอย่าง)", Sex.M, today.minusYears(10).minusMonths(2), "ป.4/1", AccountBootstrap.DEMO_PARENT1,
                        new double[][] {{12, 20, 126}, {6, 21, 129}, {0, 22, 132}},
                        new String[] {"TomYam", "KuaitiaoRuea", "SomTam", "KaengKhiaoWan", "PhatKaprao"}),
                new Kid("น้องใบเตย (ตัวอย่าง)", Sex.F, today.minusYears(11).minusMonths(4), "ป.5/2", AccountBootstrap.DEMO_PARENT2,
                        new double[][] {{12, 31, 138}, {6, 33, 141}, {0, 35, 145}},
                        new String[] {"PhatThai", "KhaoManKai", "KaengKhiaoWan", "KuaitiaoRuea", "PhatKaprao"}),
                new Kid("น้องภูมิ (ตัวอย่าง)", Sex.M, today.minusYears(8).minusMonths(9), "ป.3/2", AccountBootstrap.DEMO_PARENT2,
                        new double[][] {{6, 29, 123}, {0, 33, 127}},
                        new String[] {"KaiThot", "KhaoNiewMaMuang", "KhaoKhaMu", "PhatKaprao", "KhaoManKai"}));

        Random rnd = new Random(2569);
        for (Kid k : kids) {
            double[][] g = k.growth();
            Long ownerId = users.findByUsername(k.owner()).map(AppUser::getId).orElse(null);
            Child c = children.create(ownerId, k.name(), k.sex(), k.birth(), k.classroom(), true,
                    today.minusMonths((long) g[0][0]), g[0][1], g[0][2]);
            for (int i = 1; i < g.length; i++) {
                children.addMeasurement(c.getId(), today.minusMonths((long) g[i][0]), g[i][1], g[i][2]);
            }
            for (int day = 13; day >= 0; day--) {
                if (rnd.nextInt(10) == 0) {
                    continue; // บางวันไม่ได้บันทึก
                }
                MealType[] types = {MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER};
                int count = 1 + rnd.nextInt(3);
                for (int t = 0; t < count; t++) {
                    addMeal(c.getId(), k, types[t], today.minusDays(day), rnd);
                }
                if (rnd.nextInt(3) == 0) {
                    addMeal(c.getId(), k, MealType.SNACK, today.minusDays(day), rnd);
                }
            }
        }
    }

    private void addMeal(Long childId, Kid k, MealType type, LocalDate date, Random rnd) {
        List<MenuItem> all = menus.all();
        String code = rnd.nextInt(10) < 7
                ? k.favourites()[rnd.nextInt(k.favourites().length)]
                : all.get(rnd.nextInt(all.size())).code();
        double[] portions = {0.5, 1, 1, 1, 1.5};
        double portion = portions[rnd.nextInt(portions.length)];
        int r = rnd.nextInt(100);
        MealSource source;
        Double conf;
        if (r < 55) {
            source = MealSource.AI_AUTO;
            conf = 0.80 + rnd.nextDouble() * 0.17;
        } else if (r < 85) {
            source = MealSource.AI_CONFIRMED;
            conf = 0.45 + rnd.nextDouble() * 0.34;
        } else {
            source = MealSource.MANUAL;
            conf = null;
        }
        meals.record(childId, code, portion, type, conf, source, date);
    }
}
