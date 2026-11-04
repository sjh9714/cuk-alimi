-- 관심 주제 (ADR 014). 쉼표로 이은 주제 키. null이면 기본값(수강·학적, 계절학기, 장학금, 근로·인턴장학, 졸업)이다.
alter table app_user add column interests text;
