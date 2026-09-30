package com.harriol.baiyishop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员账号数据访问。通用 CRUD 由 MyBatis-Plus 提供，复杂查询再补 XML / 注解 SQL。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
