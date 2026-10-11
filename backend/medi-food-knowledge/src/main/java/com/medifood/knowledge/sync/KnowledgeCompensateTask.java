package com.medifood.knowledge.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.common.sync.EsSyncEvent;
import com.medifood.common.sync.EsSyncEventPublisher;
import com.medifood.knowledge.entity.Knowledge;
import com.medifood.knowledge.entity.KnowledgeIngredient;
import com.medifood.knowledge.mapper.KnowledgeMapper;
import com.medifood.knowledge.mapper.KnowledgeIngredientMapper;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnowledgeCompensateTask {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private KnowledgeIngredientMapper knowledgeIngredientMapper;

    @Autowired
    private EsSyncEventPublisher publisher;

    @Value("${es.sync.compensate.enabled:true}")
    private boolean compensateEnabled;

    @Value("${es.sync.compensate.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${es.sync.compensate.knowledge.cron:0 0/10 * * * ?}")
    public void compensate() {
        if (!compensateEnabled) {
            log.info("知识ES同步补偿任务已禁用");
            return;
        }
        log.info("开始知识ES同步补偿任务，批大小: {}", batchSize);
        long startTime = System.currentTimeMillis();

        try {
            int total = compensateKnowledge();
            long cost = System.currentTimeMillis() - startTime;
            log.info("知识ES同步补偿任务完成，共 {} 条，耗时: {}ms", total, cost);
        } catch (Exception e) {
            log.error("知识ES同步补偿任务异常: {}", e.getMessage(), e);
        }
    }

    private int compensateKnowledge() {
        int total = 0;
        long maxId = 0;
        int batchNo = 0;
        while (true) {
            LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Knowledge::getStatus, 1)
                    .gt(maxId > 0, Knowledge::getId, maxId)
                    .orderByAsc(Knowledge::getId)
                    .last("LIMIT " + batchSize);

            List<Knowledge> list = knowledgeMapper.selectList(wrapper);
            if (list.isEmpty()) {
                log.info("知识ES同步补偿任务扫描完成，共 {} 批，共 {} 条", batchNo, total);
                break;
            }

            batchNo++;
            log.info("知识ES同步补偿任务开始处理第 {} 批，本批 {} 条，起始ID>{}", batchNo, list.size(), maxId);
            for (Knowledge knowledge : list) {
                try {
                    publisher.publishUpdate(
                            knowledge.getId(),
                            EsSyncEvent.EntityType.KNOWLEDGE,
                            "knowledge_index",
                            buildKnowledgeVersion(knowledge),
                            buildKnowledgePayload(knowledge)
                    );
                    total++;
                } catch (Exception e) {
                    log.error("补偿知识索引失败: id={}", knowledge.getId(), e);
                }
                maxId = knowledge.getId();
            }
            log.info("知识ES同步补偿任务完成第 {} 批，当前累计 {} 条，最新ID={}", batchNo, total, maxId);
        }
        return total;
    }

    private Map<String, Object> buildKnowledgePayload(Knowledge knowledge) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", knowledge.getId());
        payload.put("title", knowledge.getTitle());
        payload.put("content", knowledge.getContent());
        payload.put("categoryId", knowledge.getCategoryId());
        payload.put("userId", knowledge.getUserId());
        payload.put("coverImage", knowledge.getCoverImage());
        payload.put("images", parseStringList(knowledge.getImages()));
        payload.put("ingredientNames", knowledgeIngredientMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeIngredient>()
                                .eq(KnowledgeIngredient::getKnowledgeId, knowledge.getId()))
                .stream()
                .map(KnowledgeIngredient::getIngredientName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList()));
        payload.put("status", knowledge.getStatus());
        payload.put("updateTime", knowledge.getUpdateTime() != null ? knowledge.getUpdateTime().toString() : null);
        return payload;
    }

    private Long buildKnowledgeVersion(Knowledge knowledge) {
        if (knowledge.getUpdateTime() != null) {
            return knowledge.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (knowledge.getCreateTime() != null) {
            return knowledge.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return knowledge.getId();
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, String.class);
    }
}
