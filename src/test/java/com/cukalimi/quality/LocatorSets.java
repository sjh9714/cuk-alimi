package com.cukalimi.quality;

import com.cukalimi.Fixtures;
import com.cukalimi.notice.DateItemExtractor;
import com.cukalimi.notice.ExtractedItem;
import com.cukalimi.notice.NoticeView;
import com.cukalimi.notice.NoticeViewParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 문장 찾기 평가에 쓰는 공지 묶음 (ADR 016). 녹음(LocatorLabelRun)과 채점(LocatorBacktestTest)이 같은 묶음을 쓴다.
 * dev는 개발셋의 글 공지다(gold/labels.json에서 source가 text인 것). 지시문은 이 셋으로만 다듬는다.
 * dev2는 2026-11-06의 새 공지셋이다(gold/fresh.json). 이미 규칙을 고칠 때 본 셋이라 지시문을 얼린 뒤 한 번만 잰다.
 * 운영의 문장 찾기가 읽지 않는 공지는 뺀다. 날짜가 그림에만 있는 공지와 취업 공지("취창업")다.
 */
final class LocatorSets {

    static final List<String> NAMES = List.of("dev", "dev2");
    /** 취업 게시판의 분류 이름. LocatorReader.wanted가 읽지 않는 공지다. */
    static final String CAREER = "취창업";
    private static final ObjectMapper JSON = new ObjectMapper();

    record Case(String articleNo, NoticeView view, Set<LocalDate> want, List<ExtractedItem> rule) {
    }

    private LocatorSets() {
    }

    static Path recording(String set, String who) {
        return Path.of("src/test/resources/fixtures/gold/locator-" + set + "-" + who + ".json");
    }

    static List<Case> load(String set) throws Exception {
        boolean dev = switch (set) {
            case "dev" -> true;
            case "dev2" -> false;
            default -> throw new IllegalArgumentException("LOCATOR_SET은 dev 또는 dev2: " + set);
        };
        JsonNode gold = JSON.readTree(Fixtures.read(dev ? "gold/labels.json" : "gold/fresh.json")).get("notices");
        List<Case> out = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = gold.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            if (dev && !"text".equals(e.getValue().get("source").asText())) {
                continue;
            }
            NoticeView view = NoticeViewParser.parse(Fixtures.read((dev ? "gold/notices/" : "fresh/") + e.getKey() + ".html"));
            if (CAREER.equals(view.category())) {
                continue;
            }
            Set<LocalDate> want = new TreeSet<>();
            e.getValue().get("deadlines").forEach(d -> want.add(LocalDate.parse(d.get("date").asText())));
            out.add(new Case(e.getKey(), view, want, DateItemExtractor.extract(view)));
        }
        return out;
    }
}
