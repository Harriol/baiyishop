package com.harriol.baiyishop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存数据访问。
 * <p>所有变更都用**条件更新**：把「库存够不够」写进 WHERE，靠影响行数判定，
 * 这样并发下不会出现先查后改的竞态，也不会把库存扣成负数（ADR-008 的第二道保险）。
 * <p>影响行数为 0 就表示「条件不满足」，由服务层翻译成业务异常。
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /** 锁定（下单）：可售 → 锁定 */
    @Update("""
            UPDATE inventory SET available = available - #{n}, locked = locked + #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE sku_id = #{skuId} AND available >= #{n}
            """)
    int lock(@Param("skuId") Long skuId, @Param("n") int n);

    /** 扣减（支付成功）：锁定库存真正出库 */
    @Update("""
            UPDATE inventory SET locked = locked - #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE sku_id = #{skuId} AND locked >= #{n}
            """)
    int deduct(@Param("skuId") Long skuId, @Param("n") int n);

    /** 释放（取消 / 超时）：锁定 → 可售 */
    @Update("""
            UPDATE inventory SET available = available + #{n}, locked = locked - #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE sku_id = #{skuId} AND locked >= #{n}
            """)
    int release(@Param("skuId") Long skuId, @Param("n") int n);

    /** 后台增加库存 */
    @Update("""
            UPDATE inventory SET available = available + #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE sku_id = #{skuId}
            """)
    int increase(@Param("skuId") Long skuId, @Param("n") int n);

    /** 后台减少库存 / 划拨秒杀：不允许减成负数 */
    @Update("""
            UPDATE inventory SET available = available - #{n},
                   version = version + 1, updated_at = NOW(3)
             WHERE sku_id = #{skuId} AND available >= #{n}
            """)
    int decrease(@Param("skuId") Long skuId, @Param("n") int n);
}