package com.haalkhata.feature.growth.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CustomerGrowthService {
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;

    public Map<String,Object> dashboard(CurrentUser user) {
        var customers = customerRepository.findByShopIdAndIsDeletedFalseOrderByNameAsc(user.getShopId());
        var sales = saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId());
        var payments = paymentRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId());
        var segments = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (Customer c : customers) {
            var cs = sales.stream().filter(s->s.getCustomerId().equals(c.getCustomerId())).toList();
            var last = cs.stream().map(Sale::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);
            var spent = cs.stream().mapToDouble(Sale::getTotalAmount).sum();
            var due = cs.stream().mapToDouble(Sale::getDueAmount).sum() - payments.stream().filter(p->p.getCustomerId().equals(c.getCustomerId()) && p.getSaleId()==null).mapToDouble(p->p.getAmount()).sum();
            String segment = cs.isEmpty() ? "NEW" : last != null && last.isBefore(LocalDateTime.now().minusDays(60)) ? "INACTIVE" : spent >= 10000 ? "VIP" : due >= 5000 ? "AT_RISK" : "REGULAR";
            segments.computeIfAbsent(segment,k->new ArrayList<>()).add(customer(c,spent,due,last,cs.size()));
        }
        List<Map<String,Object>> recommendations = new ArrayList<>();
        addRecommendation(recommendations, "INACTIVE", "Bring back inactive customers", "Send a personalized offer to customers who have not purchased in 60 days.", segments);
        addRecommendation(recommendations, "VIP", "Protect VIP relationships", "Offer early access or a loyalty reward to your highest-value customers.", segments);
        addRecommendation(recommendations, "AT_RISK", "Collect dues before offering credit", "Follow up with high-value customers before extending more credit.", segments);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("segments",segments); result.put("recommendations",recommendations); result.put("generatedAt",LocalDateTime.now()); return result;
    }
    private Map<String,Object> customer(Customer c,double spent,double due,LocalDateTime last,int visits){Map<String,Object> m=new LinkedHashMap<>();m.put("customerId",c.getCustomerId());m.put("name",c.getName());m.put("phone",c.getPhone());m.put("spent",spent);m.put("due",due);m.put("lastPurchaseAt",last);m.put("visits",visits);return m;}
    private void addRecommendation(List<Map<String,Object>> list,String segment,String title,String body,Map<String,List<Map<String,Object>>> segments){if(segments.containsKey(segment)&&!segments.get(segment).isEmpty()){Map<String,Object> m=new LinkedHashMap<>();m.put("segment",segment);m.put("title",title);m.put("body",body);m.put("count",segments.get(segment).size());list.add(m);}}
}
