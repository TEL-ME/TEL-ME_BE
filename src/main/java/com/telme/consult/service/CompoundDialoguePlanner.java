package com.telme.consult.service;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;

import java.util.*;

/** 같은 채팅방의 분해된 상담을 순서대로 판단한다. 소유권·버전 확인은 호출자가 담당한다. */
public final class CompoundDialoguePlanner {
    public enum Status {
        PENDING,
        WAITING_CONDITION,
        DONE,
        CANCELLED
    }

    public record Request(DialogueInput input, Status status, Long pendingQuestionMessageId) {
        public Request {
            Objects.requireNonNull(input);
            Objects.requireNonNull(status);
            if (pendingQuestionMessageId != null
                    && (pendingQuestionMessageId <= 0 || status != Status.WAITING_CONDITION)) {
                throw new IllegalArgumentException(
                        "Pending question requires waiting request and positive message id");
            }
        }
    }

    public record Waiting(long consultRequestId, long messageId) {}

    public record Plan(
            List<DialogueDecision> ready,
            List<DialogueDecision> guidance,
            DialogueDecision clarification,
            List<Waiting> awaiting,
            List<Long> deferred,
            List<Long> closed) {
        public Plan {
            ready = List.copyOf(ready);
            guidance = List.copyOf(guidance);
            awaiting = List.copyOf(awaiting);
            deferred = List.copyOf(deferred);
            closed = List.copyOf(closed);
        }
    }

    private final DialogueService dialogue;

    public CompoundDialoguePlanner(DialogueService dialogue) {
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    public Plan plan(List<Request> requests) {
        var inputs = List.copyOf(requests);
        var ids = new HashSet<Long>();
        for (var request : inputs) {
            if (!ids.add(request.input().consultRequestId()))
                throw new IllegalArgumentException("Duplicate consult request id");
            if ((request.status() == Status.DONE || request.status() == Status.CANCELLED)
                    && !request.input().updates().isEmpty())
                throw new IllegalArgumentException("Closed request cannot receive updates");
        }
        var ready = new ArrayList<DialogueDecision>();
        var guidance = new ArrayList<DialogueDecision>();
        var awaiting = new ArrayList<Waiting>();
        var candidates = new ArrayList<Request>();
        var closed = new ArrayList<Long>();
        for (var request : inputs) {
            long id = request.input().consultRequestId();
            if (request.status() == Status.DONE || request.status() == Status.CANCELLED) {
                closed.add(id);
                continue;
            }
            var decision = dialogue.assess(request.input());
            if (decision.action() == Action.PROCEED) ready.add(decision);
            else if (decision.action() == Action.ALTERNATIVE_GUIDANCE) guidance.add(decision);
            else if (request.pendingQuestionMessageId() != null)
                awaiting.add(new Waiting(id, request.pendingQuestionMessageId()));
            else candidates.add(request);
        }
        // 답을 기다리는 질문이 있으면 새 질문을 만들지 않는다.
        DialogueDecision question = null;
        if (awaiting.isEmpty() && !candidates.isEmpty())
            question = dialogue.decide(candidates.removeFirst().input());
        var deferred = candidates.stream().map(r -> r.input().consultRequestId()).toList();
        return new Plan(ready, guidance, question, awaiting, deferred, closed);
    }
}
