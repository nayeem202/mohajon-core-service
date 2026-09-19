package com.haalkhata.feature.shop.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.investment.infrastructure.InvestmentCategoryRepository;
import com.haalkhata.feature.investment.infrastructure.InvestmentMemberRepository;
import com.haalkhata.feature.investment.infrastructure.InvestmentRepository;
import com.haalkhata.feature.investment.infrastructure.InvestorRepository;
import com.haalkhata.feature.investment.infrastructure.PayoutRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentMethodRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseRepository;
import com.haalkhata.feature.purchase.infrastructure.SupplierRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.shop.domain.Shop;
import com.haalkhata.feature.shop.domain.ShopOwner;
import com.haalkhata.feature.shop.infrastructure.ShopOwnerRepository;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPaymentRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPurchaseRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopOwnerRepository shopOwnerRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final VendorRepository vendorRepository;
    private final VendorPurchaseRepository vendorPurchaseRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final InvestmentRepository investmentRepository;
    private final InvestmentMemberRepository memberRepository;
    private final InvestorRepository investorRepository;
    private final InvestmentCategoryRepository categoryRepository;
    private final PayoutRepository payoutRepository;

    public record UpdateShopRequest(String name, String location, String phoneNumber, String imagePath,
                                    List<OwnerDto> owners) {
    }

    public record OwnerDto(String ownerId, String name, Double investmentAmount) {
    }

    @Transactional
    public Map<String, Object> getOrCreate(CurrentUser user) {
        Shop shop = shopRepository.findById(user.getShopId()).orElseGet(() -> {
            Shop fresh = Shop.builder()
                    .shopId(user.getShopId())
                    .ownerUserId(user.getUserId())
                    .name(user.getName() == null ? "My Shop" : user.getName() + "'s Shop")
                    .ownerCount(1)
                    .totalInvestment(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            shopRepository.save(fresh);
            shopOwnerRepository.save(ShopOwner.builder()
                    .id(Ids.uuid())
                    .shopId(fresh.getShopId())
                    .ownerId(user.getUserId())
                    .name(user.getName())
                    .investmentAmount(0)
                    .build());
            log.info("SHOP_CREATED shopId={} owner={}", fresh.getShopId(), user.getUserId());
            return fresh;
        });
        return toJson(shop);
    }

    @Transactional
    public Map<String, Object> update(CurrentUser user, UpdateShopRequest request) {
        if (user.getRole() == null || !(user.getRole().equalsIgnoreCase("OWNER")
                || user.getRole().equalsIgnoreCase("ADMIN"))) {
            throw new com.haalkhata.core.exception.BusinessException(
                    "FORBIDDEN", "Only the shop owner or admin can update store information",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        // Auto-provision: a shop row may not exist yet (e.g. legacy user token),
        // so create it instead of failing with a 404.
        Shop shop = shopRepository.findById(user.getShopId()).orElseGet(() -> {
            Shop fresh = Shop.builder()
                    .shopId(user.getShopId())
                    .ownerUserId(user.getUserId())
                    .name(user.getName() == null ? "My Shop" : user.getName() + "'s Shop")
                    .ownerCount(1)
                    .totalInvestment(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            shopRepository.save(fresh);
            shopOwnerRepository.save(ShopOwner.builder()
                    .id(Ids.uuid())
                    .shopId(fresh.getShopId())
                    .ownerId(user.getUserId())
                    .name(user.getName())
                    .investmentAmount(0)
                    .build());
            log.info("SHOP_CREATED_FROM_UPDATE shopId={} owner={}", fresh.getShopId(), user.getUserId());
            return fresh;
        });
        if (request.name() != null && !request.name().isBlank()) {
            shop.setName(request.name().trim());
        }
        if (request.location() != null) {
            shop.setLocation(request.location().trim());
        }
        if (request.phoneNumber() != null) {
            shop.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.imagePath() != null) {
            shop.setImagePath(request.imagePath().isBlank() ? null : request.imagePath().trim());
        }
        if (request.owners() != null) {
            double total = 0;
            for (OwnerDto owner : request.owners()) {
                total += owner.investmentAmount() == null ? 0 : owner.investmentAmount();
            }
            shop.setOwnerCount(request.owners().size());
            shop.setTotalInvestment(total);
            shopOwnerRepository.deleteByShopId(shop.getShopId());
            for (OwnerDto owner : request.owners()) {
                shopOwnerRepository.save(ShopOwner.builder()
                        .id(Ids.uuid())
                        .shopId(shop.getShopId())
                        .ownerId(owner.ownerId())
                        .name(owner.name())
                        .investmentAmount(owner.investmentAmount() == null ? 0 : owner.investmentAmount())
                        .build());
            }
        }
        shop.setUpdatedAt(LocalDateTime.now());
        shopRepository.save(shop);
        log.info("SHOP_UPDATED shopId={} by={}", shop.getShopId(), user.getUserId());
        return toJson(shop);
    }

    @Transactional
    public void clearData(CurrentUser user) {
        String shopId = user.getShopId();
        purchaseRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(shopId)
                .forEach(p -> purchaseItemRepository.deleteByPurchaseId(p.getPurchaseId()));
        saleRepository.findByShopIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(shopId, LocalDateTime.of(2000, 1, 1, 0, 0))
                .forEach(s -> saleItemRepository.deleteBySaleId(s.getSaleId()));
        // Children before parents: investment_members cascades from investments.
        memberRepository.deleteByShopId(shopId);
        payoutRepository.deleteByShopId(shopId);
        categoryRepository.deleteByShopId(shopId);
        investorRepository.deleteByShopId(shopId);
        investmentRepository.deleteByShopId(shopId);
        vendorPurchaseRepository.deleteByShopId(shopId);
        vendorPaymentRepository.deleteByShopId(shopId);
        vendorRepository.deleteByShopId(shopId);
        productRepository.deleteByShopId(shopId);
        customerRepository.deleteByShopId(shopId);
        ledgerRepository.deleteByShopId(shopId);
        saleRepository.deleteByShopId(shopId);
        paymentRepository.deleteByShopId(shopId);
        purchaseRepository.deleteByShopId(shopId);
        supplierRepository.deleteByShopId(shopId);
        expenseRepository.deleteByShopId(shopId);
        shopOwnerRepository.deleteByShopId(shopId);
        log.info("SHOP_DATA_CLEARED shopId={} by={}", shopId, user.getUserId());
    }

    private Map<String, Object> toJson(Shop shop) {
        List<Map<String, Object>> owners = shopOwnerRepository.findByShopId(shop.getShopId())
                .stream().map(o -> {
                    Map<String, Object> ownerJson = new java.util.HashMap<>();
                    ownerJson.put("ownerId", o.getOwnerId() == null ? "" : o.getOwnerId());
                    ownerJson.put("name", o.getName() == null ? "" : o.getName());
                    ownerJson.put("investmentAmount", o.getInvestmentAmount());
                    return ownerJson;
                }).toList();
        Map<String, Object> json = new java.util.HashMap<>();
        json.put("shopId", shop.getShopId());
        json.put("ownerUserId", shop.getOwnerUserId());
        json.put("ownerEmail", appUserRepository.findById(shop.getOwnerUserId())
                .map(value -> value.getEmail() == null ? "" : value.getEmail()).orElse(""));
        json.put("name", shop.getName());
        json.put("location", shop.getLocation() == null ? "" : shop.getLocation());
        json.put("phoneNumber", shop.getPhoneNumber() == null ? "" : shop.getPhoneNumber());
        json.put("imagePath", shop.getImagePath() == null ? "" : shop.getImagePath());
        json.put("owners", owners);
        json.put("ownerCount", shop.getOwnerCount());
        json.put("totalInvestment", shop.getTotalInvestment());
        json.put("createdAt", shop.getCreatedAt().toString());
        json.put("updatedAt", shop.getUpdatedAt().toString());
        return json;
    }
}
