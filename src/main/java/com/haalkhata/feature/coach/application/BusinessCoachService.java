package com.haalkhata.feature.coach.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.coach.domain.BusinessCoachInsight;
import com.haalkhata.feature.coach.infrastructure.BusinessCoachInsightRepository;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.notification.application.NotificationService;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.shop.domain.Shop;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.branch.infrastructure.BranchRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessCoachService {
    private final BusinessCoachInsightRepository insightRepository;
    private final ShopRepository shopRepository;
    private final BranchRepository branchRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ExpenseRepository expenseRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(CurrentUser user) {
        return historyForShop(user.getShopId(), user.getBranchId());
    }

    @Transactional
    public List<Map<String, Object>> refresh(String shopId, String ownerUserId) {
        return refresh(shopId, ownerUserId, shopId + "-main");
    }

    @Transactional
    public List<Map<String, Object>> refresh(String shopId, String ownerUserId, String branchId) {
        LocalDate today = LocalDate.now();
        boolean alreadyGenerated = insightRepository.countByShopIdAndBranchIdAndInsightDate(shopId, branchId, today) > 0;
        List<BusinessCoachInsight> generated = generate(shopId, branchId, today);
        generated.forEach(item -> insightRepository.findByShopIdAndBranchIdAndInsightDateAndType(shopId, branchId, today, item.getType())
                .ifPresentOrElse(existing -> { item.setInsightId(existing.getInsightId()); insightRepository.save(item); },
                        () -> insightRepository.save(item)));
        if (!alreadyGenerated) {
            try {
                notificationService.sendLocalized(new CurrentUser(ownerUserId, "", "", shopId, "OWNER", List.of("OWNER"), branchId),
                        "Mohajon Business Coach", generated.isEmpty()
                                ? "Your daily business brief is ready. No urgent warning today."
                                : "Your daily business brief is ready.",
                        "মহাজন বিজনেস কোচ", generated.isEmpty()
                                ? "আজকের ব্যবসার পরামর্শ প্রস্তুত। আজ কোনো জরুরি সতর্কতা নেই।"
                                : "আজকের ব্যবসার পরামর্শ প্রস্তুত।",
                        Map.of("type", "platform", "screen", "business_coach",
                                "template", "business_coach_daily"));
            } catch (BusinessException ex) {
                log.warn("Coach history saved but push was not sent for shop {}: {}", shopId, ex.getMessage());
            }
        }
        return historyForShop(shopId, branchId);
    }

    @Scheduled(cron = "${app.business-coach.cron:0 0 9 * * *}", zone = "${app.business-coach.zone:Asia/Dhaka}")
    public void generateDailyInsights() {
        for (Shop shop : shopRepository.findAll()) {
            var branches = branchRepository.findByShopIdOrderByIsDefaultDescNameAsc(shop.getShopId());
            if (branches.isEmpty()) {
                try { refresh(shop.getShopId(), shop.getOwnerUserId()); }
                catch (Exception ex) { log.error("Daily coach generation failed for shop {}", shop.getShopId(), ex); }
            } else for (var branch : branches) {
                try { refresh(shop.getShopId(), shop.getOwnerUserId(), branch.getBranchId()); }
                catch (Exception ex) { log.error("Daily coach generation failed for shop {} branch {}", shop.getShopId(), branch.getBranchId(), ex); }
            }
        }
    }

    private List<BusinessCoachInsight> generate(String shopId, String branchId, LocalDate today) {
        List<BusinessCoachInsight> result = new ArrayList<>();
        List<Sale> sales = saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(shopId).stream()
                .filter(s -> branchId.equals(s.getBranchId())).toList();
        Map<String, Double> margins = new HashMap<>(); Map<String, String> names = new HashMap<>();
        Set<String> recentProducts = sales.stream().filter(s -> s.getCreatedAt().toLocalDate().isAfter(today.minusDays(30)))
                .flatMap(s -> saleItemRepository.findBySaleId(s.getSaleId()).stream()).map(SaleItem::getProductId)
                .filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        for (Sale sale : sales) for (SaleItem item : saleItemRepository.findBySaleId(sale.getSaleId())) {
            String key = item.getProductId() == null ? item.getName() : item.getProductId();
            margins.merge(key, (item.getUnitPrice() - item.getPurchasePrice()) * item.getQty(), Double::sum);
            names.putIfAbsent(key, item.getName());
        }
        margins.entrySet().stream().filter(e -> e.getValue() < 0).min(Map.Entry.comparingByValue()).ifPresent(e ->
                result.add(make(shopId, branchId, "profit_leak", 1, "Margin leak detected", "A product is being sold below purchase cost.",
                        "Review price or supplier cost for " + names.get(e.getKey()) + ".", "কম দামে বিক্রি হচ্ছে", "ক্রয়মূল্যের নিচে বিক্রি হচ্ছে।", "পণ্যের দাম বা supplier cost review করুন।", Math.abs(e.getValue()), today)));
        productRepository.findByShopIdAndBranchIdAndIsDeletedFalseOrderByNameEnAsc(shopId, branchId).stream().filter(p -> p.getStockQty() > 0 && !recentProducts.contains(p.getProductId())).findFirst().ifPresent(p ->
                result.add(make(shopId, branchId, "dead_stock", 2, "Dead stock needs attention", "Stock has not moved in the last 30 days.",
                        "Run an offer or pause replenishment for " + p.getNameEn() + ".", "Dead stock নজরে রাখুন", "গত ৩০ দিনে এই stock বিক্রি হয়নি।", "Offer দিন বা replenishment pause করুন।", null, today)));
        Map<String, Double> due = sales.stream().filter(s -> s.getDueAmount() > 0).collect(Collectors.groupingBy(Sale::getCustomerId, Collectors.summingDouble(Sale::getDueAmount)));
        Map<String, Customer> customers = customerRepository.findByShopIdAndIsDeletedFalseOrderByNameAsc(shopId).stream().collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a,b) -> a));
        due.entrySet().stream().filter(e -> e.getValue() >= 5000).max(Map.Entry.comparingByValue()).ifPresent(e -> {
            Customer c = customers.get(e.getKey()); String name = c == null ? "customer" : c.getName();
            result.add(make(shopId, branchId, "risky_customer", 1, "Customer due is high", "A customer has a high outstanding balance.", "Follow up with " + name + ".", "Customer due risk", "একজন customer-এর due অনেক বেশি।", "Customer-এর সাথে follow-up করুন।", e.getValue(), today));
        });
        LocalDateTime from = today.withDayOfMonth(1).atStartOfDay();
        expenseRepository.findByShopIdAndBranchIdAndIsDeletedFalseOrderByDateDesc(shopId, branchId).stream().filter(e -> e.getDate().isAfter(from.minusMonths(1)))
                .collect(Collectors.groupingBy(e -> e.getCategoryLabel() == null ? "Other" : e.getCategoryLabel(), Collectors.summingDouble(e -> e.getAmount())))
                .entrySet().stream().filter(e -> e.getValue() >= 1000).max(Map.Entry.comparingByValue()).ifPresent(e ->
                result.add(make(shopId, branchId, "unusual_expense", 2, "Expense needs review", "A spending category is unusually high this period.", "Review recent " + e.getKey() + " expenses.", "খরচ review করুন", "একটি খরচের category অস্বাভাবিকভাবে বেশি।", "সাম্প্রতিক খরচগুলো review করুন।", e.getValue(), today)));
        return result.stream().sorted(Comparator.comparingInt(BusinessCoachInsight::getPriority)).limit(8).toList();
    }

    private BusinessCoachInsight make(String shopId, String branchId, String type, int priority, String titleEn, String bodyEn, String actionEn,
                                      String titleBn, String bodyBn, String actionBn, Double amount, LocalDate date) {
        return BusinessCoachInsight.builder().insightId(Ids.uuid()).shopId(shopId).branchId(branchId).insightDate(date).type(type).priority(priority)
                .titleEn(titleEn).bodyEn(bodyEn).actionEn(actionEn).titleBn(titleBn).bodyBn(bodyBn).actionBn(actionBn)
                .amount(amount).createdAt(LocalDateTime.now()).build();
    }

    private List<Map<String, Object>> historyForShop(String shopId, String branchId) {
        return insightRepository.findTop50ByShopIdAndBranchIdOrderByCreatedAtDesc(shopId, branchId).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>(); m.put("id", i.getInsightId()); m.put("date", i.getInsightDate());
            m.put("type", i.getType()); m.put("priority", i.getPriority()); m.put("titleEn", i.getTitleEn()); m.put("titleBn", i.getTitleBn());
            m.put("bodyEn", i.getBodyEn()); m.put("bodyBn", i.getBodyBn()); m.put("actionEn", i.getActionEn()); m.put("actionBn", i.getActionBn());
            m.put("amount", i.getAmount()); m.put("createdAt", i.getCreatedAt()); return m;
        }).toList();
    }
}
