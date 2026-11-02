-- 학생이 "날짜가 달라요"로 남긴 신고 (ADR 010). 자동 공개한 그림 날짜는 신고가 오면 내리고 운영자에게 넘긴다.
create table date_report (
    id            bigserial primary key,
    date_item_id  bigint      not null references date_item(id) on delete cascade,
    user_id       bigint      not null references app_user(id),
    created_at    timestamptz not null default now(),
    unique (date_item_id, user_id)
);
create index date_report_user_idx on date_report(user_id);
