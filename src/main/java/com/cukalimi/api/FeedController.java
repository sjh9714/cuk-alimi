package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.feed.Feed;
import com.cukalimi.feed.FeedService;
import com.cukalimi.feed.Regions;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면. 누구나 읽는다. 로그인하면 연고 지역·학년·학과로 "내게 맞는 공지"가 생긴다 (ADR 009). */
@RestController
@RequestMapping("/api")
public class FeedController {

    private final AuthService auth;
    private final FeedService feeds;
    private final Clock clock;

    public FeedController(AuthService auth, FeedService feeds, Clock clock) {
        this.auth = auth;
        this.feeds = feeds;
        this.clock = clock;
    }

    public record SidoView(String name, List<String> sigungu) {
    }

    @GetMapping("/feed")
    public Feed.View feed(HttpServletRequest request) {
        return feeds.build(auth.currentUser(request).orElse(null), LocalDate.now(clock)).view();
    }

    /** 설정 화면의 연고 지역 고르기. */
    @GetMapping("/regions")
    public List<SidoView> regions() {
        return Regions.all().stream().map(s -> new SidoView(s.name(), s.sigungu())).toList();
    }
}
