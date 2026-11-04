-- 재확인에서 404가 연속으로 몇 번 났는지. 두 번째에 내리고, 다시 열리면 0으로 돌아가며 내린 것도 되살린다.
alter table notice add column gone_strikes integer not null default 0;
