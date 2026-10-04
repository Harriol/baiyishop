package com.harriol.baiyishop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 分类数据访问。 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /** 分类是否被商品引用（删除前校验，REQ-201）。商品与本表同 schema，可直接联查 */
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId} AND deleted = 0")
    long countProducts(@Param("categoryId") Long categoryId);

    /** 按物化路径前缀统计子分类数量（不含自身） */
    @Select("SELECT COUNT(*) FROM category WHERE path LIKE CONCAT(#{path}, '%') AND id <> #{id} AND deleted = 0")
    long countDescendants(@Param("path") String path, @Param("id") Long id);
}