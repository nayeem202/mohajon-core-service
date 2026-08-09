package com.haalkhata.feature.shop.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.shop.application.ShopService;
import com.haalkhata.feature.shop.application.ShopService.UpdateShopRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/me")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user) {
        return shopService.getOrCreate(user);
    }

    @PutMapping("/me")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @RequestBody UpdateShopRequest request) {
        return shopService.update(user, request);
    }

    @DeleteMapping("/me/data")
    public void clearData(@AuthenticationPrincipal CurrentUser user) {
        shopService.clearData(user);
    }
}
