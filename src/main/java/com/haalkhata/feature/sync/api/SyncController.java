package com.haalkhata.feature.sync.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.TimeUtil;
import com.haalkhata.feature.sync.application.SyncService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/push")
    public Map<String, Object> push(@AuthenticationPrincipal CurrentUser user,
                                    @RequestBody PushRequest request) {
        int saved = syncService.push(user, request.collection(), request.documents());
        return Map.of("saved", saved, "collection", request.collection());
    }

    @GetMapping("/pull")
    public Map<String, Object> pull(@AuthenticationPrincipal CurrentUser user,
                                    @RequestParam String collection,
                                    @RequestParam(required = false) String after) {
        LocalDateTime afterTime = TimeUtil.parse(after, LocalDateTime.of(2000, 1, 1, 0, 0));
        List<Map<String, Object>> documents = syncService.pull(user, collection, afterTime);
        return Map.of("collection", collection, "documents", documents);
    }

    public record PushRequest(String collection, List<Map<String, Object>> documents) {
    }
}
