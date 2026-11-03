-- 학년 대신 이번 학기를 받는다. 공지 조건이 "4학기 이상 이수", "8학기 이상 불가"처럼 학기로 적히기 때문이다.
-- 학년 차이는 학기 차이의 두 배다.
alter table app_user add column semester_adjust integer not null default 0;
update app_user set semester_adjust = grade_adjust * 2;
alter table app_user drop column grade_adjust;
