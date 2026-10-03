package com.haalkhata.feature.branch.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "branch_members") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BranchMember {
    @Id @Column(name = "membership_id", length = 36) private String membershipId;
    @Column(name = "branch_id", nullable = false, length = 80) private String branchId;
    @Column(name = "user_id", nullable = false, length = 36) private String userId;
}
