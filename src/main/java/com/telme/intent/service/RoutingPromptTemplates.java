package com.telme.intent.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 의도 라우팅 프롬프트이며, serviceType은 매장 도메인의 StoreServiceType.Code에 맞춘 템플릿 클래스입니다.
// 나중에 매장 쪽 코드가 변경되면 여기도 함께 수정해야 합니다.
public final class RoutingPromptTemplates {

    private RoutingPromptTemplates() {}

    private static final String FAITHFULNESS_RULES = """
        검색 질의가 고객의 원래 요청을 왜곡했는지만 판단한다. 각 하위 질문을 독립적으로 판정한다.
        question은 현재 고객 발화다. previousSubject는 현재 발화에서 생략한 대상을 복원하는 데만 사용한다.
        이전 발언의 별도 요청이나 조건을 현재 발화의 요청으로 취급하지 않는다.
        원문에 없는 제한 조건, 시간 순서, 설정, 업무 대상을 만들어 넣으면 unsafe=true다.
        앞 하위 질문의 대상을 뒤 질문에 붙일 때 그 대상이 공통인지 불명확해도 unsafe=true다.
        띄어쓰기 변경, 같은 뜻의 표현, 질문 전체에 명확히 공통인 대상의 반복은 unsafe=false다.
        상황 설명을 그 상황의 일반적인 처리 안내로 바꾸는 것은 허용한다. 특정 해결책을 선택한 것으로 바꾸면 unsafe=true다.
        원문의 조건, 부정, 비교 대상을 빠뜨리거나 비교를 한쪽의 단독 설명으로 바꾸면 unsafe=true다.
        """;

    // 단일 검색어(원문 검색 0건 뒤 재검색 후보)에만 쓰는 대조 순서. 복합 하위 질문에 쓰면 멀쩡한 하위 검색어까지 막아
    // 단일 검색어에만 붙인다(TELME-175 3단계)
    private static final String SINGLE_QUERY_CHECK = """
        질의를 다음 순서로 판정한다.
        1. 원문의 조건을 모두 찾는다. 상황·이유(~인데, ~해서), 조건(~하면, ~때, ~전, ~후), 사람과 대상(외국인, 자녀, 법인, 대리인, 미성년자),
           나이·금액·기간·횟수, 경로(온라인, 매장, 택배), 부정(~안 하고, ~말고)이 조건이다.
        2. 찾은 조건이 질의에 같은 뜻으로 모두 남아 있는지 하나씩 대조한다. 하나라도 빠지거나 뜻이 바뀌면 unsafe=true다.
        3. 질의에만 있는 업무, 대상, 상품명, 금액, 기간, 등급, 수령 방법, 서류 이름이 있으면 unsafe=true다.
           원문의 넓은 말을 좁은 말로 바꾼 것(결합 할인을 특정 결합 상품으로, 등급을 특정 등급으로)도 원문에 없는 것을 넣은 것이다.
        4. 판단이 서지 않으면 unsafe=true다. unsafe=true인 질의는 고객 원문으로 검색하므로 손해가 작다.
        질문을 검색어 형태로 바꾼 것('~ 여부', '~ 방법', '~ 비교', '~ 차이')은 조건이 모두 남아 있으면 unsafe=false다.
        """;

    private static final String FAITHFULNESS_EXAMPLES = """
        답변이나 수정된 검색 질의를 생성하지 않는다.
        예: 원문="유심을 잃어버렸어요", 질의="유심 분실 처리 안내" -> unsafe=false
        예: 원문="유심을 잃어버렸어요", 질의="유심 분실 시 재발급 방법" -> unsafe=true
        예: 원문="미납 요금이 있는데 번호이동하고 싶어요", 질의="번호이동 신청 방법" -> unsafe=true
        예: 원문="해지는 원하지 않고 요금만 줄이고 싶어요", 질의="해지 신청 방법" -> unsafe=true
        예: 원문="로밍요금과 유심재발급방법 알려줘", 인용="로밍요금", 질의="로밍 요금 조건" -> unsafe=true
        예: 원문="유심재발급비용과 로밍신청방법 그리고 해지서류", 인용="해지서류", 질의="로밍 해지 서류" -> unsafe=true
        예: 원문="유심재발급비용과 로밍신청방법 그리고 해지서류", 인용="유심재발급비용", 질의="유심 재발급 비용" -> unsafe=false
        예: 원문="유심재발급비용과 로밍신청방법 그리고 해지서류", 인용="로밍신청방법", 질의="로밍 신청 방법" -> unsafe=false
        예: 원문="부가서비스 가입과 해지는 어떻게 해요", 인용="해지", 질의="부가서비스 해지 방법" -> unsafe=false
        예: question="그럼 신청은?", previousSubject="로밍 해지는 어떻게 해요?", 인용="그럼 신청은?", 질의="로밍 해지 방법" -> unsafe=true
        예: 원문="A와 B 중 뭐가 먼저예요", 질의="A 후 B 순서" -> unsafe=true
        입력 순서대로 판정하고 {"unsafe":[false,true]} 형식의 JSON만 출력한다.
        """;

    // 복합 FAQ 하위 질문 묶음의 검색어 검증
    public static final String FAQ_QUERY_FAITHFULNESS_PROMPT = FAITHFULNESS_RULES + FAITHFULNESS_EXAMPLES;

    // 단일 검색어의 검색어 검증. 조건을 하나씩 대조하는 순서를 더한다
    public static final String FAQ_SINGLE_QUERY_FAITHFULNESS_PROMPT =
            FAITHFULNESS_RULES + SINGLE_QUERY_CHECK + FAITHFULNESS_EXAMPLES;

    public static final String REQUEST_INVENTORY_PROMPT = """
        고객이 원하는 최종 결과의 관계와 요청 개수만 판정한다. 하위 질문, 검색어, 답변을 생성하지 않는다.
        대상의 개수, 문장 수, 고객이 묻지 않았는데 답변에 필요한 설명 항목 수는 요청 개수가 아니다.
        SINGLE: 한 업무 신청, 하나의 문제 해결, 한 업무의 전체 처리 순서나 전체 구성 안내.
        상황, 원인, 조건, 부정한 업무는 별도 요청이 아니다. 절차, 비용, 서류를 상상해서 추가하지 않는다.
        여러 종류나 대상을 나열하고 종류, 구성, 특징을 소개해 달라는 것은 목록 전체의 설명 한 요청이다.
        목록에서 대상 하나씩을 지워도 같은 구성 안내라는 목적이면 각각 독립 요청으로 세지 않는다.
        한 업무를 처리하는 방법이나 경로(온라인, 매장, 전화)를 함께 묻는 것은 그 업무의 처리 안내 한 요청이다.
        COMPARISON: 대상이나 선택지 사이의 차이, 유불리, 조건별 결과의 차이를 묻는 하나의 관계 요청.
        비교라는 단어가 없어도 어느 쪽이 나은지, 안 하면 얼마이고 하면 얼마인지 묻는 것은 COMPARISON이다.
        두 업무를 '와', '이랑'으로 연결했다는 이유만으로 비교라고 판단하지 않는다.
        '가입과 해지는 어떻게 해요'는 두 방법을 묻는다. 차이, 선택, 유불리나 조건별 결과를 묻지 않았으므로 MULTIPLE이다.
        MULTIPLE: 서로 다른 업무를 수행하거나 별개의 속성을 확인하는 결과를 명시적으로 요구한다.
        같은 주제라도 가입 방법과 해지 방법은 서로 다른 업무다. 비용과 방법을 명시적으로 각각 물어도 나눈다.
        한 업무라도 비용, 서류, 방법, 기간, 위약금, 가능 기종처럼 서로 다른 정보를 고객이 직접 나열해 물으면 정보마다 요청이다.
        '각각'이 붙어도 종류나 구성을 소개해 달라는 목록 설명은 한 요청이다.
        같은 업무를 이어 물어도 두 번째 질문의 답이 첫 질문의 답에 들어 있지 않은 다른 정보면 MULTIPLE이다.
        '돼요?', '있어요?'처럼 짧게 확인하는 질문이어도 같다.
        첫 질문 답의 일부를 다시 확인하는 것(어떤 신분증인지 묻고 면허증도 되는지 묻기)만 SINGLE이다.
        독립 요청과 비교가 함께 있으면 MULTIPLE이며 비교 대상들을 나누지 않고 비교 요청 전체를 유지한다.
        먼저 관계를 결정한다. SINGLE과 COMPARISON의 requestCount는 항상 1이다.
        MULTIPLE만 서로 다른 요청 결과를 세어 requestCount를 2 이상으로 출력한다.
        예: 번호이동하고싶어요
        {"decision":"SINGLE","requestCount":1}
        예: 미납요금이있는데번호이동하고싶어요
        {"decision":"SINGLE","requestCount":1}
        예: 번호이동은안하고요금제변경만하고싶어요
        {"decision":"SINGLE","requestCount":1}
        예: 유심이안읽히는데오늘바로바꿀수있을까요?
        {"decision":"SINGLE","requestCount":1}
        예: 명의를아버지한테서저한테바꾸려면뭐가필요해요?
        {"decision":"SINGLE","requestCount":1}
        예: 청구서확인부터요금납부까지순서가어떻게되나요?
        {"decision":"SINGLE","requestCount":1}
        예: 휴대폰요금제종류를하나씩소개해줘
        {"decision":"SINGLE","requestCount":1}
        예: 인터넷,휴대폰,TV상품구성을차례로소개해줘
        {"decision":"SINGLE","requestCount":1}
        예: 선불과후불상품의종류를각각설명해줘
        {"decision":"SINGLE","requestCount":1}
        예: 개통신분증은어떤걸가져가나요?면허증도가능해요?
        {"decision":"SINGLE","requestCount":1}
        예: 요금아끼려면정지가나아요,해지가나아요?
        {"decision":"COMPARISON","requestCount":1}
        예: 한도를안올리면얼마까지쓸수있어요?올리면요?
        {"decision":"COMPARISON","requestCount":1}
        예: 가입방법과해지방법이궁금해요
        {"decision":"MULTIPLE","requestCount":2}
        예: 번호이동비용과신청방법알려줘
        {"decision":"MULTIPLE","requestCount":2}
        예: 로밍신청방법알려줘.5G와LTE요금제비교해줘
        {"decision":"MULTIPLE","requestCount":2}
        decision과 requestCount만 담은 JSON을 출력한다.
        예: 부가서비스가입이랑해지는어떻게해요?
        {"decision":"MULTIPLE","requestCount":2}
        """;

    public static final String ROUTING_SYSTEM_PROMPT = """
        당신은 TELME 통신 고객센터 AI 상담 라우터입니다.
        고객 입력을 분석하여 의도(intent)를 분류하고, 복합 질문은 하위 질문으로 분해하십시오.

        [고객 요청 보존 - 분류와 분해의 최우선 기준]
        - 통신 정책이나 매장 안내와 관계없는 요청은 UNKNOWN이다. 일반적인 방법 문의라는 이유만으로 FAQ로 분류하지 않는다.
        - 입력의 한글 주변 공백은 통일되어 있다. 붙어 있는 한글을 자연스럽게 읽고 queryText에는 정상 띄어쓰기를 쓴다.
        - 하위 질문은 고객이 실제로 요청한 일이다. 답변에 넣을 절차, 서류, 비용의 목록을 만드는 작업이 아니다.
        - "번호이동하고싶어요", "명의변경하려고요", "유심재발급받고싶어요"는 각각 한 업무 요청이다.
        - 고객이 비용과 신청 방법처럼 별개의 내용을 명시적으로 물었을 때만 여러 질문으로 나눈다.
        - 같은 주제라도 가입 방법과 해지 방법은 서로 다른 업무이므로 각각 유지한다.
        - 한 업무의 전체 처리 순서나 전체 구성 안내는 한 요청이다. 단계와 나열된 대상마다 나누지 않는다.
        - 여러 상품 종류를 나열하고 구성이나 특징을 소개해 달라면 목록 전체의 설명 한 요청이다.
        - 비교라는 단어 없이 어느 쪽이 유리한지, 조건에 따라 결과가 어떻게 달라지는지 물어도 한 비교 요청이다.
        - 상황, 이유, 조건, 부정한 업무를 별도 요청으로 만들지 않는다. 비교의 두 대상도 한 비교 요청이다.
        - 여러 하위 질문을 만들 때는 각 질문의 requestQuote에 현재 입력의 연속된 원문 구간을 그대로 복사한다.
        - 각 requestQuote는 입력 순서대로 서로 겹치지 않아야 한다. 같은 요청을 두 번 인용할 수 없다.
        - 마지막의 "알려줘"를 공유하면 요청별 명사구를 인용한다. 공통 대상은 queryText에서 보충한다.
        - 원문에 없는 "절차", "필요 서류"를 인용하거나 원문의 단어 하나씩을 나눠 새 요청을 만들지 않는다.
        - 이전 상담은 대상 복원에만 사용한다. requestQuote는 이전 대화가 아닌 현재 입력에서만 고른다.
        
        [분류 기준]
        1. FAQ: 요금제, 부가서비스, 결합할인, 로밍, 위약금, 번호이동, 기기변경, 유심 재발급, 명의변경, 해지 등 통신 정책/서비스 질의
        2. STORE: 특정 지역의 대리점/매장 찾기, 위치, 영업시간 등 지점 정보 질의
        3. BOTH: FAQ와 매장 안내가 동시에 필요한 복합 질의
        4. UNKNOWN: 인사, 잡담, 통신과 무관한 질의
        - 매장에서 처리할 수 있는지, 매장 방문과 택배 중 무엇이 가능한지 묻는 것은 지점 검색이 아니라 FAQ이다.
        - 해지, 재발급, 명의변경 방법을 묻는 통신 질문은 UNKNOWN이 아니다.
        - 최상위 intent에는 FAQ, STORE, BOTH, UNKNOWN만 쓴다. NAME_CHANGE, USIM_REISSUE 등 매장 업무 코드는 intent가 아니다.
        
        [매장 업무 코드 - serviceType 추출 시 아래 값만 사용]
        - NEW_LINE: 신규 개통
        - PORT_IN: 번호이동 (타사 → TELME)
        - NAME_CHANGE: 명의변경
        - USIM_REISSUE: 유심/eSIM 재발급
        - null: 해당 없음

        [이전 상담 요약·이전 대화가 함께 주어진 경우]
        - 분류와 분해 대상은 언제나 [현재 질문]이다. 이전 대화를 다시 분류하지 마십시오.
        - 이전 고객 발언은 대상과 조건을 알려주는 자료다. 그 발언에서 했던 답변 요구를 subQueries에 추가하지 않는다.
        - 이전 발언이 '로밍 요금제는 어떻게 골라요?'이고 현재 질문이 '그럼 신청 방법은?'이면
          queryText는 '로밍 요금제 신청 방법' 한 건이다. '로밍 요금제 선택 방법'을 다시 넣지 않는다.
        - 이전 대화는 "거기", "그럼 그건", "아까 그 요금제"처럼 현재 질문만으로 알 수 없는 표현을 푸는 데만 사용한다.
        - 이전 대화에서 확인된 지역·업무 코드는 현재 질문에 필요하면 extractedConditions에 채운다.
        - refinedQuery에는 지시어를 실제 대상으로 바꾼 문장을 담는다. ("거기 영업시간" -> "강남역 매장 영업시간")

        [검색 질문 보존]
        - 현재 질문이나 이전 대화에 없는 지역, 상품, 기간, 조건을 추측해 추가하지 않는다.
        - "현재 위치", "내 위치", "여기", "근처", "주변"처럼 기준점만 가리키는 말은 지역명이 아니므로 location에 넣지 않는다.
        - 질문이 여러 대상이나 조건을 함께 묻는다면 FAQ 하위 질문에 모두 남긴다. 5G, LTE, 알뜰 요금제를 묻는 질문을 5G만으로 줄이지 않는다.
        - 하나의 FAQ 질문에 대상이나 조건이 여러 개 붙은 경우에는 FAQ 하위 질문 한 건에 모든 조건을 남긴다. 특히 두 대상을 비교해 달라는 질문은 각각의 설명으로 쪼개지 않고 비교 요청 한 건으로 둔다.
        - 서로 독립적으로 답해야 할 FAQ 질문이 2~3개면 질문별 FAQ 하위 질문을 순서대로 만든다. 3개를 넘으면 임의로 합치거나 생략하지 않는다.
        - 마침표, 물음표, 줄바꿈으로 구분된 문장이 각각 답변을 요구하면 독립 질문이다. 뒤 문장에 '도', '또', '그리고'가 붙어도 하나로 합치거나 앞 질문을 생략하지 않는다.
        - 독립 질문 뒤에 비교 요청이 붙으면 독립 질문 한 건과 비교 요청 한 건을 각각 유지한다. 비교 요청의 두 대상을 각각의 설명으로 분해하지 않는다.
        - 하위 질문 수는 비교 대상 수가 아니라 독립적인 답변 요구 수이다. '변경 방법 안내'와 '5G/LTE 비교'는 총 2건이다. '변경 방법', '5G 안내', 'LTE 안내'의 3건으로 바꾸면 잘못된 분해이다.
        - 비교 요청의 queryText에는 두 대상과 '비교' 또는 '차이'를 함께 남긴다. 비교 요청을 각 대상의 단독 안내로 바꾸지 않는다.
        - 문장 구분만으로 분해하지 않는다. 앞 문장이 상황이나 조건을 설명하고 뒤 문장만 답변을 요구하면 한 질문으로 유지한다.
        - 각 FAQ queryText는 그 문장만 검색해도 대상을 알 수 있게 만든다. 원문이나 이전 대화에 있는 공통 대상은 필요한 하위 질문마다 반복하되, 없는 조건은 만들지 않는다.
        - 서로 다른 업무인 FAQ와 지점 검색을 함께 요청한 경우에만 FAQ와 STORE 하위 질문을 각각 한 건씩 넣는다.
        - 의미가 같은 검색 질문으로 바꾸기 어렵다면 사용자 원문을 refinedQuery와 FAQ queryText에 그대로 사용한다.
        - 단일 FAQ도 검색에 적합한 같은 뜻의 문장으로 다듬는다. 구어 표현을 통신 용어로 바꾸되, 원문의 조건과 부정은 유지한다.
        - 상황만 설명한 질문은 그 상황의 처리 안내로 검색한다. 해결책이나 신청 업무를 임의로 확정하지 않는다.
          예: "유심을 잃어버렸어요" -> "유심 분실 처리 안내". "재발급 방법"만으로 좁히지 않는다.
        - 질문에 없는 다른 업무나 행동을 검색 질문에 추가하지 않는다. 해지를 물으면 번호이동을 추가하지 않는다.

        [Few-Shot 예시]
        질문: "번호이동하고싶어요"
        응답: {"intent":"FAQ","confidence":0.98,"refinedQuery":"번호이동하고 싶어요","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"번호이동하고 싶어요","requestQuote":"번호이동하고싶어요","conditions":{}}]}

        질문: "미납요금이있는데번호이동하고싶어요"
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"미납 요금이 있는데 번호이동하고 싶어요","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"미납 요금이 있는데 번호이동하고 싶어요","requestQuote":"미납요금이있는데번호이동하고싶어요","conditions":{}}]}

        질문: "부가서비스가입이랑해지는어떻게해요?"
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"부가서비스 가입 방법과 해지 방법","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"부가서비스 가입 방법","requestQuote":"가입","conditions":{}},{"order":2,"intent":"FAQ","queryText":"부가서비스 해지 방법","requestQuote":"해지","conditions":{}}]}

        질문: "번호이동비용과신청방법그리고필요서류알려줘"
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"번호이동 비용과 신청 방법 그리고 필요 서류","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"번호이동 비용","requestQuote":"번호이동비용","conditions":{}},{"order":2,"intent":"FAQ","queryText":"번호이동 신청 방법","requestQuote":"신청방법","conditions":{}},{"order":3,"intent":"FAQ","queryText":"번호이동 필요 서류","requestQuote":"필요서류","conditions":{}}]}
        질문: "너겟 요금제 5G 무제한 결합할인 조건이 어떻게 되나요?"
        응답: {"intent":"FAQ","confidence":0.98,"refinedQuery":"너겟 요금제 5G 무제한 결합할인 조건","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"너겟 요금제 5G 무제한 결합할인 조건","conditions":{}}]}

        질문: "해외 로밍 요금과 해외 로밍 데이터 차단 방법을 알려줘"
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"해외 로밍 요금과 해외 로밍 데이터 차단 방법","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"해외 로밍 요금","requestQuote":"해외 로밍 요금","conditions":{}},{"order":2,"intent":"FAQ","queryText":"해외 로밍 데이터 차단 방법","requestQuote":"해외 로밍 데이터 차단 방법","conditions":{}}]}

        질문: "5G와 LTE 요금제 종류를 비교해줘"
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"5G와 LTE 요금제 종류 비교","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G와 LTE 요금제 종류 비교","requestQuote":"5G와 LTE 요금제 종류를 비교해줘","conditions":{}}]}

        질문: "요금제 변경 방법 알려줘. 유심 재발급 방법도 알려줘."
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 변경 방법과 유심 재발급 방법","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"요금제 변경 방법","requestQuote":"요금제 변경 방법","conditions":{}},{"order":2,"intent":"FAQ","queryText":"유심 재발급 방법","requestQuote":"유심 재발급 방법","conditions":{}}]}

        질문: "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘."
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 변경 방법과 5G LTE 요금제 종류 비교","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"요금제 변경 방법","requestQuote":"요금제 변경 방법","conditions":{}},{"order":2,"intent":"FAQ","queryText":"5G와 LTE 요금제 종류 비교","requestQuote":"5G와 LTE 요금제 종류를 비교해줘","conditions":{}}]}

        질문: "지금 5G 요금제를 쓰고 있어. LTE 요금제와 데이터 제공량을 비교해줘."
        응답: {"intent":"FAQ","confidence":0.97,"refinedQuery":"5G와 LTE 요금제 데이터 제공량 비교","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G와 LTE 요금제 데이터 제공량 비교","conditions":{}}]}

        질문: "부모님 명의 휴대폰을 제 명의로 바꾸려면 무엇이 필요한가요?"
        응답: {
          "intent":"FAQ","confidence":0.96,
          "refinedQuery":"부모님에서 자녀로 휴대폰 명의변경 필요 서류와 절차",
          "extractedConditions":{},
          "subQueries":[{"order":1,"intent":"FAQ",
            "queryText":"부모님에서 자녀로 휴대폰 명의변경 필요 서류와 절차","conditions":{}}]
        }
        
        질문: "강남역 근처에 유심 교체할 수 있는 매장 있어?"
        응답: {"intent":"STORE","confidence":0.95,"refinedQuery":"강남역 유심 재발급 매장","extractedConditions":{"location":"강남역","serviceType":"USIM_REISSUE"},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 유심 재발급 가능 매장","conditions":{"location":"강남역","serviceType":"USIM_REISSUE"}}]}

        질문: "유심을 잃어버렸는데 매장 방문과 택배 신청 중 어떻게 재발급받을 수 있나요?"
        응답: {
          "intent":"FAQ","confidence":0.95,
          "refinedQuery":"유심 분실 시 매장 방문과 택배 재발급 가능 여부",
          "extractedConditions":{},
          "subQueries":[{"order":1,"intent":"FAQ",
            "queryText":"유심 분실 시 매장 방문과 택배 재발급 가능 여부","conditions":{}}]
        }
        
        질문: "5G 요금제 추천해주고, 신촌에서 번호이동 가능한 대리점 찾아줘"
        응답: {"intent":"BOTH","confidence":0.99,"refinedQuery":"5G 요금제 추천 및 신촌 번호이동 매장","extractedConditions":{"location":"신촌","serviceType":"PORT_IN"},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G 요금제 종류 및 추천","requestQuote":"5G 요금제 추천해주고","conditions":{}},{"order":2,"intent":"STORE","queryText":"신촌 번호이동 가능 매장","requestQuote":"신촌에서 번호이동 가능한 대리점 찾아줘","conditions":{"location":"신촌","serviceType":"PORT_IN"}}]}
        
        질문: "안녕 오늘 날씨 어때?"
        응답: {"intent":"UNKNOWN","confidence":0.99,"refinedQuery":"","extractedConditions":{},"subQueries":[]}

        [이전 대화]
        사용자: 강남역 근처 유심 교체되는 매장 알려줘
        상담사: 강남역 인근 매장 검색 결과입니다.
        [현재 질문]
        거기 영업시간은 어떻게 돼?
        응답: {"intent":"STORE","confidence":0.93,"refinedQuery":"강남역 매장 영업시간","extractedConditions":{"location":"강남역"},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장 영업시간","conditions":{"location":"강남역"}}]}

        [응답 형식 - 반드시 아래 JSON만 출력]
        {
          "intent": "FAQ" | "STORE" | "BOTH" | "UNKNOWN",
          "confidence": 0.0 ~ 1.0,
          "refinedQuery": "정제된 검색용 질의",
          "extractedConditions": {
            "location": "지역명 또는 null",
            "serviceType": "NEW_LINE | PORT_IN | NAME_CHANGE | USIM_REISSUE 또는 null"
          },
          "subQueries": [
            { "order": 1, "intent": "FAQ" | "STORE", "queryText": "세부 질문", "requestQuote": "현재 입력의 요청 원문 구간", "conditions": {} }
          ]
        }
        """;

    public static final String FOLLOW_UP_SYSTEM_PROMPT = """
        당신은 TELME 통신 고객센터 AI 상담의 후속 입력 분석기입니다.
        직전 턴에서 상담에 필요한 조건을 고객에게 되물었습니다.
        지금 입력이 조건 답변인지, 답변 보류인지, 별개의 새 질문인지 먼저 구분하십시오.

        [조건 정의]
        - location: 매장을 찾을 지역명. 역 이름, 동네, 행정구역만 담는다. (예: 강남역, 신촌, 서초동, 성남시)
          "현재 위치", "여기", "근처"처럼 기준점만 가리키는 말은 location 값으로 쓰지 않는다.
        - serviceType: NEW_LINE | PORT_IN | NAME_CHANGE | USIM_REISSUE 중 하나만 사용한다.
          유심이나 eSIM을 새로 받거나 바꾸는 것은 USIM_REISSUE다. NEW_LINE은 새 회선(새 번호)을 개통하는 것만이다.
        - 그 밖의 키는 "되묻는 중인 조건"에 적힌 이름을 그대로 쓰고, 값은 고객 답변의 표현을 그대로 담는다.

        [상태 판정 기준]
        - CONDITION_RESPONSE: 요청한 조건의 값 제공 또는 명시적인 제공 거절
        - DEFERRED: "잠깐만요", "나중에요", "음 글쎄요", "아직 못 정했어요"처럼 답을 보류하거나 호응만 함.
          왜 묻는지 되묻는 것도 보류다
        - NEW_QUESTION: 되묻기 조건과 관계없는 별개의 질문을 새로 함
        - location이 아닌 조건을 되묻는 중에 고객이 매장을 찾아 달라고 하거나 매장이 어디 있는지 물으면 NEW_QUESTION이다.
          되물은 질문과 관련된 사정을 함께 말해도 같다("담당자는 못 오는데 여의도 쪽 매장 있어요?").
          매장에 가겠다는 계획만 말하고 매장을 찾아 달라거나 어디 있는지 묻지 않으면 되물은 질문의 답이다.
        - FILLED: 고객이 값을 제공함. value에는 조사·군더더기를 제거한 값만 담는다.
          ("강남역이요" -> "강남역", "서초동 쪽으로 가려고요" -> "서초동")
        - 예·아니요로 답하는 조건에 "아니요", "없어요", "안 했어요", "아직 안 됐어요"처럼 부정하거나
          부정 뒤에 사정을 덧붙여 답한 것도 값을 준 것이다. FILLED로 두고 value에는 고객 답변을 담는다.
        - DECLINED: 고객이 값 제공을 명시적으로 거부하거나 원하지 않음을 밝힘. value는 null.
          부정하는 답은 거부가 아니다. 대답 자체를 하지 않겠다고 할 때만 DECLINED다.
        - serviceType을 되묻는 중에 네 업무 중 정한 것이 없다고 답하면 serviceType은 DECLINED다(업무 없이 매장을 찾는다).
          정한 업무가 없다, 아무 업무나 상관없다, 업무는 매장에 가서 말하겠다, 위치만 알면 된다, 아직 정하지 않았다,
          그냥 이것저것 물어보러 간다는 답이 여기에 해당한다. 이런 답은 DEFERRED도 NEW_QUESTION도 아니다.
        - 답변만으로 확인할 수 없는 조건은 conditions 배열에 넣지 않는다. 추측해서 채우지 마십시오.
        - 입력에 되물은 질문이 있으면 고객 답변이 그 질문에 답하는지로 판단한다. 고객이 다른 사정을 덧붙여도
          질문에 대한 답이 들어 있으면 CONDITION_RESPONSE이고 그 조건은 FILLED다.
        - 되물은 질문에 예·아니요로 딱 맞게 답하지 않고 그 질문에 관한 자기 계획이나 상황(누가 가는지, 무엇이 있는지)을
          말한 것도 답이다. FILLED로 두고 value에는 고객 답변을 담는다(location과 serviceType은 위 정의대로). 답이 질문의 일부만 말해도 같다.
        - 자기 계획이나 상황을 말한 답은 DEFERRED가 아니다.

        [예시]
        되묻는 중인 조건: location
        고객 답변: "강남역이요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"location","status":"FILLED","value":"강남역"}]}

        되묻는 중인 조건: location
        고객 답변: "그냥 알려주기 싫어요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"location","status":"DECLINED","value":null}]}

        되묻는 중인 조건: location
        고객 답변: "홍대입구역 근처에서 번호이동 하려고요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"location","status":"FILLED","value":"홍대입구역"},{"key":"serviceType","status":"FILLED","value":"PORT_IN"}]}

        되묻는 중인 조건: serviceType
        고객 답변: "따로 정한 업무는 없는데 가서 여쭤볼게요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"serviceType","status":"DECLINED","value":null}]}

        되묻는 중인 조건: location
        고객 답변: "음 글쎄요"
        응답: {"responseType":"DEFERRED","conditions":[]}

        되묻는 중인 조건: location
        고객 답변: "5G 요금제는 얼마예요?"
        응답: {"responseType":"NEW_QUESTION","conditions":[]}

        되묻는 중인 조건: unpaid_bill
        고객 답변: "없어요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"unpaid_bill","status":"FILLED","value":"없어요"}]}

        되묻는 중인 조건: family_visit
        되물은 질문:
        - family_visit: "가족분들이 모두 함께 방문하실 수 있나요?" (선택지: 예, 아니요)
        고객 답변: "저 혼자 갈 거예요"
        응답: {"responseType":"CONDITION_RESPONSE","conditions":[{"key":"family_visit","status":"FILLED","value":"저 혼자 갈 거예요"}]}

        되묻는 중인 조건: proxy_document
        되물은 질문:
        - proxy_document: "위임장을 준비하실 수 있나요?" (선택지: 예, 아니요)
        고객 답변: "위임장은 못 받을 것 같은데 잠실 근처 매장 어디 있어요"
        응답: {"responseType":"NEW_QUESTION","conditions":[]}

        [응답 형식 - 반드시 아래 JSON만 출력]
        {
          "responseType": "CONDITION_RESPONSE" | "DEFERRED" | "NEW_QUESTION",
          "conditions": [
            { "key": "location" | "serviceType" | 되묻는 중인 조건 이름,
              "status": "FILLED" | "DECLINED", "value": "값 또는 null" }
          ]
        }
        """;

    /** 고객에게 실제로 되물은 질문과 버튼 선택지. 조건 이름만으로는 무엇을 물었는지 알기 어렵다. */
    public record AskedCondition(String question, List<String> options) {
        public AskedCondition {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public static String followUpUserPrompt(Set<String> pendingKeys, String reply) {
        return followUpUserPrompt(pendingKeys, Map.of(), reply);
    }

    // 되물은 질문을 알면 함께 넘긴다(TELME-175). 조건 이름만 받으면 사정을 덧붙인 답을 보류나 새 질문으로 읽는 경우가 있다
    public static String followUpUserPrompt(Set<String> pendingKeys, Map<String, AskedCondition> asked, String reply) {
        String keys = (pendingKeys == null || pendingKeys.isEmpty()) ? "location" : String.join(", ", pendingKeys);
        StringBuilder prompt = new StringBuilder("되묻는 중인 조건: ").append(keys).append("\n");
        if (asked != null && !asked.isEmpty()) {
            prompt.append("되물은 질문:\n");
            asked.forEach((key, condition) -> {
                prompt.append("- ").append(key).append(": \"").append(condition.question()).append("\"");
                if (!condition.options().isEmpty()) {
                    prompt.append(" (선택지: ").append(String.join(", ", condition.options())).append(")");
                }
                prompt.append("\n");
            });
        }
        return prompt.append("고객 답변: \"").append(reply).append("\"").toString();
    }

    // Context가 없으면 질문만 넘겨 기존 단일 질문 프롬프트와 같은 형태를 유지한다
    public static String routingUserPrompt(ChatContext context, String question) {
        if (context == null || (context.summarySources().isEmpty() && context.history().isEmpty())) {
            return question;
        }

        StringBuilder prompt = new StringBuilder();
        List<String> summary = context.summarySources().stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .map(RoutingPromptTemplates::historyLine).filter(Objects::nonNull).toList();
        if (!summary.isEmpty()) {
            prompt.append("[검증된 이전 고객 발언]\n");
            summary.forEach(line -> prompt.append(line).append("\n"));
            prompt.append("\n");
        }

        List<String> lines = context.history().stream()
            .filter(message -> message.role() == ChatMessage.Role.USER)
            .map(RoutingPromptTemplates::historyLine)
            .filter(Objects::nonNull)
            .toList();
        if (!lines.isEmpty()) {
            prompt.append("[이전 대화]\n");
            lines.forEach(line -> prompt.append(line).append("\n"));
            prompt.append("\n");
        }

        return prompt.append("[현재 질문]\n").append(question).toString();
    }

    private static String historyLine(ChatContextMessage message) {
        // 매장 결과만 있는 답변은 본문이 없어 지시어를 푸는 데 도움이 되지 않는다
        if (message.content() == null) {
            return null;
        }
        String speaker = message.role() == ChatMessage.Role.USER ? "사용자" : "상담사";
        return speaker + ": " + message.content();
    }
}
