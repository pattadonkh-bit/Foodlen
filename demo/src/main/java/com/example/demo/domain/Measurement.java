package com.example.demo.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** การวัดน้ำหนัก/ส่วนสูงของเด็กแต่ละครั้ง */
@Entity
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id")
    private Child child;

    @Column(nullable = false)
    private LocalDate measuredOn;

    @Column(nullable = false)
    private double weightKg;

    @Column(nullable = false)
    private double heightCm;

    protected Measurement() {
    }

    public Measurement(Child child, LocalDate measuredOn, double weightKg, double heightCm) {
        this.child = child;
        this.measuredOn = measuredOn;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
    }

    public Long getId() {
        return id;
    }

    public Child getChild() {
        return child;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public double getHeightCm() {
        return heightCm;
    }

    public void update(LocalDate measuredOn, double weightKg, double heightCm) {
        this.measuredOn = measuredOn;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
    }
}
