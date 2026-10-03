package com.haalkhata.feature.branch.infrastructure;
import com.haalkhata.feature.branch.domain.BranchMember;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchMemberRepository extends JpaRepository<BranchMember, String> {
    Optional<BranchMember> findByBranchIdAndUserId(String branchId, String userId);
}
