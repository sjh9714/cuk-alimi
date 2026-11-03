-- AI가 공지 글에서 읽은 대상 조건 (ADR 013). 두 번 읽어 같을 때만 audience_ai를 채우고, 어긋나면 비운 채 읽은 시각만 남긴다.
-- 본문이 바뀌면 다시 읽도록 audience_read_at을 비운다. 토큰은 두 번 읽은 합이다.
alter table notice add column audience_ai text;
alter table notice add column audience_model varchar(64);
alter table notice add column audience_read_at timestamptz;
alter table notice add column audience_input_tokens bigint not null default 0;
alter table notice add column audience_output_tokens bigint not null default 0;
