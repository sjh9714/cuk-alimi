package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.PushSubscription;
import com.cukalimi.domain.PushSubscriptionRepository;
import com.cukalimi.push.PushService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 푸시 구독 등록과 해제 (ADR 004). 공개 키는 누구나 읽고, 구독은 로그인한 사람만 남긴다. 주소는 알려진 푸시 서비스만 받는다. */
@RestController
@RequestMapping("/api")
public class PushController {

    /** 한 계정에 기기 열 대면 넉넉하다. 더 받으면 보내는 쪽 일이 사용자 수가 아니라 요청 수에 비례해 커진다. */
    static final int MAX_DEVICES = 10;

    private final AuthService auth;
    private final PushSubscriptionRepository subscriptions;
    private final PushService.Sender sender;

    public PushController(AuthService auth, PushSubscriptionRepository subscriptions, PushService.Sender sender) {
        this.auth = auth;
        this.subscriptions = subscriptions;
        this.sender = sender;
    }

    /** 브라우저의 PushSubscription.toJSON()과 같은 꼴. */
    public record SubscriptionRequest(@NotBlank @Size(max = 2000) String endpoint, @Valid Keys keys) {
        public record Keys(@NotBlank @Size(max = 120) String p256dh, @NotBlank @Size(max = 40) String auth) {
        }
    }

    public record PushState(boolean enabled, String publicKey, long devices) {
    }

    /** 켜져 있는지와 공개 키. 로그인했으면 이 계정에 등록된 기기 수도 준다. */
    @GetMapping("/push")
    public PushState state(HttpServletRequest request) {
        long devices = auth.currentUser(request).map(u -> subscriptions.countByUserId(u.getId())).orElse(0L);
        return new PushState(sender.enabled(), sender.publicKey(), devices);
    }

    @PostMapping("/me/push")
    @Transactional
    public ResponseEntity<Void> subscribe(HttpServletRequest request, @Valid @RequestBody SubscriptionRequest body) {
        AppUser user = auth.requireUser(request);
        if (body.keys() == null || !com.cukalimi.push.WebPush.allowedEndpoint(body.endpoint())) {
            return ResponseEntity.badRequest().build();
        }
        // 새 기기이거나 다른 계정에서 넘어오는 기기면 이 계정의 기기 수를 센다. 같은 사람이 동시에 두 번 눌러 11대가 되는 것은 받아들인다.
        var existing = subscriptions.findByEndpoint(body.endpoint());
        boolean countsAsNew = existing.isEmpty() || !existing.get().getUserId().equals(user.getId());
        if (countsAsNew && subscriptions.countByUserId(user.getId()) >= MAX_DEVICES) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        }
        existing.ifPresentOrElse(
                s -> s.reassign(user.getId(), body.keys().p256dh(), body.keys().auth()),
                () -> subscriptions.save(new PushSubscription(user.getId(), body.endpoint(), body.keys().p256dh(), body.keys().auth())));
        return ResponseEntity.noContent().build();
    }

    /** 이 기기의 구독만 지운다. 다른 기기는 그대로다. */
    @DeleteMapping("/me/push")
    @Transactional
    public ResponseEntity<Void> unsubscribe(HttpServletRequest request, @RequestBody Map<String, String> body) {
        AppUser user = auth.requireUser(request);
        subscriptions.findByEndpoint(body.getOrDefault("endpoint", ""))
                .filter(s -> s.getUserId().equals(user.getId()))
                .ifPresent(subscriptions::delete);
        return ResponseEntity.noContent().build();
    }
}
