package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.AppUserRepository;
import com.cukalimi.feed.FeedService;
import com.cukalimi.feed.Ics;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캘린더 구독 (ADR 004). 주소에 토큰이 붙어 로그인 없이 읽는다. 구글·애플 캘린더가 몇 시간에 한 번씩 받아 간다.
 * 틀린 토큰은 404다. 토큰을 다시 만들면 이전 주소는 바로 끊긴다.
 */
@RestController
@RequestMapping("/api")
public class CalendarController {

    static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8);

    private final AuthService auth;
    private final AppUserRepository users;
    private final FeedService feeds;
    private final Clock clock;

    public CalendarController(AuthService auth, AppUserRepository users, FeedService feeds, Clock clock) {
        this.auth = auth;
        this.users = users;
        this.feeds = feeds;
        this.clock = clock;
    }

    @GetMapping(value = "/calendar/{token}.ics")
    @Transactional(readOnly = true)
    public ResponseEntity<String> calendar(@PathVariable String token) {
        AppUser user = users.findByCalendarToken(token).orElse(null);
        if (user == null || token.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        String body = Ics.of(feeds.build(user, LocalDate.now(clock)).view(), LocalDateTime.now(clock));
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cuk-alimi.ics\"")
                .body(body);
    }

    /** 주소가 샜을 때. 새 토큰을 돌려준다. */
    @PostMapping("/me/calendar/rotate")
    @Transactional
    public AuthController.MeView rotate(HttpServletRequest request) {
        AppUser user = users.findById(auth.requireUser(request).getId()).orElseThrow();
        user.rotateCalendarToken();
        return AuthController.MeView.of(user, auth.isAdmin(user));
    }
}
