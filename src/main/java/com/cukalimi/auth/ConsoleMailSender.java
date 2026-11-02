package com.cukalimi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬용. 메일 대신 로그에 찍는다. 테스트는 마지막 본문을 읽어 링크를 꺼낸다. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "console", matchIfMissing = true)
public class ConsoleMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailSender.class);

    private volatile String lastTo;
    private volatile String lastBody;

    @Override
    public void send(String to, String subject, String body) {
        lastTo = to;
        lastBody = body;
        log.info("\n==== 메일 (console 모드) ====\nTo: {}\nSubject: {}\n{}\n============================", to, subject, body);
    }

    public String lastTo() {
        return lastTo;
    }

    public String lastBody() {
        return lastBody;
    }
}
