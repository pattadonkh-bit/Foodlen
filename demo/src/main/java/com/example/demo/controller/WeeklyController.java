package com.example.demo.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.service.WeeklySummary;
import com.example.demo.service.WeeklySummaryService;

@Controller
public class WeeklyController {

    private final WeeklySummaryService weekly;

    public WeeklyController(WeeklySummaryService weekly) {
        this.weekly = weekly;
    }

    @GetMapping("/children/{id}/weekly")
    public String weekly(@PathVariable Long id,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                         Model model) {
        LocalDate endDate = end == null ? LocalDate.now() : end;
        WeeklySummary s = weekly.summarize(id, endDate);
        model.addAttribute("s", s);
        model.addAttribute("prevEnd", endDate.minusDays(7));
        model.addAttribute("nextEnd", endDate.plusDays(7));
        model.addAttribute("hasNext", !endDate.plusDays(7).isAfter(LocalDate.now().plusDays(6)));
        return "weekly";
    }
}
