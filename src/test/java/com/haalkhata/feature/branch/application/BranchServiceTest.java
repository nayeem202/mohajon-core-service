package com.haalkhata.feature.branch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.branch.domain.Branch;
import com.haalkhata.feature.branch.infrastructure.BranchMemberRepository;
import com.haalkhata.feature.branch.infrastructure.BranchRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branches;
    @Mock
    private BranchMemberRepository members;
    @Mock
    private AppUserRepository users;
    @InjectMocks
    private BranchService service;

    @Test
    void create_succeeds_when_owner_is_only_in_shared_auth_service() {
        CurrentUser owner = new CurrentUser(
                "owner-1", "owner@example.com", "Owner", "shop-1", "OWNER", List.of("OWNER"));
        when(branches.save(any(Branch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.existsById("owner-1")).thenReturn(false);

        Map<String, Object> result = service.upsert(
                owner,
                new BranchService.UpsertRequest(null, "North branch", "Road 7", null, null, null));

        assertThat(result.get("shopId")).isEqualTo("shop-1");
        assertThat(result.get("name")).isEqualTo("North branch");
        verify(branches).save(any(Branch.class));
        verify(members, never()).save(any());
    }
}
