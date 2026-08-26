package io.memoryvault.repository;

import io.memoryvault.domain.UserContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserContextRepository extends JpaRepository<UserContext, Long> {

    List<UserContext> findByUserId(Long userId);

    Optional<UserContext> findByUserIdAndContextType(Long userId, String contextType);
}
