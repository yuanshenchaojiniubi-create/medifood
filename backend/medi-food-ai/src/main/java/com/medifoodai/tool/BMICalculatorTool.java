package com.medifoodai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * BMI 计算工具
 * <p>
 * 根据身高(cm)和体重(kg)计算 BMI 指数，给出健康分类和食疗建议。
 * 参考中国 BMI 标准进行分类。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class BMICalculatorTool {

    private static final DecimalFormat DF = new DecimalFormat("0.0");

    @Tool(name = "bmi_calculator", description = "根据身高(cm)和体重(kg)计算BMI指数，给出健康评估和饮食建议。参数：height(Double, 必填，单位厘米), weight(Double, 必填，单位千克)")
    public Map<String, Object> bmiCalculator(Double height, Double weight) {
        Map<String, Object> result = new HashMap<>();

        if (height == null || weight == null || height <= 0 || weight <= 0) {
            result.put("error", "身高和体重必须为正数");
            return result;
        }

        try {
            double heightM = height / 100.0;
            double bmi = weight / (heightM * heightM);
            String bmiStr = DF.format(bmi);

            String category;
            String advice;
            String dietDirection;

            if (bmi < 18.5) {
                category = "偏瘦";
                advice = "你的体重偏轻，可能存在营养不良的风险。建议适当增加蛋白质和健康脂肪的摄入。";
                dietDirection = "健脾益气 + 补充营养：多吃山药、红枣、牛肉、鸡蛋、牛奶。推荐食谱：四神汤（山药、莲子、芡实、茯苓炖排骨）";
            } else if (bmi < 24.0) {
                category = "正常";
                advice = "你的体重在健康范围内，继续保持均衡饮食和适量运动即可。";
                dietDirection = "平衡膳食，顺应季节养生。多吃时令蔬果，粗细搭配。";
            } else if (bmi < 28.0) {
                category = "超重";
                advice = "你的体重略超标准范围，建议注意控制饮食，减少高油高糖食物，增加运动量。";
                dietDirection = "健脾祛湿 + 控制热量：多吃冬瓜、薏米、赤小豆、芹菜、燕麦。推荐食谱：冬瓜薏米排骨汤、凉拌木耳黄瓜。";
            } else {
                category = "肥胖";
                advice = "你的体重已超过健康范围，建议在专业医师指导下进行合理减重，控制饮食并增加有氧运动。";
                dietDirection = "化痰祛湿 + 温阳健脾：多吃山楂、荷叶、决明子、苦瓜。少食甜腻油炸食物。推荐食谱：山楂荷叶茶、苦瓜炒蛋。";
            }

            result.put("height_cm", height);
            result.put("weight_kg", weight);
            result.put("bmi", Double.parseDouble(bmiStr));
            result.put("category", category);
            result.put("advice", advice);
            result.put("diet_direction", dietDirection);

            log.debug("BMI 计算完成: height={}, weight={}, bmi={}, category={}", height, weight, bmiStr, category);
        } catch (Exception e) {
            log.error("BMI 计算异常: {}", e.getMessage());
            result.put("error", "BMI 计算失败：" + e.getMessage());
        }

        return result;
    }
}
