package com.cukalimi.notice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * 공지를 줄 단위 글로 편 것 (ADR 016). 문장 찾기 AI가 읽는 글이고, AI가 옮겨 온 인용문을 맞춰 보는 원문이기도 하다.
 * 첫 줄은 제목이다. 본문은 문단, 줄바꿈, 목록마다 한 줄이고 표는 행마다 한 줄이며 칸은 " | "로 잇는다. 칸 안의 문단과 줄바꿈은 줄을 나누지 않는다.
 * 규칙 추출기(DateItemExtractor)는 p, li, 표만 보지만 여기는 본문 div에 바로 적힌 글도 줄로 만든다. 규칙이 못 보는 자리를 AI가 보게 하려는 것이다.
 */
public record NoticeText(List<String> lines) {

    /** AI에 보내는 글의 길이 상한. 대상 읽기(ClaudeAudienceClassifier)와 같다. */
    public static final int MAX_CHARS = 20_000;

    private static final Set<String> BLOCK = Set.of("p", "div", "li", "ul", "ol", "tr", "table", "thead", "tbody", "h1", "h2", "h3",
            "h4", "h5", "h6", "blockquote", "section", "article", "pre", "dl", "dt", "dd", "hr");

    public static NoticeText of(NoticeView view) {
        List<String> lines = new ArrayList<>();
        add(lines, view.title());
        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            /** 표의 칸 안에 있는 동안은 0보다 크다. 칸 안의 문단과 줄바꿈은 줄을 나누지 않는다. 행 하나가 한 줄이어야 이름과 날짜가 같이 읽힌다. */
            private int inCell;
            /** 칸 안에 표가 또 있으면 그 표의 행은 다시 한 줄씩이다. 바깥 칸의 깊이를 여기 넣어 두고 안쪽 표가 끝나면 되돌린다. */
            private final Deque<Integer> outer = new ArrayDeque<>();

            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    sb.append(t.text());
                } else if (node instanceof Element e) {
                    if (e.normalName().equals("table")) {
                        outer.push(inCell);
                        inCell = 0;
                        sb.append('\n');
                    } else if (isCell(e)) {
                        inCell++;
                    } else if (BLOCK.contains(e.normalName()) || e.normalName().equals("br")) {
                        sb.append(inCell > 0 ? ' ' : '\n');
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e) {
                    if (e.normalName().equals("table")) {
                        inCell = outer.isEmpty() ? 0 : outer.pop();
                        sb.append('\n');
                    } else if (isCell(e)) {
                        inCell--;
                        sb.append(" | ");
                    } else if (BLOCK.contains(e.normalName())) {
                        sb.append(inCell > 0 ? ' ' : '\n');
                    }
                }
            }
        }, view.body());
        for (String line : sb.toString().split("\n")) {
            add(lines, line);
        }
        return new NoticeText(List.copyOf(lines));
    }

    private static boolean isCell(Element e) {
        return e.normalName().equals("td") || e.normalName().equals("th");
    }

    private static void add(List<String> lines, String raw) {
        if (raw == null) {
            return;
        }
        // 칸 끝에 붙인 " | "가 줄 끝에 남으면 뗀다. 빈 칸이 이어져도 한 번만 남긴다.
        String line = raw.replace(' ', ' ').replaceAll("\\s+", " ").replaceAll("(\\s*\\|\\s*)+$", "").replaceAll("^(\\s*\\|\\s*)+", "")
                .replaceAll("(\\s*\\|\\s*){2,}", " | ").trim();
        if (!line.isEmpty()) {
            lines.add(line);
        }
    }

    /** AI에 보내는 글. 줄을 줄바꿈으로 잇고 상한에서 자른다. */
    public String text() {
        String joined = String.join("\n", lines);
        return joined.length() <= MAX_CHARS ? joined : joined.substring(0, MAX_CHARS);
    }
}
