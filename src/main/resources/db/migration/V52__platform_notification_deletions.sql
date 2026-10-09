create table if not exists platform_notification_user_deletions (
    id varchar(36) primary key,
    notification_id varchar(36) not null references platform_notifications(id) on delete cascade,
    user_id varchar(60) not null,
    deleted_at timestamp not null,
    constraint uq_platform_notification_user_deletion unique (notification_id, user_id)
);

create index if not exists idx_platform_notification_user_deletions_user
    on platform_notification_user_deletions (user_id, notification_id);
