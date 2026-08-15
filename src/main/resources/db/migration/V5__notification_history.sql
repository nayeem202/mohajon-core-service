create table if not exists notification_history (
    id varchar(36) primary key,
    shop_id varchar(60) not null,
    sender_user_id varchar(60) not null,
    type varchar(30) not null,
    title varchar(255) not null,
    body text not null,
    created_at timestamp not null
);

create index if not exists idx_notification_history_shop_created
    on notification_history(shop_id, created_at desc);
