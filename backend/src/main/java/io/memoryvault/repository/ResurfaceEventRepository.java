package io.memoryvault.repository;

import io.memoryvault.domain.ResurfaceEvent;
import io.memoryvault.domain.enums.ResurfaceAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ResurfaceEventRepository extends JpaRepository<ResurfaceEvent, Long> {

    List<ResurfaceEvent> findByUserIdAndShownAtAfter(Long userId, Instant since);

    List<ResurfaceEvent> findByVaultItemId(Long vaultItemId);

    List<ResurfaceEvent> findByUserIdAndActionIn(Long userId, List<ResurfaceAction> actions);
}
