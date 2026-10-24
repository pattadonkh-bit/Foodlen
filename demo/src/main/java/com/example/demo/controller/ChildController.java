package com.example.demo.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.domain.Child;
import com.example.demo.domain.Measurement;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.menu.MenuItem;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.ChildOverview;
import com.example.demo.service.ChildService;
import com.example.demo.service.MealService;

@Controller
public class ChildController {

    private final ChildService children;
    private final MealService meals;
    private final MenuCatalog menus;

    public ChildController(ChildService children, MealService meals, MenuCatalog menus) {
        this.children = children;
        this.meals = meals;
        this.menus = menus;
    }

    @GetMapping("/children/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ChildForm());
        return "child-form";
    }

    @PostMapping("/children")
    public String create(@ModelAttribute("form") ChildForm form, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("error", "ข้อมูลที่กรอกไม่ถูกต้อง กรุณาตรวจสอบวันที่และตัวเลข");
            return "child-form";
        }
        try {
            Child c = children.create(CurrentUser.get().id(), form.getName(), form.getSex(), form.getBirthDate(),
                    form.getClassroom(), form.isConsent(), form.getMeasuredOn(),
                    required(form.getWeightKg(), "น้ำหนัก"), required(form.getHeightCm(), "ส่วนสูง"));
            return "redirect:/children/" + c.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "child-form";
        }
    }

    @GetMapping("/children/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ChildOverview ov = children.overview(id);
        model.addAttribute("ov", ov);
        model.addAttribute("history", children.history(ov.child()));
        model.addAttribute("recentMeals", meals.recent(id));
        Map<String, MenuItem> byCode = menus.all().stream()
                .collect(Collectors.toMap(MenuItem::code, Function.identity()));
        model.addAttribute("menuByCode", byCode);
        model.addAttribute("today", LocalDate.now());
        return "child-detail";
    }

    // ---------- แก้ไขข้อมูลเด็ก ----------

    @GetMapping("/children/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Child c = children.get(id);
        ChildForm form = new ChildForm();
        form.setName(c.getName());
        form.setSex(c.getSex());
        form.setBirthDate(c.getBirthDate());
        form.setClassroom(c.getClassroom());
        model.addAttribute("form", form);
        model.addAttribute("child", c);
        return "child-edit";
    }

    @PostMapping("/children/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute("form") ChildForm form, BindingResult binding,
                       Model model, RedirectAttributes redirect) {
        Child c = children.get(id);
        if (binding.hasErrors()) {
            model.addAttribute("child", c);
            model.addAttribute("error", "ข้อมูลที่กรอกไม่ถูกต้อง กรุณาตรวจสอบวันที่");
            return "child-edit";
        }
        try {
            children.update(id, form.getName(), form.getSex(), form.getBirthDate(), form.getClassroom());
            redirect.addFlashAttribute("message", "แก้ไขข้อมูลเด็กแล้ว");
            return "redirect:/children/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("child", c);
            model.addAttribute("error", e.getMessage());
            return "child-edit";
        }
    }

    @PostMapping("/children/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        children.delete(id);
        redirect.addFlashAttribute("message", "ลบข้อมูลเด็กและประวัติทั้งหมดแล้ว");
        return "redirect:/";
    }

    // ---------- น้ำหนักและส่วนสูง ----------

    @PostMapping("/children/{id}/measurements")
    public String addMeasurement(@PathVariable Long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measuredOn,
                                 @RequestParam Double weightKg, @RequestParam Double heightCm,
                                 RedirectAttributes redirect) {
        try {
            children.addMeasurement(id, measuredOn, weightKg, heightCm);
            redirect.addFlashAttribute("message", "บันทึกน้ำหนักและส่วนสูงใหม่แล้ว");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/children/" + id;
    }

    @GetMapping("/children/{id}/measurements/{mid}/edit")
    public String editMeasurementForm(@PathVariable Long id, @PathVariable Long mid, Model model) {
        Measurement m = children.getMeasurement(id, mid);
        model.addAttribute("ov", children.overview(id));
        model.addAttribute("m", m);
        model.addAttribute("today", LocalDate.now());
        return "measurement-edit";
    }

    @PostMapping("/children/{id}/measurements/{mid}/edit")
    public String editMeasurement(@PathVariable Long id, @PathVariable Long mid,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measuredOn,
                                  @RequestParam Double weightKg, @RequestParam Double heightCm,
                                  Model model, RedirectAttributes redirect) {
        try {
            children.updateMeasurement(id, mid, measuredOn, weightKg, heightCm);
            redirect.addFlashAttribute("message", "แก้ไขข้อมูลการวัดแล้ว");
            return "redirect:/children/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("ov", children.overview(id));
            model.addAttribute("m", children.getMeasurement(id, mid));
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("error", e.getMessage());
            return "measurement-edit";
        }
    }

    @PostMapping("/children/{id}/measurements/{mid}/delete")
    public String deleteMeasurement(@PathVariable Long id, @PathVariable Long mid, RedirectAttributes redirect) {
        children.deleteMeasurement(id, mid);
        redirect.addFlashAttribute("message", "ลบข้อมูลการวัดแล้ว");
        return "redirect:/children/" + id;
    }

    private static double required(Double v, String label) {
        if (v == null) {
            throw new IllegalArgumentException("กรุณากรอก" + label);
        }
        return v;
    }
}
