package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.MqOutbox;
import org.apache.ibatis.annotations.Mapper;

/** 本地消息表数据访问（docs/database.md 9.2）。 */
@Mapper
public interface MqOutboxMapper extends BaseMapper<MqOutbox> {
}
