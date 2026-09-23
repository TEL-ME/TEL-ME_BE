package com.telme.consult.service;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.repository.JdbcConsultStateStore.ClarificationAlreadyPending;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.repository.JdbcConsultStateStore.Snapshot;
import com.telme.global.common.exception.GeneralException;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RequiredArgsConstructor
public class ConsultService {
    private final JdbcConsultStateStore stateStore;
    private final DialogueService dialogueService;

    @Builder
    public record PreparedTurn(long sessionId, int expectedVersion, DialogueDecision decision) {
        public PreparedTurn {
            // ConsultRequest의 JPA @Version은 신규 저장 시 0부터 시작하므로 0은 정상 값이다.
            if (sessionId <= 0 || expectedVersion < 0) {
                throw new IllegalArgumentException("Invalid consultation reference");
            }
            Objects.requireNonNull(decision, "decision");
        }
    }

    // 대기 중에도 prepared가 있으면 정정된 조건을 저장해야 한다.
    @Builder
    public record PreparationResult(PreparedTurn prepared, Long pendingMessageId) {
        public PreparationResult {
            if (prepared == null && pendingMessageId == null
                    || pendingMessageId != null
                            && (pendingMessageId <= 0
                                    || prepared != null
                                            && prepared.decision().action() != Action.ASK)) {
                throw new IllegalArgumentException("준비 결과 또는 유효한 대기 메시지 ID가 필요합니다.");
            }
        }

        public boolean waitingForReply() {
            return pendingMessageId != null;
        }
    }

    // 대기 중인 질문은 다시 만들지 않는다.
    public PreparationResult prepareTurn(
            long sessionId,
            long requestId,
            Purpose purpose,
            Map<String, Condition> updates,
            LocationStatus locationStatus) {
        try {
            return new PreparationResult(
                    prepare(sessionId, requestId, purpose, updates, locationStatus), null);
        } catch (ClarificationAlreadyPending pending) {
            if (updates.isEmpty()) {
                return new PreparationResult(null, pending.messageId());
            }
            // 질문은 유지하고, 정정된 조건만 저장하도록 반환한다.
            var snapshot = stateStore.load(sessionId, requestId);
            var decision =
                    dialogueService.assess(
                            new DialogueInput(
                                    requestId,
                                    purpose,
                                    snapshot.conditions(),
                                    updates,
                                    locationStatus));
            if (!"WAITING_CONDITION".equals(snapshot.status()) || decision.action() != Action.ASK) {
                throw new JdbcConsultStateStore.StateConflict();
            }
            return new PreparationResult(
                    new PreparedTurn(sessionId, snapshot.version(), decision), pending.messageId());
        }
    }

    // Chat에서 소유권 확인 후, 메시지 저장 트랜잭션 밖에서 호출한다.
    public PreparedTurn prepare(
            long sessionId,
            long requestId,
            Purpose purpose,
            Map<String, Condition> updates,
            LocationStatus locationStatus) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("상담 준비는 메시지 저장 트랜잭션이 끝난 뒤 호출해야 합니다.");
        }
        var snapshot = stateStore.load(sessionId, requestId);
        if (Set.of("DONE", "CANCELLED").contains(snapshot.status())) {
            throw new GeneralException(ConsultErrorCode.REQUEST_CLOSED);
        }
        // 상태를 읽는 트랜잭션은 모델 호출 전에 끝난다.
        var input =
                new DialogueInput(
                        requestId, purpose, snapshot.conditions(), updates, locationStatus);
        var assessment = dialogueService.assess(input);
        if (assessment.action() == Action.ASK) {
            // 답을 기다리는 질문이 있으면 모델을 다시 호출하지 않는다.
            stateStore
                    .findPendingClarificationMessageId(
                            sessionId, requestId, assessment.waitingField())
                    .ifPresent(
                            messageId -> {
                                throw new ClarificationAlreadyPending(messageId);
                            });
            return new PreparedTurn(sessionId, snapshot.version(), dialogueService.decide(input));
        }
        return new PreparedTurn(sessionId, snapshot.version(), assessment);
    }

    // 새 메시지 없이 조건만 바꾸고 기존 질문의 대기를 유지한다.
    @Transactional
    public Snapshot persistWaitingChanges(PreparationResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.waitingForReply() || result.prepared() == null) {
            throw new IllegalArgumentException("저장할 대기 중 조건 변경이 없습니다.");
        }
        var prepared = result.prepared();
        return stateStore.saveWhileWaiting(
                prepared.sessionId(),
                prepared.expectedVersion(),
                prepared.decision(),
                result.pendingMessageId());
    }

    // Chat 메시지 저장과 같은 트랜잭션에서 호출한다.
    @Transactional
    public Snapshot persist(PreparedTurn prepared, MessageLinks links) {
        Objects.requireNonNull(prepared, "prepared");
        return stateStore.save(
                prepared.sessionId(), prepared.expectedVersion(), prepared.decision(), links);
    }

    // 최종 답변 메시지가 커밋된 뒤 호출한다. 잠근 뒤 읽은 현재 버전으로 닫으며,
    // 아직 조건을 기다리는 상담인지·최종 메시지가 맞는지는 stateStore.complete가 검증한다.
    @Transactional
    public Snapshot complete(long sessionId, long requestId, long finalMessageId) {
        var snapshot = stateStore.load(sessionId, requestId);
        return stateStore.complete(sessionId, requestId, snapshot.version(), finalMessageId);
    }
}
