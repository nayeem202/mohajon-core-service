create table if not exists support_tickets (
    ticket_id varchar(36) primary key,
    shop_id varchar(60) not null,
    opened_by_user_id varchar(60) not null,
    shop_name varchar(150) not null,
    status varchar(24) not null,
    last_message_preview varchar(300) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index if not exists idx_support_tickets_shop_updated
    on support_tickets(shop_id, updated_at desc);
create index if not exists idx_support_tickets_updated
    on support_tickets(updated_at desc);

create table if not exists support_messages (
    message_id varchar(36) primary key,
    ticket_id varchar(36) not null references support_tickets(ticket_id) on delete cascade,
    sender_user_id varchar(60) not null,
    sender_type varchar(16) not null,
    body text,
    image_url varchar(500),
    created_at timestamp not null,
    constraint support_messages_content_check check (
        nullif(trim(coalesce(body, '')), '') is not null or image_url is not null
    )
);
create index if not exists idx_support_messages_ticket_created
    on support_messages(ticket_id, created_at desc);
