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
}
