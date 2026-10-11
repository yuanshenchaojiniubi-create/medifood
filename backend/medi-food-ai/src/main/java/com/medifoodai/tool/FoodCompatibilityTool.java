package com.medifoodai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 食物搭配检测工具
 * <p>
 * 根据中医食疗原则和现代营养学，判断两种食物是否适合一起食用。
 * 内置常见食物搭配数据库，包含适宜搭配和禁忌搭配。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class FoodCompatibilityTool {

    /**
     * 适宜搭配的食物组合及理由
     * 格式：A+B -> 理由
     */
    private static final Map<String, String> GOOD_PAIRS = new LinkedHashMap<>();

    /**
     * 不适宜搭配的食物组合及理由
     * 格式：A+B -> 理由
     */
    private static final Map<String, String> BAD_PAIRS = new LinkedHashMap<>();

    static {
        // 适宜搭配
        GOOD_PAIRS.put("枸杞|红枣", "枸杞补肝肾，红枣补气血，搭配效果更佳");
        GOOD_PAIRS.put("山药|薏米", "山药健脾，薏米祛湿，搭配健脾祛湿效果更好");
        GOOD_PAIRS.put("黄芪|当归", "黄芪补气，当归补血，气血双补");
        GOOD_PAIRS.put("银耳|百合", "银耳润肺，百合清心，搭配滋阴润肺安神");
        GOOD_PAIRS.put("绿豆|薏米", "绿豆清热解毒，薏米健脾祛湿，适合夏季祛暑");
        GOOD_PAIRS.put("莲子|桂圆", "莲子养心安神，桂圆补血安神，搭配助眠效果好");
        GOOD_PAIRS.put("山楂|荷叶", "山楂消食，荷叶降脂，适合控制体重");
        GOOD_PAIRS.put("黑豆|核桃", "黑豆补肾，核桃补脑，搭配益肾健脑");
        GOOD_PAIRS.put("鲫鱼|豆腐", "鲫鱼和豆腐都富含蛋白质，搭配营养互补");
        GOOD_PAIRS.put("当归|羊肉", "当归补血，羊肉温阳，适合冬季进补暖身");

        // 禁忌搭配
        BAD_PAIRS.put("萝卜|人参", "萝卜破气，会抵消人参的补气功效");
        BAD_PAIRS.put("柿子|螃蟹", "两者都属寒性，同食易引起腹痛腹泻");
        BAD_PAIRS.put("韭菜|蜂蜜", "韭菜辛温，蜂蜜甘滑，同食易导致腹泻");
        BAD_PAIRS.put("菠菜|豆腐", "菠菜草酸会与豆腐钙结合形成草酸钙，影响钙吸收");
        BAD_PAIRS.put("豆浆|鸡蛋", "豆浆含胰蛋白酶抑制剂，影响蛋白质消化吸收");
        BAD_PAIRS.put("红薯|柿子", "红薯含淀粉多，与柿子同食易形成胃结石");
        BAD_PAIRS.put("虾|大枣", "虾中五价砷可能与大枣维C反应，不宜大量同食");
        BAD_PAIRS.put("大蒜|蜂蜜", "大蒜辛温发散，蜂蜜甘缓收敛，功效相反");
        BAD_PAIRS.put("茶|药物", "茶中的鞣酸会影响药物吸收，服药期间不宜饮茶");
        BAD_PAIRS.put("牛奶|巧克力", "牛奶钙质与巧克力草酸结合，影响钙吸收");
    }

    @Tool(name = "food_compatibility", description = "检查两种食物是否适合一起食用，根据中医食物搭配原则给出建议。参数：food1(String, 必填), food2(String, 必填)")
    public Map<String, Object> foodCompatibility(String food1, String food2) {
        Map<String, Object> result = new HashMap<>();

        if (food1 == null || food1.isEmpty() || food2 == null || food2.isEmpty()) {
            result.put("error", "两种食物名称都不能为空");
            return result;
        }

        try {
            String f1 = food1.trim();
            String f2 = food2.trim();

            result.put("food1", f1);
            result.put("food2", f2);

            // 检查是否在禁忌搭配中
            String badMatch = checkPairs(BAD_PAIRS, f1, f2);
            if (badMatch != null) {
                result.put("compatible", false);
                result.put("level", "不推荐");
                result.put("reason", badMatch);
                return result;
            }

            // 检查是否在适宜搭配中
            String goodMatch = checkPairs(GOOD_PAIRS, f1, f2);
            if (goodMatch != null) {
                result.put("compatible", true);
                result.put("level", "推荐搭配");
                result.put("reason", goodMatch);
                return result;
            }

            // 不在已知搭配中，给出一般性建议
            result.put("compatible", true);
            result.put("level", "暂无已知冲突");
            result.put("reason", "数据库中没有「" + f1 + "」和「" + f2 + "」的已知冲突记录。"
                    + "一般来说，如果没有特殊禁忌，适量搭配食用是安全的。"
                    + "建议根据个人体质和季节，合理控制食量。");

            log.debug("食物搭配检查: {} + {} -> {}", f1, f2, result.get("level"));
        } catch (Exception e) {
            log.error("食物搭配检查失败: food1={}, food2={}, error={}", food1, food2, e.getMessage());
            result.put("error", "检查失败：" + e.getMessage());
        }

        return result;
    }

    /**
     * 检查两个食物是否匹配规则库中的某条记录
     */
    private String checkPairs(Map<String, String> pairs, String f1, String f2) {
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            String[] foods = entry.getKey().split("\\|");
            if (foods.length >= 2) {
                // 双向匹配
                if ((foods[0].contains(f1) || f1.contains(foods[0]))
                        && (foods[1].contains(f2) || f2.contains(foods[1]))) {
                    return entry.getValue();
                }
                if ((foods[0].contains(f2) || f2.contains(foods[0]))
                        && (foods[1].contains(f1) || f1.contains(foods[1]))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
