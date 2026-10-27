package com.example.demo.controller;

import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.advice.Advice;
import com.example.demo.advice.MealType;
import com.example.demo.domain.Meal;
import com.example.demo.domain.MealSource;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;
import com.example.demo.service.AiPrediction;
import com.example.demo.service.AiResult;
import com.example.demo.service.AiServiceException;
import com.example.demo.service.ChildOverview;
import com.example.demo.service.ChildService;
import com.example.demo.service.MealService;
import com.example.demo.service.YoloService;

/**
 * ขั้นตอนบันทึกมื้ออาหาร: อัปโหลดภาพ -> AI จำแนก -> (ถ้าความมั่นใจต่ำกว่าเกณฑ์ ให้ผู้ใช้ยืนยันเมนู)
 * -> เลือกสัดส่วน -> บันทึกและแสดงผลประเมิน
 */
@Controller
public class MealController {

    public record Candidate(String code, String nameTh, double confidence, boolean hasEnergy,
                            Double energyKcal, String serving) {
    }

    private final ChildService children;
    private final MealService meals;
    private final MenuCatalog menus;
    private final YoloService ai;
    private final double threshold;

    public MealController(ChildService children, MealService meals, MenuCatalog menus, YoloService ai,
                          @Value("${foodlen.confidence-threshold:0.80}") double threshold) {
        this.children = children;
        this.meals = meals;
        this.menus = menus;
        this.ai = ai;
        this.threshold = threshold;
    }

    @GetMapping("/children/{id}/meals/new")
    public String newMeal(@PathVariable Long id, Model model) {
        ai.wakeAsync(); // ปลุกบริการ AI ล่วงหน้า (แพ็กเกจฟรีหลับเมื่อไม่มีผู้ใช้) ระหว่างที่ผู้ใช้เลือกภาพ
        fillBase(model, id);
        return "meal-new";
    }

    @PostMapping("/children/{id}/meals/analyze")
    public String analyze(@PathVariable Long id,
                          @RequestParam("image") MultipartFile image,
                          @RequestParam MealType mealType,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eatenDate,
                          Model model) {
        fillBase(model, id);
        model.addAttribute("mealType", mealType);
        model.addAttribute("eatenDate", eatenDate == null ? LocalDate.now() : eatenDate);

        if (image.isEmpty()) {
            model.addAttribute("error", "กรุณาเลือกรูปอาหารก่อน");
            return "meal-new";
        }
        try {
            AiResult result = ai.predict(image);
            List<Candidate> candidates = new ArrayList<>();
            for (AiPrediction p : result.predictions()) {
                menus.find(p.code()).ifPresent(m ->
                        candidates.add(new Candidate(m.code(), m.nameTh(), p.confidence(), m.hasEnergy(),
                                m.energyKcal(), m.serving())));
            }
            if (candidates.isEmpty()) {
                model.addAttribute("error", "AI จำแนกเป็นเมนูที่ระบบยังไม่รองรับ กรุณาเลือกเมนูเอง");
                return "meal-new";
            }
            Candidate top = candidates.get(0);
            model.addAttribute("candidates", candidates.stream().limit(3).toList());
            model.addAttribute("topCode", top.code());
            model.addAttribute("topConfidence", top.confidence());
            model.addAttribute("confident", top.confidence() >= threshold);
            model.addAttribute("modelName", result.model());
            model.addAttribute("imageData", dataUri(image));
            model.addAttribute("aiUsed", true);
            return "meal-confirm";
        } catch (AiServiceException e) {
            model.addAttribute("error", e.getMessage() + " — เลือกเมนูเองได้ที่ปุ่ม \"เลือกเมนูเอง\"");
            return "meal-new";
        }
    }

    @GetMapping("/children/{id}/meals/manual")
    public String manual(@PathVariable Long id,
                         @RequestParam(defaultValue = "LUNCH") MealType mealType,
                         Model model) {
        fillBase(model, id);
        model.addAttribute("mealType", mealType);
        model.addAttribute("eatenDate", LocalDate.now());
        model.addAttribute("aiUsed", false);
        model.addAttribute("confident", false);
        return "meal-confirm";
    }

    @PostMapping("/children/{id}/meals")
    public String save(@PathVariable Long id,
                       @RequestParam String menuCode,
                       @RequestParam double portion,
                       @RequestParam MealType mealType,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eatenDate,
                       @RequestParam(required = false) String aiTop,
                       @RequestParam(required = false) Double aiConfidence,
                       RedirectAttributes redirect) {
        MealSource source;
        if (aiConfidence == null || aiTop == null || aiTop.isBlank()) {
            source = MealSource.MANUAL;
        } else if (aiTop.equals(menuCode) && aiConfidence >= threshold) {
            source = MealSource.AI_AUTO;
        } else {
            source = MealSource.AI_CONFIRMED;
        }
        try {
            Meal m = meals.record(id, menuCode, portion, mealType, aiConfidence, source, eatenDate);
            return "redirect:/children/" + id + "/meals/" + m.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/children/" + id + "/meals/new";
        }
    }

    @GetMapping("/children/{id}/meals/{mealId}")
    public String result(@PathVariable Long id, @PathVariable Long mealId, Model model) {
        ChildOverview ov = children.overview(id);
        Meal meal = meals.get(mealId);
        if (!meal.getChild().getId().equals(id)) {
            throw new IllegalArgumentException("มื้ออาหารนี้ไม่ใช่ของเด็กคนนี้");
        }
        MenuItem menu = menus.require(meal.getMenuCode());
        model.addAttribute("ov", ov);
        model.addAttribute("meal", meal);
        model.addAttribute("menu", menu);
        model.addAttribute("messages", meal.getAdviceText() == null || meal.getAdviceText().isBlank()
                ? List.of() : List.of(meal.getAdviceText().split("\n")));
        return "meal-result";
    }

    // ---------- ประวัติ แก้ไข ลบมื้อ ----------

    @GetMapping("/children/{id}/meals")
    public String history(@PathVariable Long id, Model model) {
        model.addAttribute("ov", children.overview(id));
        model.addAttribute("meals", meals.history(id));
        model.addAttribute("menuByCode", menuByCode());
        return "meal-list";
    }

    @GetMapping("/children/{id}/meals/{mealId}/edit")
    public String editForm(@PathVariable Long id, @PathVariable Long mealId, Model model) {
        fillEdit(model, id, ownedMeal(id, mealId));
        return "meal-edit";
    }

    @PostMapping("/children/{id}/meals/{mealId}/edit")
    public String edit(@PathVariable Long id, @PathVariable Long mealId,
                       @RequestParam String menuCode,
                       @RequestParam double portion,
                       @RequestParam MealType mealType,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eatenDate,
                       Model model) {
        Meal meal = ownedMeal(id, mealId);
        try {
            meals.update(mealId, menuCode, portion, mealType, eatenDate);
            return "redirect:/children/" + id + "/meals/" + mealId;
        } catch (IllegalArgumentException e) {
            fillEdit(model, id, meal);
            model.addAttribute("error", e.getMessage());
            return "meal-edit";
        }
    }

    @PostMapping("/children/{id}/meals/{mealId}/delete")
    public String delete(@PathVariable Long id, @PathVariable Long mealId, RedirectAttributes redirect) {
        ownedMeal(id, mealId);
        meals.delete(mealId);
        redirect.addFlashAttribute("message", "ลบมื้ออาหารแล้ว");
        return "redirect:/children/" + id;
    }

    /** ดึงมื้อและตรวจว่าเป็นของเด็กในที่อยู่ URL จริง (กันการเดารหัสมื้อของเด็กคนอื่น) */
    private Meal ownedMeal(Long childId, Long mealId) {
        Meal meal = meals.get(mealId);
        if (!meal.getChild().getId().equals(childId)) {
            throw new IllegalArgumentException("มื้ออาหารนี้ไม่ใช่ของเด็กคนนี้");
        }
        return meal;
    }

    private void fillEdit(Model model, Long id, Meal meal) {
        fillBase(model, id);
        model.addAttribute("meal", meal);
        model.addAttribute("eatenDate", meal.getEatenAt().toLocalDate());
    }

    private Map<String, MenuItem> menuByCode() {
        return menus.all().stream().collect(Collectors.toMap(MenuItem::code, Function.identity()));
    }

    private void fillBase(Model model, Long id) {
        ChildOverview ov = children.overview(id);
        model.addAttribute("ov", ov);
        // เรียงชื่อเมนูตามลำดับตัวอักษรไทย เพื่อให้หาในรายการยาวๆ (200+ เมนู) ได้ง่าย
        Collator thai = Collator.getInstance(Locale.forLanguageTag("th"));
        model.addAttribute("menus", menus.all().stream()
                .sorted(Comparator.comparing(MenuItem::nameTh, thai)).toList());
        model.addAttribute("mealTypes", MealType.values());
        model.addAttribute("threshold", threshold);
        model.addAttribute("today", LocalDate.now());
    }

    private static String dataUri(MultipartFile image) {
        try {
            String type = image.getContentType() != null && image.getContentType().startsWith("image/")
                    ? image.getContentType() : "image/jpeg";
            return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            return null;
        }
    }
}
