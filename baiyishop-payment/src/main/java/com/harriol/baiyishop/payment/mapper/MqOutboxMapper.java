package com.harriol.baiyishop.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.payment.entity.MqOutbox;
import org.apache.ibatis.annotations.Mapper;

/** 本地消息表数据访问。 */
@Mapper
public interface MqOutboxMapper extends BaseMapper<MqOutbox> {
}
