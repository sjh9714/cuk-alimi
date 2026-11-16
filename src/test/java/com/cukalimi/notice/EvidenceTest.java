package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import com.cukalimi.dateparse.DateRange;
import com.cukalimi.dateparse.KoreanDateParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 근거 (ADR 016). 뽑은 날짜마다 그 날짜를 읽은 글이 따라오고, 형광펜을 그을 글자는 그 글 안에 있어야 한다. */
class EvidenceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final KoreanDateParser PARSER = new KoreanDateParser(LocalDate.of(2026, 10, 1));

    @Test
    void highlightsTheDeadlineNotTheFirstDateOfAMergedRange() {
        String cell = "1차 11. 3.(화) 10:00 2차 11. 5.(목) 17:00";
        List<DateRange> found = PARSER.parse(cell);
        assertThat(found).hasSizeGreaterThan(1);
        DateRange last = found.get(found.size() - 1);
        DateRange merged = new DateRange(found.get(0).start(), last.end(), null, null, found.get(0).matchedText());

        Evidence evidence = Evidence.of("수강신청 변경 | " + cell, found, merged);

        assertThat(evidence.match()).isEqualTo(last.matchedText());
        assertThat(evidence.text()).contains(evidence.match());
    }

    @Test
    void dropsTheTildeTheParserAddsToEndOnlyDates() {
        String line = "신청 기한 : ~ 2026. 10. 30.(금)까지";
        DateRange r = PARSER.parse(line).get(0);
        assertThat(r.matchedText()).startsWith("~");

        Evidence evidence = Evidence.of(line, List.of(r), r);

        assertThat(evidence.match()).doesNotStartWith("~");
        assertThat(line).contains(evidence.match());
    }

    @Test
    void keepsTheDateWhenALongParagraphIsClipped() {
        String date = "11. 13.(금)까지";
        String line = "가".repeat(400) + " 신청은 " + date + " 받습니다. " + "나".repeat(400);
        DateRange r = PARSER.parse(line).get(0);

        Evidence evidence = Evidence.of(line, List.of(r), r);

        assertThat(evidence.text().length()).isLessThanOrEqualTo(Evidence.LIMIT + 2);
        assertThat(evidence.text()).startsWith("…").endsWith("…").contains(evidence.match());
    }

    @Test
    void everyDateExtractedFromTheGoldAndFreshNoticesCarriesItsSourceText() throws Exception {
        List<String> missing = new ArrayList<>();
        int items = 0;
        for (String set : List.of("gold/labels.json", "gold/fresh.json")) {
            JsonNode notices = JSON.readTree(Fixtures.read(set)).get("notices");
            String dir = set.endsWith("fresh.json") ? "fresh/" : "gold/notices/";
            for (Iterator<String> it = notices.fieldNames(); it.hasNext();) {
                String no = it.next();
                JsonNode label = notices.get(no);
                boolean image = label.has("source") && "image".equals(label.get("source").asText());
                NoticeView view = NoticeViewParser.parse(Fixtures.read(dir + no + ".html"));
                List<ExtractedItem> extracted = image
                        ? ImageNoticeReader.extractFromTranscripts(view, List.of(Fixtures.read("gold/transcripts/" + no + ".html")))
                        : DateItemExtractor.extract(view);
                for (ExtractedItem item : extracted) {
                    items++;
                    Evidence e = item.evidence();
                    if (e == null || e.text().isBlank() || e.match() == null || !e.text().contains(e.match())) {
                        missing.add(no + " " + item.where() + " " + item.title());
                    }
                }
            }
        }
        assertThat(items).isGreaterThan(100);
        assertThat(missing).as("근거가 없거나 날짜 글자가 근거 안에 없는 항목").isEmpty();
    }
}
