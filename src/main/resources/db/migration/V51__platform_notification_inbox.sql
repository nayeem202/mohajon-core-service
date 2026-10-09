create table if not exists platform_notifications (
    id varchar(36) primary key,
    sender_user_id varchar(60) not null,
    audience varchar(12) not null,
    recipient_user_id varchar(60),
    title varchar(255) not null,
    body text not null,
    title_bn varchar(255),
    body_bn text,
    created_at timestamp not null,
    constraint chk_platform_notification_audience check (audience in ('ALL', 'USER'))
);

create index if not exists idx_platform_notifications_inbox
    on platform_notifications (audience, recipient_user_id, created_at desc);
