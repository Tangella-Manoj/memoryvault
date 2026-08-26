package io.memoryvault.repository;

import io.memoryvault.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
}
