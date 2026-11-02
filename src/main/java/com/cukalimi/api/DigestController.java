package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 아침 알림 켜고 끄기. 메일의 링크는 로그인 없이도 끈다. */
@RestController
@RequestMapping("/api")
public class DigestController {

    private final AuthService auth;
    private final AppUserRepository users;
    private final AppProperties props;

    public DigestController(AuthService auth, AppUserRepository users, AppProperties props) {
        this.auth = auth;
        this.users = users;
        this.props = props;
    }

    public record DigestRequest(boolean enabled) {
    }

    @PutMapping("/me/digest")
    @Transactional
    public AuthController.MeView setDigest(HttpServletRequest request, @RequestBody DigestRequest body) {
        AppUser user = users.findById(auth.requireUser(request).getId()).orElseThrow();
        user.setDigestEnabled(body.enabled());
        return AuthController.MeView.of(user, auth.isAdmin(user));
    }

    /** 메일 속 링크. 토큰이 맞으면 끄고 설정 화면으로 보낸다. 틀려도 같은 곳으로 보내되 아무것도 바꾸지 않는다. */
    @GetMapping("/digest/unsubscribe")
    @Transactional
    public ResponseEntity<Void> unsubscribe(@RequestParam String token) {
        String result = users.findByUnsubscribeToken(token).map(u -> {
            u.setDigestEnabled(false);
            return "off";
        }).orElse("invalid");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(props.publicUrl() + "/settings/?digest=" + result)).build();
    }
}
