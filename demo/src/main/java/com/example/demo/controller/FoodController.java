
package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.service.YoloService;

@Controller
public class FoodController {

    private final YoloService yoloService;

    public FoodController(YoloService yoloService) {
        this.yoloService = yoloService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam("image") MultipartFile image,
            Model model) {

        if (image.isEmpty()) {
            model.addAttribute("error", "กรุณาเลือกรูปอาหารก่อน");
            return "index";
        }

        try {
            String result = yoloService.detect(image);

            model.addAttribute("fileName", image.getOriginalFilename());
            model.addAttribute("result", result);
            model.addAttribute("message",
                    "YOLO ตรวจจับวัตถุจากรูปภาพเรียบร้อยแล้ว");

            return "result";

     } catch (Exception e) {
    e.printStackTrace();

    model.addAttribute("error",
            "ไม่สามารถวิเคราะห์รูปภาพได้: " + e.getMessage());

    return "index";
}
    }
}
