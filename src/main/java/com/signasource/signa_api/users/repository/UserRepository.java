package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

    /** Case-insensitive contains match. Exact username hits sort first. */
    @Query(
            "SELECT u FROM User u WHERE u.id <> :excludeId AND u.enabled = true "
                    + "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) "
                    + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))) "
                    + "ORDER BY CASE WHEN LOWER(u.username) = LOWER(:query) THEN 0 ELSE 1 END, u.username ASC")
    List<User> searchByUsernameOrName(
            @Param("query") String query, @Param("excludeId") UUID excludeId, Pageable pageable);

    List<User> findByRole(Role role);

    long countByEnabled(boolean enabled);

    long countByVerifiedFalseAndEnabledTrue();

    @Query(
            value =
                    "SELECT u FROM User u WHERE u.enabled = true "
                            + "AND (:query IS NULL OR LOWER(u.username) LIKE :pattern "
                            + "OR LOWER(u.name) LIKE :pattern OR LOWER(u.email) LIKE :pattern) "
                            + "AND (:role IS NULL OR u.role = :role)",
            countQuery =
                    "SELECT COUNT(u) FROM User u WHERE u.enabled = true "
                            + "AND (:query IS NULL OR LOWER(u.username) LIKE :pattern "
                            + "OR LOWER(u.name) LIKE :pattern OR LOWER(u.email) LIKE :pattern) "
                            + "AND (:role IS NULL OR u.role = :role)")
    Page<User> adminSearch(
            @Param("query") String query,
            @Param("pattern") String pattern,
            @Param("role") Role role,
            Pageable pageable);
}
