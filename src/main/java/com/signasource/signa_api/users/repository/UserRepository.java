package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByName(String name);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Case-insensitive "contains" match on username or display name, excluding the caller and
     * disabled accounts. Exact username matches sort first so typing a full handle finds it at the
     * top.
     */
    @Query(
            "SELECT u FROM User u WHERE u.id <> :excludeId AND u.enabled = true "
                    + "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) "
                    + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))) "
                    + "ORDER BY CASE WHEN LOWER(u.username) = LOWER(:query) THEN 0 ELSE 1 END, u.username ASC")
    List<User> searchByUsernameOrName(
            @Param("query") String query, @Param("excludeId") UUID excludeId, Pageable pageable);
}
