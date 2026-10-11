package com.medifood.doctor.dto;

import lombok.Data;

/**
 * 创建咨询 DTO
 *
 * @author MediFood Team
 */
@Data
public class DietConsultationCreateDTO {

    /** 医师ID */
    private Long doctorId;

    /** 咨询标题 */
    private String title;

    /** 咨询内容 */
    private String content;

    /** 图片列表（JSON） */
    private String images;
}