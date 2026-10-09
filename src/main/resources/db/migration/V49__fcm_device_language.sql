alter table fcm_device_tokens
    add column if not exists language_code varchar(8) not null default 'en';
