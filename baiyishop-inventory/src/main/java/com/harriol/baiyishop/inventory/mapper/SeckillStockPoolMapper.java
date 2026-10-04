package com.harriol.baiyishop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.inventory.entity.SeckillStockPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 秒杀库存池数据访问。同样用条件更新保证不为负。 */
@Mapper
public interface SeckillStockPoolMapper extends BaseMapper<SeckillStockPool> {

    /** 划拨入池：total 与 remaining 同时增加 */
    @Update("""
            UPDATE seckill_stock_pool SET total = total + #{n}, remaining = remaining + #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE activity_sku_id = #{activitySkuId}
            """)
    int allocate(@Param("activitySkuId") Long activitySkuId, @Param("n") int n);

    /** 售出：remaining 减少、sold 增加 */
    @Update("""
            UPDATE seckill_stock_pool SET remaining = remaining - #{n}, sold = sold + #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE activity_sku_id = #{activitySkuId} AND remaining >= #{n}
            """)
    int deduct(@Param("activitySkuId") Long activitySkuId, @Param("n") int n);

    /** 取消回滚：remaining 增加、sold 减少 */
    @Update("""
            UPDATE seckill_stock_pool SET remaining = remaining + #{n}, sold = sold - #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE activity_sku_id = #{activitySkuId} AND sold >= #{n}
            """)
    int rollback(@Param("activitySkuId") Long activitySkuId, @Param("n") int n);

    /** 活动结束清空剩余（回补普通库存时用），返回被清掉的数量由调用方先读出 */
    @Update("""
            UPDATE seckill_stock_pool SET remaining = 0,
                   version = version + 1, updated_at = NOW(3)
             WHERE activity_sku_id = #{activitySkuId} AND remaining > 0
            """)
    int clearRemaining(@Param("activitySkuId") Long activitySkuId);
}