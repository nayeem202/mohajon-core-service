package com.haalkhata.feature.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.notification.application.NotificationService;
import com.haalkhata.feature.shop.domain.Shop;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.support.domain.SupportMessage;
import com.haalkhata.feature.support.domain.SupportTicket;
import com.haalkhata.feature.support.infrastructure.SupportMessageRepository;
import com.haalkhata.feature.support.infrastructure.SupportTicketRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportInboxServiceTest {
    @Mock private SupportTicketRepository tickets;
    @Mock private SupportMessageRepository messages;
    @Mock private ShopRepository shops;
    @Mock private NotificationService notifications;
    @Mock private AppProperties properties;
    @InjectMocks private SupportInboxService service;

    @Test
    void createTicket_keepsMessageAndImageAsOneSupportConversation() {
        var user = new CurrentUser("owner-1", "owner@example.com", "Owner", "shop-1", "OWNER", List.of("OWNER"));
        when(shops.findById("shop-1")).thenReturn(Optional.of(Shop.builder().shopId("shop-1").name("Demo Shop").build()));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(call -> call.getArgument(0));
        when(messages.save(any(SupportMessage.class))).thenAnswer(call -> call.getArgument(0));

        Map<String, Object> result = service.createTicket(user,
                new SupportInboxService.MessageRequest("Photo upload is failing", "/api/v1/files/error.png"));

        assertThat(result.get("shopName")).isEqualTo("Demo Shop");
        assertThat(result.get("status")).isEqualTo("OPEN");
        var message = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messages).save(message.capture());
        assertThat(message.getValue().getImageUrl()).isEqualTo("/api/v1/files/error.png");
        assertThat(message.getValue().getBody()).isEqualTo("Photo upload is failing");
    }
}
