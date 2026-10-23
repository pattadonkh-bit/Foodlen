package com.example.demo.service;

import java.util.List;

public record AiResult(List<AiPrediction> predictions, String model, String device) {

    public AiPrediction top() {
        return predictions.get(0);
    }
}
