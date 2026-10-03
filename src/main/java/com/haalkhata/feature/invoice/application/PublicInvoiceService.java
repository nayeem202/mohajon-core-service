package com.haalkhata.feature.invoice.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.security.JwtService;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import io.jsonwebtoken.Claims;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicInvoiceService {
    private final SaleRepository saleRepository;
    private final SaleItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final JwtService jwtService;
    private final AppProperties props;

    public Map<String, Object> createLink(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(
                        saleId, user.getShopId(), user.getBranchId())
                .orElseThrow(() -> BusinessException.notFound("Sale not found"));
        if (props.getPublicBaseUrl() == null || props.getPublicBaseUrl().isBlank()) {
            throw BusinessException.badRequest("Public invoice URL is not configured");
        }
        String base = props.getPublicBaseUrl().replaceAll("/+$", "");
        return Map.of("url", base + "/public/invoices/"
                + jwtService.issuePublicInvoiceToken(sale.getSaleId(), sale.getShopId(), sale.getBranchId()));
    }

    public String render(String token) {
        Claims claims = jwtService.parse(token);
        if (!"public-invoice".equals(claims.get("purpose", String.class))) {
            throw BusinessException.notFound("Invoice link is invalid");
        }
        String saleId = claims.getSubject();
        String shopId = claims.get("shopId", String.class);
        String branchId = claims.get("branchId", String.class);
        Sale sale = (branchId == null || branchId.isBlank()
                ? saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(saleId, shopId)
                : saleRepository.findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(saleId, shopId, branchId))
                .orElseThrow(() -> BusinessException.notFound("Invoice not found"));
        var shop = shopRepository.findById(shopId).orElse(null);
        String shopName = shop == null ? "Mohajon" : shop.getName();
        String customer = customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(sale.getCustomerId(), shopId)
                .map(c -> c.getName()).orElse("Customer");
        StringBuilder rows = new StringBuilder();
        itemRepository.findBySaleId(saleId).forEach(item -> rows.append("<tr><td>")
                .append(escape(item.getName())).append("</td><td>").append(item.getQty())
                .append("</td><td>").append(item.getUnitPrice()).append("</td></tr>"));
        double subtotal = itemRepository.findBySaleId(saleId).stream()
                .mapToDouble(item -> item.getQty() * item.getUnitPrice()).sum();
        double taxable = Math.max(0, subtotal - sale.getDiscount());
        double vat = taxable * (sale.getVatPercent() / 100d);
        String taxIds = (shop == null || shop.getTin() == null || shop.getTin().isBlank()
                ? "" : "TIN: " + escape(shop.getTin()))
                + (shop == null || shop.getBin() == null || shop.getBin().isBlank()
                ? "" : (shop == null || shop.getTin() == null || shop.getTin().isBlank() ? "" : " · ")
                + "BIN: " + escape(shop.getBin()));
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Invoice</title>"
                + "<style>body{font-family:Arial,sans-serif;max-width:680px;margin:32px auto;padding:0 18px;color:#182230}h1{margin-bottom:4px}.muted{color:#64748b;font-size:13px}table{width:100%;border-collapse:collapse;margin-top:24px}th,td{padding:10px 6px;border-bottom:1px solid #e5e7eb;text-align:left}td:nth-child(n+2),th:nth-child(n+2){text-align:right}.summary{max-width:280px;margin:18px 0 0 auto}.summary div{display:flex;justify-content:space-between;padding:4px 0}.total{font-size:20px;font-weight:700;text-align:right;margin-top:20px}</style></head><body>"
                + "<h1>" + escape(shopName) + "</h1><div class='muted'>" + taxIds + "</div><div>Invoice: " + escape(saleId) + "</div><div>Customer: " + escape(customer) + "</div>"
                + "<table><thead><tr><th>Item</th><th>Qty</th><th>Price</th></tr></thead><tbody>" + rows + "</tbody></table>"
                + "<div class='summary'><div><span>Subtotal</span><span>" + subtotal + "</span></div>"
                + "<div><span>Discount</span><span>-" + sale.getDiscount() + "</span></div>"
                + "<div><span>VAT (" + sale.getVatPercent() + "%)</span><span>" + vat + "</span></div></div>"
                + "<div class='total'>Total: " + sale.getTotalAmount() + "</div><div>Paid: " + sale.getPaidAmount() + " · Due: " + sale.getDueAmount() + "</div></body></html>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
