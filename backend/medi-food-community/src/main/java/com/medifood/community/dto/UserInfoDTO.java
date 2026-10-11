package com.medifood.community.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserInfoDTO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private String phone;

    private Integer gender;

    private LocalDate birthday;

    private String bio;
}