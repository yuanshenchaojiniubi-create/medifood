package com.medifood.user.service;

import com.medifood.user.entity.Banner;

import java.util.List;

public interface BannerService {

    List<Banner> listActive();

    List<Banner> listAll();

    Banner create(Banner banner);

    Banner update(Long id, Banner banner);

    void delete(Long id);
}