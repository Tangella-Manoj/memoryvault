package io.memoryvault.repository;

import io.memoryvault.domain.DailyDigest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyDigestRepository extends JpaRepository<DailyDigest, Long> {

    Optional<DailyDigest> findByUserIdAndDigestDate(Long userId, LocalDate digestDate);
}
