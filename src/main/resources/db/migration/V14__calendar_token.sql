-- 캘린더 구독 주소에 붙는 토큰. 주소를 아는 사람은 누구나 그 학생의 저장한 마감과 내게 맞는 마감을 본다.
-- 설정에서 다시 만들면 이전 주소는 바로 끊긴다.
alter table app_user add column calendar_token varchar(32) not null default '';
update app_user set calendar_token = replace(gen_random_uuid()::text, '-', '');
create unique index app_user_calendar_token on app_user (calendar_token);
