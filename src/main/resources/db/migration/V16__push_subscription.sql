-- 웹 푸시 구독 (ADR 004). 기기(브라우저)마다 한 줄이다. 푸시 서비스가 404나 410을 돌려주면 지운다.
create table push_subscription (
    id          bigserial    primary key,
    user_id     bigint       not null references app_user(id),
    endpoint    text         not null unique,
    p256dh      varchar(120) not null,
    auth        varchar(40)  not null,
    created_at  timestamptz  not null default now()
);
create index push_subscription_user on push_subscription (user_id);
