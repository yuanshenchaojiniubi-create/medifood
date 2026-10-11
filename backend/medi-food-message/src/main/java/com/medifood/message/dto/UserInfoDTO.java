package com.medifood.message.dto;

import lombok.Data;

@Data
public class UserInfoDTO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}