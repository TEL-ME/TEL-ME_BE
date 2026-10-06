package com.telme.llm.repository;

import com.telme.llm.entity.LlmGeneration;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmGenerationRepository extends JpaRepository<LlmGeneration, Long> {
    
    // 같은 시각의 기록이 여러 건일 수 있어 id를 보조 정렬로 둔다.
    @Query("""
            select g from LlmGeneration g
            where g.status in (com.telme.llm.entity.LlmGeneration$Status.TIMEOUT,
                               com.telme.llm.entity.LlmGeneration$Status.CONNECTION_FAILED,
                               com.telme.llm.entity.LlmGeneration$Status.MODEL_ERROR)
              and g.status in :statuses
              and g.taskType in :taskTypes
            order by g.createdAt desc, g.generationId desc
            """)
    Page<LlmGeneration> findAdminErrors(
            @Param("statuses") Collection<LlmGeneration.Status> statuses,
            @Param("taskTypes") Collection<LlmGeneration.TaskType> taskTypes,
            Pageable pageable);
}
