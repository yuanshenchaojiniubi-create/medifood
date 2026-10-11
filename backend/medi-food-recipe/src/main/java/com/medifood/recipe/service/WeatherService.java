package com.medifood.recipe.service;

import com.medifood.recipe.dto.SeasonalDietDTO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    public static class WeatherDietConfig {
        public String weatherName;
        public String dietPrinciple;
        public String healthTip;
        public String breakfastKeywords;
        public String lunchKeywords;
        public String dinnerKeywords;
    }

    private static final Map<String, WeatherDietConfig> WEATHER_DIET_MAP = new LinkedHashMap<>();

    static {
        add("晴热", "宜清热解暑，清淡饮食，多补水",
                "避免正午外出，多饮绿豆汤，饮食宜清淡",
                "绿豆 小米 山药 百合 莲子 燕麦 牛奶",
                "苦瓜 冬瓜 丝瓜 黄瓜 番茄 鸭肉 豆腐 鱼肉",
                "银耳 枸杞 莲藕 西瓜 薏仁 绿豆芽 莴笋");

        add("多云", "宜温和调养，均衡搭配，健脾养胃",
                "适度户外运动，保持心情舒畅",
                "山药 红枣 小米 燕麦 莲子 鸡蛋 豆浆",
                "瘦肉 鱼肉 豆腐 胡萝卜 西兰花 香菇 番茄",
                "百合 银耳 枸杞 桂圆 薏仁 莲藕 青菜");

        add("阴天", "宜行气解郁，温阳健脾，多食辛香",
                "可饮玫瑰花茶疏肝，适当运动提振阳气",
                "生姜 红枣 山药 小米 核桃 燕麦 蜂蜜",
                "鸡肉 韭菜 洋葱 胡萝卜 香菇 豆腐 瘦肉",
                "桂圆 枸杞 黑芝麻 当归 黄芪 板栗 红薯");

        add("雨天", "宜健脾祛湿，温中散寒，忌食生冷",
                "保持居室干燥通风，可艾灸足三里祛湿",
                "生姜 红枣 山药 薏仁 赤小豆 小米 燕麦",
                "鲫鱼 冬瓜 鸡肉 豆腐 香菇 胡萝卜 瘦肉",
                "茯苓 芡实 莲子 百合 桂圆 陈皮 薏仁");

        add("寒冷", "宜温补助阳，补肾壮骨，忌食寒凉",
                "早卧晚起，注意保暖，睡前可泡脚",
                "桂圆 红枣 核桃 黑芝麻 山药 小米 燕麦",
                "羊肉 牛肉 鸡肉 生姜 萝卜 香菇 板栗",
                "枸杞 当归 黄芪 党参 黑豆 芡实 阿胶");

        add("干燥", "宜滋阴润燥，养肺生津，多食酸甘",
                "多饮温水，室内可放置加湿器",
                "百合 银耳 梨 蜂蜜 山药 小米 牛奶",
                "鸭肉 鱼肉 莲藕 南瓜 豆腐 胡萝卜 鸡肉",
                "枸杞 红枣 桂圆 银耳 莲子 芝麻 核桃");
    }

    private static void add(String weather, String principle, String tip,
                            String breakfast, String lunch, String dinner) {
        WeatherDietConfig config = new WeatherDietConfig();
        config.weatherName = weather;
        config.dietPrinciple = principle;
        config.healthTip = tip;
        config.breakfastKeywords = breakfast;
        config.lunchKeywords = lunch;
        config.dinnerKeywords = dinner;
        WEATHER_DIET_MAP.put(weather, config);
    }

    public WeatherDietConfig getWeatherDiet(String weatherCondition) {
        for (Map.Entry<String, WeatherDietConfig> entry : WEATHER_DIET_MAP.entrySet()) {
            if (weatherCondition.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return WEATHER_DIET_MAP.get("多云");
    }

    public List<String> getAllWeatherConditions() {
        return List.copyOf(WEATHER_DIET_MAP.keySet());
    }
}