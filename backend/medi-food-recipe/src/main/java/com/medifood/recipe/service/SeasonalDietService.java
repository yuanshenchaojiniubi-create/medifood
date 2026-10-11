package com.medifood.recipe.service;

import com.medifood.recipe.dto.RecipeDTO;
import com.medifood.recipe.dto.SeasonalDietDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeasonalDietService {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private WeatherService weatherService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    private static final String REDIS_KEY_PREFIX = "seasonal_diet:recent:";
    private static final Duration RECENT_TTL = Duration.ofDays(3);
    private static final int MEAL_COUNT = 3;

    private static final Set<String> SPECIAL_TERMS = Set.of(
            "立春", "春分", "立夏", "夏至", "立秋", "秋分", "立冬", "冬至"
    );

    // ==================== 节气计算 ====================

    private static final String[] INFERRED_WEATHER = {
            "寒冷", "寒冷", "多云", "雨天", "多云", "多云",
            "多云", "雨天", "晴热", "晴热", "晴热", "晴热",
            "晴热", "晴热", "晴热", "干燥", "干燥", "干燥",
            "干燥", "多云", "寒冷", "寒冷", "寒冷", "寒冷"
    };

    private static final List<SolarTermInfo> SOLAR_TERMS = new ArrayList<>();

    static {
        int[][] dates = {
                {1, 5}, {1, 20}, {2, 4}, {2, 19}, {3, 6}, {3, 21},
                {4, 5}, {4, 20}, {5, 5}, {5, 21}, {6, 6}, {6, 21},
                {7, 7}, {7, 23}, {8, 7}, {8, 23}, {9, 8}, {9, 23},
                {10, 8}, {10, 23}, {11, 7}, {11, 22}, {12, 7}, {12, 22}
        };
        String[] names = {
                "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
                "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
                "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
                "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
        };
        for (int i = 0; i < 24; i++) {
            SolarTermInfo info = new SolarTermInfo();
            info.name = names[i];
            info.monthDay = MonthDay.of(dates[i][0], dates[i][1]);
            info.weatherInferred = INFERRED_WEATHER[i];
            SOLAR_TERMS.add(info);
        }
    }

    // ==================== 节气 → 饮食字典（仅8个特殊节气） ====================

    private static final Map<String, TermDiet> TERM_DIET_MAP = new LinkedHashMap<>();

    static {
        addTerm("立春", "立春一日，百草回芽",
                "宜辛甘发散，助阳气生发，多吃绿色蔬菜",
                "早睡早起，适当春捂，多到户外散步",
                "葱 姜 蒜 韭菜 豆芽 红枣 蜂蜜",
                "荠菜 春笋 菠菜 瘦肉 鸡蛋 豆腐",
                "枸杞 山药 百合 银耳 薏仁 莲子");

        addTerm("春分", "春分者，阴阳相半也，昼夜均而寒暑平",
                "宜平和清淡，养肝健脾，寒热均衡",
                "保持心情舒畅，适度运动，饮食寒热均衡",
                "山药 红枣 小米 燕麦 莲子 蜂蜜 鸡蛋",
                "荠菜 春笋 香椿 菠菜 豆芽 瘦肉 鲫鱼 鸡肉",
                "百合 银耳 枸杞 桂圆 薏仁 豆腐 莴笋");

        addTerm("立夏", "立夏之日，天地始交，万物并秀",
                "宜清淡养心，多食苦味，少食辛辣",
                "午间小憩养心，避免大汗伤津",
                "绿豆 小米 百合 莲子 燕麦 山药",
                "苦瓜 黄瓜 番茄 冬瓜 鸭肉 豆腐 鱼肉",
                "银耳 枸杞 薏仁 丝瓜 莲藕 西瓜");

        addTerm("夏至", "日北至，日长之至，日影短至",
                "宜清心泻火，养阴生津，饮食宜清淡",
                "晚睡早起，午休不可少，避免暴晒",
                "绿豆 小米 山药 百合 莲子 荞麦",
                "苦瓜 黄瓜 冬瓜 鸭肉 鱼肉 豆腐 番茄",
                "银耳 枸杞 莲藕 西瓜 薏仁 丝瓜");

        addTerm("立秋", "秋，揫也，物于此而揫敛也",
                "宜滋阴润肺，少辛增酸，收敛神气",
                "早卧早起，收敛神气，勿贪凉",
                "百合 银耳 山药 小米 莲子 蜂蜜",
                "莲藕 鸭肉 豆腐 鱼肉 南瓜 胡萝卜",
                "枸杞 红枣 桂圆 梨 银耳 百合 芝麻");

        addTerm("秋分", "秋分者，阴阳相半也，昼夜均而寒暑平",
                "宜滋阴润燥，养肺健脾，少辛增酸",
                "早卧早起，保持乐观，防秋燥",
                "百合 银耳 山药 小米 梨 蜂蜜 燕麦",
                "鸭肉 鱼肉 莲藕 南瓜 豆腐 胡萝卜 鸡肉",
                "枸杞 红枣 桂圆 银耳 莲子 芝麻 核桃");

        addTerm("立冬", "冬，终也，万物收藏也",
                "宜温补养肾，藏精固本，少食生冷",
                "早卧晚起，必待日光，注意保暖",
                "红枣 桂圆 山药 小米 核桃 黑芝麻",
                "羊肉 牛肉 鸡肉 萝卜 白菜 豆腐 香菇",
                "枸杞 当归 黄芪 党参 黑豆 板栗");

        addTerm("冬至", "阴极之至，阳气始生，日南至",
                "宜温补阳气，养精蓄锐，适当进补",
                "早卧晚起，去寒就温，睡前泡脚",
                "红枣 桂圆 山药 核桃 黑芝麻 小米",
                "羊肉 牛肉 鸡肉 萝卜 香菇 板栗 白菜",
                "枸杞 当归 黄芪 党参 黑豆 芡实 阿胶");
    }

    private static void addTerm(String name, String desc, String principle,
                                String tip, String breakfast, String lunch, String dinner) {
        TermDiet diet = new TermDiet();
        diet.name = name;
        diet.desc = desc;
        diet.principle = principle;
        diet.healthTip = tip;
        diet.breakfastKeywords = breakfast;
        diet.lunchKeywords = lunch;
        diet.dinnerKeywords = dinner;
        TERM_DIET_MAP.put(name, diet);
    }

    // ==================== 主方法 ====================

    public SeasonalDietDTO getSeasonalDiet() {
        return getSeasonalDiet(null, null, false);
    }

    public SeasonalDietDTO getSeasonalDiet(Double lat, Double lng, boolean useDefault) {
        String termName = getCurrentSolarTerm();
        boolean isSpecial = SPECIAL_TERMS.contains(termName);
        log.info("🌤 [SeasonalDiet] 开始获取节气饮食: term={}, isSpecial={}, lat={}, lng={}", termName, isSpecial, lat, lng);

        SeasonalDietDTO dto = new SeasonalDietDTO();
        dto.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")));

        // 始终尝试获取真实天气（即使 lat/lng 为空，ThirdParty 也会用 IP 定位兜底）
        log.info("🌤 [SeasonalDiet] 调用 fetchRealWeather(lat={}, lng={})...", lat, lng);
        RealWeather realWeather = fetchRealWeather(lat, lng);
        log.info("🌤 [SeasonalDiet] fetchRealWeather 返回: {}", realWeather != null
                ? String.format("city=%s, temp=%s, condition=%s, source=%s", realWeather.city, realWeather.temperature, realWeather.condition, realWeather.source)
                : "null（所有天气源均不可用）");

        if (isSpecial) {
            buildTermDiet(dto, termName, realWeather);
        } else {
            buildWeatherDiet(dto, getCurrentSolarTermInfo(), realWeather);
        }

        return dto;
    }

    private void buildTermDiet(SeasonalDietDTO dto, String termName, RealWeather realWeather) {
        TermDiet diet = TERM_DIET_MAP.get(termName);
        if (diet == null) {
            buildWeatherDiet(dto, getCurrentSolarTermInfo(), realWeather);
            return;
        }

        dto.setSourceType("TERM");
        dto.setSourceName(termName);
        dto.setSourceDesc(diet.desc);
        dto.setDietPrinciple(diet.principle);
        dto.setHealthTip(diet.healthTip);
        dto.setSpecialTerm(true);

        if (realWeather != null) {
            SeasonalDietDTO.WeatherInfo weatherInfo = new SeasonalDietDTO.WeatherInfo();
            weatherInfo.setCondition(realWeather.condition);
            weatherInfo.setTemperature(realWeather.temperature);
            weatherInfo.setCity(realWeather.city);
            weatherInfo.setSource(realWeather.source);
            dto.setWeather(weatherInfo);
        } else {
            dto.setWeather(null);
        }

        dto.setBreakfast(searchAndRotate("breakfast", diet.breakfastKeywords));
        dto.setLunch(searchAndRotate("lunch", diet.lunchKeywords));
        dto.setDinner(searchAndRotate("dinner", diet.dinnerKeywords));
    }

    private void buildWeatherDiet(SeasonalDietDTO dto, SolarTermInfo termInfo, RealWeather realWeather) {
        String weatherCondition;
        String temperature;
        String city;

        if (realWeather != null) {
            weatherCondition = realWeather.condition;
            temperature = realWeather.temperature;
            city = realWeather.city;
        } else {
            weatherCondition = termInfo.weatherInferred;
            temperature = getApproxTemperature(termInfo);
            city = "您的城市";
        }

        WeatherService.WeatherDietConfig weatherConfig = weatherService.getWeatherDiet(weatherCondition);

        SeasonalDietDTO.WeatherInfo weatherInfo = new SeasonalDietDTO.WeatherInfo();
        weatherInfo.setCondition(weatherCondition);
        weatherInfo.setTemperature(temperature);
        weatherInfo.setCity(city);
        weatherInfo.setSource(realWeather != null ? realWeather.source : "inferred");
        dto.setWeather(weatherInfo);

        dto.setSourceType("WEATHER");
        dto.setSourceName(weatherCondition + " · " + termInfo.name);
        dto.setSourceDesc(termInfo.name + "节气，天气" + weatherCondition);
        dto.setDietPrinciple(weatherConfig.dietPrinciple);
        dto.setHealthTip(weatherConfig.healthTip);
        dto.setSpecialTerm(false);

        dto.setBreakfast(searchAndRotate("breakfast", weatherConfig.breakfastKeywords));
        dto.setLunch(searchAndRotate("lunch", weatherConfig.lunchKeywords));
        dto.setDinner(searchAndRotate("dinner", weatherConfig.dinnerKeywords));
    }

    // ==================== 食谱搜索 + 去重轮换 ====================

    /**
     * 双层策略搜索食谱：
     * 1. 第一层：使用 suitable_seasons 字段精确匹配节气名称
     * 2. 第二层：关键词搜索（兼容旧数据）
     *
     * @param mealType 餐别（breakfast/lunch/dinner）
     * @param keywords 关键词（空格分隔）
     * @return 匹配的食谱列表
     */
    private List<SeasonalDietDTO.MealDTO> searchAndRotate(String mealType, String keywords) {
        Set<Long> recentIds = getRecentRecipeIds(mealType);
        List<SeasonalDietDTO.MealDTO> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // ===== 第一层：节气精确匹配 =====
        String termName = getCurrentSolarTerm();
        try {
            var seasonPage = recipeService.searchBySeason(termName, 1, 10, null);
            if (seasonPage != null && seasonPage.getRecords() != null) {
                for (RecipeDTO r : seasonPage.getRecords()) {
                    if (seen.contains(r.getId())) continue;
                    if (recentIds.contains(r.getId())) continue;
                    seen.add(r.getId());
                    results.add(toMealDTO(r));
                    if (results.size() >= MEAL_COUNT) break;
                }
            }
        } catch (Exception e) {
            log.warn("节气精确匹配失败, 降级为关键词搜索: termName={}", termName, e);
        }

        // ===== 第二层：关键词搜索兜底 =====
        if (results.size() < MEAL_COUNT) {
            for (String kw : keywords.split(" ")) {
                if (kw.length() < 2) continue;
                try {
                    var page = recipeService.searchRecipe(kw, 1, 10, null);
                    if (page == null || page.getRecords() == null) continue;

                    for (RecipeDTO r : page.getRecords()) {
                        if (seen.contains(r.getId())) continue;
                        if (recentIds.contains(r.getId())) continue;
                        seen.add(r.getId());

                        results.add(toMealDTO(r));
                        if (results.size() >= MEAL_COUNT) break;
                    }
                } catch (Exception e) {
                    log.warn("节气食谱关键词搜索失败: keyword={}", kw, e);
                }
                if (results.size() >= MEAL_COUNT) break;
            }
        }

        if (!results.isEmpty()) {
            saveRecentRecipeIds(mealType, results.stream()
                    .map(SeasonalDietDTO.MealDTO::getId)
                    .collect(Collectors.toSet()));
        }

        return results;
    }

    /**
     * 将 RecipeDTO 转换为 MealDTO
     */
    private SeasonalDietDTO.MealDTO toMealDTO(RecipeDTO r) {
        SeasonalDietDTO.MealDTO meal = new SeasonalDietDTO.MealDTO();
        meal.setId(r.getId());
        meal.setTitle(r.getTitle());
        meal.setCoverImage(r.getCoverImage());
        meal.setDescription(r.getDescription());
        meal.setEffects(r.getEffects());
        meal.setTags(r.getTags());
        return meal;
    }

    private Set<Long> getRecentRecipeIds(String mealType) {
        if (stringRedisTemplate == null) return Collections.emptySet();
        try {
            String key = REDIS_KEY_PREFIX + mealType + ":" + LocalDate.now();
            Set<String> members = stringRedisTemplate.opsForSet().members(key);
            if (members == null || members.isEmpty()) return Collections.emptySet();
            return members.stream().map(Long::valueOf).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis读取近期食谱失败: mealType={}", mealType, e);
            return Collections.emptySet();
        }
    }

    private void saveRecentRecipeIds(String mealType, Set<Long> ids) {
        if (stringRedisTemplate == null || ids.isEmpty()) return;
        try {
            String key = REDIS_KEY_PREFIX + mealType + ":" + LocalDate.now();
            stringRedisTemplate.opsForSet().add(key,
                    ids.stream().map(String::valueOf).toArray(String[]::new));
            stringRedisTemplate.expire(key, RECENT_TTL);

            String globalKey = REDIS_KEY_PREFIX + "all";
            stringRedisTemplate.opsForSet().add(globalKey,
                    ids.stream().map(String::valueOf).toArray(String[]::new));
            stringRedisTemplate.expire(globalKey, RECENT_TTL);
        } catch (Exception e) {
            log.warn("Redis保存近期食谱失败: mealType={}", mealType, e);
        }
    }

    // ==================== 节气/天气计算 ====================

    private String getCurrentSolarTerm() {
        return getCurrentSolarTermInfo().name;
    }

    private SolarTermInfo getCurrentSolarTermInfo() {
        MonthDay today = MonthDay.from(LocalDate.now());
        SolarTermInfo current = SOLAR_TERMS.get(SOLAR_TERMS.size() - 1);
        for (int i = SOLAR_TERMS.size() - 1; i >= 0; i--) {
            if (!today.isBefore(SOLAR_TERMS.get(i).monthDay)) {
                current = SOLAR_TERMS.get(i);
                break;
            }
        }
        return current;
    }

    private String getApproxTemperature(SolarTermInfo termInfo) {
        return switch (termInfo.weatherInferred) {
            case "寒冷" -> "-5°C ~ 5°C";
            case "多云" -> "10°C ~ 20°C";
            case "雨天" -> "12°C ~ 18°C";
            case "晴热" -> "28°C ~ 35°C";
            case "干燥" -> "15°C ~ 25°C";
            default -> "15°C ~ 22°C";
        };
    }

    // ==================== 内部类 ====================

    static class SolarTermInfo {
        String name;
        MonthDay monthDay;
        String weatherInferred;
    }

    static class TermDiet {
        String name;
        String desc;
        String principle;
        String healthTip;
        String breakfastKeywords;
        String lunchKeywords;
        String dinnerKeywords;
    }

    static class RealWeather {
        String condition;
        String temperature;
        String city;
        String source;
    }

    /**
     * ThirdParty 服务地址（Nacos 服务名，通过 @LoadBalanced RestTemplate 调用）
     * 不使用端口号，由 Spring Cloud LoadBalancer 自动解析 Nacos 注册实例
     */
    private static final String[] THIRD_PARTY_URLS = {
            "http://medi-food-third-party"
    };

    private RealWeather fetchRealWeather() {
        return fetchRealWeather(null, null);
    }

    private RealWeather fetchRealWeather(Double lat, Double lng) {
        if (restTemplate == null) {
            log.error("🌤 [fetchRealWeather] restTemplate 为 null！Recipe 模块未注入 RestTemplate Bean");
            return null;
        }

        // 构建查询参数
        String queryParams = "";
        if (lat != null && lng != null) {
            queryParams = "?lat=" + lat + "&lng=" + lng;
        }

        // 依次尝试不同的服务地址
        for (String baseUrl : THIRD_PARTY_URLS) {
            try {
                String url = baseUrl + "/api/weather/current" + queryParams;
                log.info("🌤 [fetchRealWeather] 尝试调用: {}", url);

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
                log.info("🌤 [fetchRealWeather] {} 返回: {}", baseUrl, resp);

                if (resp != null && Boolean.TRUE.equals(resp.get("success"))) {
                    RealWeather rw = new RealWeather();
                    rw.condition = (String) resp.get("condition");
                    rw.temperature = (String) resp.get("temperature");
                    rw.city = (String) resp.get("city");
                    rw.source = (String) resp.getOrDefault("source", "unknown");
                    log.info("🌤 [fetchRealWeather] ✅ 天气获取成功: source={}, city={}, temp={}, condition={}", rw.source, rw.city, rw.temperature, rw.condition);
                    return rw;
                } else {
                    log.warn("🌤 [fetchRealWeather] {} 返回 success=false: {}", baseUrl, resp);
                }
            } catch (Exception e) {
                log.error("🌤 [fetchRealWeather] ❌ {} 调用失败: {}", baseUrl, e.getMessage(), e);
            }
        }

        log.warn("🌤 [fetchRealWeather] 所有 ThirdParty 服务地址均不可用，降级为节气推测天气");
        return null;
    }
}