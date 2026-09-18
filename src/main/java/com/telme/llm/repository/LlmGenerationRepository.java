package com.telme.llm.repository;

import com.telme.llm.entity.LlmGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmGenerationRepository extends JpaRepository<LlmGeneration, Long> {
}
