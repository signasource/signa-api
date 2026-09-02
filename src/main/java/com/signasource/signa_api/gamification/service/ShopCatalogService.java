package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopCatalogService {

    private final ShopItemRepository shopItemRepository;

    @Transactional(readOnly = true)
    public List<ShopItemResponse> getItems(ShopItemType itemType) {
        List<ShopItem> items =
                itemType == null
                        ? shopItemRepository.findByActiveTrueOrderByPriceGemsAsc()
                        : shopItemRepository.findByItemTypeAndActiveTrueOrderByPriceGemsAsc(
                                itemType);

        return items.stream().map(ShopItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShopItemResponse getItemById(UUID id) {
        ShopItem item =
                shopItemRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Shop item not found"));

        return ShopItemResponse.from(item);
    }
}
