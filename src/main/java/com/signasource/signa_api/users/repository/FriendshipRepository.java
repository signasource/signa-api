package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    List<Friendship> findByRequesterAndStatus(User requester, FriendshipStatus status);

    @Query(
            "SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = :status")
    List<Friendship> findAllFriendshipsByUserAndStatus(
            @Param("user") User user, @Param("status") FriendshipStatus status);

    /**
     * Every relation the user takes part in, whatever its status. Used to resolve search results.
     */
    @Query("SELECT f FROM Friendship f WHERE f.requester = :user OR f.addressee = :user")
    List<Friendship> findAllByUser(@Param("user") User user);

    /**
     * How many of {@code myFriendIds} are also accepted friends of {@code candidateId} — the "N
     * amigos en común" shown on a search result.
     */
    @Query(
            "SELECT COUNT(f) FROM Friendship f WHERE f.status = com.signasource.signa_api.users.entity.FriendshipStatus.ACCEPTED "
                    + "AND ((f.requester.id = :candidateId AND f.addressee.id IN :myFriendIds) "
                    + "OR (f.addressee.id = :candidateId AND f.requester.id IN :myFriendIds))")
    long countMutualFriends(
            @Param("candidateId") UUID candidateId,
            @Param("myFriendIds") Collection<UUID> myFriendIds);
}
