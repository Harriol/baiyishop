package com.harriol.baiyishop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.user.entity.UserWechat;
import org.apache.ibatis.annotations.Mapper;

/** 微信绑定数据访问。 */
@Mapper
public interface UserWechatMapper extends BaseMapper<UserWechat> {
}
