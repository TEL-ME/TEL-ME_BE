package com.telme.llm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.entity.LlmGeneration.TaskType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LlmGenerationPromptVersionTest {

    @Autowired
    private LlmGenerationRepository generations;

    @Autowired
    private ChatExecutionRepository executions;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("30자를 넘는 문맥 해석 프롬프트 버전도 호출 기록에 저장된다")
    void 긴_프롬프트_버전을_저장한다() {
        String version = "multiturn-resolution-v31-reference";

        LlmGeneration saved = generations.save(LlmGeneration.builder()
                .execution(executions.getReferenceById(1L))
                .taskType(TaskType.CONTEXT_RESOLUTION)
                .attempt((short) 1)
                .promptVersion(version)
                .status(Status.SUCCESS)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(generations.findById(saved.getGenerationId()))
                .hasValueSatisfying(found -> assertThat(found.getPromptVersion()).isEqualTo(version));
    }
}
