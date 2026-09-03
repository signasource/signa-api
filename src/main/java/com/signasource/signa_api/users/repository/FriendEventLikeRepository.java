package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.FriendEventLike;
import com.signasource.signa_api.users.entity.FriendEventType;
import com.signasource.signa_api.users.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendEventLikeRepository extends JpaRepository<FriendEventLike, Long> {

    Optional<FriendEventLike> findByUserAndEventTypeAndEventRefId(
            User user, FriendEventType eventType, UUID eventRefId);

    List<FriendEventLike> findByUserAndEventRefIdIn(User user, Collection<UUID> eventRefIds);
}
