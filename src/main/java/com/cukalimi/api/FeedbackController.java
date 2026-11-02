package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.Feedback;
import com.cukalimi.domain.FeedbackRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "이건 왜 없나요?" 한 줄 피드백. 로그인한 사람만 남기고 운영자만 읽는다. */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    static final int MAX_LENGTH = 300;
    /** 하루에 한 사람이 남길 수 있는 수. 장난으로 표가 차지 않게. */
    static final int DAILY_LIMIT = 10;

    private final AuthService auth;
    private final FeedbackRepository feedback;
    private final Clock clock;

    public FeedbackController(AuthService auth, FeedbackRepository feedback, Clock clock) {
        this.auth = auth;
        this.feedback = feedback;
        this.clock = clock;
    }

    public record FeedbackRequest(String message) {
    }

    public record FeedbackView(long id, String message, OffsetDateTime createdAt) {
    }

    @PostMapping("/feedback")
    @Transactional
    public ResponseEntity<Void> leave(HttpServletRequest request, @RequestBody FeedbackRequest body) {
        AppUser user = auth.requireUser(request);
        String message = body.message() == null ? "" : body.message().strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("내용이 비었습니다");
        }
        if (message.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(MAX_LENGTH + "자 안으로 적어 주세요");
        }
        if (feedback.countByUserIdAndCreatedAtAfter(user.getId(), OffsetDateTime.now(clock).minusDays(1)) >= DAILY_LIMIT) {
            throw new IllegalArgumentException("오늘은 더 남길 수 없어요. 내일 다시 적어 주세요");
        }
        feedback.save(new Feedback(user.getId(), message));
        return ResponseEntity.noContent().build();
    }

    /** 누가 썼는지는 보여주지 않는다. 읽는 데 필요 없다. */
    @GetMapping("/admin/feedback")
    @Transactional(readOnly = true)
    public List<FeedbackView> list(HttpServletRequest request) {
        auth.requireAdmin(request);
        return feedback.findTop100ByOrderByCreatedAtDesc().stream()
                .map(f -> new FeedbackView(f.getId(), f.getMessage(), f.getCreatedAt()))
                .toList();
    }
}
