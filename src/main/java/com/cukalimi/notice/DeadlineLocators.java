package com.cukalimi.notice;

/** 문장 찾기 모델 둘 (ADR 016). small이 공지마다 두 번 읽고, 둘이 엇갈릴 때만 large가 한 번 더 읽는다. */
public record DeadlineLocators(DeadlineLocator small, DeadlineLocator large) {
}
