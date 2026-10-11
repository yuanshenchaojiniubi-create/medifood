package com.medifood.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.common.exception.BusinessException;
import com.medifood.user.entity.Banner;
import com.medifood.user.mapper.BannerMapper;
import com.medifood.user.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;

    @Override
    public List<Banner> listActive() {
        return bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSortOrder)
        );
    }

    @Override
    public List<Banner> listAll() {
        return bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSortOrder)
        );
    }

    @Override
    public Banner create(Banner banner) {
        if (StrUtil.isBlank(banner.getImageUrl())) {
            throw new BusinessException("请上传轮播图片");
        }
        banner.setStatus(banner.getStatus() != null ? banner.getStatus() : 1);
        banner.setSortOrder(banner.getSortOrder() != null ? banner.getSortOrder() : 0);
        banner.setCreatedAt(LocalDateTime.now());
        banner.setUpdatedAt(LocalDateTime.now());
        bannerMapper.insert(banner);
        return banner;
    }

    @Override
    public Banner update(Long id, Banner banner) {
        Banner exist = bannerMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("轮播图不存在");
        }
        if (StrUtil.isNotBlank(banner.getImageUrl())) {
            exist.setImageUrl(banner.getImageUrl());
        }
        if (banner.getTitle() != null) {
            exist.setTitle(banner.getTitle());
        }
        if (banner.getLinkUrl() != null) {
            exist.setLinkUrl(banner.getLinkUrl());
        }
        if (banner.getSortOrder() != null) {
            exist.setSortOrder(banner.getSortOrder());
        }
        if (banner.getStatus() != null) {
            exist.setStatus(banner.getStatus());
        }
        exist.setUpdatedAt(LocalDateTime.now());
        bannerMapper.updateById(exist);
        return exist;
    }

    @Override
    public void delete(Long id) {
        if (bannerMapper.selectById(id) == null) {
            throw new BusinessException("轮播图不存在");
        }
        bannerMapper.deleteById(id);
    }
}