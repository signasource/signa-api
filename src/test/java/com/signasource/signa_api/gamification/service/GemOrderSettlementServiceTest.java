package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GemOrderSettlementServiceTest {

    @Mock private GemOrderRepository orderRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private PurchaseService purchaseService;

    private GemOrderSettlementService service;
    private GemOrder order;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        service =
                new GemOrderSettlementService(
                        orderRepository, userStatsRepository, purchaseService);
        user = User.builder().id(UUID.randomUUID()).build();
        order =
                GemOrder.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gemPack(GemPack.builder().gems(100).productId("pack-sm").build())
                        .gems(100)
                        .amount(BigDecimal.valueOf(500))
                        .currency("ARS")
                        .status(GemOrderStatus.PAID)
                        .build();
        stats = UserStats.builder().id(UUID.randomUUID()).gems(100).build();
    }

    @Test
    void shouldTakeBackGemsAndMarkRefundedWhenReversedManually() {
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(purchaseService.getOrCreateStats(user)).thenReturn(stats);

        service.reverseManual(order.getId());

        assertEquals(GemOrderStatus.REFUNDED, order.getStatus());
        assertNotNull(order.getRefundedAt());
        assertEquals(0, stats.getGems());
        verify(userStatsRepository).save(stats);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldNotAllowManualRefundOfNonPaidOrder() {
        order.setStatus(GemOrderStatus.PENDING);
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(InvalidInputException.class, () -> service.reverseManual(order.getId()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenOrderDoesNotExistForManualRefund() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findForUpdate(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.reverseManual(unknown));
    }
}
