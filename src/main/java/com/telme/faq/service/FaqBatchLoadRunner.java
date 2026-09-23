package com.telme.faq.service;

import com.telme.faq.config.FaqBatchLoadProperties;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// faq.batch-load.enabled=true로 기동했을 때만 등록
// 기동 후 JSON을 적재하고 exit-after면 프로세스를 종료
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "faq.batch-load.enabled", havingValue = "true")
public class FaqBatchLoadRunner {

    private final FaqBatchLoader loader;
    private final FaqBatchLoadProperties properties;
    private final ConfigurableApplicationContext context;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        int exitCode = 0;
        try {
            FaqBatchLoader.LoadResult result = loader.load(Path.of(properties.path()));
            log.info("[FaqBatchLoadRunner] 완료 - 파일 {}건, 파일 내 중복 {}건, 이미 적재됨 {}건, 신규 적재 {}건",
                    result.total(), result.duplicateInFile(), result.alreadyInDb(), result.inserted());
        } catch (RuntimeException e) {
            log.error("[FaqBatchLoadRunner] 적재 실패 - 커밋된 청크는 유지됨. 원인 해결 후 재실행하면 이어서 적재됩니다.", e);
            exitCode = 1;
        }
        if (properties.exitAfter()) {
            int code = exitCode;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }
}
