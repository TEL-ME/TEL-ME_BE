package com.telme.faq.service;

import com.telme.faq.config.FaqReembedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// faq.reembed.enabled=true로 기동했을 때만 등록
// 기동 후 전량 재임베딩하고 exit-after면 프로세스를 종료
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "faq.reembed.enabled", havingValue = "true")
public class FaqReembedRunner {

    private final FaqReembedder reembedder;
    private final FaqReembedProperties properties;
    private final ConfigurableApplicationContext context;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        int exitCode = 0;
        try {
            FaqReembedder.ReembedResult result = reembedder.reembedAll();
            log.info("[FaqReembedRunner] 완료 - 전체 {}건, 갱신 {}건, 신규 {}건, 답변 잘림 {}건",
                    result.total(), result.updated(), result.created(), result.answerTruncated());
        } catch (RuntimeException e) {
            // 건너뛸 기준이 없어 재실행은 처음부터 다시 돈다(1,150건 1분)
            log.error("[FaqReembedRunner] 재임베딩 실패 - 커밋된 청크는 유지됩니다. 원인 해결 후 처음부터 다시 실행하세요.", e);
            exitCode = 1;
        }
        if (properties.exitAfter()) {
            int code = exitCode;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }
}
