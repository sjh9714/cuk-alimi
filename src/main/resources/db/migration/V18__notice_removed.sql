-- 학교가 지웠거나(다시 열면 404) 같은 날 같은 제목으로 다시 올려 묵은 공지. 홈, 메일, 캘린더, 푸시, 검수 큐에서 뺀다.
alter table notice add column removed_at timestamptz;
alter table notice add column removed_reason varchar(16);
