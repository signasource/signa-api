package com.signasource.signa_api.gamification.config;

import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopCatalogSeeder implements CommandLineRunner {

    private final ShopItemRepository shopItemRepository;

    @Override
    public void run(String... args) {
        List<ShopItem> defaults =
                List.of(
                        ShopItem.builder()
                                .code("xp_multiplier_1_5x")
                                .title("Multiplicador de XP x1.5")
                                .description("Ganá 1.5 veces más XP durante 30 minutos.")
                                .itemType(ShopItemType.XP_MULTIPLIER)
                                .priceGems(50)
                                .quantity(1)
                                .durationMinutes(30)
                                .multiplierValue(1.5)
                                .build(),
                        ShopItem.builder()
                                .code("xp_multiplier_3x")
                                .title("Multiplicador de XP x3")
                                .description("Ganá 3 veces más XP durante 30 minutos.")
                                .itemType(ShopItemType.XP_MULTIPLIER)
                                .priceGems(120)
                                .quantity(1)
                                .durationMinutes(30)
                                .multiplierValue(3.0)
                                .build(),
                        ShopItem.builder()
                                .code("unlimited_lives_15m")
                                .title("Vidas ilimitadas por 15 minutos")
                                .description("Practicá sin límite de vidas durante 15 minutos.")
                                .itemType(ShopItemType.UNLIMITED_LIVES)
                                .priceGems(80)
                                .quantity(1)
                                .durationMinutes(15)
                                .build(),
                        ShopItem.builder()
                                .code("life_refill_single")
                                .title("Recarga de 1 vida")
                                .description("Recuperá 1 vida al instante.")
                                .itemType(ShopItemType.LIFE)
                                .priceGems(20)
                                .quantity(1)
                                .build(),
                        ShopItem.builder()
                                .code("life_refill_full")
                                .title("Recarga completa de vidas")
                                .description("Recuperá tus 5 vidas al instante.")
                                .itemType(ShopItemType.LIFE)
                                .priceGems(90)
                                .quantity(5)
                                .build(),
                        ShopItem.builder()
                                .code("streak_shield_x1")
                                .title("Escudo de racha x1")
                                .description("Protegé tu racha por 1 día sin practicar.")
                                .itemType(ShopItemType.STREAK_SHIELD)
                                .priceGems(40)
                                .quantity(1)
                                .build(),
                        ShopItem.builder()
                                .code("streak_shield_x7")
                                .title("Escudo de racha x7")
                                .description("Protegé tu racha por 7 días sin practicar.")
                                .itemType(ShopItemType.STREAK_SHIELD)
                                .priceGems(250)
                                .quantity(7)
                                .build(),
                        ShopItem.builder()
                                .code("mystery_chest")
                                .title("Cofre sorpresa")
                                .description(
                                        "Abrí el cofre y ganá una recompensa al azar: gemas, vidas,"
                                                + " multiplicadores de XP, vidas ilimitadas o escudos de racha.")
                                .itemType(ShopItemType.MYSTERY_CHEST)
                                .priceGems(60)
                                .quantity(1)
                                .build());

        for (ShopItem item : defaults) {
            if (!shopItemRepository.existsByCode(item.getCode())) {
                shopItemRepository.save(item);
            }
        }
    }
}
