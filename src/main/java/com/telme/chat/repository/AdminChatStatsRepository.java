package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 관리자 대시보드 집계 전용. 목록·상세는 AdminUnansweredRepository가 맡는다
public interface AdminChatStatsRepository extends JpaRepository<ChatMessage, Long> {

    // 지금은 USER 메시지가 모두 QUESTION이지만, 다른 유형이 생겨도 질문 수가 부풀지 않게 함께 건다
    @Query("""
            select count(m) from ChatMessage m
            where m.role = com.telme.chat.entity.ChatMessage$Role.USER
              and m.messageType = com.telme.chat.entity.ChatMessage$MessageType.QUESTION
              and m.createdAt >= :from and m.createdAt < :to
            """)
    long countQuestions(@Param("from") Instant from, @Param("to") Instant to);
}
