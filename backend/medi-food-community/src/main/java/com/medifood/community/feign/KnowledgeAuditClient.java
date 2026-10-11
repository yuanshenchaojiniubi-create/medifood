package com.medifood.community.feign;

import com.medifood.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 知识模块内部接口客户端（内容审核用）
 *
 * <p>知识文章为"先审后发"内容：AI/管理员审核通过后，
 * 调用知识模块内部接口触发 ES 重新同步（复用知识模块自己的文档构造逻辑）。
 */
@FeignClient(contextId = "knowledgeAuditClient", name = "medi-food-knowledge", path = "/knowledge",
        fallback = KnowledgeAuditClient.Fallback.class)
public interface KnowledgeAuditClient {

    /** 触发指定知识文章的 ES 重新同步（审核通过后调用） */
    @PostMapping("/internal/es-resync/{id}")
    Result<Void> resyncEs(@PathVariable("id") Long id);

    /** 降级：知识服务不可用时仅记录，不影响审核主流程 */
    @Component
    class Fallback implements KnowledgeAuditClient {
        @Override
        public Result<Void> resyncEs(Long id) {
            return Result.fail("知识服务不可用，ES同步稍后可手动触发");
        }
    }
}
