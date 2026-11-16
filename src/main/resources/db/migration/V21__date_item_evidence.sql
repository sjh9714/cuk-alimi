-- 날짜를 읽은 자리 (ADR 016). evidence_text 는 공지에서 그 날짜가 적힌 줄, 표의 행, 또는 제목이고
-- evidence_match 는 그 안의 날짜 글자다. 화면이 근거로 보여 주고 날짜에 형광펜을 긋는다.
-- 손으로 넣은 항목과 학사일정 표 항목에는 없다. 기존 항목은 RUN_ONCE=reparse 로 채운다.
alter table date_item
    add column evidence_text text,
    add column evidence_match text;
