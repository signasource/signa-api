package com.signasource.signa_api.gamification.config;

import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the gem packs sold through Google Play. The product ids must match the in-app products
 * created in Play Console; prices live there, only the gem amount lives here.
 */
@Component
@RequiredArgsConstructor
public class GemPackSeeder implements CommandLineRunner {

    private final GemPackRepository gemPackRepository;

    @Override
    public void run(String... args) {
        List<GemPack> defaults =
                List.of(
                        GemPack.builder().productId("gems_pack_120").gems(120).sortOrder(1).build(),
                        GemPack.builder().productId("gems_pack_300").gems(300).sortOrder(2).build(),
                        GemPack.builder().productId("gems_pack_850").gems(850).sortOrder(3).build(),
                        GemPack.builder()
                                .productId("gems_pack_2000")
                                .gems(2000)
                                .sortOrder(4)
                                .build());

        for (GemPack pack : defaults) {
            if (!gemPackRepository.existsByProductId(pack.getProductId())) {
                gemPackRepository.save(pack);
            }
        }
    }
}
