-- 신고로 내린 자동 공개 날짜. 신고 기록이 탈퇴로 지워져도 다시 자동 공개되지 않게 항목에 남긴다.
alter table date_item add column withdrawn_at timestamptz;
