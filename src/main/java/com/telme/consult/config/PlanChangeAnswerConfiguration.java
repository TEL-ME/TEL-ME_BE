package com.telme.consult.config;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.PlanChangeAnswerProvider;
import com.telme.consult.service.PlanChangeAnswerer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

// 채팅 요금제 변경. 상담 답변 빈(ConsultRagAnswerConfiguration.consultAnswerProvider)을 고치지 않고 만들어진 뒤 바깥에서 감싼다
@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
public class PlanChangeAnswerConfiguration {

    static final String ANSWER_BEAN = "consultAnswerProvider";

    // 후처리기는 다른 빈보다 먼저 만들어져서 static으로 두고, 필요한 빈은 감쌀 때 꺼낸다
    @Bean
    static BeanPostProcessor planChangeAnswerDecorator(ObjectProvider<PlanChangeAnswerer> changes,
            ObjectProvider<ChatSessionRepository> sessions, ObjectProvider<ExecutionTrace> trace,
            ObjectProvider<PlanQuestionProperties> properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!ANSWER_BEAN.equals(beanName) || !(bean instanceof AnswerProvider answers)
                        || bean instanceof PlanChangeAnswerProvider) {
                    return bean;
                }
                PlanChangeAnswerer answerer = changes.getIfAvailable();
                ChatSessionRepository repository = sessions.getIfAvailable();
                if (answerer == null || repository == null) {
                    return bean;
                }
                PlanQuestionProperties settings = properties.getIfAvailable();
                // 요금제 답변을 끄면 변경 요청도 지금처럼 기존 경로가 답한다
                return new PlanChangeAnswerProvider(answers, answerer, repository::findUserIdBySessionId,
                        trace.getIfAvailable(ExecutionTrace::noop), settings == null || settings.answerEnabled());
            }
        };
    }
}
