package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.UserSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {}
