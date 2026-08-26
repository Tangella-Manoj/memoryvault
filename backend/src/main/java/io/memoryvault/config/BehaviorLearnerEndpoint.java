package io.memoryvault.config;

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

    @WriteOperation
    public Map<String, Object> runForUser(@Selector Long userId) {
        return behaviorLearnerService.runForUser(userId);
    }
}
