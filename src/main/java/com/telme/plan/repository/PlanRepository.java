package com.telme.plan.repository;

import com.telme.plan.entity.Plan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status status);

    Optional<Plan> findByPlanIdAndStatus(Long planId, Plan.Status status);
}
