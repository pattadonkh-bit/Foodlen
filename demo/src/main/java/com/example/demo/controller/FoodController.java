package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.demo.advice.EnergyTargetService;
import com.example.demo.advice.RecommendationService;
import com.example.demo.menu.MenuCatalog;
import com.example.demo.service.ChartDataService;
import com.example.demo.service.DashboardService;
import com.example.demo.service.YoloService;

/** หน้าแรก (รายชื่อเด็ก) และหน้าข้อมูลอ้างอิงของระบบ (กฎ เมนู พลังงานอ้างอิง) */
@Controller
public class FoodController {

    private final DashboardService dashboard;
    private final YoloService ai;
    private final RecommendationService recommendations;
    private final EnergyTargetService energyTargets;
    private final MenuCatalog menus;
    private final ChartDataService charts;
    private final double threshold;

    public FoodController(DashboardService dashboard, YoloService ai, RecommendationService recommendations,
                          EnergyTargetService energyTargets, MenuCatalog menus, ChartDataService charts,
                          @Value("${foodlen.confidence-threshold:0.80}") double threshold) {
        this.dashboard = dashboard;
        this.ai = ai;
        this.recommendations = recommendations;
        this.energyTargets = energyTargets;
        this.menus = menus;
        this.charts = charts;
        this.threshold = threshold;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("cards", dashboard.cards());
        model.addAttribute("aiUp", ai.isUp());
        model.addAttribute("stats", charts.stats());
        return "index";
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("stats", charts.stats());
        return "stats";
    }

    @GetMapping("/rules")
    public String rules(Model model) {
        model.addAttribute("rules", recommendations.rules());
        model.addAttribute("targets", energyTargets.targets());
        model.addAttribute("menus", menus.all());
        model.addAttribute("threshold", threshold);
        return "rules";
    }
}
