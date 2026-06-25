package com.signasource.signa_api.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.signasource.signa_api.notification.entity.DeviceToken;
import com.signasource.signa_api.users.entity.User;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

	Optional<DeviceToken> findByToken(String token);

	@Query("""
				select dt.token
				from DeviceToken dt
				where dt.user.id = :userId
			""")
	List<String> findTokensByUserId(UUID userId);

	@Query("""
				select dt.token
				from DeviceToken dt
				where dt.user.id in :userIds
			""")
	List<String> findTokensByUserIds(Collection<UUID> userIds);

	void deleteByTokenAndUser(String token, User user);

	void deleteByUser(User user);

	void deleteByTokenIn(Collection<String> tokens);
}
