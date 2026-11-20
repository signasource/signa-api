package com.signasource.signa_api.gamification.config;

import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the gem packs sold through Mercado Pago, in ARS. Prices are defaults for a pack that has
 * none yet: a price already set (e.g. edited in the database) is never overwritten.
 */
@Component
@RequiredArgsConstructor
public class GemPackSeeder implements CommandLineRunner {

    private final GemPackRepository gemPackRepository;

    @Override
    public void run(String... args) {
        List<GemPack> defaults =
                List.of(
                        pack("gems_pack_120", 120, "1500", 1),
                        pack("gems_pack_300", 300, "3300", 2),
                        pack("gems_pack_850", 850, "8500", 3),
                        pack("gems_pack_2000", 2000, "17500", 4));

        for (GemPack pack : defaults) {
            gemPackRepository
                    .findByProductId(pack.getProductId())
                    .ifPresentOrElse(
                            existing -> {
                                if (existing.getPrice() == null
                                        || existing.getPrice().signum() <= 0) {
                                    existing.setPrice(pack.getPrice());
                                    gemPackRepository.save(existing);
                                }
                            },
                            () -> gemPackRepository.save(pack));
        }
    }

    private static GemPack pack(String code, int gems, String priceArs, int sortOrder) {
        return GemPack.builder()
                .productId(code)
                .gems(gems)
                .price(new BigDecimal(priceArs))
                .sortOrder(sortOrder)
                .build();
    }
}
