package com.haalkhata.core.security;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUser {

    private String userId;
    private String email;
    private String name;
    private String shopId;
    private String role;
    private List<String> roles;
    private String branchId;

    /** Backward-compatible constructor for jobs/tests that do not carry branch context. */
    public CurrentUser(String userId, String email, String name, String shopId,
                       String role, List<String> roles) {
        this(userId, email, name, shopId, role, roles, shopId + "-main");
    }
}
