package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MealNotFoundException extends RuntimeException {

    public MealNotFoundException(Long id) {
        super("ไม่พบมื้ออาหารรหัส " + id);
    }
}
