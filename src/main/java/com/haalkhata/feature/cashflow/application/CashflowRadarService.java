package com.haalkhata.feature.cashflow.application;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.sale.returning.infrastructure.SaleReturnRepository;
import java.time.LocalDateTime; import java.util.LinkedHashMap; import java.util.Map;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CashflowRadarService {
 private final PaymentRepository payments; private final ExpenseRepository expenses; private final SaleRepository sales; private final SaleReturnRepository returns;
 public Map<String,Object> forecast(CurrentUser user,double change){
  var now=LocalDateTime.now(); var from=now.minusDays(30); var ps=payments.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId());
  double in=ps.stream().filter(p->user.getBranchId().equals(p.getBranchId())).filter(p->p.getCreatedAt().isAfter(from)).mapToDouble(p->p.getAmount()).sum();
  in-=returns.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream().filter(x->user.getBranchId().equals(x.getBranchId())).filter(x->x.getCreatedAt().isAfter(from)).mapToDouble(x->x.getRefundAmount()).sum();
  double out=expenses.findByShopIdAndIsDeletedFalseOrderByDateDesc(user.getShopId()).stream().filter(e->e.getDate().isAfter(from)).mapToDouble(e->e.getAmount()).sum();
  double due=sales.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream().filter(s->user.getBranchId().equals(s.getBranchId())).mapToDouble(s->s.getDueAmount()).sum();
  double projected=in*(1+change/100)-out+due*.6; double runway=out<=0?90:Math.max(0,(in-out)/(out/30));
  Map<String,Object> m=new LinkedHashMap<>(); m.put("currentCashIn30d",r(in)); m.put("currentCashOut30d",r(out)); m.put("outstandingDue",r(due)); m.put("dailyNetCash",r((in-out)/30)); m.put("projected30dNet",r(projected)); m.put("runwayDays",Math.round(runway)); m.put("salesChangePercent",change); m.put("risk",projected<0?"HIGH":runway<30?"MEDIUM":"LOW"); m.put("generatedAt",now); m.put("message",projected<0?"Cash gap may appear within the next 30 days.":"Cash position is currently sustainable."); return m;
 }
 private double r(double n){return Math.round(n*100)/100.0;}
}
