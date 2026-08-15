alter table fcm_device_tokens
    add column if not exists notification_types text not null
    default 'sale,stock,expense,payment,investment,platform';
