alter table platform_notifications
    add column if not exists image_url varchar(500);
