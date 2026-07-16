package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query(
            "SELECT f FROM Friendship f WHERE (f.requester = :user1 AND f.addressee = :user2) "
                    + "OR (f.requester = :user2 AND f.addressee = :user1)")
    Optional<Friendship> findFriendshipBetween(
            @Param("user1") User user1, @Param("user2") User user2);

    Optional<Friendship> findByRequesterAndAddressee(User requester, User addressee);

    List<Friendship> findByAddresseeAndStatus(User addressee, FriendshipStatus status);

    @Query(
            "SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriendsByUser(@Param("user") User user);
}
