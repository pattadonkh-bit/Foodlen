package com.example.demo.service;

import java.util.List;

/** ข้อมูลหน้าภาพรวมสำหรับครู/นักโภชนาการ: แถวเด็กเรียงตามความเสี่ยง + ตัวเลขสรุป + รายชื่อชั้น/ห้อง */
public record ClassOverview(
        List<ClassRow> rows,
        int total,
        int high,
        int medium,
        int notLogged,
        List<String> classrooms) {
}
