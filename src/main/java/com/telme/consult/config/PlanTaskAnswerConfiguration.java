package com.telme.consult.config;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.PlanTaskAnswerProvider;
import com.telme.consult.service.PlanTaskHandler;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

// 채팅 요금제 업무. 상담 답변 빈(ConsultRagAnswerConfiguration.consultAnswerProvider)을 고치지 않고 만들어진 뒤 바깥에서 감싼다
@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
public class PlanTaskAnswerConfiguration {

    static final String ANSWER_BEAN = "consultAnswerProvider";

    // 후처리기는 다른 빈보다 먼저 만들어져서 static으로 두고, 필요한 빈은 감쌀 때 꺼낸다
    @Bean
    static BeanPostProcessor planTaskAnswerDecorator(ObjectProvider<PlanTaskHandler> handlers,
            ObjectProvider<ChatSessionRepository> sessions, ObjectProvider<ExecutionTrace> trace,
            ObjectProvider<PlanQuestionProperties> properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!ANSWER_BEAN.equals(beanName) || !(bean instanceof AnswerProvider answers)
                        || bean instanceof PlanTaskAnswerProvider) {
                    return bean;
                }
                List<PlanTaskHandler> tasks = handlers.orderedStream().toList();
                ChatSessionRepository repository = sessions.getIfAvailable();
                if (tasks.isEmpty() || repository == null) {
                    return bean;
                }
                PlanQuestionProperties settings = properties.getIfAvailable();
                // 요금제 답변을 끄면 업무 요청도 지금처럼 기존 경로가 답한다
                return new PlanTaskAnswerProvider(answers, tasks, repository::findUserIdBySessionId,
                        trace.getIfAvailable(ExecutionTrace::noop), settings == null || settings.answerEnabled());
            }
        };
    }
}
