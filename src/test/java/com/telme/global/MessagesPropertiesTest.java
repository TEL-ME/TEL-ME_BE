package com.telme.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

//키를 빠뜨리면 응답에 "{chat.size.min}"이 그대로 나가거나 영어 화면에 한국어가 섞인다. 파일만 읽어 빠르게 잡는다
class MessagesPropertiesTest {

 // 코드의 message = "{키}". {value}처럼 점이 없는 것은 검증 어노테이션 값이라 제외한다
    private static final Pattern MESSAGE_KEY = Pattern.compile("message = \"\\{([a-z][\\w-]*(?:\\.[\\w-]+)+)}\"");
    
    @Test
    @DisplayName("코드에서 쓰는 검증 메시지 키가 한국어 파일에 모두 있다")
    void 코드의_키가_한국어_파일에_있다() throws IOException {
        Set<String> used;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            used = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(MessagesPropertiesTest::keysIn)
                    .collect(Collectors.toSet());
        }

        assertThat(used).isNotEmpty();
        assertThat(load("messages.properties").stringPropertyNames()).containsAll(used);
    }

    @Test
    @DisplayName("한국어 파일의 키는 영어 파일에도 모두 있다")
    void 한국어_키가_영어_파일에_있다() throws IOException {
        assertThat(load("messages_en.properties").stringPropertyNames())
                .containsAll(load("messages.properties").stringPropertyNames());
    }

    private static Stream<String> keysIn(Path path) {
        try {
            Matcher matcher = MESSAGE_KEY.matcher(Files.readString(path));
            return matcher.results().map(result -> result.group(1)).toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }
    
    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                MessagesPropertiesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
