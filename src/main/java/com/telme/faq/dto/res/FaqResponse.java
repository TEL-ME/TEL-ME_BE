package com.telme.faq.dto.res;

//사용자에게는 공개 내용만 보낸다. 버전·해시·작성자·인용 횟수는 관리자 응답에만 있다
public record FaqResponse(
        Long faqId,
        String category,
        String question,
        String answer
        ) {

}
