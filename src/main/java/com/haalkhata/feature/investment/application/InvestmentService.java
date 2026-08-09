package com.haalkhata.feature.investment.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.investment.domain.Investment;
import com.haalkhata.feature.investment.domain.InvestmentCategory;
import com.haalkhata.feature.investment.domain.InvestmentMember;
import com.haalkhata.feature.investment.domain.Investor;
import com.haalkhata.feature.investment.domain.Payout;
import com.haalkhata.feature.investment.infrastructure.InvestmentCategoryRepository;
import com.haalkhata.feature.investment.infrastructure.InvestmentMemberRepository;
import com.haalkhata.feature.investment.infrastructure.InvestmentRepository;
import com.haalkhata.feature.investment.infrastructure.InvestorRepository;
import com.haalkhata.feature.investment.infrastructure.PayoutRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final InvestmentRepository investmentRepository;
    private final InvestorRepository investorRepository;
    private final InvestmentMemberRepository memberRepository;
    private final InvestmentCategoryRepository categoryRepository;
    private final PayoutRepository payoutRepository;

    public record InvestorRequest(String investorId, String name, String phone, String email,
                                  String address, String photoUrl, String notes) {
    }

    public record CategoryRequest(String id, String name, String icon, String color) {
    }

    public record MemberRequest(String investorId, double investmentAmount, Double sharePercentage) {
    }

    public record CreateInvestmentRequest(String investmentId, String title, String description,
                                          String categoryId, String categoryName, Double totalInvestment,
                                          String profitShareMethod, String notes,
                                          List<MemberRequest> members) {
    }

    public record PayoutRequest(String investmentId, String investorId, double amount, String note) {
    }

    public record ProfitRequest(double roi, Double totalProfit) {
    }

    // -------- Investors --------

    public List<Map<String, Object>> listInvestors(CurrentUser user) {
        return investorRepository.findByShopIdOrderByCreatedAtDesc(user.getShopId())
                .stream().map(this::investorJson).toList();
    }

    public Map<String, Object> getInvestor(CurrentUser user, String investorId) {
        return investorRepository.findByInvestorIdAndShopId(investorId, user.getShopId())
                .map(this::investorJson)
                .orElseThrow(() -> BusinessException.notFound("Investor not found"));
    }

    @Transactional
    public Map<String, Object> upsertInvestor(CurrentUser user, InvestorRequest request) {
        Investor investor;
        boolean isNew = request.investorId() == null || request.investorId().isBlank();
        if (isNew) {
            investor = Investor.builder()
                    .investorId(Ids.uuid())
                    .shopId(user.getShopId())
                    .totalInvestment(0)
                    .totalProfit(0)
                    .totalLoss(0)
                    .outstanding(0)
                    .investmentCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            investor = investorRepository.findByInvestorIdAndShopId(request.investorId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Investor not found"));
        }
        investor.setName(request.name() == null ? investor.getName() : request.name().trim());
        investor.setPhone(request.phone());
        investor.setEmail(request.email());
        investor.setAddress(request.address());
        investor.setPhotoUrl(request.photoUrl());
        investor.setNotes(request.notes());
        investor.setUpdatedAt(LocalDateTime.now());
        investorRepository.save(investor);
        log.info("INVESTOR_{} investorId={} shopId={} name={}",
                isNew ? "CREATED" : "UPDATED", investor.getInvestorId(), user.getShopId(), investor.getName());
        return investorJson(investor);
    }

    @Transactional
    public void deleteInvestor(CurrentUser user, String investorId) {
        Investor investor = investorRepository.findByInvestorIdAndShopId(investorId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Investor not found"));
        investorRepository.delete(investor);
        log.info("INVESTOR_DELETED investorId={} shopId={}", investorId, user.getShopId());
    }

    // -------- Categories --------

    public List<Map<String, Object>> listCategories(CurrentUser user) {
        return categoryRepository.findByShopIdOrderByCreatedAtDesc(user.getShopId())
                .stream().map(c -> {
                    Map<String, Object> json = new HashMap<>();
                    json.put("id", c.getId());
                    json.put("name", c.getName());
                    json.put("icon", c.getIcon());
                    json.put("color", c.getColor());
                    json.put("shopId", c.getShopId());
                    json.put("createdAt", c.getCreatedAt().format(ISO));
                    return json;
                }).toList();
    }

    @Transactional
    public Map<String, Object> upsertCategory(CurrentUser user, CategoryRequest request) {
        InvestmentCategory category;
        boolean isNew = request.id() == null || request.id().isBlank();
        if (isNew) {
            category = InvestmentCategory.builder()
                    .id(Ids.uuid())
                    .shopId(user.getShopId())
                    .createdAt(LocalDateTime.now())
                    .build();
        } else {
            category = categoryRepository.findById(request.id())
                    .filter(c -> c.getShopId().equals(user.getShopId()))
                    .orElseThrow(() -> BusinessException.notFound("Category not found"));
        }
        category.setName(request.name() == null ? category.getName() : request.name().trim());
        category.setIcon(request.icon());
        category.setColor(request.color());
        categoryRepository.save(category);
        log.info("INVESTMENT_CATEGORY_{} id={} name={}",
                isNew ? "CREATED" : "UPDATED", category.getId(), category.getName());
        Map<String, Object> json = new HashMap<>();
        json.put("id", category.getId());
        json.put("name", category.getName());
        json.put("icon", category.getIcon());
        json.put("color", category.getColor());
        json.put("shopId", category.getShopId());
        json.put("createdAt", category.getCreatedAt().format(ISO));
        return json;
    }

    @Transactional
    public void deleteCategory(CurrentUser user, String id) {
        InvestmentCategory category = categoryRepository.findById(id)
                .filter(c -> c.getShopId().equals(user.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Category not found"));
        categoryRepository.delete(category);
        log.info("INVESTMENT_CATEGORY_DELETED id={} shopId={}", id, user.getShopId());
    }

    // -------- Investments --------

    public List<Map<String, Object>> listInvestments(CurrentUser user) {
        return investmentRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().map(this::investmentJson).toList();
    }

    public Map<String, Object> getInvestment(CurrentUser user, String investmentId) {
        return investmentRepository.findByInvestmentIdAndShopIdAndIsDeletedFalse(investmentId, user.getShopId())
                .map(this::investmentJson)
                .orElseThrow(() -> BusinessException.notFound("Investment not found"));
    }

    @Transactional
    public Map<String, Object> createInvestment(CurrentUser user, CreateInvestmentRequest request) {
        if (request.members() == null || request.members().isEmpty()) {
            throw BusinessException.badRequest("Investment must have at least one member");
        }
        double totalInvestment = request.totalInvestment() == null ? 0 : request.totalInvestment();
        LocalDateTime now = LocalDateTime.now();
        Investment investment = Investment.builder()
                .investmentId(request.investmentId() == null || request.investmentId().isBlank()
                        ? Ids.uuid() : request.investmentId())
                .shopId(user.getShopId())
                .title(request.title() == null ? "Investment" : request.title().trim())
                .description(request.description())
                .categoryId(request.categoryId())
                .categoryName(request.categoryName())
                .totalInvestment(totalInvestment)
                .profitShareMethod(request.profitShareMethod() == null || request.profitShareMethod().isBlank()
                        ? "proportional" : request.profitShareMethod())
                .notes(request.notes())
                .createdBy(user.getUserId())
                .status("ACTIVE")
                .roi(0)
                .totalProfit(0)
                .totalPaid(0)
                .outstanding(totalInvestment)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        investmentRepository.save(investment);

        List<InvestmentMember> members = new ArrayList<>();
        for (MemberRequest member : request.members()) {
            Investor investor = investorRepository.findByInvestorIdAndShopId(member.investorId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Investor not found: " + member.investorId()));
            double share = "fixed".equals(investment.getProfitShareMethod())
                    && member.sharePercentage() != null
                    ? member.sharePercentage()
                    : totalInvestment > 0
                            ? (member.investmentAmount() / totalInvestment) * 100 : 0;
            InvestmentMember im = InvestmentMember.builder()
                    .id(Ids.uuid())
                    .investmentId(investment.getInvestmentId())
                    .investorId(investor.getInvestorId())
                    .shopId(user.getShopId())
                    .investmentAmount(member.investmentAmount())
                    .sharePercentage(share)
                    .profit(0)
                    .loss(0)
                    .paidAmount(0)
                    .remainingAmount(member.investmentAmount())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            memberRepository.save(im);
            members.add(im);

            investor.setTotalInvestment(investor.getTotalInvestment() + member.investmentAmount());
            investor.setOutstanding(investor.getOutstanding() + member.investmentAmount());
            investor.setInvestmentCount(investor.getInvestmentCount() + 1);
            investor.setUpdatedAt(now);
            investorRepository.save(investor);
        }

        log.info("INVESTMENT_CREATED investmentId={} shopId={} total={} members={}",
                investment.getInvestmentId(), user.getShopId(), totalInvestment, members.size());
        return investmentJson(investment);
    }

    @Transactional
    public Map<String, Object> recordPayout(CurrentUser user, PayoutRequest request) {
        Investment investment = investmentRepository
                .findByInvestmentIdAndShopIdAndIsDeletedFalse(request.investmentId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Investment not found"));
        InvestmentMember member = memberRepository
                .findFirstByInvestmentIdAndInvestorId(request.investmentId(), request.investorId())
                .orElseThrow(() -> BusinessException.notFound("Member not found in this investment"));
        Investor investor = investorRepository.findByInvestorIdAndShopId(request.investorId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Investor not found"));

        LocalDateTime now = LocalDateTime.now();
        Payout payout = Payout.builder()
                .payoutId(Ids.uuid())
                .investmentId(investment.getInvestmentId())
                .investorId(investor.getInvestorId())
                .shopId(user.getShopId())
                .amount(request.amount())
                .note(request.note())
                .createdBy(user.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        payoutRepository.save(payout);

        member.setPaidAmount(member.getPaidAmount() + request.amount());
        member.setRemainingAmount(Math.max(0, member.getRemainingAmount() - request.amount()));
        member.setUpdatedAt(now);
        memberRepository.save(member);

        investment.setTotalPaid(investment.getTotalPaid() + request.amount());
        investment.setOutstanding(Math.max(0, investment.getOutstanding() - request.amount()));
        if (investment.getOutstanding() <= 0 && investment.getTotalProfit() > 0) {
            investment.setStatus("COMPLETED");
        }
        investment.setUpdatedAt(now);
        investmentRepository.save(investment);

        investor.setOutstanding(Math.max(0, investor.getOutstanding() - request.amount()));
        investor.setUpdatedAt(now);
        investorRepository.save(investor);

        log.info("PAYOUT_RECORDED investmentId={} investorId={} amount={}",
                request.investmentId(), request.investorId(), request.amount());
        return payoutJson(payout);
    }

    @Transactional
    public Map<String, Object> updateInvestmentProfit(CurrentUser user, String investmentId, ProfitRequest request) {
        Investment investment = investmentRepository
                .findByInvestmentIdAndShopIdAndIsDeletedFalse(investmentId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Investment not found"));
        investment.setRoi(request.roi());
        investment.setTotalProfit(request.totalProfit() == null
                ? investment.getTotalInvestment() * request.roi() / 100 : request.totalProfit());
        investment.setUpdatedAt(LocalDateTime.now());
        investmentRepository.save(investment);

        List<InvestmentMember> members = memberRepository.findByInvestmentIdOrderByCreatedAtAsc(investmentId);
        for (InvestmentMember member : members) {
            double profitShare = "fixed".equals(investment.getProfitShareMethod())
                    ? investment.getTotalProfit() / members.size()
                    : investment.getTotalProfit() * member.getSharePercentage() / 100;
            member.setProfit(profitShare);
            member.setRemainingAmount(member.getInvestmentAmount() - member.getPaidAmount() + profitShare);
            member.setUpdatedAt(LocalDateTime.now());
            memberRepository.save(member);

            investorRepository.findByInvestorIdAndShopId(member.getInvestorId(), user.getShopId())
                    .ifPresent(investor -> {
                        investor.setTotalProfit(investor.getTotalProfit() + profitShare);
                        investor.setUpdatedAt(LocalDateTime.now());
                        investorRepository.save(investor);
                    });
        }
        log.info("INVESTMENT_PROFIT_UPDATED investmentId={} roi={} totalProfit={}",
                investmentId, request.roi(), investment.getTotalProfit());
        return investmentJson(investment);
    }

    @Transactional
    public void deleteInvestment(CurrentUser user, String investmentId) {
        Investment investment = investmentRepository
                .findByInvestmentIdAndShopIdAndIsDeletedFalse(investmentId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Investment not found"));
        investment.setDeleted(true);
        investment.setUpdatedAt(LocalDateTime.now());
        investmentRepository.save(investment);
        log.info("INVESTMENT_DELETED investmentId={} shopId={}", investmentId, user.getShopId());
    }

    // -------- JSON --------

    private Map<String, Object> investorJson(Investor i) {
        Map<String, Object> json = new HashMap<>();
        json.put("investorId", i.getInvestorId());
        json.put("name", i.getName());
        json.put("phone", i.getPhone());
        json.put("email", i.getEmail());
        json.put("address", i.getAddress());
        json.put("photoUrl", i.getPhotoUrl());
        json.put("notes", i.getNotes());
        json.put("totalInvestment", i.getTotalInvestment());
        json.put("totalProfit", i.getTotalProfit());
        json.put("totalLoss", i.getTotalLoss());
        json.put("outstanding", i.getOutstanding());
        json.put("investmentCount", i.getInvestmentCount());
        json.put("createdAt", i.getCreatedAt().format(ISO));
        return json;
    }

    private Map<String, Object> investmentJson(Investment inv) {
        List<Map<String, Object>> members = memberRepository.findByInvestmentIdOrderByCreatedAtAsc(
                        inv.getInvestmentId())
                .stream().map(m -> {
                    Map<String, Object> json = new HashMap<>();
                    json.put("investmentId", m.getInvestmentId());
                    json.put("investorId", m.getInvestorId());
                    json.put("investmentAmount", m.getInvestmentAmount());
                    json.put("sharePercentage", m.getSharePercentage());
                    json.put("profit", m.getProfit());
                    json.put("loss", m.getLoss());
                    json.put("paidAmount", m.getPaidAmount());
                    json.put("remainingAmount", m.getRemainingAmount());
                    return json;
                }).toList();
        Map<String, Object> json = new HashMap<>();
        json.put("investmentId", inv.getInvestmentId());
        json.put("title", inv.getTitle());
        json.put("description", inv.getDescription());
        json.put("categoryId", inv.getCategoryId());
        json.put("categoryName", inv.getCategoryName());
        json.put("totalInvestment", inv.getTotalInvestment());
        json.put("profitShareMethod", inv.getProfitShareMethod());
        json.put("notes", inv.getNotes());
        json.put("createdBy", inv.getCreatedBy());
        json.put("status", inv.getStatus());
        json.put("roi", inv.getRoi());
        json.put("totalProfit", inv.getTotalProfit());
        json.put("totalPaid", inv.getTotalPaid());
        json.put("outstanding", inv.getOutstanding());
        json.put("members", members);
        json.put("createdAt", inv.getCreatedAt().format(ISO));
        json.put("updatedAt", inv.getUpdatedAt().format(ISO));
        return json;
    }

    private Map<String, Object> payoutJson(Payout p) {
        Map<String, Object> json = new HashMap<>();
        json.put("payoutId", p.getPayoutId());
        json.put("investmentId", p.getInvestmentId());
        json.put("investorId", p.getInvestorId());
        json.put("amount", p.getAmount());
        json.put("note", p.getNote());
        json.put("createdBy", p.getCreatedBy());
        json.put("createdAt", p.getCreatedAt().format(ISO));
        return json;
    }

    public List<Map<String, Object>> payoutsForInvestment(CurrentUser user, String investmentId) {
        return payoutRepository.findByInvestmentIdOrderByCreatedAtDesc(investmentId)
                .stream().map(this::payoutJson).toList();
    }

    public List<Map<String, Object>> payoutsForInvestor(CurrentUser user, String investorId) {
        return payoutRepository.findByInvestorIdOrderByCreatedAtDesc(investorId)
                .stream().map(this::payoutJson).toList();
    }
}
