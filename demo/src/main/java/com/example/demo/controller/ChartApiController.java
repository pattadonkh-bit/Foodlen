package com.example.demo.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.ChartData;
import com.example.demo.service.ChartDataService;

/** ข้อมูล JSON สำหรับกราฟ (ใช้กับ Chart.js ในหน้าเว็บ) */
@RestController
public class ChartApiController {

    private final ChartDataService charts;

    public ChartApiController(ChartDataService charts) {
        this.charts = charts;
    }

    @GetMapping("/api/children/{id}/growth")
    public ChartData.Growth growth(@PathVariable Long id) {
        return charts.growth(id);
    }

    @GetMapping("/api/children/{id}/weekly")
    public ChartData.Weekly weekly(@PathVariable Long id,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return charts.weekly(id, end == null ? LocalDate.now() : end);
    }

    @GetMapping("/api/stats")
    public ChartData.Stats stats() {
        return charts.stats();
    }
}
