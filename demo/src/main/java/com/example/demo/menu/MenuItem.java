package com.example.demo.menu;

import java.util.List;
import java.util.Set;

/**
 * เมนูอาหารและข้อมูลโภชนาการต่อหนึ่งหน่วยบริโภค (ค่าว่าง = ยังไม่มีค่าที่ตรวจสอบได้)
 * status: provisional = ค่าจากแหล่งทุติยภูมิ รอตรวจสอบ / no_data = ยังไม่มีข้อมูล
 */
public record MenuItem(
        String code,
        String nameTh,
        String nameEn,
        String serving,
        Double energyKcal,
        Double proteinG,
        Double fatG,
        Double carbG,
        Double sugarG,
        Set<String> tags,
        List<String> foodGroups,
        String source,
        String status) {

    public boolean hasEnergy() {
        return energyKcal != null;
    }
}
