package com.medifood.community.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容审核台账 Mapper
 */
@Mapper
public interface ContentAuditRecordMapper extends BaseMapper<ContentAuditRecord> {
}
