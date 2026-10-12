package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.DailyCount;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminChatStatsRepository extends JpaRepository<ChatMessage, Long> {

    // 이틀을 한 번에 읽고 todayStart로 갈라, 하루씩 두 번 훑지 않는다
    @Query("""
            select new com.telme.chat.repository.QuestionCounts(
                       coalesce(sum(case when m.createdAt >= :todayStart then 1L else 0L end), 0L),
                       coalesce(sum(case when m.createdAt < :todayStart then 1L else 0L end), 0L))
            from ChatMessage m
            where m.role = com.telme.chat.entity.ChatMessage$Role.USER
              and m.messageType = com.telme.chat.entity.ChatMessage$MessageType.QUESTION
              and m.createdAt >= :yesterdayStart and m.createdAt < :tomorrowStart
            """)
    QuestionCounts countQuestions(@Param("yesterdayStart") Instant yesterdayStart,
            @Param("todayStart") Instant todayStart, @Param("tomorrowStart") Instant tomorrowStart);
    
    // 한국 시간 날짜로 묶는다. created_at은 UTC로 저장돼 그대로 날짜를 자르면 오전 9시에 날이 바뀐다.
    // 조건은 V21 ix_chat_messages_question_created_at과 같은 식이다. 기록이 없는 날은 행이 없다
    @Query(value = """
            select cast(created_at at time zone 'Asia/Seoul' as date) as day, count(*) as count
            from chat_messages
            where role = 'USER' and message_type = 'QUESTION'
              and created_at >= :from and created_at < :to
            group by 1
            """, nativeQuery = true)
    List<DailyCount> countQuestionsByDay(@Param("from") Instant from, @Param("to") Instant to);
}
