package com.telme.chat.safety;

import static com.telme.chat.guard.MaskSpan.INSPECTION_BOUNDARY;
import static com.telme.chat.guard.MaskSpan.MASK;

import com.telme.chat.guard.InputInspection;
import com.telme.chat.guard.MaskSpan;
import java.util.List;

/** 서버가 확인한 마스크와 양옆 단어가 함께 인용된 경우에만 검사 경계를 복원한다. */
public record OutputMaskContext(List<Source> sources) {
    public OutputMaskContext {
        sources = List.copyOf(sources);
    }

    public static OutputMaskContext empty() {
        return new OutputMaskContext(List.of());
    }

    public record Source(String content, List<MaskSpan> spans) {
        public Source {
            spans = List.copyOf(spans);
            int previousEnd = 0;
            for (MaskSpan span : spans) {
                if (span.start() < previousEnd || span.end() > content.length()
                        || !content.substring(span.start(), span.end()).equals(MASK)) {
                    throw new IllegalArgumentException("저장된 마스크 위치가 문구와 일치하지 않습니다.");
                }
                previousEnd = span.end();
            }
        }

        @Override
        public String toString() {
            return "Source[content=REDACTED, spans=" + spans + "]";
        }

        public static Source from(InputInspection inspection) {
            return new Source(inspection.content(), inspection.maskSpans());
        }
    }

    String protect(String value) {
        if (sources.isEmpty() || !value.contains(MASK)) {
            return value;
        }
        boolean[] protectedCharacters = new boolean[value.length()];
        for (Source source : sources) {
            for (MaskSpan span : source.spans()) {
                // 공백 없이 글자 사이에 끼운 마스크는 출처가 있어도 기존 출력 욕설 검사에 남긴다.
                if (span.start() > 0 && span.end() < source.content().length()
                        && !Character.isWhitespace(source.content().charAt(span.start() - 1))
                        && !Character.isSpaceChar(source.content().charAt(span.start() - 1))
                        && !Character.isWhitespace(source.content().charAt(span.end()))
                        && !Character.isSpaceChar(source.content().charAt(span.end()))) {
                    continue;
                }
                int left = span.start();
                while (left > 0 && separator(source.content().charAt(left - 1))) {
                    left--;
                }
                int right = span.end();
                while (right < source.content().length() && separator(source.content().charAt(right))) {
                    right++;
                }
                // 출처에 없던 앞·뒤 글자가 마스크를 통해 새 욕설을 만드는 경우는 신뢰하지 않는다.
                if (left == 0 || right == source.content().length()) {
                    continue;
                }
                while (left > 0 && !Character.isWhitespace(source.content().charAt(left - 1))) {
                    left--;
                }
                while (right < source.content().length() && !Character.isWhitespace(source.content().charAt(right))) {
                    right++;
                }
                String quote = source.content().substring(left, right);
                for (int offset = value.indexOf(quote); offset >= 0; offset = value.indexOf(quote, offset + 1)) {
                    int end = offset + quote.length();
                    if (offset > 0 && Character.isLetterOrDigit(value.charAt(offset - 1))
                            || end < value.length() && Character.isLetterOrDigit(value.charAt(end))) {
                        continue;
                    }
                    for (int index = offset + span.start() - left; index < offset + span.end() - left; index++) {
                        protectedCharacters[index] = true;
                    }
                }
            }
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            result.append(protectedCharacters[index] ? INSPECTION_BOUNDARY : value.charAt(index));
        }
        return result.toString();
    }

    private static boolean separator(char value) {
        return Character.isWhitespace(value) || value == '*';
    }
}
