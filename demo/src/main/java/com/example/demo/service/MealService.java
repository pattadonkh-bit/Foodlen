package com.example.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.advice.Advice;
import com.example.demo.advice.MealType;
import com.example.demo.advice.RecommendationService;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealRepository;
import com.example.demo.domain.MealSource;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;
import com.example.demo.nutrition.StatusCode;

@Service
@Transactional
public class MealService {

    private final ChildService children;
    private final MenuCatalog menus;
    private final RecommendationService recommendations;
    private final MealRepository meals;

    public MealService(ChildService children, MenuCatalog menus, RecommendationService recommendations,
                       MealRepository meals) {
        this.children = children;
        this.menus = menus;
        this.recommendations = recommendations;
        this.meals = meals;
    }

    /** ประเมินมื้ออาหารโดยไม่บันทึก */
    @Transactional(readOnly = true)
    public Advice advise(ChildOverview overview, MealType type, MenuItem menu, double portion) {
        StatusCode status = overview.status() != null ? overview.status().status() : StatusCode.NORMAL;
        return recommendations.evaluate(status, type, menu, portion, overview.dailyKcal());
    }

    public Meal record(Long childId, String menuCode, double portion, MealType type,
                       Double aiConfidence, MealSource source, LocalDate date) {
        validatePortion(portion);
        if (type == null || source == null) {
            throw new IllegalArgumentException("ข้อมูลมื้ออาหารไม่ครบ");
        }
        validateDate(date);
        MenuItem menu = menus.require(menuCode);
        ChildOverview ov = children.overview(childId);

        Meal m = new Meal();
        m.setChild(ov.child());
        LocalDate day = date == null ? LocalDate.now() : date;
        m.setEatenAt(LocalDateTime.of(day, LocalTime.now().withNano(0)));
        m.setAiConfidence(aiConfidence);
        m.setSource(source);
        evaluateInto(m, ov, menu, portion, type);
        return meals.save(m);
    }

    /**
     * แก้ไขมื้อที่บันทึกไว้ (เมนู สัดส่วน มื้อ วัน) แล้วประเมินใหม่ด้วยข้อมูลเด็กปัจจุบัน
     * ถ้าเปลี่ยนเมนู ถือว่าผู้ใช้แก้ผลเอง: มื้อที่เคยใช้ AI เป็น "AI + ยืนยัน" ส่วนมื้ออื่นเป็น "เลือกเอง"
     */
    public Meal update(Long mealId, String menuCode, double portion, MealType type, LocalDate date) {
        validatePortion(portion);
        if (type == null) {
            throw new IllegalArgumentException("ข้อมูลมื้ออาหารไม่ครบ");
        }
        validateDate(date);
        Meal m = get(mealId);
        MenuItem menu = menus.require(menuCode);
        ChildOverview ov = children.overview(m.getChild().getId());

        if (!menu.code().equals(m.getMenuCode())) {
            m.setSource(m.getAiConfidence() != null ? MealSource.AI_CONFIRMED : MealSource.MANUAL);
        }
        if (date != null) {
            m.setEatenAt(LocalDateTime.of(date, m.getEatenAt().toLocalTime()));
        }
        evaluateInto(m, ov, menu, portion, type);
        return m;
    }

    public void delete(Long mealId) {
        meals.delete(get(mealId));
    }

    /** ดึงมื้อ พร้อมตรวจว่าผู้ใช้มีสิทธิ์เข้าถึงเด็กเจ้าของมื้อนั้น */
    @Transactional(readOnly = true)
    public Meal get(Long mealId) {
        Meal m = meals.findById(mealId).orElseThrow(() -> new MealNotFoundException(mealId));
        children.get(m.getChild().getId());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Meal> recent(Long childId) {
        return meals.findTop10ByChildOrderByEatenAtDescIdDesc(children.get(childId));
    }

    /** มื้อของเด็กทั้งหมด (ล่าสุด 200 มื้อ) ใช้ในหน้าประวัติมื้อ */
    @Transactional(readOnly = true)
    public List<Meal> history(Long childId) {
        return meals.findTop200ByChildOrderByEatenAtDescIdDesc(children.get(childId));
    }

    private void evaluateInto(Meal m, ChildOverview ov, MenuItem menu, double portion, MealType type) {
        Advice advice = advise(ov, type, menu, portion);
        m.setMealType(type);
        m.setMenuCode(menu.code());
        m.setPortion(portion);
        m.setEnergyKcal(advice.energyKcal());
        m.setShareOfDaily(advice.shareOfDaily());
        m.setLevel(advice.level());
        m.setStatusAtMeal(ov.status() == null ? null : ov.status().status());
        String text = String.join("\n", advice.messages());
        m.setAdviceText(text.length() > 2000 ? text.substring(0, 2000) : text);
    }

    private static void validatePortion(double portion) {
        if (portion < 0.25 || portion > 10.0) {
            throw new IllegalArgumentException("สัดส่วนอาหารต้องอยู่ระหว่าง 0.25-10 หน่วยบริโภค");
        }
    }

    private static void validateDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("วันที่กินต้องไม่เป็นอนาคต");
        }
    }
}
