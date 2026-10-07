package com.harriol.baiyishop.seckill.client;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.web.client.InternalApiClient;
import com.harriol.baiyishop.common.web.client.InternalApiClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 按票据回查订单的客户端契约（REQ-903、ADR-008）。
 *
 * <p>订单服务对「没有对应订单」的约定是返回 50001，客户端必须把它声明为「允许为空」；
 * 否则秒杀对账任务会在这里抛异常，导致「预扣成功但没落单」的票据永远停在 QUEUED、预扣的库存不回收。
 *
 * <p>这里用替身按订单服务的真实应答语义回放（声明了 50001 才返回空，否则抛业务异常）——
 * SeckillApiTests 把整个 OrderClient mock 掉了，覆盖不到这个契约。
 */
class OrderClientTests {

    private InternalApiClient apiClient;
    private OrderClient orderClient;

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(InternalApiClient.class);
        InternalApiClients clients = Mockito.mock(InternalApiClients.class);
        when(clients.client(anyString())).thenReturn(apiClient);
        orderClient = new OrderClient(clients);
        // 模拟订单服务：把 50001 声明进 emptyCodes 才返回 empty，否则抛「订单不存在」
        when(apiClient.getOptional(anyString(), any(), any(int[].class))).thenAnswer(invocation -> {
            // Mockito 把 int... 作为「若干个独立参数」传进来，这里两种情况都兼容
            Object[] args = invocation.getArguments();
            for (int i = 2; i < args.length; i++) {
                if (args[i] instanceof Integer value && value == ErrorCode.ORDER_NOT_FOUND.getCode()) {
                    return Optional.empty();
                }
                if (args[i] instanceof int[] codes) {
                    for (int code : codes) {
                        if (code == ErrorCode.ORDER_NOT_FOUND.getCode()) {
                            return Optional.empty();
                        }
                    }
                }
            }
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        });
    }

    @Test
    @DisplayName("订单不存在（50001）时返回 null，而不是抛异常")
    void missingOrderReturnsNull() {
        assertThatCode(() -> assertThat(orderClient.orderNoOfTicket("TK-1")).isNull())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("把 50001 作为「允许为空」的业务码传给内部调用客户端")
    void declaresOrderNotFoundAsEmptyCode() {
        orderClient.orderNoOfTicket("TK-1");

        ArgumentCaptor<int[]> codes = ArgumentCaptor.forClass(int[].class);
        verify(apiClient).getOptional(eq("/internal/orders/by-ticket/TK-1"), any(), codes.capture());
        assertThat(codes.getValue()).contains(ErrorCode.ORDER_NOT_FOUND.getCode());
    }
}
