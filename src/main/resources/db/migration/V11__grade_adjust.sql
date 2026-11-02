-- 입학년도로 센 학년과 실제 학년의 차이. 휴학이나 군 복무로 늦어지면 음수다.
alter table app_user add column grade_adjust integer not null default 0;
