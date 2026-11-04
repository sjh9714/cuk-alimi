package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.AppUser;
import com.cukalimi.feed.Regions;
import com.cukalimi.domain.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final AppProperties props;
    private final AppUserRepository users;

    public AuthController(AuthService auth, AppProperties props, AppUserRepository users) {
        this.auth = auth;
        this.props = props;
        this.users = users;
    }

    public record LoginRequest(String email) {
    }

    /** semester는 이번 학기(9는 초과학기), grade는 거기서 나온 학년이다. */
    public record MeView(String email, Integer entryYear, String major, String secondMajor, String homeRegion,
            Integer grade, Integer semester, Integer autoSemester, boolean semesterManual, boolean admin, boolean digestEnabled,
            String calendarToken, java.util.List<String> interests) {
        static MeView of(AppUser u, boolean admin) {
            LocalDate today = LocalDate.now();
            return new MeView(u.getEmail(), u.getEntryYear(), u.getMajor(), u.getSecondMajor(), u.getHomeRegion(),
                    u.grade(today), u.semester(today), u.autoSemester(today), u.isSemesterManual(), admin, u.isDigestEnabled(),
                    u.getCalendarToken(), u.interests().stream().map(com.cukalimi.feed.Topic::key).toList());
        }
    }

    /**
     * semester는 학생이 고른 이번 학기(1~8, 9는 초과학기)이고 null이면 입학년도로 센다.
     * homeRegion은 "전라남도 순천시"나 "전라남도". 목록(regions.yml)에 없는 값은 받지 않는다.
     */
    /** interests는 관심 주제 키 목록이다. null이면 바꾸지 않는다 (ADR 014). */
    public record ProfileRequest(Integer entryYear, Integer semester, String major, String secondMajor, String homeRegion,
            java.util.List<String> interests) {
    }

    @PostMapping("/auth/request")
    public Map<String, String> request(HttpServletRequest request, @RequestBody LoginRequest body) {
        // Caddy가 X-Forwarded-For를 실제 접속 주소로 덮어쓰고, ForwardedHeaderFilter가 그 값을 getRemoteAddr()로 준다.
        auth.requestLogin(body.email(), request.getRemoteAddr());
        return Map.of("message", "메일을 보냈습니다. 15분 안에 링크를 눌러 주세요.");
    }

    /** 메일의 링크. 쿠키를 심고 화면으로 보낸다. */
    @GetMapping("/auth/consume")
    public ResponseEntity<Void> consume(@RequestParam String token, HttpServletResponse response) {
        String session = auth.consume(token);
        response.addCookie(auth.sessionCookie(session, false));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(props.publicUrl() + "/?login=ok")).build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request);
        response.addCookie(auth.sessionCookie("", true));
        return ResponseEntity.noContent().build();
    }

    /** 탈퇴. 저장·체크·세션·알림 기록과 계정을 지운다. 되돌릴 수 없다. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(HttpServletRequest request, HttpServletResponse response) {
        AppUser user = auth.requireUser(request);
        auth.deleteAccount(user);
        response.addCookie(auth.sessionCookie("", true));
        return ResponseEntity.noContent().build();
    }

    /** 개인정보 처리방침의 문의처. 보내는 메일 주소를 그대로 쓴다. */
    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of("contactEmail", props.mail().from() == null ? "" : props.mail().from());
    }

    @GetMapping("/me")
    public MeView me(HttpServletRequest request) {
        AppUser user = auth.requireUser(request);
        return MeView.of(user, auth.isAdmin(user));
    }

    @PutMapping("/me/profile")
    @Transactional
    public MeView profile(HttpServletRequest request, @RequestBody ProfileRequest body) {
        AppUser user = auth.requireUser(request);
        if (body.entryYear() != null && (body.entryYear() < 1990 || body.entryYear() > LocalDate.now().getYear())) {
            throw new IllegalArgumentException("입학년도가 이상합니다");
        }
        if (body.semester() != null && (body.semester() < 1 || body.semester() > 9)) {
            throw new IllegalArgumentException("학기는 1학기부터 초과학기까지 골라 주세요");
        }
        if (body.semester() != null && body.entryYear() == null) {
            throw new IllegalArgumentException("학기를 고르려면 입학년도를 넣어 주세요");
        }
        String home = body.homeRegion() == null || body.homeRegion().isBlank() ? null
                : Regions.parse(body.homeRegion()).map(Regions.Region::label)
                        .orElseThrow(() -> new IllegalArgumentException("연고 지역은 목록에서 골라 주세요"));
        AppUser managed = users.findById(user.getId()).orElseThrow();
        managed.updateProfile(body.entryYear(), body.semester(), body.major(), body.secondMajor(), home, LocalDate.now());
        if (body.interests() != null) {
            managed.updateInterests(com.cukalimi.feed.Topic.parse(String.join(",", body.interests())));
        }
        return MeView.of(managed, auth.isAdmin(managed));
    }
}
