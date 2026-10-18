package com.telme.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

// 접수 요청이 명령에 실어 보낸 시작 값을 실행 완료 기록까지 이어 준다.
// 완료 기록은 처리 스레드 밖(타임아웃 정리)에서도 일어나므로 ThreadLocal이 아니라 실행 ID로 찾는다.
// 등록은 "이 실행을 처리하는 작업"의 표시이기도 하다. 디스패처는 등록에 성공한 작업만 실행기에 넘긴다.
@Component
public class ChatResponseTimer {

    private final Map<Long, Registration> running = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public ChatResponseTimer() {
        this(System::nanoTime);
    }

    ChatResponseTimer(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    // 확인과 등록을 한 번에 해야 동시에 들어온 같은 실행 중 하나만 처리를 맡는다.
    // 시작 값이 없는 명령도 처리 담당은 있으므로 같은 규칙으로 등록한다.
    Registration register(ChatProcessingCommand command) {
        Registration registration = new Registration(command.stopwatch());
        return running.putIfAbsent(command.executionId(), registration) == null ? registration : null;
    }

    // 자기가 등록한 항목만 지운다. 끝난 작업이 같은 ID로 새로 등록된 작업을 지우지 않게 한다.
    void release(Long executionId, Registration registration) {
        running.remove(executionId, registration);
    }

    // 시작 값이 없으면(서버 재시작 뒤 남은 실행 등) 0ms로 꾸미지 않고 측정 불가로 둔다.
    Integer findElapsedMillis(Long executionId) {
        Registration registration = running.get(executionId);
        if (registration == null || registration.stopwatch == null) {
            return null;
        }
        return registration.stopwatch.elapsedMillis(nanoTime.getAsLong());
    }

    int countTracked() {
        return running.size();
    }

    // 등록마다 새 객체라 같은 시작 값이어도 서로 다른 등록으로 구분된다(equals를 재정의하지 않는다).
    static final class Registration {

        private final ChatResponseStopwatch stopwatch;

        private Registration(ChatResponseStopwatch stopwatch) {
            this.stopwatch = stopwatch;
        }
    }
}
