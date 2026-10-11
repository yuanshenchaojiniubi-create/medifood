package com.medifood.recipe.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeasonalDietDTO {

    private String date;

    private String sourceType;

    private String sourceName;

    private String sourceDesc;

    private String dietPrinciple;

    private String healthTip;

    private boolean isSpecialTerm;

    private WeatherInfo weather;

    private List<MealDTO> breakfast;
    private List<MealDTO> lunch;
    private List<MealDTO> dinner;

    @Data
    public static class WeatherInfo {
        private String condition;
        private String temperature;
        private String city;
        /** 天气数据来源：qweather / wttr / fallback */
        private String source;
    }

    @Data
    public static class MealDTO {
        private Long id;
        private String title;
        private String coverImage;
        private String description;
        private String effects;
        private List<String> tags;
    }
}