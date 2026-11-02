package com.cukalimi.auth;

import com.cukalimi.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 운영용. Gmail SMTP(앱 비밀번호). 설정은 application.yml의 spring.mail. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "smtp")
public class SmtpMailSender implements MailSender {

    private final JavaMailSender mail;
    private final AppProperties props;

    public SmtpMailSender(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
