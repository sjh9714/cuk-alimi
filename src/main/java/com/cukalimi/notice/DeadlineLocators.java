package com.cukalimi.notice;

/** 문장 찾기 모델 둘 (ADR 016). small이 공지마다 두 번 읽고, 그 답이 화면을 바꾸려 할 때만 large가 한 번 읽어 확인한다. */
public record DeadlineLocators(DeadlineLocator small, DeadlineLocator large) {
}
