package com.haalkhata.feature.user.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.user.application.UserService;
import com.haalkhata.feature.user.application.UserService.InviteRequest;
import com.haalkhata.feature.user.application.UserService.UpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return userService.listStaff(user);
    }

    @GetMapping("/me/menus")
    public List<String> myMenus(@AuthenticationPrincipal CurrentUser user) {
        return userService.myMenuCodes(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
        return userService.me(user);
    }

    @PostMapping
    public Map<String, Object> invite(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody InviteRequest request) {
        return userService.invite(user, request);
    }

    @PatchMapping("/{userId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String userId,
                                      @RequestBody UpdateRequest request) {
        return userService.update(user, userId, request);
    }

    @DeleteMapping("/{userId}")
    public void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable String userId) {
        userService.remove(user, userId);
    }
}
