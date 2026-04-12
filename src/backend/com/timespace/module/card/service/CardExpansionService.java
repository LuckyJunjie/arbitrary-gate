package com.timespace.module.card.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timespace.module.card.entity.CardExpansion;
import com.timespace.module.card.mapper.CardExpansionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 卡池扩展包服务
 * 用于管理卡池数据的分包扩展机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardExpansionService extends ServiceImpl<CardExpansionMapper, CardExpansion> {

    /**
     * 获取所有已启用的扩展包列表（按 sortOrder 排序）
     */
    public List<CardExpansion> getEnabledExpansions() {
        LambdaQueryWrapper<CardExpansion> wrapper = new LambdaQueryWrapper<CardExpansion>()
                .eq(CardExpansion::getEnabled, 1)
                .orderByAsc(CardExpansion::getSortOrder);
        return list(wrapper);
    }

    /**
     * 根据 expansionCode 获取扩展包信息
     */
    public CardExpansion getByCode(String expansionCode) {
        LambdaQueryWrapper<CardExpansion> wrapper = new LambdaQueryWrapper<CardExpansion>()
                .eq(CardExpansion::getExpansionCode, expansionCode)
                .eq(CardExpansion::getEnabled, 1);
        return getOne(wrapper);
    }
}
