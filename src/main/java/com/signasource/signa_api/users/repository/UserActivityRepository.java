package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.UserActivity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {}
