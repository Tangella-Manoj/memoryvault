package io.memoryvault.repository;

import io.memoryvault.domain.ChromeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChromeSessionRepository extends JpaRepository<ChromeSession, Long> {

    Optional<ChromeSession> findBySessionToken(String sessionToken);
}
