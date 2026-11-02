package com.campusalimi.auth;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.AppUserRepository;
import com.campusalimi.domain.LoginToken;
import com.campusalimi.domain.LoginTokenRepository;
import com.campusalimi.domain.SavedItemRepository;
import com.campusalimi.domain.FeedbackRepository;
import com.campusalimi.domain.SentNotificationRepository;
import com.campusalimi.domain.UserBenefitRepository;
import com.campusalimi.domain.UserSession;
import com.campusalimi.domain.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이메일 매직링크 로그인 (ADR 005). 비밀번호가 없다.
 * 링크는 15분, 한 번만. 세션은 30일. 둘 다 해시만 저장한다.
 */
@Service
public class AuthService {

    public static final String COOKIE = "ca_session";
    static final Duration LINK_TTL = Duration.ofMinutes(15);
    static final Duration SESSION_TTL = Duration.ofDays(30);
    /** 같은 주소로 한 시간에 보내는 링크 수. 남의 주소로 메일 폭탄을 보내지 못하게. */
    static final int HOURLY_PER_ADDRESS = 3;
    /** 하루 전체가 이 수를 넘으면 아직 가입하지 않은 주소로는 보내지 않는다. 가입한 사람의 몫을 남기려고. */
    static final int DAILY_NEW_ADDRESSES = 100;
    /** 누구에게든 보내는 하루 상한. Gmail 하루 한도(500통)에 닿아 계정이 정지되지 않게, 아침 메일 몫을 남긴다. */
    static final int DAILY_CEILING = 300;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppProperties props;
    private final MailSender mail;
    private final AppUserRepository users;
    private final LoginTokenRepository tokens;
    private final LoginIpLimiter ipLimiter;
    private final TransactionTemplate transaction;
    private final UserSessionRepository sessions;
    private final SavedItemRepository savedItems;
    private final UserBenefitRepository userBenefits;
    private final SentNotificationRepository sentNotifications;
    private final FeedbackRepository feedback;

    public AuthService(AppProperties props, MailSender mail, AppUserRepository users, LoginTokenRepository tokens, LoginIpLimiter ipLimiter,
            TransactionTemplate transaction,
            UserSessionRepository sessions, SavedItemRepository savedItems, UserBenefitRepository userBenefits,
            SentNotificationRepository sentNotifications, FeedbackRepository feedback) {
        this.savedItems = savedItems;
        this.userBenefits = userBenefits;
        this.sentNotifications = sentNotifications;
        this.feedback = feedback;
        this.props = props;
        this.mail = mail;
        this.users = users;
        this.tokens = tokens;
        this.ipLimiter = ipLimiter;
        this.transaction = transaction;
        this.sessions = sessions;
    }

    /**
     * 링크를 보낸다. 가입 여부와 상관없이 같은 응답을 준다. 주소가 있는지 밖에서 알 수 없게.
     * 세고 저장하는 부분만 잠그고, 메일은 트랜잭션이 끝난 뒤 보낸다. 느린 SMTP가 다른 요청을 붙잡지 않게.
     */
    public void requestLogin(String rawEmail, String clientIp) {
        String email = normalize(rawEmail);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 아닙니다");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!ipLimiter.allow(clientIp, now.toInstant())) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 한 시간 뒤에 다시 시도해 주세요");
        }
        String token = transaction.execute(status -> reserveLink(email, now));
        if (token == null) {
            return;
        }
        String link = props.apiUrlOrPublic() + "/api/auth/consume?token=" + token;
        mail.send(email, "[CUK-Alimi] 로그인 링크",
                "아래 링크를 15분 안에 누르면 로그인됩니다. 한 번만 쓸 수 있습니다.\n\n" + link
                        + "\n\n본인이 요청하지 않았다면 이 메일은 무시해도 됩니다.");
    }

    /** 한도 안이면 토큰을 저장해 돌려준다. 새 주소 한도를 넘으면 null이고, 응답은 보낸 것과 같다. */
    private String reserveLink(String email, OffsetDateTime now) {
        tokens.lockLoginRequests();
        if (tokens.countByEmailAndCreatedAtAfter(email, now.minusHours(1)) >= HOURLY_PER_ADDRESS) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "링크를 너무 많이 요청했어요. 받은 메일을 확인하거나 한 시간 뒤에 다시 시도해 주세요");
        }
        long today = tokens.countByCreatedAtAfter(now.minusDays(1));
        if (today >= DAILY_CEILING) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "오늘 보낼 수 있는 로그인 메일을 다 썼어요. 내일 다시 시도해 주세요");
        }
        if (today >= DAILY_NEW_ADDRESSES && users.findByEmail(email).isEmpty()) {
            return null;
        }
        String token = Tokens.random();
        tokens.save(new LoginToken(email, Tokens.hash(token), now.plus(LINK_TTL)));
        return token;
    }

    /** 링크를 쓰고 세션 토큰(쿠키 값)을 돌려준다. */
    @Transactional
    public String consume(String token) {
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized();
        }
        OffsetDateTime now = OffsetDateTime.now();
        LoginToken login = tokens.findByTokenHash(Tokens.hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,
                        "링크가 만료됐거나 이미 사용됐습니다"));
        login.markUsed();
        AppUser user = users.findByEmail(login.getEmail()).orElseGet(() -> users.save(new AppUser(login.getEmail())));
        user.touchLogin();
        String session = Tokens.random();
        sessions.save(new UserSession(user.getId(), Tokens.hash(session), now.plus(SESSION_TTL)));
        return session;
    }

    /** 탈퇴. 사용자를 가리키는 것부터 지우고 마지막에 사용자를 지운다. */
    @Transactional
    public void deleteAccount(AppUser user) {
        sessions.deleteByUserId(user.getId());
        savedItems.deleteByUserId(user.getId());
        userBenefits.deleteByUserId(user.getId());
        sentNotifications.deleteByUserId(user.getId());
        feedback.deleteByUserId(user.getId());
        tokens.deleteByEmail(user.getEmail());
        users.deleteById(user.getId());
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        sessionToken(request).ifPresent(t -> sessions.deleteByTokenHash(Tokens.hash(t)));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> currentUser(HttpServletRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        return sessionToken(request)
                .flatMap(t -> sessions.findByTokenHash(Tokens.hash(t)))
                .filter(s -> s.isAlive(now))
                .flatMap(s -> users.findById(s.getUserId()));
    }

    public AppUser requireUser(HttpServletRequest request) {
        return currentUser(request).orElseThrow(AuthException::unauthorized);
    }

    public AppUser requireAdmin(HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (!props.isAdmin(user.getEmail())) {
            throw AuthException.forbidden();
        }
        return user;
    }

    public boolean isAdmin(AppUser user) {
        return props.isAdmin(user.getEmail());
    }

    public Cookie sessionCookie(String token, boolean clear) {
        Cookie cookie = new Cookie(COOKIE, clear ? "" : token);
        cookie.setHttpOnly(true);
        cookie.setSecure(props.cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(clear ? 0 : (int) SESSION_TTL.toSeconds());
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private static Optional<String> sessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
