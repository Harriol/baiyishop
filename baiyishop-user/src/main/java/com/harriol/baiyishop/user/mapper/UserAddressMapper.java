package com.harriol.baiyishop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/** 收货地址数据访问。 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}