package com.medifood.community.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class PostPublishDTO {
    
    @NotBlank(message = "内容不能为空")
    private String content;
    
    private List<String> images;
    
    @NotNull(message = "动态类型不能为空")
    private Integer postType;
}

