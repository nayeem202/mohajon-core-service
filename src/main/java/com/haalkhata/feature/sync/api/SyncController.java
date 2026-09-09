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
                                    @RequestParam(required = false) String after,
                                    @RequestParam(required = false) Integer limit) {
        LocalDateTime afterTime = TimeUtil.parse(after, LocalDateTime.of(2000, 1, 1, 0, 0));
        List<Map<String, Object>> all = syncService.pull(user, collection, afterTime, limit);
        // Legacy clients omit `limit` and get the full result set. Paging
        // clients pass `limit` and follow `hasMore` until it is false.
        boolean hasMore = false;
        List<Map<String, Object>> documents = all;
        if (limit != null && limit > 0 && all.size() > limit) {
            documents = all.subList(0, limit);
            hasMore = true;
        }
        return Map.of("collection", collection, "documents", documents, "hasMore", hasMore);
    }

    public record PushRequest(String collection, List<Map<String, Object>> documents) {
    }
}
