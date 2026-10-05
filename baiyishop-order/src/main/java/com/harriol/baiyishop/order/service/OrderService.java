package com.harriol.baiyishop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.order.client.InventoryClient;
import com.harriol.baiyishop.order.client.ProductClient;
import com.harriol.baiyishop.order.client.UserClient;
import com.harriol.baiyishop.order.config.OrderProperties;
import com.harriol.baiyishop.order.domain.OrderSource;
import com.harriol.baiyishop.order.domain.OrderStatus;
import com.harriol.baiyishop.order.dto.AddressSnapshot;
import com.harriol.baiyishop.order.dto.CreateOrderRequest;
import com.harriol.baiyishop.order.dto.InventoryOpItem;
import com.harriol.baiyishop.order.dto.ItemPreview;
import com.harriol.baiyishop.order.dto.OrderCommand;
import com.harriol.baiyishop.order.dto.OrderCreateResponse;
import com.harriol.baiyishop.order.dto.OrderDetailView;
import com.harriol.baiyishop.order.dto.OrderItemView;
import com.harriol.baiyishop.order.dto.OrderLine;
import com.harriol.baiyishop.order.dto.OrderSummary;
import com.harriol.baiyishop.order.dto.PayableOrderView;
import com.harriol.baiyishop.order.dto.ReceiverView;
import com.harriol.baiyishop.order.dto.SettleRequest;
import com.harriol.baiyishop.order.dto.SettleView;
import com.harriol.baiyishop.order.dto.SkuSnapshot;
import com.harriol.baiyishop.order.dto.StatusLogView;
import com.harriol.baiyishop.order.dto.TicketOrderView;
import com.harriol.baiyishop.order.entity.CartItem;
import com.harriol.baiyishop.order.entity.Order;
import com.harriol.baiyishop.order.entity.OrderItem;
import com.harriol.baiyishop.order.entity.OrderRequest;
import com.harriol.baiyishop.order.entity.OrderStatusLog;
import com.harriol.baiyishop.order.mapper.CartItemMapper;
import com.harriol.baiyishop.order.mapper.OrderItemMapper;
import com.harriol.baiyishop.order.mapper.OrderMapper;
import com.harriol.baiyishop.order.mapper.OrderRequestMapper;
import com.harriol.baiyishop.order.mapper.OrderStatusLogMapper;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单主流程（REQ-701 ~ REQ-707）。
 * <p>分工：本类负责「事务外的读 + 编排 + 状态流转」，真正的写入事务在
 * {@link OrderCreator}（全局事务）与各条件的原子更新里。
 * <p>所有状态流转都是**条件更新**（WHERE status = 期望值）：影响行数为 0 即表示
 * 状态已变（并发取消、重复消息），据此判定非法流转或静默跳过，天然幂等（REQ-703）。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String REASON_USER_CANCEL = "用户取消";
    private static final String REASON_TIMEOUT = "超时未支付";
    private static final String REASON_AUTO_RECEIVE = "发货 7 天自动确认收货";
    private static final String REASON_PAID = "支付成功";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderRequestMapper orderRequestMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderCreator orderCreator;
    private final OrderItemResolver itemResolver;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final SeckillCancelNotifier seckillCancelNotifier;
    private final OrderProperties properties;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        OrderStatusLogMapper statusLogMapper,
                        OrderRequestMapper orderRequestMapper,
                        CartItemMapper cartItemMapper,
                        OrderCreator orderCreator,
                        OrderItemResolver itemResolver,
                        InventoryClient inventoryClient,
                        ProductClient productClient,
                        UserClient userClient,
                        SeckillCancelNotifier seckillCancelNotifier,
                        OrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.orderRequestMapper = orderRequestMapper;
        this.cartItemMapper = cartItemMapper;
        this.orderCreator = orderCreator;
        this.itemResolver = itemResolver;
        this.inventoryClient = inventoryClient;
        this.productClient = productClient;
        this.userClient = userClient;
        this.seckillCancelNotifier = seckillCancelNotifier;
        this.properties = properties;
    }

    // ==================== 结算试算（REQ-602、REQ-701） ====================

    /**
     * 结算试算：只读，不改任何状态。
     * <p>与下单不同，这里**不因失效商品直接报错**：失效行单独放在 invalidItems 里，
     * 前端置灰并提示，用户自己决定去掉还是继续（REQ-602）。
     * <p>购物车模式未传 cartItemIds 时按「全部勾选项」结算。
     */
    public SettleView settle(long userId, SettleRequest request) {
        OrderSource source = OrderSource.of(request.source() == null ? "" : request.source());
        if (source == OrderSource.SECKILL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "秒杀订单请从秒杀活动进入");
        }

        List<CartItem> cartItems = source == OrderSource.CART
                ? cartItemsOf(userId, request.cartItemIds())
                : List.of();
        List<Long> skuIds = source == OrderSource.CART
                ? cartItems.stream().map(CartItem::getSkuId).toList()
                : List.of(request.skuId() == null ? -1L : request.skuId());

        Map<Long, SkuSnapshot> skus = productClient.skus(skuIds);
        Map<Long, Integer> stock = inventoryClient.availableBatch(skuIds);

        List<ItemPreview> valid = new ArrayList<>();
        List<ItemPreview> invalid = new ArrayList<>();
        if (source == OrderSource.CART) {
            for (CartItem item : cartItems) {
                ItemPreview preview = preview(item.getSkuId(), item.getQuantity(),
                        skus.get(item.getSkuId()), stock.get(item.getSkuId()));
                (preview.valid() ? valid : invalid).add(preview);
            }
        } else {
            int quantity = request.quantity() == null ? 0 : request.quantity();
            ItemPreview preview = preview(skuIds.get(0), quantity, skus.get(skuIds.get(0)), stock.get(skuIds.get(0)));
            (preview.valid() ? valid : invalid).add(preview);
        }

        if (valid.isEmpty()) {
            throw new BizException(invalid.isEmpty() ? ErrorCode.CART_EMPTY_CHECKED : ErrorCode.CART_ITEM_INVALID);
        }
        long total = valid.stream().mapToLong(ItemPreview::totalAmount).sum();
        return new SettleView(valid, invalid, total, 0L, total,
                userClient.defaultAddress(userId).orElse(null));
    }

    // ==================== 提交订单（REQ-701） ====================

    /**
     * 下单编排：
     * <ol>
     *   <li>requestId 幂等：同一请求重复提交直接返回首次订单号</li>
     *   <li>事务外读商品与地址（不进全局事务，避免持锁做远程调用）</li>
     *   <li>写事务交给 {@link OrderCreator}（锁库存 + 写订单，全局事务）</li>
     *   <li>成功后清理购物车中已结算的条目（失败不影响订单结果）</li>
     * </ol>
     */
    public OrderCreateResponse create(long userId, String requestId, CreateOrderRequest request) {
        if (!StringUtils.hasText(requestId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少请求标识 X-Request-Id");
        }
        Optional<OrderRequest> processed = findByRequestId(requestId);
        if (processed.isPresent()) {
            log.info("下单请求幂等命中 requestId={} orderNo={}", requestId, processed.get().getOrderNo());
            return toResponse(require(processed.get().getOrderNo()));
        }

        OrderSource source = OrderSource.of(request.source() == null ? "" : request.source());
        List<OrderLine> lines = switch (source) {
            case CART -> itemResolver.fromCart(userId, request.cartItemIds());
            case BUY_NOW -> itemResolver.buyNow(requireSkuId(request), requireQuantity(request));
            case SECKILL -> throw new BizException(ErrorCode.PARAM_INVALID, "秒杀订单由秒杀链路创建");
        };
        AddressSnapshot address = resolveAddress(userId, request.addressId());

        OrderCommand command = new OrderCommand(userId, requestId, source, lines, address, request.remark());
        Order order;
        try {
            order = orderCreator.create(command);
        } catch (RuntimeException ex) {
            if (!isDuplicateKey(ex)) {
                throw ex;
            }
            // 并发重复提交：uk_request_id 挡住，返回首次订单（REQ-701 约定 2）。
            // 抛出的异常可能被 Seata 包了一层（"try to proceed invocation error"），故沿 cause 链判断
            Optional<OrderRequest> first = awaitRequestMapping(requestId);
            if (first.isEmpty()) {
                throw ex;
            }
            log.info("并发重复提交命中唯一索引 requestId={} orderNo={}", requestId, first.get().getOrderNo());
            order = require(first.get().getOrderNo());
            return toResponse(order);
        }
        clearSettledCartItems(userId, lines);
        return toResponse(order);
    }

    // ==================== 查询（REQ-702） ====================

    public PageResult<OrderSummary> page(long userId, String status, long page, long size) {
        var query = Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId);
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            query.eq(Order::getStatus, OrderStatus.of(status).name());
        }
        Page<Order> result = orderMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                query.orderByDesc(Order::getId));
        Map<String, List<OrderItem>> items = itemsByOrderNo(result.getRecords().stream()
                .map(Order::getOrderNo).toList());
        List<OrderSummary> list = result.getRecords().stream()
                .map(order -> new OrderSummary(order.getOrderNo(), order.getStatus(),
                        OrderStatus.of(order.getStatus()).label(), order.getPayAmount(),
                        itemsOf(items, order.getOrderNo()).stream().mapToInt(OrderItem::getQuantity).sum(),
                        order.getCreatedAt(),
                        itemsOf(items, order.getOrderNo()).stream().map(OrderItemView::from).toList()))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), list);
    }

    /** 用户查看自己的订单详情 */
    public OrderDetailView detail(long userId, String orderNo) {
        return detailOf(requireOwned(userId, orderNo));
    }

    /** 按订单号查详情（后台 / 内部复用，不做归属校验） */
    public OrderDetailView detailByOrderNo(String orderNo) {
        return detailOf(require(orderNo));
    }

    /** 订单可支付性（内部接口，REQ-801）：payment-service 发起支付前回查 */
    public PayableOrderView payableView(String orderNo) {
        return PayableOrderView.from(require(orderNo));
    }

    /** 按秒杀票据回查订单（内部接口，供 seckill 对账，REQ-903） */
    public TicketOrderView ticketView(String ticketId) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getSeckillTicketId, ticketId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return new TicketOrderView(order.getOrderNo(), order.getStatus());
    }

    // ==================== 状态流转（REQ-703 ~ REQ-707） ====================

    /** 用户取消订单：仅待付款可取消（REQ-705），取消与超时并发时只有一方成功 */
    @GlobalTransactional(name = "baiyishop-order-cancel", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void cancel(long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        if (!OrderStatus.of(order.getStatus()).cancellable()) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .set(Order::getCancelReason, REASON_USER_CANCEL)
                .set(Order::getCancelTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        releaseStock(order, REASON_USER_CANCEL);
        writeStatusLog(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                OrderStatusLog.OPERATOR_USER, userId, REASON_USER_CANCEL);
    }

    /**
     * 超时未支付自动取消（REQ-704）：延时消息与兜底扫描共用。
     * <p>条件里同时要求「已到超时时间」，因此消息提前到达或重复投递都是安全的空操作。
     */
    @GlobalTransactional(name = "baiyishop-order-timeout", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void cancelByTimeout(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null || !OrderStatus.of(order.getStatus()).cancellable()) {
            log.debug("订单无需超时取消 orderNo={}", orderNo);
            return;
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .le(Order::getTimeoutAt, LocalDateTime.now())
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .set(Order::getCancelReason, REASON_TIMEOUT)
                .set(Order::getCancelTime, LocalDateTime.now()));
        if (rows == 0) {
            return;
        }
        releaseStock(order, REASON_TIMEOUT);
        writeStatusLog(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                OrderStatusLog.OPERATOR_SYSTEM, null, REASON_TIMEOUT);
        log.info("订单超时取消并释放库存 orderNo={}", orderNo);
    }

    /** 用户确认收货：仅待收货可确认（REQ-706） */
    @Transactional(rollbackFor = Exception.class)
    public void receive(long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        if (!OrderStatus.of(order.getStatus()).receivable()) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_RECEIPT.name())
                .set(Order::getStatus, OrderStatus.COMPLETED.name())
                .set(Order::getReceiveTime, LocalDateTime.now())
                .set(Order::getFinishTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED);
        }
        writeStatusLog(order, OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED,
                OrderStatusLog.OPERATOR_USER, userId, "确认收货");
    }

    /** 发货 7 天自动确认收货（REQ-707）：延时消息与兜底扫描共用，条件里校验已到自动收货时间 */
    @Transactional(rollbackFor = Exception.class)
    public void autoReceive(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null || !OrderStatus.of(order.getStatus()).receivable()) {
            return;
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_RECEIPT.name())
                .le(Order::getAutoReceiveAt, LocalDateTime.now())
                .set(Order::getStatus, OrderStatus.COMPLETED.name())
                .set(Order::getReceiveTime, LocalDateTime.now())
                .set(Order::getFinishTime, LocalDateTime.now()));
        if (rows == 0) {
            return;
        }
        writeStatusLog(order, OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED,
                OrderStatusLog.OPERATOR_SYSTEM, null, REASON_AUTO_RECEIVE);
        log.info("订单自动确认收货 orderNo={}", orderNo);
    }

    /**
     * 支付成功：订单流转为待发货 + 扣减库存（REQ-802-3，docs/architecture.md 5.2）。
     * <p>两者同属一个 Seata 全局事务：扣减失败会连同状态流转一起回滚，订单仍停在待付款，
     * 由支付侧的对账 / 重投补上。消息重复投递时，条件更新返回 0 行即直接返回，不重复扣减。
     * <p>若订单已被超时取消（用户付了钱但订单已关），这里**不扣库存**并打 WARN 日志，
     * 交由人工/对账走退款，绝不把已关闭的订单改成待发货。
     */
    @GlobalTransactional(name = "baiyishop-order-paid", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(String orderNo, String channel, String channelTradeNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            log.warn("支付成功事件找不到订单，需人工核对 orderNo={} channelTradeNo={}", orderNo, channelTradeNo);
            return;
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
                log.warn("订单已取消但收到支付成功，需人工退款 orderNo={} channelTradeNo={}", orderNo, channelTradeNo);
            } else {
                log.info("订单已是终态，忽略重复的支付成功事件 orderNo={} status={}", orderNo, order.getStatus());
            }
            return;
        }

        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(Order::getStatus, OrderStatus.PENDING_SHIPMENT.name())
                .set(Order::getPayType, channel)
                .set(Order::getPayTime, LocalDateTime.now()));
        if (rows == 0) {
            log.info("订单状态已被并发流转，忽略支付成功事件 orderNo={}", orderNo);
            return;
        }
        inventoryClient.deduct(orderNo, inventoryItems(orderNo));
        writeStatusLog(order, OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_SHIPMENT,
                OrderStatusLog.OPERATOR_SYSTEM, null, REASON_PAID + "（" + channel + "）");
        log.info("订单已支付待发货 orderNo={} channel={}", orderNo, channel);
    }

    // ==================== 内部方法 ====================

    private ItemPreview preview(Long skuId, int quantity, SkuSnapshot sku, Integer available) {
        if (sku == null) {
            return new ItemPreview(skuId, null, null, null, null, quantity, 0L, false, "商品已删除");
        }
        String reason = null;
        if (!sku.sellable()) {
            reason = "商品已下架";
        } else if (quantity < 1) {
            reason = "购买数量至少为 1";
        } else if (quantity > properties.maxQuantityPerSku()) {
            reason = "超出单品限购 " + properties.maxQuantityPerSku() + " 件";
        } else {
            int stock = available == null ? 0 : available;
            if (stock <= 0) {
                reason = "已售罄";
            } else if (stock < quantity) {
                reason = "库存不足（剩 " + stock + " 件）";
            }
        }
        long amount = reason == null ? sku.price() * quantity : 0L;
        return new ItemPreview(skuId, sku.productId(), sku.productName(), sku.image(), sku.price(),
                quantity, amount, reason == null, reason);
    }

    private List<CartItem> cartItemsOf(long userId, List<Long> cartItemIds) {
        var query = Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId);
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            query.eq(CartItem::getChecked, true);
        } else {
            query.in(CartItem::getId, cartItemIds);
        }
        return cartItemMapper.selectList(query.orderByAsc(CartItem::getId));
    }

    private AddressSnapshot resolveAddress(long userId, Long addressId) {
        if (addressId == null) {
            throw new BizException(ErrorCode.ORDER_ADDRESS_REQUIRED);
        }
        AddressSnapshot address = userClient.address(addressId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_ADDRESS_NOT_FOUND));
        if (!address.userId().equals(userId)) {
            // 不暴露「地址存在但不属于你」，统一按不存在处理（REQ-701 约定 4）
            throw new BizException(ErrorCode.USER_ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private long requireSkuId(CreateOrderRequest request) {
        if (request.skuId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择商品规格");
        }
        return request.skuId();
    }

    private int requireQuantity(CreateOrderRequest request) {
        if (request.quantity() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写购买数量");
        }
        return request.quantity();
    }

    /** 下单后清理购物车中被结算的条目：失败只记日志，不影响已生成的订单 */
    private void clearSettledCartItems(long userId, List<OrderLine> lines) {
        List<Long> cartItemIds = lines.stream().map(OrderLine::cartItemId).filter(java.util.Objects::nonNull).toList();
        if (cartItemIds.isEmpty()) {
            return;
        }
        try {
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                    .eq(CartItem::getUserId, userId)
                    .in(CartItem::getId, cartItemIds));
        } catch (Exception ex) {
            log.warn("清理购物车条目失败 userId={} cartItemIds={}", userId, cartItemIds, ex);
        }
    }

    private List<InventoryOpItem> inventoryItems(String orderNo) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderNo, orderNo))
                .stream()
                .map(item -> new InventoryOpItem(item.getSkuId(), item.getQuantity()))
                .toList();
    }

    /**
     * 取消 / 超时释放库存：普通订单直接释放回可售，**秒杀订单交给 seckill 回补秒杀池**（REQ-905）。
     * <p>秒杀库存不在普通可售里，用常规释放会把它还错地方，所以这里按 source 分流。
     */
    private void releaseStock(Order order, String reason) {
        if (OrderSource.SECKILL.name().equals(order.getSource())) {
            seckillCancelNotifier.append(order.getOrderNo());
            log.info("秒杀订单取消，已通知秒杀侧回补 orderNo={} reason={}", order.getOrderNo(), reason);
            return;
        }
        inventoryClient.release(order.getOrderNo(), inventoryItems(order.getOrderNo()));
    }

    private Optional<OrderRequest> findByRequestId(String requestId) {
        return Optional.ofNullable(orderRequestMapper.selectOne(
                Wrappers.<OrderRequest>lambdaQuery().eq(OrderRequest::getRequestId, requestId)));
    }

    /** 并发场景下首次请求可能还没提交，短暂等待后再取一次，避免把「抢输」当成系统错误返回 */
    private Optional<OrderRequest> awaitRequestMapping(String requestId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<OrderRequest> mapping = findByRequestId(requestId);
            if (mapping.isPresent()) {
                return mapping;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean isDuplicateKey(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private Order require(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private Order requireOwned(long userId, String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        if (order == null) {
            // 不区分「不存在」与「别人的订单」，避免探测
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private OrderDetailView detailOf(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderNo, order.getOrderNo())
                .orderByAsc(OrderItem::getId));
        List<StatusLogView> logs = statusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                        .eq(OrderStatusLog::getOrderNo, order.getOrderNo())
                        .orderByAsc(OrderStatusLog::getId))
                .stream().map(StatusLogView::from).toList();
        return new OrderDetailView(order.getOrderNo(), order.getStatus(),
                OrderStatus.of(order.getStatus()).label(), order.getSource(),
                order.getTotalAmount(), order.getFreightAmount(), order.getPayAmount(),
                order.getRemark(), order.getCancelReason(), order.getTrackingNo(),
                order.getTimeoutAt(), order.getPayTime(), order.getShipTime(), order.getReceiveTime(),
                order.getFinishTime(), order.getCancelTime(), order.getCreatedAt(),
                new ReceiverView(order.getReceiverName(), order.getReceiverPhone(), order.getReceiverAddress()),
                items.stream().map(OrderItemView::from).toList(), logs);
    }

    private Map<String, List<OrderItem>> itemsByOrderNo(List<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderNo, orderNos)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderNo));
    }

    private List<OrderItem> itemsOf(Map<String, List<OrderItem>> grouped, String orderNo) {
        return grouped.getOrDefault(orderNo, List.of());
    }

    private void writeStatusLog(Order order, OrderStatus from, OrderStatus to,
                                String operatorType, Long operatorId, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setOrderNo(order.getOrderNo());
        log.setFromStatus(from == null ? null : from.name());
        log.setToStatus(to.name());
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        statusLogMapper.insert(log);
    }

    private OrderCreateResponse toResponse(Order order) {
        return new OrderCreateResponse(order.getOrderNo(), order.getPayAmount(), order.getStatus(),
                order.getTimeoutAt());
    }
}
