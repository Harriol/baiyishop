-- ============================================================================
-- 修正秒杀库存池的表注释（仅注释，不改结构）
--
-- 背景：V2 建表时把不变量写成 remaining + sold = total，但这条在**活动结束回补后不成立**：
-- 未售出的 remaining 被还回普通库存，而 total 是「划拨总量」这一不可变的历史事实，
-- 于是出现 remaining=0、sold=0、total=40 的合法状态。
-- 正确表述：remaining >= 0 且 remaining + sold <= total，
-- 差额（total - remaining - sold）就是活动结束已回补的数量。
-- ============================================================================

ALTER TABLE seckill_stock_pool
    COMMENT = '秒杀库存池（remaining >= 0；remaining + sold <= total，差额为活动结束已回补部分）';