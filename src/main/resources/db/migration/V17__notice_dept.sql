-- 학과 게시판에서 온 공지는 어느 학과인지 적는다 (ADR 002 추가). 중앙 게시판 공지는 비어 있다.
alter table notice add column dept varchar(40);
create index notice_dept on notice (dept) where dept is not null;
