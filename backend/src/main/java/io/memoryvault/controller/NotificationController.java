package io.memoryvault.controller;

import io.memoryvault.domain.User;
import io.memoryvault.domain.UserNotificationPreference;
import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.NotificationPreferenceResponse;
import io.memoryvault.dto.PushSubscriptionRequest;
import io.memoryvault.repository.UserNotificationPreferenceRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.EngagementPatternService;
import io.memoryvault.service.NotificationSchedulerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final EngagementPatternService engagementPatternService;
    private final NotificationSchedulerService notificationSchedulerService;

    public NotificationController(
            UserNotificationPreferenceRepository preferenceRepository,
            UserRepository userRepository,
            EngagementPatternService engagementPatternService,
            NotificationSchedulerService notificationSchedulerService
    ) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.engagementPatternService = engagementPatternService;
        this.notificationSchedulerService = notificationSchedulerService;
    }

    @GetMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> preferences() {
        Long userId = SecurityUtil.currentUserId();
        var preference = preferenceRepository.findByUserId(userId);
        if (preference.isEmpty()) {
            return ApiResponse.success(new NotificationPreferenceResponse(false, false, 8, null), "Preferences");
        }
        var p = preference.get();
        return ApiResponse.success(
                new NotificationPreferenceResponse(p.isNotificationsEnabled(), p.getPushSubscriptionJson() != null, p.getOptimalHour(), p.getLastCalculatedAt()),
                "Preferences"
        );
    }

    @PostMapping("/subscribe")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<NotificationPreferenceResponse> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        Long userId = SecurityUtil.currentUserId();
        User user = userRepository.getReferenceById(userId);

        UserNotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserNotificationPreference.builder().user(user).build());
        preference.setPushSubscriptionJson(request.subscription());
        preference.setNotificationsEnabled(true);
        preferenceRepository.save(preference);

        return ApiResponse.success(
                new NotificationPreferenceResponse(true, true, preference.getOptimalHour(), preference.getLastCalculatedAt()),
                "Subscribed"
        );
    }

    @PostMapping("/unsubscribe")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Void> unsubscribe() {
        Long userId = SecurityUtil.currentUserId();
        preferenceRepository.findByUserId(userId).ifPresent(p -> {
            p.setPushSubscriptionJson(null);
            p.setNotificationsEnabled(false);
            preferenceRepository.save(p);
        });
        return ApiResponse.success(null, "Unsubscribed");
    }

    @PostMapping("/recalculate")
    public ApiResponse<Map<String, Integer>> recalculate() {
        Long userId = SecurityUtil.currentUserId();
        int hour = engagementPatternService.recalculateForUser(userId);
        return ApiResponse.success(Map.of("optimalHour", hour), "Recalculated");
    }

    /**
     * Manually fires the hourly notification scheduler for a given hour — for testing.
     * Restricted to admins since it processes every due user's data, not just the caller's.
     */
    @PostMapping("/test-trigger")
    public ApiResponse<Map<String, Integer>> testTrigger(@RequestParam int hour) {
        if (!SecurityUtil.currentUserIsAdmin()) {
            throw new io.memoryvault.exception.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin only");
        }
        int sent = notificationSchedulerService.run(hour);
        return ApiResponse.success(Map.of("notificationsSent", sent), "Scheduler run complete");
    }
}
