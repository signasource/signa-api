package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopCatalogServiceTest {

    @Mock private ShopItemRepository shopItemRepository;

    @InjectMocks private ShopCatalogService shopCatalogService;

    private ShopItem item() {
        return ShopItem.builder()
                .id(UUID.randomUUID())
                .code("streak_shield_x1")
                .title("Escudo de racha x1")
                .description("desc")
                .itemType(ShopItemType.STREAK_SHIELD)
                .priceGems(40)
                .quantity(1)
                .active(true)
                .build();
    }

    @Test
    void getItems_whenNoTypeFilter_returnsAllActiveItems() {
        when(shopItemRepository.findByActiveTrueOrderByPriceGemsAsc()).thenReturn(List.of(item()));

        List<ShopItemResponse> result = shopCatalogService.getItems(null);

        assertEquals(1, result.size());
    }

    @Test
    void getItems_whenTypeFilterProvided_returnsFilteredItems() {
        when(shopItemRepository.findByItemTypeAndActiveTrueOrderByPriceGemsAsc(
                        ShopItemType.STREAK_SHIELD))
                .thenReturn(List.of(item()));

        List<ShopItemResponse> result = shopCatalogService.getItems(ShopItemType.STREAK_SHIELD);

        assertEquals(1, result.size());
        assertEquals(ShopItemType.STREAK_SHIELD, result.get(0).itemType());
    }

    @Test
    void getItemById_whenFound_returnsItem() {
        ShopItem item = item();
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        ShopItemResponse result = shopCatalogService.getItemById(item.getId());

        assertEquals(item.getCode(), result.code());
    }

    @Test
    void getItemById_whenNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(shopItemRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> shopCatalogService.getItemById(id));
    }
}
