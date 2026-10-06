package com.telme.llm.converter;

import com.telme.llm.dto.req.AdminLatencySearchRequest;
import com.telme.llm.dto.res.AdminLatencyResponse;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.repository.AdminLatencyRepository.StatsView;
import com.telme.llm.repository.AdminLatencyRepository.TaskStatsView;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminLatencyConverter {

    public AdminLatencyResponse toResponse(
            AdminLatencySearchRequest request, StatsView overall, StatsView firstToken, List<TaskStatsView> tasks) {
        return new AdminLatencyResponse(request.from(), request.to(), 
                toStats(overall), toStats(firstToken), toTaskStats(tasks));
    }
    
    private AdminLatencyResponse.Stats toStats(StatsView view) {
        return new AdminLatencyResponse.Stats(view.getCount(), 
                round(view.getAvgMs()), round(view.getP50Ms()), round(view.getP95Ms()));
    }
    
    private List<AdminLatencyResponse.TaskStats> toTaskStats(List<TaskStatsView> views) {
        Map<String, TaskStatsView> byType = views.stream()
                .collect(Collectors.toMap(TaskStatsView::getTaskType, Function.identity()));
        return Arrays.stream(TaskType.values())
                     .map(type -> {
                          TaskStatsView view = byType.get(type.name());
                          return view == null ? new AdminLatencyResponse.TaskStats(type.name(), 0, null, null, null)
                                              : new AdminLatencyResponse.TaskStats(type.name(), view.getCount(), 
                                                      round(view.getAvgMs()), round(view.getP50Ms()), round(view.getP95Ms()));
        }).toList();
    }
    
    private Long round(Double ms) {
        return ms == null ? null : Math.round(ms);
    }
}
