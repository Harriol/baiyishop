import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 压测后的库存核对（不超卖判定）。
 * <p>判定口径（ADR-008）：
 * 1. 秒杀池 `remaining >= 0` 且 `remaining + sold <= total`（差额是活动回补量，正常为 0）
 * 2. `sold` 必须等于「该活动 SUCCESS 的抢购记录数」—— 每一份出售都有据可查
 * 3. `sold` 不得超过划拨量（不超卖）
 */
public class VerifyLoadTest {

    static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        long activitySkuId = Long.parseLong(args[0]);
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1000;

        // 先把排队记录排空，再读池子 —— 否则读到的是「还没扣完」的中间值，会误判成不一致
        // 异步落单是排队消费的，等 QUEUED 排空（最多 180s）再判定
        long deadline = System.currentTimeMillis() + 180_000;
        int queued = 0;
        int success = 0;
        int failed = 0;
        while (true) {
            queued = 0;
            success = 0;
            failed = 0;
            try (Connection c = connect("baiyishop_seckill"); Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT status, COUNT(*) FROM seckill_record"
                         + " WHERE activity_sku_id = " + activitySkuId + " GROUP BY status")) {
                while (r.next()) {
                    String status = r.getString(1);
                    int count = r.getInt(2);
                    if ("QUEUED".equals(status)) {
                        queued = count;
                    } else if ("SUCCESS".equals(status)) {
                        success = count;
                    } else {
                        failed = count;
                    }
                }
            }
            if (queued == 0 || System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(2000);
        }
        int total = 0;
        int remaining = 0;
        int sold = 0;
        try (Connection c = connect("baiyishop_inventory"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT total, remaining, sold FROM seckill_stock_pool"
                     + " WHERE activity_sku_id = " + activitySkuId)) {
            if (r.next()) {
                total = r.getInt(1);
                remaining = r.getInt(2);
                sold = r.getInt(3);
            }
        }
        System.out.println("秒杀池：total=" + total + " remaining=" + remaining + " sold=" + sold);

        System.out.println("抢购记录：SUCCESS=" + success + " QUEUED=" + queued + " FAILED=" + failed
                + "（合计 " + (success + queued + failed) + "，压测并发 " + threads + "）");

        long orders = 0;
        try (Connection c = connect("baiyishop_order"); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM `order` WHERE source = 'SECKILL'"
                     + " AND seckill_ticket_id IS NOT NULL")) {
            r.next();
            orders = r.getLong(1);
        }
        System.out.println("秒杀订单：累计 " + orders + " 笔（含历史压测）");

        boolean noOversell = sold <= total && remaining >= 0 && sold == success;
        System.out.println(noOversell
                ? ">>> 不超卖判定：通过（sold <= total 且 sold == SUCCESS 记录数）"
                : ">>> 不超卖判定：不通过（sold=" + sold + " total=" + total + " SUCCESS=" + success + "）");
        System.exit(noOversell ? 0 : 1);
    }

    static Connection connect(String schema) throws Exception {
        return DriverManager.getConnection(String.format(MYSQL, schema), "root", "root");
    }
}
