-- AI 문장 찾기 (ADR 016). 읽기마다 한 줄로 모델의 원본 답과 토큰을 남긴다. 예산 가드와 하루 상한이 이 표를 센다.
create table locator_reading (
    id bigserial primary key,
    notice_id bigint not null references notice (id) on delete cascade,
    model text not null,
    prompt_hash text not null,
    status text not null,
    answer text,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    created_at timestamptz not null default now()
);
create index locator_reading_notice_idx on locator_reading (notice_id);
create index locator_reading_created_idx on locator_reading (created_at);

alter table notice add column locator_read_at timestamptz;
-- AI가 "학생의 할 일이 아니다"라고 본 규칙 날짜에 'doubt'를 적는다. 숨기지 않고 화면에 "확인"을 단다.
alter table date_item add column ai_note text;
