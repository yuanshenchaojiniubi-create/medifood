package com.medifood.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.community.audit.ContentAuditAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员内容审核控制器（三级漏斗第三层入口）
 *
 * <p>管理员对 AI 判定"疑似违规"的内容进行人工复核：
 * 查看待审列表、通过（先审后发内容上架）、拒绝（删除）、拒绝并警告。
 * 权限校验：每个接口内部通过用户服务校验管理员身份。
 */
@RestController
@RequestMapping("/api/community/admin/audit")
public class ContentAuditAdminController {

    @Autowired
    private ContentAuditAdminService auditAdminService;

    /**
     * 审核台账列表（管理后台）
     *
     * @param contentType 内容类型筛选（1-动态 2-食谱 3-知识 4-社区评论 5-食谱评论 6-知识评论 7-提问 8-回答，null=全部）
     * @param status      台账状态筛选（0-待审核 1-已通过 2-已拒绝 3-已拒绝并警告，null=全部）
     */
    @GetMapping("/list")
    public Result<Page<ContentAuditAdminService.AuditRecordDTO>> listRecords(
            @CurrentUser Long adminUserId,
            @RequestParam(required = false) Integer contentType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        auditAdminService.requireAdmin(adminUserId);
        return Result.success(auditAdminService.listRecords(contentType, status, page, size));
    }

    /** 待审数量统计（pending/approved/rejected） */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(@CurrentUser Long adminUserId) {
        auditAdminService.requireAdmin(adminUserId);
        return Result.success(auditAdminService.getStats());
    }

    /**
     * 复核通过
     * <p>先审后发内容（食谱/知识）自动上架并同步搜索索引；
     * 先发后审内容本就在线展示，仅台账结案。
     *
     * @param body 可选 { "remark": "备注" }
     */
    @PostMapping("/{recordId}/approve")
    public Result<?> approve(@CurrentUser Long adminUserId,
                             @PathVariable Long recordId,
                             @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        auditAdminService.approve(adminUserId, recordId, remark);
        return Result.success("已通过");
    }

    /**
     * 复核拒绝（删除内容）
     *
     * @param warn 是否同时警告发布者（true=拒绝并警告）
     * @param body 可选 { "remark": "备注" }
     */
    @PostMapping("/{recordId}/reject")
    public Result<?> reject(@CurrentUser Long adminUserId,
                            @PathVariable Long recordId,
                            @RequestParam(defaultValue = "false") boolean warn,
                            @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        auditAdminService.reject(adminUserId, recordId, warn, remark);
        return Result.success(warn ? "已删除并警告用户" : "已删除");
    }
}
