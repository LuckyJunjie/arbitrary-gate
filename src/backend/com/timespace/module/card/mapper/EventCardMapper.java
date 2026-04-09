package com.timespace.module.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timespace.module.card.entity.EventCard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 历史事件卡牌 Mapper
 */
@Mapper
public interface EventCardMapper extends BaseMapper<EventCard> {
}
