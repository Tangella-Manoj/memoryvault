package io.memoryvault.repository;

import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VaultItemRepository extends JpaRepository<VaultItem, Long>, JpaSpecificationExecutor<VaultItem> {

    Page<VaultItem> findByUserIdOrderBySavedAtDesc(Long userId, Pageable pageable);

    List<VaultItem> findByUserIdAndStatus(Long userId, ItemStatus status);

    List<VaultItem> findByStatusAndEmbeddingIsNull(ItemStatus status);

    long countByUserId(Long userId);

    boolean existsByUserIdAndExternalId(Long userId, String externalId);

    long countByUserIdAndSource(Long userId, io.memoryvault.domain.enums.ItemSource source);

    @Query("""
        select v from VaultItem v
        where v.user.id = :userId
          and v.savedAt <= :cutoff
          and (v.viewCount is null or v.viewCount = 0)
        order by v.importanceScore desc
        """)
    List<VaultItem> findForgottenCandidates(@Param("userId") Long userId, @Param("cutoff") Instant cutoff);

    @Query("""
        select v from VaultItem v
        where v.user.id = :userId
          and v.savedAt between :from and :to
        """)
    List<VaultItem> findSavedOnDate(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);
}
