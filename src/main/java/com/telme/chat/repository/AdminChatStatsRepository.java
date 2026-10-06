package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import java.time.Instant;
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
}
