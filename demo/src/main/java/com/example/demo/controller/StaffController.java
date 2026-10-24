package com.example.demo.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.demo.service.ClassOverview;
import com.example.demo.service.ClassOverviewService;
import com.example.demo.service.ClassRow;

/** หน้าภาพรวมเด็กทั้งหมดสำหรับครู/นักโภชนาการ (จำกัดสิทธิ์ใน SecurityConfig: /staff/** เฉพาะ STAFF) */
@Controller
public class StaffController {

    private final ClassOverviewService overview;

    public StaffController(ClassOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/staff/class")
    public String classOverview(Model model) {
        model.addAttribute("co", overview.build());
        return "staff-class";
    }

    /** ส่งออกตารางเป็น CSV (UTF-8 มี BOM เพื่อให้ Excel อ่านภาษาไทยได้) */
    @GetMapping("/staff/class.csv")
    public ResponseEntity<byte[]> csv() {
        ClassOverview co = overview.build();
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("ชื่อ,ชั้น/ห้อง,เพศ,อายุ,ภาวะโภชนาการ,BMI,z-score,วัดล่าสุด,มื้อใน 7 วัน,ไฟแดงใน 7 วัน,"
                + "บันทึกมื้อล่าสุด,ระดับความเสี่ยง,เหตุผล\n");
        for (ClassRow r : co.rows()) {
            var ov = r.ov();
            var st = ov.status();
            sb.append(cell(ov.child().getName())).append(',')
                    .append(cell(ov.child().getClassroom())).append(',')
                    .append(cell(ov.child().getSex().getLabel())).append(',')
                    .append(cell(ov.getAgeText())).append(',')
                    .append(cell(st == null ? "" : st.status().getLabel())).append(',')
                    .append(st == null ? "" : String.format(Locale.ROOT, "%.1f", st.bmi())).append(',')
                    .append(st == null ? "" : String.format(Locale.ROOT, "%.2f", st.z())).append(',')
                    .append(ov.latest() == null ? "" : ov.latest().getMeasuredOn().toString()).append(',')
                    .append(r.meals7()).append(',')
                    .append(r.red7()).append(',')
                    .append(r.lastMealDate() == null ? "" : r.lastMealDate().toString()).append(',')
                    .append(cell(r.risk().getLabel())).append(',')
                    .append(cell(String.join("; ", r.reasons()))).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nutrikid-class-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** ใส่เครื่องหมายคำพูดและกันสูตร Excel (ค่าที่ขึ้นต้นด้วย = + - @ ถูกนำหน้าด้วย ') */
    private static String cell(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
