package com.haalkhata.feature.sync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseRepository;
import com.haalkhata.feature.purchase.infrastructure.SupplierRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPaymentRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPurchaseRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * Proves the delta-pull paging contract for {@code products}: rows arrive in
 * deterministic {@code (updatedAt, productId)} order, each {@code pull} is
 * capped at {@code limit + 1} (the +1 is the client's {@code hasMore} probe),
 * and a full client-style page walk over {@code after}-chained pulls delivers
 * every row exactly once with no loss and no duplicates.
 */
@ExtendWith(MockitoExtension.class)
class SyncServicePullTest {

    @Mock
    private ObjectMapper mapper;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private SaleItemRepository saleItemRepository;
    @Mock
    private CustomerLedgerEntryRepository ledgerRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseItemRepository purchaseItemRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private VendorPurchaseRepository vendorPurchaseRepository;
    @Mock
    private VendorPaymentRepository vendorPaymentRepository;
    @InjectMocks
    private SyncService service;

    private CurrentUser user;

    /** Live "table" the mocked repositories filter, emulating the DB queries. */
    private final List<Product> liveProducts = new ArrayList<>();

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 10, 0);

    @BeforeEach
    void setUp() {
        user = new CurrentUser("u1", "u@test.com", "User1", "shop_1", "OWNER", List.of("OWNER"));
        liveProducts.clear();
    }

    /**
     * Stubs the product queries against the {@link #liveProducts} "table",
     * emulating the DB queries filtered by {@code after}. Rows are returned
     * in insertion order (deliberately NOT sorted) so the tests prove the
     * service sorts instead of relying on DB ordering. Kept out of
     * {@code setUp} so tests that never query don't trip strict-stubs.
     */
    private void stubProductQueries() {
        Answer<List<Product>> liveDeleted = inv -> {
            LocalDateTime after = inv.getArgument(2);
            return liveProducts.stream()
                    .filter(p -> p.isDeleted() && p.getUpdatedAt().isAfter(after))
                    .toList();
        };
        Answer<List<Product>> liveActive = inv -> {
            LocalDateTime after = inv.getArgument(2);
            return liveProducts.stream()
                    .filter(p -> !p.isDeleted() && p.getUpdatedAt().isAfter(after))
                    .toList();
        };
        when(productRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(eq("shop_1"), eq("shop_1-main"), any()))
                .thenAnswer(liveActive);
        when(productRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(eq("shop_1"), eq("shop_1-main"), any()))
                .thenAnswer(liveDeleted);
    }

    private static Product product(String id, LocalDateTime updatedAt, boolean deleted) {
        return Product.builder()
                .productId(id).shopId("shop_1").branchId("shop_1-main").nameEn("Product " + id)
                .buyingPrice(10.0).purchasePrice(10.0).sellingPrice(12.0)
                .stockQty(5).lowStockAlert(1)
                .isDeleted(deleted).updatedAt(updatedAt)
                .build();
    }

    private static List<String> ids(List<Map<String, Object>> docs) {
        return docs.stream().map(d -> (String) d.get("productId")).toList();
    }

    private static List<LocalDateTime> updatedAts(List<Map<String, Object>> docs) {
        return docs.stream()
                .map(d -> LocalDateTime.parse((String) d.get("updatedAt"), SyncService.ISO))
                .toList();
    }

    @Test
    void pull_products_fullPageWalk_deliversEveryRowExactlyOnceInOrder() {
        stubProductQueries();
        // Five rows with strictly increasing timestamps, inserted scrambled.
        liveProducts.add(product("p3", T0.plusHours(3), false));
        liveProducts.add(product("p1", T0.plusHours(1), false));
        liveProducts.add(product("p5", T0.plusHours(5), false));
        liveProducts.add(product("p2", T0.plusHours(2), false));
        liveProducts.add(product("p4", T0.plusHours(4), false));

        int limit = 2;
        LocalDateTime after = T0;
        List<String> consumed = new ArrayList<>();
        int pages = 0;

        // Client-style walk: consume at most `limit` docs per page, follow
        // `hasMore` (page size > limit), advance `after` to the newest seen.
        while (true) {
            SyncService.PullResult result = service.pull(user, "products", after, limit);
            List<Map<String, Object>> docs = result.documents();
            assertThat(docs).size().isLessThanOrEqualTo(limit + 1);
            assertThat(updatedAts(docs)).isSorted();

            boolean hasMore = docs.size() > limit;
            List<Map<String, Object>> received = hasMore ? docs.subList(0, limit) : docs;
            LocalDateTime newest = after;
            for (Map<String, Object> doc : received) {
                consumed.add((String) doc.get("productId"));
                LocalDateTime updated = LocalDateTime.parse((String) doc.get("updatedAt"), SyncService.ISO);
                if (updated.isAfter(newest)) {
                    newest = updated;
                }
            }
            if (!hasMore) {
                break;
            }
            assertThat(newest).isAfter(after);
            after = newest;
            if (++pages > 10) {
                fail("paging did not terminate");
            }
        }

        assertThat(consumed).containsExactly("p1", "p2", "p3", "p4", "p5");
    }

    @Test
    void pull_products_equalTimestamps_sortedByIdWithHasMoreProbe() {
        stubProductQueries();
        LocalDateTime tie = T0.plusHours(1);
        liveProducts.add(product("c", tie, false));
        liveProducts.add(product("a", tie, false));
        liveProducts.add(product("b", tie, false));

        SyncService.PullResult result = service.pull(user, "products", T0, 2);

        // limit + 1 rows so the client can detect `hasMore`, in stable
        // (updatedAt, productId) order regardless of DB row order.
        assertThat(ids(result.documents())).containsExactly("a", "b", "c");
        assertThat(updatedAts(result.documents())).allMatch(tie::equals);
    }

    @Test
    void pull_products_includesSoftDeletedRows() {
        stubProductQueries();
        liveProducts.add(product("p1", T0.plusHours(1), false));
        liveProducts.add(product("gone", T0.plusHours(2), true));

        SyncService.PullResult result = service.pull(user, "products", T0, null);

        assertThat(ids(result.documents())).containsExactly("p1", "gone");
        Map<String, Object> deleted = result.documents().stream()
                .filter(d -> "gone".equals(d.get("productId")))
                .findFirst()
                .orElseThrow();
        assertThat(deleted.get("isDeleted")).isEqualTo(true);
    }

    @Test
    void pull_serverNow_isCapturedAroundTheQuery() {
        stubProductQueries();
        liveProducts.add(product("p1", T0.plusHours(1), false));

        LocalDateTime before = LocalDateTime.now();
        SyncService.PullResult result = service.pull(user, "products", T0, 10);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result.serverNow()).isNotNull();
        assertThat(result.serverNow()).isAfterOrEqualTo(before);
        assertThat(result.serverNow()).isBeforeOrEqualTo(after);
    }

    @Test
    void pull_unknownCollection_throwsBadRequest() {
        assertThatThrownBy(() -> service.pull(user, "nope", T0, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown collection");
    }
}
