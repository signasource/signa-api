package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.SignLanguage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SignLanguageRepository extends JpaRepository<SignLanguage, UUID> {

    Optional<SignLanguage> findByCode(String code);
}
