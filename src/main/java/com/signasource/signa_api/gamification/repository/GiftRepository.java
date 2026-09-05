package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Gift;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftRepository extends JpaRepository<Gift, UUID> {

    @EntityGraph(attributePaths = {"sender", "recipient", "shopItem"})
    List<Gift> findByRecipientOrderBySentAtDesc(User recipient);

    @EntityGraph(attributePaths = {"sender", "recipient", "shopItem"})
    List<Gift> findByRecipientAndStatusOrderBySentAtDesc(User recipient, GiftStatus status);

    @EntityGraph(attributePaths = {"sender", "recipient", "shopItem"})
    List<Gift> findBySenderOrderBySentAtDesc(User sender);

    @EntityGraph(attributePaths = {"sender", "recipient", "shopItem"})
    Optional<Gift> findByIdAndRecipient(UUID id, User recipient);
}
