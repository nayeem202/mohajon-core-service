alter table notification_history
    add column if not exists deleted_for_everyone boolean not null default false;

create table if not exists notification_user_deletions (
    id varchar(36) primary key,
    notification_id varchar(36) not null references notification_history(id) on delete cascade,
    user_id varchar(60) not null,
    deleted_at timestamp not null,
    constraint uq_notification_user_deletion unique (notification_id, user_id)
);
