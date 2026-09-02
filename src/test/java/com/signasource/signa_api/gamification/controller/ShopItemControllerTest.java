package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.ShopCatalogService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ShopItemControllerTest {

    @Mock private ShopCatalogService shopCatalogService;

    @InjectMocks private ShopItemController shopItemController;

    @Test
    void shouldReturnItemsList() {
        ShopItemResponse item =
                new ShopItemResponse(
                        UUID.randomUUID(),
                        "streak_shield_x1",
                        "Escudo de racha x1",
                        "desc",
                        ShopItemType.STREAK_SHIELD,
                        40,
                        1,
                        null,
                        null,
                        true);
        when(shopCatalogService.getItems(null)).thenReturn(List.of(item));

        ResponseEntity<List<ShopItemResponse>> response = shopItemController.getItems(null);

        verify(shopCatalogService).getItems(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldReturnItemById() {
        UUID id = UUID.randomUUID();
        ShopItemResponse item =
                new ShopItemResponse(
                        id,
                        "mystery_chest",
                        "Cofre sorpresa",
                        "desc",
                        ShopItemType.MYSTERY_CHEST,
                        60,
                        1,
                        null,
                        null,
                        true);
        when(shopCatalogService.getItemById(id)).thenReturn(item);

        ResponseEntity<ShopItemResponse> response = shopItemController.getItemById(id);

        verify(shopCatalogService).getItemById(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(item, response.getBody());
    }
}
