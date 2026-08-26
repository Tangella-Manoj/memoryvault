package io.memoryvault.config;

import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.intelligence.BehaviorLearnerService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "behaviorLearner")
public class BehaviorLearnerEndpoint {

    private final BehaviorLearnerService behaviorLearnerService;

    public BehaviorLearnerEndpoint(BehaviorLearnerService behaviorLearnerService) {
        this.behaviorLearnerService = behaviorLearnerService;
    }

    /**
     * Manually triggers the nightly behavior-learning pass for a single user.
     * Restricted to the user themselves or an ADMIN — see {@link SecurityUtil#requireSelfOrAdmin}.
     *
     * @param userId the user to recompute patterns for
     * @return a summary of items refreshed, context counts, and the recomputed intelligence score
     */
    @WriteOperation
    public Map<String, Object> runForUser(@Selector Long userId) {
        SecurityUtil.requireSelfOrAdmin(userId);
        return behaviorLearnerService.runForUser(userId);
    }
}
