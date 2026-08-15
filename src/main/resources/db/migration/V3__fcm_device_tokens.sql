create table if not exists fcm_device_tokens (
    token_id varchar(36) primary key,
    user_id varchar(60) not null,
    shop_id varchar(60) not null,
    token text not null unique,
    platform varchar(20) not null,
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_fcm_device_tokens_shop_active
    on fcm_device_tokens(shop_id, active);
