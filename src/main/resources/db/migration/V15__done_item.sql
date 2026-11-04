-- "끝냈어요" 체크를 서버에 둔다. 지금까지는 브라우저 localStorage라 기기가 바뀌면 사라졌다.
-- 저장(saved_item)과 같은 꼴이다. 끝낸 마감은 아침 메일과 캘린더 구독에서 뺀다.
create table done_item (
    user_id       bigint      not null references app_user(id),
    date_item_id  bigint      not null references date_item(id) on delete cascade,
    created_at    timestamptz not null default now(),
    primary key (user_id, date_item_id)
);
