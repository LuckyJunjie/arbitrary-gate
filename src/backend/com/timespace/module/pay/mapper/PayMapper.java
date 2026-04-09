package com.timespace.module.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timespace.module.pay.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayMapper extends BaseMapper<PayOrder> {
}
