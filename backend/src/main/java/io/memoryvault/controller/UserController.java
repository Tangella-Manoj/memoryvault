package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.UserProfileResponse;
import io.memoryvault.exception.ResourceNotFoundException;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.security.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        Long userId = SecurityUtil.currentUserId();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return ApiResponse.success(UserProfileResponse.from(user), "Profile");
    }
}
