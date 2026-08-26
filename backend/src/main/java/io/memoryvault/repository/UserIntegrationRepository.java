package io.memoryvault.repository;

import io.memoryvault.domain.UserIntegration;
import io.memoryvault.domain.enums.IntegrationPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserIntegrationRepository extends JpaRepository<UserIntegration, Long> {

    Optional<UserIntegration> findByUserIdAndPlatform(Long userId, IntegrationPlatform platform);

    List<UserIntegration> findByPlatformAndSyncEnabledTrue(IntegrationPlatform platform);
}
