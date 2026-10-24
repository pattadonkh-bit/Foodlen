package com.example.demo.domain;

import java.time.LocalDateTime;

import com.example.demo.advice.Level;
import com.example.demo.advice.MealType;
import com.example.demo.nutrition.StatusCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** มื้ออาหารที่บันทึก (ไม่เก็บไฟล์ภาพ เก็บเฉพาะเมนูและผลประเมิน) */
@Entity
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id")
    private Child child;

    @Column(nullable = false)
    private LocalDateTime eatenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealType mealType;

    @Column(nullable = false, length = 50)
    private String menuCode;

    @Column(nullable = false)
    private double portion;

    /** ความมั่นใจของ AI สำหรับเมนูอันดับ 1 (null ถ้าผู้ใช้เลือกเมนูเอง) */
    private Double aiConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealSource source;

    /** พลังงานของมื้อ (หลังคูณสัดส่วน) หรือ null ถ้าเมนูไม่มีข้อมูล */
    private Double energyKcal;

    private Double shareOfDaily;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusCode statusAtMeal;

    @Column(length = 2000)
    private String adviceText;

    public Long getId() {
        return id;
    }

    public Child getChild() {
        return child;
    }

    public void setChild(Child child) {
        this.child = child;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public void setEatenAt(LocalDateTime eatenAt) {
        this.eatenAt = eatenAt;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public String getMenuCode() {
        return menuCode;
    }

    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    public double getPortion() {
        return portion;
    }

    public void setPortion(double portion) {
        this.portion = portion;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public MealSource getSource() {
        return source;
    }

    public void setSource(MealSource source) {
        this.source = source;
    }

    public Double getEnergyKcal() {
        return energyKcal;
    }

    public void setEnergyKcal(Double energyKcal) {
        this.energyKcal = energyKcal;
    }

    public Double getShareOfDaily() {
        return shareOfDaily;
    }

    public void setShareOfDaily(Double shareOfDaily) {
        this.shareOfDaily = shareOfDaily;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public StatusCode getStatusAtMeal() {
        return statusAtMeal;
    }

    public void setStatusAtMeal(StatusCode statusAtMeal) {
        this.statusAtMeal = statusAtMeal;
    }

    public String getAdviceText() {
        return adviceText;
    }

    public void setAdviceText(String adviceText) {
        this.adviceText = adviceText;
    }
}
