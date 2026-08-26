package io.memoryvault.repository;

import io.memoryvault.domain.UserBehaviorPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBehaviorPatternRepository extends JpaRepository<UserBehaviorPattern, Long> {

    List<UserBehaviorPattern> findByUserId(Long userId);

    Optional<UserBehaviorPattern> findByUserIdAndPatternType(Long userId, String patternType);
}
