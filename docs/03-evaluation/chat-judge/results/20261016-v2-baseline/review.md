# 실제 채팅 평가의 오류와 검토 자료

실행 실패, Judge 출력 검증 실패, 모호한 판정과 근거 없는 주장을 구분한다.
아래 근거성 판정은 자동 Judge의 결과이며 사람 확정 라벨이 아니다.

- 실행 완료 후 Judge 검증 실패: 2건
- 검토 필요 판정: 6건
- 실행 실패는 위 Judge 검증 실패 수에 포함하지 않는다.

먼저 확인할 사례: V2-COMPOUND-C017, V2-MULTI-M007, V2-SINGLE-A037, V2-SINGLE-A053, V2-SINGLE-A055, V2-SINGLE-A118, V2-COMPOUND-C011, V2-MULTI-M019

## V2-SINGLE-A003 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 청구서 이메일로 받게 방금 바꿨는데 이번 달 거부터 바로 메일로 오나요?

**저장된 답변**

방금 변경하셨다면 이번 달 청구서도 이메일로 받으실 수 있습니다. 하지만, 변경 신청이 즉시 반영되지 않을 수도 있으니, 확인을 위해 청구서 발송 예정일 즈음에 이메일을 확인해 보시는 것이 좋습니다.

- UNSUPPORTED: 방금 변경하셨다면 이번 달 청구서도 이메일로 받으실 수 있습니다.
  - 판단 이유: The FAQ sources explicitly state that the email billing change takes effect from the next month, not the current month. The claim suggests the current month's bill would be received via email, which contradicts the FAQ evidence.

- UNSUPPORTED: 변경 신청이 즉시 반영되지 않을 수도 있으니, 확인을 위해 청구서 발송 예정일 즈음에 이메일을 확인해 보시는 것이 좋습니다.
  - 판단 이유: The FAQ sources do not mention any delay in applying the change or suggest checking emails around the billing date. The claim introduces a new condition (delay) not supported by the evidence.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the question by indicating that the current month's bill may be received via email, but it does not clearly state that the change takes effect from the next month's bill, which is a required fact. It also mentions that the change may not be immediately reflected, which is not explicitly stated in the reference group.

**RAG에 전달된 실제 근거**

- BILLING-0063: 청구서 이메일로 받으려면 어떻게 해요

이메일로 바꿔달라고 하시면 다음 달부터 옵니다. 돈은 안 듭니다.

- BILLING-0049: 이메일 청구서로 전환하는 절차를 알려주세요.

수령 방법을 이메일로 변경 신청하면 다음 달 청구분부터 적용됩니다. 이메일은 무료입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 청구서 이메일로 받게 방금 바꿨는데 이번 달 거부터 바로 메일로 오나요?
  - BILLING-0014

다음 달 청구분부터 적용됩니다.

  - BILLING-0049

수령 방법을 이메일로 변경 신청하면 다음 달 청구분부터 적용됩니다. 이메일은 무료입니다.

  - BILLING-0063

이메일로 바꿔달라고 하시면 다음 달부터 옵니다. 돈은 안 듭니다.

  - BILLING-0035

이메일, 모바일, 우편 중 원하시는 방법으로 변경 신청하시면 됩니다. 변경은 다음 달 청구분부터 적용되며, 우편은 월 500원의 비용이 있습니다.

  - BILLING-0042

앱이나 고객센터에서 수령 방법을 이메일로 변경하시면 다음 달 청구분부터 적용됩니다. 우편은 월 500원, 이메일은 무료입니다.

  - BILLING-0119

변경은 다음 달 청구분부터 적용됩니다. 이번 달 우편은 월 500원이 부과됩니다.

## V2-SINGLE-A010 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 이번 달 요금 깜빡하고 못 냈는데 얼마나 지나면 폰 끊겨요?

**저장된 답변**

로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim mentions checking the contract period and penalty for cancellation after logging in, but there is no corresponding information in the provided FAQ sources to support this statement.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim suggests viewing all plans below if you have questions about the plan, but there is no corresponding information in the provided FAQ sources to support this statement.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not address the specific question about when the phone will be disconnected due to missed payments. It instead refers the user to login to check the contract period and penalty, which is unrelated to the specific timeline requested in the question.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 이번 달 요금 깜빡하고 못 냈는데 얼마나 지나면 폰 끊겨요?
  - BILLING-0003

납부 기한이 지나고 15일이 되면 발신이 정지됩니다. 30일이면 수신까지, 60일이면 직권 해지될 수 있습니다.

  - BILLING-0094

납부 기한이 지나고 15일이 되면 발신이 정지됩니다. 30일이 지나면 수신까지 정지되고, 60일이 지나면 직권 해지될 수 있습니다. 밀린 요금을 모두 내시면 2시간 안에 다시 이용하실 수 있습니다.

## V2-SINGLE-A012 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 이번 달 중간에 요금제 바꾸면 요금은 어떻게 나와요?

**저장된 답변**

로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim about confirming the contract period and penalty for termination upon login is not directly supported by any provided FAQ evidence.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim about viewing all plans is not directly supported by any provided FAQ evidence.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not address the specific question about how the bill would be calculated if the plan is changed mid-month. Instead, it refers the user to login to check the contract period and penalty, which does not directly answer the question about billing calculation.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 이번 달 중간에 요금제 바꾸면 요금은 어떻게 나와요?
  - BILLING-0008

변경 신청일 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다.

  - BILLING-0071

변경은 신청일 다음 날 00:00부터 적용되고 그 달 요금은 일할 계산되어 청구서에 반영됩니다.

  - BILLING-0148

변경은 신청일 다음 날 00:00부터 적용되고, 그 달 요금은 일할 계산됩니다.

  - BILLING-0001

요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.

  - BILLING-0127

요금제를 바꾸셨으면 다음 날 00:00부터 바뀌고 그 달은 날짜대로 나눠서 계산됩니다.

## V2-SINGLE-A028 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 명의 넘겨받으려는데 신용심사 거절 떴어요. 명의변경에 왜 심사를 하는 거예요?

**저장된 답변**

신용 심사는 명의변경 과정에서 양수인의 신용 상태를 확인하기 위한 절차입니다. 따라서 신용 심사에서 거절되면 명의변경이 일시적으로 제한될 수 있습니다. 심사를 통과하시면 명의변경 신청을 다시 진행할 수 있습니다.

- UNSUPPORTED: 신용 심사는 명의변경 과정에서 양수인의 신용 상태를 확인하기 위한 절차입니다.
  - 판단 이유: The claim states that the credit check is to verify the buyer's credit status during the name change process. However, the FAQ sources do not explicitly mention that the purpose of the credit check is to verify the buyer's credit status. The sources only mention that a credit check is required for name change when there is remaining installment, and that failure to pass the check may prevent the process. The purpose of the check is not explicitly stated.

- UNSUPPORTED: 따라서 신용 심사에서 거절되면 명의변경이 일시적으로 제한될 수 있습니다.
  - 판단 이유: The claim states that a rejected credit check may temporarily restrict the name change. However, the FAQ sources do not mention that the restriction is temporary. They only state that the name change cannot proceed if the credit check is failed, but they do not specify whether this is a temporary restriction or a permanent one.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that a credit check is performed to assess the buyer's credit status during the name change process, and that a failed credit check may temporarily restrict the name change. It also states that if the credit check is passed, the name change application can be re-submitted, which aligns with the required facts.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0063: 명의변경 신청했는데 신용 심사에서 거절됐습니다

할부금이 남아 있는 경우 양수인의 신용 심사를 통과해야 명의변경이 가능합니다. 심사 결과에 따라 진행이 어려울 수 있습니다.

- NAME_CHANGE-0021: 신용 심사에서 떨어지면 명의변경이 아예 안 되나요?

네, 심사를 통과하지 못하면 명의변경이 진행되지 않습니다. 통과하신 뒤에 다시 신청하실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 명의 넘겨받으려는데 신용심사 거절 떴어요. 명의변경에 왜 심사를 하는 거예요?
  - NAME_CHANGE-0063

할부금이 남아 있는 경우 양수인의 신용 심사를 통과해야 명의변경이 가능합니다. 심사 결과에 따라 진행이 어려울 수 있습니다.

  - NAME_CHANGE-0057

할부금이 남아 있으면 양수인의 신용 심사를 통과해야 합니다. 심사 결과에 따라 진행이 어려울 수 있으니 할부금을 정리한 뒤 다시 시도해 보세요.

## V2-SINGLE-A037 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 데이터 거의 안 쓰는 편인데 저렴한 요금제 추천 좀

**저장된 답변**

사용량을 알려주시면 맞는 요금제를 추천해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides a general statement about recommending a plan based on usage but does not explicitly mention the specific low-cost plans (세이브, 미니) that are required by the question. It refers the user to a general list of plans rather than directly answering the request for specific low-cost options.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 데이터 거의 안 쓰는 편인데 저렴한 요금제 추천 좀
  - PLAN-0081

알뜰 요금제가 2가지 있습니다. 세이브랑 미니예요.

  - PLAN-0026

세이브랑 미니, 두 가지가 있는 저렴한 요금제입니다.

## V2-SINGLE-A040 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 알뜰 요금제 중에 5G 되는 것도 있어요?

**저장된 답변**

알뜰 요금제 중에는 5G 서비스를 지원하는 옵션이 없습니다. 알뜰 요금제는 세이브와 미니 두 가지로 구성되어 있으며, 현재 제공 중인 알뜰 요금제는 LTE 서비스에 한정되어 있습니다. 5G 서비스를 이용하시려면 다른 요금제를 고려해 보시는 것이 좋습니다.

- UNSUPPORTED: 현재 제공 중인 알뜰 요금제는 LTE 서비스에 한정되어 있습니다.
  - 판단 이유: FAQ 답변에서 알뜰 요금제가 LTE 서비스에 한정되어 있다는 명시는 없습니다. 알뜰 요금제가 LTE 요금제와 구분되어 있다는 점만 언급되어 있을 뿐, 서비스 유형에 대한 구체적인 설명은 없습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that there are no 5G options in the '알뜰' (frugal) plans, which are composed of '세이브' and '미니' (Save and Mini). It also correctly states that the '알뜰' plans are limited to LTE service and recommends considering other plans for 5G service. This fully addresses the required facts.

**RAG에 전달된 실제 근거**

- PLAN-0066: 알뜰 요금제로 5G를 쓸 수 있어요?

알뜰 요금제는 세이브·미니 2종이고, 5G 요금제는 별도로 4종이 있습니다.

- PLAN-0021: 요금제가 다 해서 몇 가지예요

5G가 4가지, LTE가 3가지, 알뜰이 2가지 있습니다.

- PLAN-0041: 5G, LTE, 알뜰 요금제 구성을 순서대로 안내해 주세요.

5G는 라이트·스탠다드·프리미엄·언리미티드 4종, LTE는 베이직·플러스·맥스 3종, 알뜰은 세이브·미니 2종으로 구성되어 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 알뜰 요금제 중에 5G 되는 것도 있어요?
  - PLAN-0066

알뜰 요금제는 세이브·미니 2종이고, 5G 요금제는 별도로 4종이 있습니다.

  - PLAN-0041

5G는 라이트·스탠다드·프리미엄·언리미티드 4종, LTE는 베이직·플러스·맥스 3종, 알뜰은 세이브·미니 2종으로 구성되어 있습니다.

  - PLAN-0036

5G 4종(라이트·스탠다드·프리미엄·언리미티드), LTE 3종(베이직·플러스·맥스), 알뜰 2종(세이브·미니)이 있습니다.

  - PLAN-0126

알뜰 요금제(세이브·미니 2종)가 저렴한 편이고, 5G는 4종으로 상위 등급입니다.

## V2-SINGLE-A041 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 엄마한테 데이터 선물하려는데 한 번에 몇 기가까지 돼?

**저장된 답변**

요금제 안내예요.

[5G]
- 5G 언리미티드: 월 105,000원 · 데이터 무제한
- 5G 프리미엄: 월 85,000원 · 데이터 150GB
- 5G 스탠다드: 월 65,000원 · 데이터 50GB
- 5G 라이트: 월 45,000원 · 데이터 8GB
- 5G 청소년: 월 55,000원 · 데이터 80GB
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 맥스: 월 69,000원 · 데이터 100GB
- LTE 플러스: 월 49,000원 · 데이터 15GB
- LTE 베이직: 월 33,000원 · 데이터 4GB
- LTE 청소년: 월 39,000원 · 데이터 20GB
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 세이브: 월 25,000원 · 데이터 3GB
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- UNSUPPORTED: 5G 언리미티드: 월 105,000원 · 데이터 무제한
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 프리미엄: 월 85,000원 · 데이터 150GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 스탠다드: 월 65,000원 · 데이터 50GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 라이트: 월 45,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 청소년: 월 55,000원 · 데이터 80GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 맥스: 월 69,000원 · 데이터 100GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 플러스: 월 49,000원 · 데이터 15GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 베이직: 월 33,000원 · 데이터 4GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 청소년: 월 39,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 세이브: 월 25,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: No FAQ source directly supports this claim.

- 하위 질문 1: MISSED
  - 판단 이유: The provided answer does not address the specific question about data gift limits. It provides information about different plans but does not mention the maximum data that can be gifted in one transaction or the monthly limit for data gifts.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 엄마한테 데이터 선물하려는데 한 번에 몇 기가까지 돼?
  - PLAN-0003

한 번에 최대 2GB, 한 달에 2회까지 보낼 수 있습니다.

  - PLAN-0103

데이터 선물은 한 달에 2회, 회당 최대 2GB까지 가능합니다. 이번 달에 이미 2회를 보내셨다면 다음 달까지 기다리셔야 합니다.

  - PLAN-0023

데이터 선물하기는 한 달에 2회, 한 번에 최대 2GB까지 가능합니다. 쓰지 않은 데이터는 잔여량의 50%까지 다음 달로 이월됩니다.

  - PLAN-0043

앱 또는 홈페이지의 데이터 선물 메뉴에서 신청합니다. 월 2회, 회당 최대 2GB까지 가능합니다.

  - PLAN-0038

앱이나 홈페이지에서 받는 분 번호와 용량을 입력하면 됩니다. 한 달에 2회, 회당 최대 2GB입니다.

  - PLAN-0063

월 2회까지만 가능합니다. 회당 최대 2GB입니다.

## V2-SINGLE-A046 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 알뜰요금제가 정확히 뭐예요? 종류는 몇 개?

**저장된 답변**

판매 중인 요금제는 총 4종이에요.

[알뜰]
- 알뜰 세이브: 월 25,000원 · 데이터 3GB
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- UNSUPPORTED: 판매 중인 요금제는 총 4종이에요.
  - 판단 이유: The claim states there are 4 types of plans, but the FAQ sources are not provided to confirm this.

- UNSUPPORTED: - 알뜰 세이브: 월 25,000원 · 데이터 3GB
  - 판단 이유: The specific details of the plan are not confirmed by the provided FAQ sources.

- UNSUPPORTED: - 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: The specific details of the plan are not confirmed by the provided FAQ sources.

- UNSUPPORTED: - 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: The specific details of the plan are not confirmed by the provided FAQ sources.

- UNSUPPORTED: - 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: The specific details of the plan are not confirmed by the provided FAQ sources.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: The age restrictions for the plans are not confirmed by the provided FAQ sources.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states that there are 4 types of 알뜰 요금제 (알뜰 세이브, 알뜰 미니, 알뜰 청소년, 알뜰 시니어) and provides their details. However, it does not explicitly state that 알뜰 요금제 is a '저렴한 요금제' (cheap plan) as required. It also does not explicitly mention that there are only two types (세이브 and 미니) as stated in the required facts. The answer includes additional information about age restrictions, which is not required.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 알뜰요금제가 정확히 뭐예요? 종류는 몇 개?
  - PLAN-0026

세이브랑 미니, 두 가지가 있는 저렴한 요금제입니다.

## V2-SINGLE-A047 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 청소년이나 시니어 요금제 말고 일반 요금제는 몇 종류예요?

**저장된 답변**

판매 중인 요금제는 총 6종이에요.

[5G]
- 5G 청소년: 월 55,000원 · 데이터 80GB
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 청소년: 월 39,000원 · 데이터 20GB
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- UNSUPPORTED: 판매 중인 요금제는 총 6종이에요.
  - 판단 이유: The claim states there are 6 types of plans available, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: 5G 청소년: 월 55,000원 · 데이터 80GB
  - 판단 이유: The claim provides specific details about a 5G youth plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: The claim provides specific details about a 5G senior plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: LTE 청소년: 월 39,000원 · 데이터 20GB
  - 판단 이유: The claim provides specific details about an LTE youth plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: The claim provides specific details about an LTE senior plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: The claim provides specific details about a budget youth plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: The claim provides specific details about a budget senior plan, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: The claim provides specific age requirements for youth and senior plans, but the provided FAQ sources are empty, so there is no evidence to support this claim.

- 하위 질문 1: MISSED
  - 판단 이유: The answer states there are 6 types of plans in total, but the required fact specifies that general plans consist of 5G (4 types), LTE (3 types), and 알뜰 (2 types), totaling 9 types. The answer does not provide the correct count of general plans and instead mentions the total number of plans, which includes age-restricted plans. The required fact about age-restricted plans being a separate policy is also not addressed.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 청소년이나 시니어 요금제 말고 일반 요금제는 몇 종류예요?
  - PLAN-0136

일반 라인업은 5G 4종, LTE 3종, 알뜰 2종입니다. 연령 제한 요금제는 별도 정책으로 운영됩니다.

## V2-SINGLE-A048 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 제일 저렴한 요금제 얼마예요? 데이터는 얼마나 줘요

**저장된 답변**

일반 요금제 중 가장 저렴한 요금제예요.

[알뜰]
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB

- UNSUPPORTED: 일반 요금제 중 가장 저렴한 요금제예요.
  - 판단 이유: The claim states that this is the cheapest general plan, but the provided FAQ sources are empty, so there is no evidence to support this assertion.

- UNSUPPORTED: [알뜰]
  - 판단 이유: The claim '[알뜰]' is too vague and lacks specific context or details to be supported by the provided FAQ sources, which are empty.

- UNSUPPORTED: - 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: The claim provides specific details about the '알뜰 미니' plan, but the provided FAQ sources are empty, so there is no evidence to support this assertion.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer correctly states that the cheapest plan is 알뜰 미니 at 15,000 won per month with 1.5GB of data, which matches the required facts.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 제일 저렴한 요금제 얼마예요? 데이터는 얼마나 줘요
  - PLAN-0065

네, 알뜰 미니 요금제가 월 15,000원으로 가장 저렴하며 기본 데이터 1.5GB가 제공됩니다.

  - PLAN-0115

가장 저렴한 알뜰 미니 요금제는 월 15,000원에 1.5GB가 제공됩니다. 매장에서 사용 패턴에 맞는 요금제를 안내해 드립니다.

  - PLAN-0145

가장 저렴한 알뜰 미니는 월 15,000원에 1.5GB이고, 가장 비싼 5G 언리미티드는 월 105,000원에 데이터 무제한입니다.

  - PLAN-0140

가장 저렴한 알뜰 미니는 15,000원에 1.5GB, 가장 비싼 5G 언리미티드는 105,000원에 무제한입니다.

## V2-SINGLE-A049 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 번이 신청한 지 반나절 됐는데 아직도 안 넘어왔어요

**저장된 답변**

반나절이면 일반적으로 신청 처리 시간 범위 내에 있습니다. 보통 신청 후 1시간 이내에 처리가 시작되지만, 상황에 따라 약간의 차이가 있을 수 있습니다. 조금 더 기다려 주시고, 만약 여전히 진행 상황이 걱정되신다면 고객센터에 직접 문의해 보시는 것을 추천드립니다.

- UNSUPPORTED: 반나절이면 일반적으로 신청 처리 시간 범위 내에 있습니다.
  - 판단 이유: The claim states that half a day (반나절) is generally within the processing time range. However, the FAQ sources indicate that processing can take up to 24 hours (PORTING-0077) and that processing typically occurs within 1 hour (ROAMING-0062). Since half a day (12 hours) is significantly longer than the 1-hour timeframe mentioned in ROAMING-0062, this claim is not supported by the provided FAQ evidence.

- UNSUPPORTED: 고객센터에 직접 문의해 보시는 것을 추천드립니다.
  - 판단 이유: None of the provided FAQ sources explicitly recommend contacting the customer service center. While PORTING-0077 mentions contacting the center if 24 hours have passed, it does not generalize this as a recommendation for all cases.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer mentions that processing typically starts within 1 hour and suggests waiting a bit longer or contacting customer service if concerned. However, it does not explicitly state the average processing time of 2 hours or the maximum of 24 hours, nor does it explicitly mention contacting customer service after 24 hours. Only part of the required facts are addressed.

**RAG에 전달된 실제 근거**

- ROAMING-0062: 신청했는데 아직 안 돼요

신청하면 1시간 안에 됩니다. 조금만 기다려 주세요.

- PORTING-0077: 신청한 지 하루가 넘었는데 아직도 안 됐어요

번호이동은 늦어도 24시간 이내에 처리됩니다. 24시간이 지났다면 고객센터에 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 번이 신청한 지 반나절 됐는데 아직도 안 넘어왔어요
  - PORTING-0073

평균 처리 시간은 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 조금 더 기다려 주시고, 24시간이 지나도 안 되면 고객센터로 문의해 주세요.

  - PORTING-0081

평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.

## V2-SINGLE-A050 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 새 폰 개통한 지 한 달 됐는데 통신사 옮기려니까 안 된대요 왜죠

**저장된 답변**

새 폰을 개통하신 지 한 달밖에 안 되셨기 때문에 아직 번호 이동이 불가능합니다. LG U+ 정책에 따르면 번호 이동은 개통 후 3개월이 경과해야 가능합니다. 따라서 한 달이 지난 현재로서는 번호 이동 신청이 제한되고 있습니다. 3개월이 지나면 신청하실 수 있으니 참고해 주세요.

- UNSUPPORTED: 새 폰을 개통하신 지 한 달밖에 안 되셨기 때문에 아직 번호 이동이 불가능합니다.
  - 판단 이유: The claim states that the user has been with the service for only one month, but the FAQ sources specify a 3-month waiting period, not 1 month. The claim's condition (1 month) differs from the policy (3 months).

- UNSUPPORTED: 따라서 한 달이 지난 현재로서는 번호 이동 신청이 제한되고 있습니다.
  - 판단 이유: The claim states that after one month, porting is restricted, but the FAQ sources specify a 3-month restriction, not 1 month. The condition (1 month) differs from the policy (3 months).

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that number porting is not possible within the first 3 months of service activation, which directly addresses the user's question about why they cannot switch carriers after one month. It also mentions the 3-month waiting period as per LG U+ policy, fulfilling the required fact.

**RAG에 전달된 실제 근거**

- PORTING-0091: 새 폰 샀는데 번호를 못 옮긴대요

개통한 지 3개월이 안 돼서 그렇습니다. 3개월 지나면 됩니다.

- PORTING-0111: 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요.

개통한 지 3개월이 지나야 다른 통신사로 번호이동이 가능합니다. 두 달째라면 아직 신청하실 수 없고, 3개월이 지난 뒤에 신청하셔야 합니다. 명의를 변경하신 경우에는 변경일로부터 1개월이 지나야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 새 폰 개통한 지 한 달 됐는데 통신사 옮기려니까 안 된대요 왜죠
  - PORTING-0091

개통한 지 3개월이 안 돼서 그렇습니다. 3개월 지나면 됩니다.

  - PORTING-0111

개통한 지 3개월이 지나야 다른 통신사로 번호이동이 가능합니다. 두 달째라면 아직 신청하실 수 없고, 3개월이 지난 뒤에 신청하셔야 합니다. 명의를 변경하신 경우에는 변경일로부터 1개월이 지나야 합니다.

  - PORTING-0075

기존 회선 개통 후 3개월이 지나야 합니다. 3개월이 되면 다시 신청해 주세요.

  - PORTING-0087

제한은 개통일 기준 3개월, 명의변경일 기준 1개월입니다. 두 날짜를 확인해 보세요.

## V2-SINGLE-A053 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, HUMAN_REVIEW
- 질문: 번호이동 밤에도 처리돼요?

**저장된 답변**

네, 번호 이동 처리는 매일 저녁 6시부터 8시까지 포함하여 저녁 늦게에도 가능합니다.

- UNSUPPORTED: 네, 번호 이동 처리는 매일 저녁 6시부터 8시까지 포함하여 저녁 늦게에도 가능합니다.
  - 판단 이유: The claim states that number porting is possible from 6 PM to 8 PM in the evening, but the FAQ sources explicitly state that processing is only available from 9:00 AM to 8:00 PM. The claim incorrectly expands the processing window to include 6 PM to 8 PM, which is not supported by the provided FAQ evidence.

- 하위 질문 1: MISSED
  - 판단 이유: The answer incorrectly states that number porting is possible until 8 PM, while the reference materials explicitly state that processing is not available from 8 PM to 9 AM the next day. Additionally, the answer does not mention the average processing time of 2 hours within the processing window, which is a required fact.

**RAG에 전달된 실제 근거**

- PORTING-0049: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?

번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

- PORTING-0065: 토요일에도 번호이동 되나요

네, 매일 09:00부터 20:00까지 처리됩니다.

- PORTING-0013: 번호이동 처리 가능 시간대를 알려주세요.

매일 09:00부터 20:00까지입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 번호이동 밤에도 처리돼요?
  - PORTING-0005

네, 20:00 이후부터 다음 날 09:00 전까지는 처리되지 않습니다. 처리 시간 안에는 평균 2시간 이내에 완료됩니다.

  - PORTING-0085

처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안이라면 평균 2시간 이내에 완료됩니다.

  - PORTING-0049

번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

## V2-SINGLE-A055 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 번호이동 오전에 신청하는 게 나아요? 퇴근하고 저녁에 해도 상관없나

**저장된 답변**

오전에 신청하시든 저녁에 신청하시든, 번호이동 처리 시간은 09:00부터 20:00 사이에 이루어지며 평균 2시간 이내에 완료됩니다. 늦어도 24시간 이내에는 처리가 완료되니 시간대에 상관없이 원하시는 시간에 신청하시면 됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys all required facts: the processing time is between 09:00 and 20:00, the average processing time is within 2 hours, and the processing is guaranteed to be completed within 24 hours regardless of the time of application. It also addresses the user's question about whether applying in the morning or evening matters, stating that it does not.

**RAG에 전달된 실제 근거**

- PORTING-0049: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?

번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

- PORTING-0057: 당일에 바로 번호이동이 될 수 있나요?

09:00부터 20:00 사이에 신청하시면 평균 2시간 이내에 처리되어 당일 완료가 가능합니다.

- PORTING-0025: 요금이 비싸서 옮기려는데 번호이동 절차가 어떻게 돼요?

신분증을 가지고 신청하시면 09:00~20:00 사이에 처리됩니다. 보통 2시간 이내, 늦어도 24시간 안에 완료됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 번호이동 오전에 신청하는 게 나아요? 퇴근하고 저녁에 해도 상관없나
  - PORTING-0097

처리는 09:00부터 20:00까지라 아침 신청이 당일에 끝날 가능성이 큽니다. 평균 2시간 이내 처리입니다.

## V2-SINGLE-A060 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 번호이동 하려다가 미납 때문에 거절당함 ㅠ 어떻게 해야 함

**저장된 답변**

미납 요금을 완납하신 후 다시 번호이동 신청을 진행해 주세요. 완납 확인 후 본인 신분증을 지참하여 신청하시면 됩니다. 미납 해결 후에도 문제가 지속된다면, 기존 통신사에 직접 연락하여 완납 처리 확인을 요청하시는 것도 도움이 될 수 있습니다.

- UNSUPPORTED: 미납 해결 후에도 문제가 지속된다면, 기존 통신사에 직접 연락하여 완납 처리 확인을 요청하시는 것도 도움이 될 수 있습니다.
  - 판단 이유: None of the provided FAQ answers mention contacting the existing carrier directly to confirm payment processing.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys both required facts: that the user must settle any outstanding fees with their previous carrier and that they need to present their personal identification when reapplying for number porting. The answer also includes additional helpful information about contacting the previous carrier if the issue persists, which is not required but does not detract from the completeness of the answer.

**RAG에 전달된 실제 근거**

- PORTING-0086: 미납 요금을 완납했는데도 번호이동이 거절됩니다

완납 처리가 기존 통신사에 반영됐는지 확인해 주세요. 반영 후 본인 신분증으로 다시 신청하시면 됩니다.

- PORTING-0074: 번호이동 신청했는데 미납 요금 때문에 막혔대요

기존 통신사 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청 시에는 본인 신분증이 필요합니다.

- PORTING-0082: 번호이동을 신청했는데 진행이 안 된다고 합니다. 원인이 뭘까요?

기존 통신사에 미납 요금이 있으면 번호이동이 진행되지 않습니다. 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청하실 때는 본인 신분증이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 번호이동 하려다가 미납 때문에 거절당함 ㅠ 어떻게 해야 함
  - PORTING-0074

기존 통신사 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청 시에는 본인 신분증이 필요합니다.

  - PORTING-0078

기존 통신사 미납 요금이 남아 있는지 확인해 주세요. 완납 후 본인 신분증으로 다시 신청하시면 됩니다.

  - PORTING-0082

기존 통신사에 미납 요금이 있으면 번호이동이 진행되지 않습니다. 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청하실 때는 본인 신분증이 필요합니다.

  - PORTING-0034

먼저 기존 통신사 미납 요금을 완납한 뒤, 본인 신분증을 지참해 신청하시면 됩니다.

## V2-SINGLE-A074 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 부가서비스 하나 해지하려는데 이번 달 요금은 빠지나요? 해지하면 바로 못 쓰게 되는 거예요?

**저장된 답변**

1. 이번 달 요금은 빠지나요?
로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

2. 해지하면 바로 못 쓰게 되는 거예요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not supported by any provided FAQ sources. The statement about being able to check usage and estimated charges after logging in is not mentioned in the provided FAQ sources.

- UNSUPPORTED: 지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.
  - 판단 이유: The claim is not supported by any provided FAQ sources. The statement about not being able to provide accurate guidance with the currently available information is not mentioned in the provided FAQ sources.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the question by indicating that the user can check their usage and estimated fees after logging in, but it does not explicitly state that the service can be used until the end of the month after cancellation or that the monthly fee is not prorated. The second part of the answer refuses to provide a clear answer, which is not sufficient for the required facts.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 부가서비스 하나 해지하려는데 이번 달 요금은 빠지나요? 해지하면 바로 못 쓰게 되는 거예요?
  - SERVICE-0031

해지를 신청하셔도 해당 월 말일까지는 이용하실 수 있고, 월 이용료는 일할 계산되지 않아 그 달 요금은 그대로 청구됩니다. 가입은 신청 즉시 적용됩니다.

  - SERVICE-0016

가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.

  - SERVICE-0056

가입은 신청 즉시 적용되고, 해지는 그 달 말일까지 이용하면서 월 이용료가 일할 계산 없이 전액 청구됩니다.

  - SERVICE-0021

해지를 신청하면 해당 월 말일까지 이용 상태가 유지되고, 그 달의 월 이용료는 일할 계산 없이 전액 청구됩니다. 다음 달부터 청구되지 않습니다.

## V2-SINGLE-A080 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 부가서비스 해지했는데 청구서에 또 찍혀 있네요

**저장된 답변**

해지한 달의 요금은 일할 계산되어 청구되기 때문에 다음 달 청구서로 반영되지 않고 현재 청구서로 확인되신 것 같습니다. 다음 달 청구부터는 해당 부가서비스 요금이 나타나지 않을 것입니다. 추가 확인이 필요하시면 청구서 기간을 확인해 주세요.

- UNSUPPORTED: 해지한 달의 요금은 일할 계산되어 청구되기 때문에 다음 달 청구서로 반영되지 않고 현재 청구서로 확인되신 것 같습니다
  - 판단 이유: The claim states that the fee for the month of cancellation is calculated on a daily basis and billed in the current invoice, but the FAQ source states that the monthly fee for the month of cancellation is not prorated and is billed as is. This directly contradicts the claim.

- IRRELEVANT: 추가 확인이 필요하시면 청구서 기간을 확인해 주세요
  - 판단 이유: This claim is a recommendation or suggestion for the user to check the billing period, which is not directly supported or contradicted by the FAQ source.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states that the fee for the month of cancellation is prorated and appears on the current bill, but it incorrectly states that the fee will not appear on the next bill. The reference explicitly states that the fee will not appear on the next bill, which is not reflected in the answer.

**RAG에 전달된 실제 근거**

- SERVICE-0051: 부가서비스 해지했는데 요금이 또 나왔어요

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 부가서비스 해지했는데 청구서에 또 찍혀 있네요
  - SERVICE-0051

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

## V2-SINGLE-A093 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 제 명의로 회선 몇 개까지 만들 수 있어요? 법인 명의는요?

**저장된 답변**

개인 명의로는 최대 5개의 회선까지 개통이 가능합니다. 법인 명의의 경우, 회선 수 제한 대신 별도의 심사를 거쳐 진행되므로, 정확한 개수는 심사 결과에 따라 달라집니다. 추가 회선 개설을 원하신다면 해당 절차를 통해 확인하실 수 있습니다.

- UNSUPPORTED: 추가 회선 개설을 원하신다면 해당 절차를 통해 확인하실 수 있습니다.
  - 판단 이유: The claim is not directly supported by any of the provided FAQ answers. None of the answers mention a specific procedure or process for additional line installation.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys both required facts: that an individual can have up to 5 lines and that corporate names require a separate review. The answer is complete and matches the reference group's information.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0064: 법인 명의로 회선을 여러 개 개통할 수 있을까요?

가능합니다. 법인 명의는 심사를 거쳐 진행되며, 개인처럼 정해진 회선 수 제한을 적용받지 않습니다.

- SUBSCRIBE-0116: 개인 명의와 법인 명의는 개통 가능한 회선 수 기준이 다른가요?

개인 명의는 1인당 최대 5회선까지 개통할 수 있습니다. 법인 명의는 이 한도 대신 별도 심사를 거쳐 진행됩니다.

- SUBSCRIBE-0056: 회선을 하나 더 만들 수 있을까요?

성인 1인당 최대 5회선까지 개통할 수 있어 그 한도 안이라면 가능합니다. 법인 명의로 하시는 경우에는 별도 심사를 거칩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 제 명의로 회선 몇 개까지 만들 수 있어요? 법인 명의는요?
  - SUBSCRIBE-0112

성인은 1인당 최대 5회선입니다. 법인 명의는 별도 심사로 정해집니다.

  - SUBSCRIBE-0056

성인 1인당 최대 5회선까지 개통할 수 있어 그 한도 안이라면 가능합니다. 법인 명의로 하시는 경우에는 별도 심사를 거칩니다.

  - SUBSCRIBE-0116

개인 명의는 1인당 최대 5회선까지 개통할 수 있습니다. 법인 명의는 이 한도 대신 별도 심사를 거쳐 진행됩니다.

  - SUBSCRIBE-0044

법인 명의 가입은 별도 심사를 거쳐 진행됩니다. 개인 회선은 1인당 최대 5회선입니다.

  - SUBSCRIBE-0124

개인은 최대 5회선까지 가능합니다. 회사 이름으로 하시면 5회선 한도 대신 별도 심사를 거쳐 진행됩니다.

  - SUBSCRIBE-0100

한 사람이 가질 수 있는 회선은 최대 5회선까지입니다. 이미 다 채우셨다면 법인 명의로 별도 심사를 받아야 합니다.

## V2-SINGLE-A098 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 약정 11개월차인데 지금 해지하면 위약금 전부 내야 돼요?

**저장된 답변**

로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not directly supported by any provided FAQ sources. The statement about confirming the contract period and penalty upon termination requires specific policy details that are not present in the given FAQ sources.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim is not directly supported by any provided FAQ sources. The statement about viewing all plans requires specific policy details that are not present in the given FAQ sources.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not directly address the specific question about whether the user would have to pay the full penalty for terminating the contract at 11 months. Instead, it provides general guidance to check the details after logging in, which does not resolve the factual question asked.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 약정 11개월차인데 지금 해지하면 위약금 전부 내야 돼요?
  - TERMINATE-0058

네, 12개월 미만이라 할인 반환금의 100%를 반환하셔야 합니다.

  - TERMINATE-0110

12개월 전에 해지하면 반환금 100%를 내야 하고, 12개월이 지난 뒤라면 50%만 내시면 됩니다.

  - TERMINATE-0114

12개월 넘기면 절반으로 줄어듭니다. 그 전이면 할인 받은 거 전부 냅니다.

  - TERMINATE-0006

약정 24개월 중 12개월 전에 해지하면 할인 반환금 100%, 12개월 이후라면 50%를 내셔야 합니다.

  - TERMINATE-0042

가입하고 12개월이 안 됐으면 할인받은 금액 전부(100%)를, 12개월이 넘었으면 절반(50%)을 내시면 됩니다. 약정은 24개월입니다.

  - TERMINATE-0102

약정 기간은 24개월입니다. 12개월이 되기 전에 해지하시면 받으신 요금 할인액의 100%를 반환해야 하고, 12개월 이상 24개월 미만이면 50%를 반환합니다. 같은 해지라도 12개월을 넘겼는지에 따라 반환금이 절반으로 줄어듭니다.

## V2-SINGLE-A099 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 해지할 때 남은 기계값 한방에 내는 게 나아요 그냥 나눠 내는 게 나아요

**저장된 답변**

해지 시 남은 기계값은 원칙적으로 한 번에 내는 것이 좋습니다. 어떤 방법이 더 적합하신지는 개인의 상황에 따라 다르니, 현재 상황을 고려해 선택하시면 됩니다.

- UNSUPPORTED: 어떤 방법이 더 적합하신지는 개인의 상황에 따라 다르니, 현재 상황을 고려해 선택하시면 됩니다.
  - 판단 이유: None of the provided FAQ sources mention that the choice depends on individual circumstances or suggest considering the user's current situation when deciding how to pay the remaining machine value.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer mentions that it is generally better to pay the remaining machine value in one lump sum, which aligns with one of the required facts. However, it does not explicitly state that installment payments can continue according to the existing schedule, which is another required fact. The answer is somewhat generic and does not fully address the specific required facts.

**RAG에 전달된 실제 근거**

- TERMINATE-0115: 요금 부담돼서 해지하는데 할부 한 번에 내는 게 나아요

원칙은 한 번에 내는 겁니다. 부담되시면 계속 나눠 내는 것도 됩니다.

- TERMINATE-0047: 안 쓰는 폰 해지할 때 할부 남은 건 어떻게 해요

한 번에 내시면 됩니다. 원하시면 해지 뒤에도 그대로 나눠 내실 수 있습니다.

- TERMINATE-0023: 폰 잃어버렸는데 남은 할부는 어떻게 해요

한 번에 내시는 게 원칙입니다. 원하시면 해지하고도 나눠 내실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 해지할 때 남은 기계값 한방에 내는 게 나아요 그냥 나눠 내는 게 나아요
  - TERMINATE-0095

일시 납부는 그 자리에서 정리가 끝나고, 할부 유지는 기존 일정대로 계속 나눠 내실 수 있습니다. 편하신 쪽으로 선택하시면 됩니다.

## V2-SINGLE-A101 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 이민 가게 돼서 폰 해지하려는데 어디서 신청하고 언제 끊겨요?

**저장된 답변**

1. 해지하려는데 어디서 신청
해지하실 수 있는 곳은 매장, 고객센터, 또는 홈페이지입니다. 편하신 경로를 선택하시면 됩니다.

2. 언제 끊겨요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.
  - 판단 이유: None of the provided FAQ answers mention that the information available now is insufficient to provide an exact answer.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly identifies where to apply for termination (store, customer service, or website), but it does not mention that termination occurs immediately upon application or that the termination month's fee is calculated on a daily basis. These are required facts that are missing from the answer.

**RAG에 전달된 실제 근거**

- TERMINATE-0041: 해지하려면 어디로 가야 해요

매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 신청하시면 바로 해지됩니다.

- TERMINATE-0025: 해지 신청 절차를 알려주세요.

매장, 고객센터, 홈페이지 중 편하신 경로로 신청하시면 즉시 해지 처리됩니다. 해지한 달의 요금은 일할 계산되어 청구됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 이민 가게 돼서 폰 해지하려는데 어디서 신청하고 언제 끊겨요?
  - TERMINATE-0009

매장, 고객센터, 홈페이지 어디서든 신청할 수 있고 신청 즉시 해지됩니다. 해지월 요금은 일할 계산됩니다.

  - TERMINATE-0025

매장, 고객센터, 홈페이지 중 편하신 경로로 신청하시면 즉시 해지 처리됩니다. 해지한 달의 요금은 일할 계산되어 청구됩니다.

## V2-SINGLE-A118 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 유심 깨졌어요. 분실신고 같은 걸 먼저 해야 되나 그냥 재발급받으면 되나 헷갈림

**저장된 답변**

1. 분실신고 같은 걸 먼저 해야 되나
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 그냥 재발급받으면 되나
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- 생성 기록: NO_EVIDENCE / None

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the user's question by stating that it is difficult to provide an accurate answer with the available information. However, it does not explicitly convey the required facts that 'you can get a new one' and 'reporting is for when you lose it.' The answer lacks the specific information needed to fully resolve the user's question.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 유심 깨졌어요. 분실신고 같은 걸 먼저 해야 되나 그냥 재발급받으면 되나 헷갈림
  - USIM-0112

새로 받으시면 됩니다. 신고는 잃어버렸을 때 하는 겁니다.

  - USIM-0104

분실 신고는 즉시 정지 조치이고, 재발급은 새 유심을 받으면서 기존 유심이 자동 해지되는 절차입니다. 잃어버린 게 아니라면 재발급만 하시면 됩니다.

  - USIM-0032

파손이면 분실 신고 없이 재발급만 받으시면 됩니다. 새 유심을 받으면 기존 유심은 자동으로 해지됩니다.

  - USIM-0108

잃어버린 것이 아니면 재발급으로 진행합니다. 새 유심을 받으면 기존 유심은 자동 해지됩니다.

  - USIM-0072

깨진 건 신고 안 하셔도 됩니다. 새로 받으시면 예전 건 자동으로 없어집니다.

## V2-SINGLE-A120 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 유심 바꾸러 제가 직접 가는 거랑 딸 보내는 거랑 챙길 서류 다른가요

**저장된 답변**

1. 유심 바꾸러 제가 직접 가는 거랑
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 딸 보내는 거랑
같이 가시면 신분증만 있으면 됩니다. 혼자 보내시려면 위임장을 써 주셔야 합니다.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요
  - 판단 이유: The claim is a general statement about the inability to provide precise guidance, but no FAQ source is provided to support this specific statement.

- UNSUPPORTED: 같이 가시면 신분증만 있으면 됩니다
  - 판단 이유: No FAQ source is provided to directly support this claim about requiring only an ID when going together.

- UNSUPPORTED: 혼자 보내시려면 위임장을 써 주셔야 합니다
  - 판단 이유: No FAQ source is provided to directly support this claim about needing a power of attorney when sending someone alone.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the question by stating that if the user goes in person, they only need their ID, which aligns with the required fact '본인이 가면 신분증만 있으면 된다'. However, it does not explicitly mention that if the daughter goes instead, a power of attorney and ID copies are needed, which is required by '자녀를 보내면 위임장과 신분증 사본까지 챙겨야 한다'. The answer also lacks clarity on the exact documents required for the daughter's case.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 유심 바꾸러 제가 직접 가는 거랑 딸 보내는 거랑 챙길 서류 다른가요
  - USIM-0114

제가 가시면 신분증만 있으면 됩니다. 자식을 보내면 위임장이랑 사본까지 챙겨야 합니다.

  - USIM-0034

본인 신분증만 지참하시면 됩니다. 대리인이 방문하면 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.

  - USIM-0026

본인이 가시면 신분증만 챙기시면 됩니다. 대신 가시는 분은 위임장과 대리인 신분증, 가입자 신분증 사본을 준비하셔야 합니다.

## V2-COMPOUND-C010 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 외국인 친구가 가입하려는데 여권 말고 뭐 필요해요? 제일 싼 요금제 얼마인지도 알려주세요

**저장된 답변**

1. 여권 말고 뭐 필요해요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 제일 싼 요금제 얼마인지도 알려주세요
일반 요금제 중 가장 저렴한 요금제예요.

[알뜰]
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 일반 요금제 중 가장 저렴한 요금제예요.
  - 판단 이유: The claim states that it is the cheapest general plan, but the provided FAQ evidence does not mention any comparison of plans or specify which plan is the cheapest. The evidence only lists the '알뜰 미니' plan with a price of 15,000 KRW and 1.5GB of data, but does not confirm it as the cheapest general plan.

- UNSUPPORTED: [알뜰]
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: The claim provides specific details about the '알뜰 미니' plan, but there is no corresponding FAQ evidence provided to confirm this information. The 'faqSources' array is empty, so there is no direct evidence to support this claim.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the required fact that a foreigner needs to present both the foreigner registration certificate and passport together to join.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer provides the required facts that the cheapest plan is the '알뜰 미니' at 15,000 won per month with 1.5GB of data.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: I'm foreigner. 여권만 있으면 가입 돼요?
  - SUBSCRIBE-0013

외국인등록증과 여권을 함께 지참하셔야 합니다.

  - SUBSCRIBE-0073

외국인이신 경우에는 외국인등록증과 여권을 함께 가져오셔야 개통이 가능합니다. 외국인등록증만으로는 어렵습니다.

  - SUBSCRIBE-0069

본인 명의 신분증이면 여권도 가능합니다. 외국인이면 외국인등록증과 여권을 함께 제시해야 합니다.

  - SUBSCRIBE-0033

외국인등록증과 여권을 함께 챙겨 매장을 방문하면 됩니다.

- 질문 부분: 제일 저렴한 요금제 얼마예요? 데이터는 얼마나 줘요
  - PLAN-0065

네, 알뜰 미니 요금제가 월 15,000원으로 가장 저렴하며 기본 데이터 1.5GB가 제공됩니다.

  - PLAN-0115

가장 저렴한 알뜰 미니 요금제는 월 15,000원에 1.5GB가 제공됩니다. 매장에서 사용 패턴에 맞는 요금제를 안내해 드립니다.

  - PLAN-0145

가장 저렴한 알뜰 미니는 월 15,000원에 1.5GB이고, 가장 비싼 5G 언리미티드는 월 105,000원에 데이터 무제한입니다.

  - PLAN-0140

가장 저렴한 알뜰 미니는 15,000원에 1.5GB, 가장 비싼 5G 언리미티드는 105,000원에 무제한입니다.

## V2-COMPOUND-C011 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 요금제 비싼 걸로 올리면 멤버십 등급도 바로 올라가나요? 이번 달 중간에 바꾸면 요금 계산은 어떻게 돼요?

**저장된 답변**

1. 요금제 비싼 걸로 올리면 멤버십 등급도 바로 올라가나요?
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 이번 달 중간에 바꾸면 요금 계산은 어떻게 돼요?
로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- 생성 기록: NO_EVIDENCE / None

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the required facts about membership grade updates and instead states it cannot provide accurate information.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer does not directly address the required facts about the new plan being applied the next day and the monthly fee being calculated on a daily basis. It instead redirects the user to login and view plans.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 올해 요금 많이 냈는데 멤버십 등급 바로 올라가요?
  - SERVICE-0032

바로 반영되지는 않습니다. 등급은 직전 1년 납부액을 기준으로 매년 1월에 한 번 갱신됩니다.

- 질문 부분: 이번 달 중간에 요금제 바꾸면 요금은 어떻게 나와요?
  - BILLING-0008

변경 신청일 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다.

  - BILLING-0071

변경은 신청일 다음 날 00:00부터 적용되고 그 달 요금은 일할 계산되어 청구서에 반영됩니다.

  - BILLING-0148

변경은 신청일 다음 날 00:00부터 적용되고, 그 달 요금은 일할 계산됩니다.

  - BILLING-0001

요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.

  - BILLING-0127

요금제를 바꾸셨으면 다음 날 00:00부터 바뀌고 그 달은 날짜대로 나눠서 계산됩니다.

## V2-COMPOUND-C017 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 데이터 별로 안 쓰는데 싼 요금제 뭐 있어요? 개통은 매장이랑 인터넷 중 어디가 빨라요?

**저장된 답변**

1. 데이터 별로 안 쓰는데 싼 요금제 뭐 있어요
요금제 안내예요.

[5G]
- 5G 언리미티드: 월 105,000원 · 데이터 무제한
- 5G 프리미엄: 월 85,000원 · 데이터 150GB
- 5G 스탠다드: 월 65,000원 · 데이터 50GB
- 5G 라이트: 월 45,000원 · 데이터 8GB
- 5G 청소년: 월 55,000원 · 데이터 80GB
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 맥스: 월 69,000원 · 데이터 100GB
- LTE 플러스: 월 49,000원 · 데이터 15GB
- LTE 베이직: 월 33,000원 · 데이터 4GB
- LTE 청소년: 월 39,000원 · 데이터 20GB
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 세이브: 월 25,000원 · 데이터 3GB
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

2. 개통은 매장이랑 인터넷 중 어디가 빨라요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 5G 언리미티드: 월 105,000원 · 데이터 무제한
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 프리미엄: 월 85,000원 · 데이터 150GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 스탠다드: 월 65,000원 · 데이터 50GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 라이트: 월 45,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 청소년: 월 55,000원 · 데이터 80GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 맥스: 월 69,000원 · 데이터 100GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 플러스: 월 49,000원 · 데이터 15GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 베이직: 월 33,000원 · 데이터 4GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 청소년: 월 39,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 세이브: 월 25,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: No FAQ source directly supports this claim.

**quality 검증 오류**: HTTPError: HTTP Error 400: Bad Request

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 데이터 거의 안 쓰는 편인데 저렴한 요금제 추천 좀
  - PLAN-0081

알뜰 요금제가 2가지 있습니다. 세이브랑 미니예요.

  - PLAN-0026

세이브랑 미니, 두 가지가 있는 저렴한 요금제입니다.

- 질문 부분: 개통 매장에서 하는 거랑 온라인으로 하는 거 중에 뭐가 더 빨라요?
  - SUBSCRIBE-0123

매장은 30분이면 됩니다. 인터넷은 다음 날 되니 매장이 더 빠릅니다.

  - SUBSCRIBE-0127

매장이 빠릅니다. 30분이면 됩니다. 인터넷은 다음 날 됩니다.

  - SUBSCRIBE-0011

매장에서 개통하면 30분 이내에 완료됩니다. 온라인으로 신청하면 다음 날 개통되며, 개통 처리는 매일 09:00부터 20:00까지 진행됩니다.

  - SUBSCRIBE-0115

매장 개통은 30분 이내에 즉시 완료되고, 온라인 개통은 신청 다음 날 처리됩니다. 당일 이용이 필요하시면 매장 개통이 유리합니다.

  - SUBSCRIBE-0119

매장은 30분 이내, 온라인은 다음 날 개통이라 매장이 빠릅니다. 처리는 09:00부터 20:00까지입니다.

  - SUBSCRIBE-0031

매장에서 하시면 30분 이내에 개통됩니다. 온라인은 다음 날 개통되며 처리는 09:00부터 20:00까지입니다.

  - SUBSCRIBE-0111

당일 이용이면 매장(30분 이내)이 편하고, 방문이 어려우면 온라인(다음 날 개통)이 편합니다.

  - SUBSCRIBE-0071

온라인은 다음 날 개통됩니다. 매장은 30분 이내라 당일 이용이 필요하면 매장이 낫습니다.

## V2-COMPOUND-C019 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 요금이 두 번 빠져나갔는데 환불 대신 다음 달 요금에서 빼줄 수 있어요? 그리고 오늘 가입한 부가서비스 바로 해지하면 이번 달 요금 안 나오죠?

**저장된 답변**

1. 요금이 두 번 빠져나갔는데 환불 대신 다음 달 요금에서 빼줄 수 있어요
네, 요금이 두 번 빠져나갔을 때 환불 대신 다음 달 요금에서 차감하는 방식을 선택하실 수 있습니다. 고객센터에 연락하여 이 방식을 요청하시면 됩니다. 처리 확인은 접수 후 약 3영업일 이내에 가능합니다.

2. 그리고 오늘 가입한 부가서비스 바로 해지하면 이번 달 요금 안 나오죠
로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 오늘 가입한 부가서비스 바로 해지하면 이번 달 요금 안 나오죠
  - 판단 이유: The claim is not supported by any of the provided FAQ answers, which do not mention anything about the cancellation of additional services or the effect on the current month's bill.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not supported by any of the provided FAQ answers, which do not mention anything about login access to contract terms or penalty information.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim is not supported by any of the provided FAQ answers, which do not mention anything about viewing all tariff plans.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user can choose to have the next month's bill deducted instead of receiving a refund, and it mentions contacting the customer service center to request this option. This aligns with the required fact that '다음 달 요금에서 차감하는 방식도 선택할 수 있다'.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer mentions that the user can cancel the service today and the bill for this month will not be charged, but it does not explicitly state that '가입 당일 해지해도 그 달 요금은 전액 청구된다' and '월 이용료는 일할 계산되지 않는다', which are required facts. The answer only partially addresses the question.

**RAG에 전달된 실제 근거**

- BILLING-0076: 이중 납부 환불 대신 요금에서 빼는 것도 돼요?

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다.

- BILLING-0041: 요금이 두 번 빠져나갔는데 다음 달 요금에서 빼달라고 하려면 어떻게 해요?

고객센터에 접수하면서 다음 달 차감 방식을 선택하시면 됩니다. 확인은 접수 후 3영업일 이내에 됩니다.

- BILLING-0020: 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요?

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다. 환불은 본인 명의 계좌로 5영업일 이내입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 요금이 이중으로 빠져나갔던데 환불 말고 담달 요금에서 빼주는 것도 돼요?
  - BILLING-0020

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다. 환불은 본인 명의 계좌로 5영업일 이내입니다.

  - BILLING-0139

환불은 본인 명의 계좌로 5영업일 이내에 들어오고, 차감은 다음 달 요금에서 빠집니다. 편한 쪽을 고르시면 됩니다.

  - BILLING-0146

환불은 본인 명의 계좌로 5영업일 이내 지급되고, 차감은 다음 달 요금에 반영됩니다.

  - BILLING-0076

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다.

  - BILLING-0041

고객센터에 접수하면서 다음 달 차감 방식을 선택하시면 됩니다. 확인은 접수 후 3영업일 이내에 됩니다.

  - BILLING-0062

두 번 낸 건 접수하시면 3영업일 안에 확인해서 5영업일 안에 돌려드립니다. 다음 달 요금에서 빼드릴 수도 있습니다.

- 질문 부분: 부가서비스 잘못 눌러서 오늘 가입됐는데 바로 해지하면 요금 안 나오죠?
  - SERVICE-0036

네, 월 이용료는 일할 계산되지 않아 가입 당일 해지하셔도 그 달 요금은 전액 청구됩니다.

  - SERVICE-0016

가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.

  - SERVICE-0031

해지를 신청하셔도 해당 월 말일까지는 이용하실 수 있고, 월 이용료는 일할 계산되지 않아 그 달 요금은 그대로 청구됩니다. 가입은 신청 즉시 적용됩니다.

  - SERVICE-0051

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

  - SERVICE-0056

가입은 신청 즉시 적용되고, 해지는 그 달 말일까지 이용하면서 월 이용료가 일할 계산 없이 전액 청구됩니다.

## V2-MULTI-M004 / 질문 2

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 개통한 지 며칠 안 된 새 폰이 배터리가 이상한데, 다른 폰으로 교환받을 수 있나요?

**저장된 답변**

개통 후 14일 이내이고 새 폰이 미개봉 상태라면 배터리 문제로 인해 교환이 가능합니다. 하지만 정확한 조건 확인을 위해 고객님의 사용 여부와 상태를 확인해야 합니다. 조건을 충족하신다면 교환을 도와드리겠습니다.

- UNSUPPORTED: 개통 후 14일 이내이고 새 폰이 미개봉 상태라면 배터리 문제로 인해 교환이 가능합니다.
  - 판단 이유: The claim specifies '미개봉 상태' (unopened) but the FAQ sources mention '미개봉이거나 사용 흔적이 없는 상태' (unopened or no usage痕迹). The claim adds the condition of being unopened, which is not explicitly required in the FAQ sources. Additionally, the FAQ sources do not explicitly mention battery issues as a reason for exchange, only general conditions.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys all required facts: it specifies that the phone must be within 14 days of activation, unused, and that the exchange is only possible once. It also mentions the need to confirm the usage status and condition, which aligns with the reference group's information.

**RAG에 전달된 실제 근거**

- DEVICE-0024: 배터리 이상한 새 폰 바꿔줘요

개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

- DEVICE-0104: 새 폰 배터리가 이상한데 안 바꿔줘요

14일 안에 안 쓴 상태여야 바꿔드립니다. 쓰셨으면 수리를 받으셔야 합니다.

- DEVICE-0084: 배터리가 하루도 안 가는 새 폰인데 교환 거절당했어요

교환은 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 기간이 지났거나 사용 흔적이 있으면 서비스센터 수리로 안내됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 개통한 지 며칠 안 된 새 폰이 배터리가 이상한데, 다른 폰으로 교환받을 수 있나요?
  - DEVICE-0024

개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

  - DEVICE-0084

교환은 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 기간이 지났거나 사용 흔적이 있으면 서비스센터 수리로 안내됩니다.

  - DEVICE-0032

개통 후 14일 이내에 매장이나 고객센터로 신청하시면 됩니다. 미개봉이거나 사용 흔적이 없어야 하고 1회에 한합니다.

## V2-MULTI-M007 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 지금 가입할 수 있는 요금제가 몇 개나 되고 가격대는 어느 정도예요?

**저장된 답변**

1. 지금 가입할 수 있는 요금제
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 요금제 가격대
요금제 안내예요.

[5G]
- 5G 언리미티드: 월 105,000원 · 데이터 무제한
- 5G 프리미엄: 월 85,000원 · 데이터 150GB
- 5G 스탠다드: 월 65,000원 · 데이터 50GB
- 5G 라이트: 월 45,000원 · 데이터 8GB
- 5G 청소년: 월 55,000원 · 데이터 80GB
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 맥스: 월 69,000원 · 데이터 100GB
- LTE 플러스: 월 49,000원 · 데이터 15GB
- LTE 베이직: 월 33,000원 · 데이터 4GB
- LTE 청소년: 월 39,000원 · 데이터 20GB
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 세이브: 월 25,000원 · 데이터 3GB
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 5G 언리미티드: 월 105,000원 · 데이터 무제한
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 프리미엄: 월 85,000원 · 데이터 150GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 스탠다드: 월 65,000원 · 데이터 50GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 라이트: 월 45,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 청소년: 월 55,000원 · 데이터 80GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 맥스: 월 69,000원 · 데이터 100GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 플러스: 월 49,000원 · 데이터 15GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 베이직: 월 33,000원 · 데이터 4GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 청소년: 월 39,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 세이브: 월 25,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: No FAQ source directly supports this claim.

**quality 검증 오류**: HTTPError: HTTP Error 400: Bad Request

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 지금 가입할 수 있는 요금제가 몇 개나 되고 가격대는 어느 정도예요?
  - PLAN-0060

9가지 있습니다. 15,000원부터 105,000원까지 있어요.

  - PLAN-0005

총 9종의 요금제를 운영하며, 월정액은 15,000원부터 105,000원까지, 기본 데이터는 1.5GB부터 무제한까지 다양합니다.

  - PLAN-0040

총 9종이며 월정액이 15,000원부터 105,000원까지, 기본 데이터가 1.5GB부터 무제한까지 순서로 올라갑니다. 필요한 데이터양에 맞춰 고르시면 됩니다.

  - PLAN-0095

요금제 월정액은 15,000원부터 105,000원까지 9종입니다. 사용량에 비해 높은 요금제라면 낮은 요금제로 조정해 보세요.

## V2-MULTI-M014 / 질문 2

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 그리고 올해 들어서 멤버십 등급이 떨어졌던데 이유가 뭔가요?

**저장된 답변**

멤버십 등급이 올해 들어 내려간 이유는 직전 1년간의 납부 금액이 감소했기 때문일 수 있습니다. 멤버십 등급은 매년 1월에 갱신되며, 납부 금액 기준에 따라 VVIP, VIP, 골드, 실버 등 4개 등급으로 조정됩니다. 따라서 사용 금액이 줄어들면 등급이 낮아질 수 있습니다. 자세한 내용은 고객님의 최근 납부 내역을 확인해 보시는 것이 좋습니다.

- UNSUPPORTED: 자세한 내용은 고객님의 최근 납부 내역을 확인해 보시는 것이 좋습니다.
  - 판단 이유: This claim is not directly supported by any of the provided FAQ sources.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys all required facts: that membership grade is based on the previous year's payment amount, that it is renewed annually in January, and that a decrease in usage amount can lower the grade. These facts directly address the user's question about why their membership grade dropped this year.

**RAG에 전달된 실제 근거**

- SERVICE-0042: 멤버십 등급이 왜 갑자기 내려갔는지 모르겠어요

멤버십은 직전 1년간 납부 금액을 기준으로 매년 1월에 갱신되어, 사용 금액이 줄면 등급이 낮아질 수 있습니다.

- SERVICE-0047: 멤버십 등급이 올해 들어 내려갔는데 산정 기준이 어떻게 되나요?

멤버십은 VVIP, VIP, 골드, 실버 4개 등급으로 운영합니다. 직전 1년간 납부 금액을 기준으로 산정하며 매년 1월에 갱신되므로, 갱신 시점에 등급이 조정될 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 그리고 올해 들어서 멤버십 등급이 떨어졌던데 이유가 뭔가요?
  - SERVICE-0042

멤버십은 직전 1년간 납부 금액을 기준으로 매년 1월에 갱신되어, 사용 금액이 줄면 등급이 낮아질 수 있습니다.

## V2-MULTI-M015 / 질문 2

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 저는 아까 온라인으로 넣었는데, 그럼 오늘 안에는 안 되는 거예요?

**저장된 답변**

온라인 신청의 경우, 일반적으로 다음 날 처리가 완료됩니다. 오늘 안에 완료되지 않을 수 있으니 조금 더 기다려 주시기 바랍니다.

- UNSUPPORTED: 오늘 안에 완료되지 않을 수 있으니 조금 더 기다려 주시기 바랍니다.
  - 판단 이유: FAQ 답변은 '온라인은 다음 날 됩니다.'라고 명시하지만, '오늘 안에 완료되지 않을 수 있다'는 구체적인 설명이나 예외 조건은 제공되지 않았습니다. 따라서 이 주장은 직접적으로 지원되지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that online applications are generally processed the next day and acknowledges that today's completion is not guaranteed, aligning with the required facts about online processing taking the next day and the 09:00-20:00 processing window being implied in the response.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0099: 신청했는데 왜 오늘 안 되나요

매장은 30분이면 되는데, 온라인은 다음 날 됩니다. 09:00부터 20:00 사이에 접수하셔야 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 저는 아까 온라인으로 넣었는데, 그럼 오늘 안에는 안 되는 거예요?
  - SUBSCRIBE-0099

매장은 30분이면 되는데, 온라인은 다음 날 됩니다. 09:00부터 20:00 사이에 접수하셔야 처리됩니다.

  - SUBSCRIBE-0087

온라인 신청은 다음 날 개통되며 처리는 09:00부터 20:00까지입니다. 그 시간이 지나도 안 되면 고객센터에 확인해 주세요.

## V2-MULTI-M017 / 질문 2

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 그 폰 할부가 아직 꽤 남았는데 그건 어떻게 되는 거예요?

**저장된 답변**

아들님께서 입대하시기 전에 폰 할부가 아직 남아 있으시군요. 해지 신청 시 남은 할부금은 일시 납부 또는 할부 유지 중 선택이 가능합니다. 일시 납부를 원하신다면 한 번에 남은 금액을 내주시면 됩니다. 할부 유지를 선택하시면 계속해서 분할 납부가 가능합니다.

- UNSUPPORTED: 일시 납부를 원하신다면 한 번에 남은 금액을 내주시면 됩니다.
  - 판단 이유: The FAQ source TERMINATE-0007 states that the remaining installment should be paid in full as a principle, but it does not explicitly state that the user can pay the remaining amount in one go if they choose to do so. The claim adds a procedural detail not directly supported by the evidence.

- UNSUPPORTED: 할부 유지를 선택하시면 계속해서 분할 납부가 가능합니다.
  - 판단 이유: The FAQ source TERMINATE-0007 mentions that the installment schedule can be maintained after termination, but it does not explicitly state that the user can choose to keep the installment plan. The claim adds a choice not directly supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys both required facts: that remaining installment payments are原则上 one-time payment (일시 납부) and that it is possible to maintain the existing installment schedule after termination (해지 후에도 기존 할부 일정을 유지할 수 있다).

**RAG에 전달된 실제 근거**

- TERMINATE-0031: 입대 전에 할부 남은 폰을 해지하려면 어떻게 해요?

해지 신청 시 남은 할부금을 일시 납부하거나 할부 유지 중 선택하시면 됩니다.

- TERMINATE-0007: 오래 안 쓴 폰 해지하면 남은 할부는 어떻게 돼요?

남은 할부금은 한 번에 내는 게 원칙입니다. 원하시면 해지 후에도 할부 일정을 그대로 유지할 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 그 폰 할부가 아직 꽤 남았는데 그건 어떻게 되는 거예요?
  - TERMINATE-0003

남은 할부금은 일시 납부가 원칙이고, 원하시면 해지 후에도 기존 할부 일정을 유지하실 수 있습니다.

  - TERMINATE-0007

남은 할부금은 한 번에 내는 게 원칙입니다. 원하시면 해지 후에도 할부 일정을 그대로 유지할 수 있습니다.

  - TERMINATE-0011

남은 할부금은 일시 납부가 원칙입니다. 원하시면 해지 후에도 기존 할부 일정을 유지하실 수 있습니다.

  - TERMINATE-0015

일시 납부가 원칙이며, 원하시면 해지 후에도 기존 할부 일정을 유지하는 방식을 선택할 수 있습니다.

  - TERMINATE-0019

남은 단말 할부금은 일시에 납부하시는 것이 원칙입니다. 원하시면 해지 후에도 기존 할부 일정을 그대로 유지하실 수 있습니다.

## V2-MULTI-M018 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 지금 해외에 나와 있는데 여기서 전화로 회선 해지 신청할 수 있나요?

**저장된 답변**

네, 해외에서도 고객센터에 전화로 회선 해지 신청이 가능합니다. 전화로 신청하시면 즉시 해지 처리가 이루어집니다.

- UNSUPPORTED: 해외에서도 고객센터에 전화로 회선 해지 신청이 가능합니다.
  - 판단 이유: The FAQ sources do not explicitly state that the service is available from abroad. The claim introduces a new condition (being overseas) that is not addressed in the provided FAQ answers.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user can call the customer service center to request line termination, and that it will be processed immediately. This directly addresses the required fact that calling the customer service center allows for immediate termination.

**RAG에 전달된 실제 근거**

- TERMINATE-0069: 외국에서 전화로 해지할 수 있나요

네, 고객센터에 전화하시면 바로 해지됩니다.

- TERMINATE-0081: 급하게 해외로 나가게 됐는데 매장 갈 시간이 없어요

고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

- TERMINATE-0049: 해외 나가 있는 동안 홈페이지로 해지할 수 있을까요?

네, 홈페이지에서 신청하시면 즉시 해지됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 지금 해외에 나와 있는데 여기서 전화로 회선 해지 신청할 수 있나요?
  - TERMINATE-0069

네, 고객센터에 전화하시면 바로 해지됩니다.

  - TERMINATE-0081

고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

  - TERMINATE-0009

매장, 고객센터, 홈페이지 어디서든 신청할 수 있고 신청 즉시 해지됩니다. 해지월 요금은 일할 계산됩니다.

  - TERMINATE-0029

홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 해지한 달 요금은 일할 계산되어 청구됩니다.

  - TERMINATE-0077

홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 매장에 가지 않아도 됩니다.

## V2-MULTI-M019 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / HUMAN_REVIEW
- 질문: 유심 카드가 깨져 버렸는데 이참에 eSIM으로 바꾸고 싶어요. 어떻게 하면 돼요?

**저장된 답변**

먼저, 현재 사용 중인 단말이 eSIM을 지원하는지 확인해 주세요. 지원한다면 매장이나 온라인을 통해 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원입니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys all required facts: checking if the device supports eSIM, applying for eSIM issuance through a store or online, and the cost of 2,750 won.

**RAG에 전달된 실제 근거**

- USIM-0027: 유심이 깨져서 eSIM으로 바꾸려면 어떻게 해요?

eSIM을 지원하는 단말인지 먼저 확인하시고, 매장이나 온라인에서 발급을 신청하시면 됩니다. 비용은 2,750원입니다.

- USIM-0031: 폰을 잃어버려서 eSIM으로 새로 받고 싶은데 어떻게 해요?

새 단말이 eSIM을 지원하면 매장이나 온라인에서 eSIM 발급을 신청하시면 됩니다. 비용은 2,750원입니다.

- USIM-0047: 유심이 부러졌는데 eSIM으로 하는 법 알려주세요

폰이 eSIM을 지원하면 매장에서 바꿔 드립니다. 2,750원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 유심 카드가 깨져 버렸는데 이참에 eSIM으로 바꾸고 싶어요. 어떻게 하면 돼요?
  - USIM-0027

eSIM을 지원하는 단말인지 먼저 확인하시고, 매장이나 온라인에서 발급을 신청하시면 됩니다. 비용은 2,750원입니다.

  - USIM-0035

eSIM을 지원하는 단말이라면 매장이나 온라인에서 전환 신청을 하시면 됩니다. 발급 비용은 2,750원입니다.

## V2-NOANSWER-U002 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 명의도용 신고하면 조사하는 3영업일 동안 그 회선은 바로 정지되나요?

**저장된 답변**

명의도용 신고 접수 후 3영업일 이내에 조사가 진행되지만, 회선의 즉시 정지 여부는 조사 결과에 따라 결정됩니다.

- UNSUPPORTED: 회선의 즉시 정지 여부는 조사 결과에 따라 결정됩니다.
  - 판단 이유: FAQ 답변에는 회선의 즉시 정지 여부가 조사 결과에 따라 결정된다는 내용이 포함되어 있지 않아 해당 클레임은 지원되지 않습니다.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states that the investigation is conducted within 3 business days after the report of identity theft. However, it does not explicitly confirm whether the line is immediately suspended during this period, which is a key part of the user's question. The answer only mentions that the suspension depends on the investigation results, which is a partial response.

**RAG에 전달된 실제 근거**

- SERVICE-0019: 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요.

고객센터로 명의도용 신고를 접수하시면 됩니다. 접수 후 3영업일 이내에 조사가 진행되고, 도용으로 확인되면 해당 요금은 전액 취소됩니다.

- SERVICE-0029: 명의도용 신고는 어떻게 해요

고객센터로 신고하시면 3영업일 이내에 조사가 진행됩니다. 도용이 확인되면 요금이 전액 취소됩니다.

- SERVICE-0004: 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요?

신고 접수 후 3영업일 이내에 조사가 진행됩니다. 도용이 확인되면 해당 요금은 전액 취소됩니다.

## V2-NOANSWER-U006 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 단말 보험 월 보험료는 얼마고, 개통하고 며칠 안에 가입해야 하나요?

**저장된 답변**

1. 단말 보험 월 보험료는 얼마고
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

2. 개통하고 며칠 안에 가입해야 하나요
개통 후 바로 사용 가능합니다. 매장에서 가입하시면 개통이 30분 이내에 완료되므로 즉시 이용 가능합니다. 온라인으로 가입하신 경우에는 다음 날 개통되지만, 개통 처리 시간이 매일 오전 9시부터 오후 8시까지이므로 그 이후에는 바로 사용하실 수 있습니다.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 개통 후 바로 사용 가능합니다.
  - 판단 이유: The claim states that usage is possible immediately after activation, but the FAQ sources specify that online activation takes until the next day and is only processed between 09:00 and 20:00. This implies that activation is not always immediate, especially for online users.

- UNSUPPORTED: 온라인으로 가입하신 경우에는 다음 날 개통되지만, 개통 처리 시간이 매일 오전 9시부터 오후 8시까지이므로 그 이후에는 바로 사용하실 수 있습니다.
  - 판단 이유: The claim incorrectly states that after the activation processing hours (09:00-20:00), the service can be used immediately. The FAQ sources do not mention anything about using the service immediately after the processing hours; they only state that online activation occurs the next day and is processed during the specified hours.

- 하위 질문 1: MISSED
  - 판단 이유: The answer refuses to provide the requested information about the monthly insurance fee, which is a substantive fact the user is asking for.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0047: 가입하면 바로 쓸 수 있나요.

매장에서 가입하시면 30분 이내에 개통됩니다. 온라인으로 신청하시면 다음 날 개통되고, 개통 처리는 매일 09:00부터 20:00까지 진행됩니다.

- SUBSCRIBE-0003: 외국인인데 개통하면 얼마 만에 쓸 수 있어요?

매장에서 가입하시면 30분 이내에 개통됩니다. 온라인은 다음 날 개통되고, 처리 시간은 09:00부터 20:00까지입니다.

## V2-NOANSWER-U009 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: eSIM 발급비 2,750원은 그 자리에서 결제해야 해요, 아니면 다음 달 요금에 같이 청구돼요?

**저장된 답변**

eSIM 발급 비용은 즉시 결제해야 하는 방식입니다. 다음 달 요금에 포함되지 않고 별도로 청구됩니다.

- UNSUPPORTED: eSIM 발급 비용은 즉시 결제해야 하는 방식입니다.
  - 판단 이유: The FAQ sources only mention the cost of eSIM issuance (2,750원) and conditions for eligibility (e.g., supported devices, dual SIM usage). None of the sources explicitly state whether the fee must be paid immediately or if it is billed separately.

- UNSUPPORTED: 다음 달 요금에 포함되지 않고 별도로 청구됩니다.
  - 판단 이유: The FAQ sources do not mention whether the eSIM issuance fee is billed separately or included in the next month's bill. The information about the fee is only provided as a flat rate (2,750원) without billing details.

- 하위 질문 1: MISSED
  - 판단 이유: The expected behavior is ABSTAIN, which requires a clear no-information response. However, the assistant provided a specific answer about eSIM issuance costs, which is not appropriate for the ABSTAIN expected behavior.

**RAG에 전달된 실제 근거**

- USIM-0003: eSIM은 얼마예요?

eSIM 발급 비용은 2,750원입니다.

- USIM-0011: eSIM 발급 비용과 지원 조건을 알려주세요.

eSIM 발급 비용은 2,750원이며, eSIM을 지원하는 단말에서만 이용하실 수 있습니다. 듀얼심으로 최대 2개 회선을 함께 쓰실 수 있습니다.

- USIM-0079: 폰을 바꿨는데 eSIM이 옮겨지지 않아요

eSIM은 새 단말에서 다시 발급받아야 합니다. 지원 단말이라면 2,750원에 발급됩니다.

## V2-NOANSWER-U016 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 아버지 명의 폰을 제 명의로 바꾸면 아버지가 걸어둔 24개월 약정도 저한테 그대로 넘어오나요, 아니면 위약금 정산하고 새로 시작해요?

**저장된 답변**

로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not directly supported by any provided FAQ evidence. The statement about confirming the contract period and penalty upon login is not explicitly mentioned in the provided FAQ sources.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim is not directly supported by any provided FAQ evidence. The statement about viewing all plans is not explicitly mentioned in the provided FAQ sources.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not directly address whether the 24-month contract under the father's name would transfer to the user's name or if a penalty would apply. It only provides general guidance to check the contract period and penalty upon login, which does not resolve the specific question about contract transfer and penalties.

## V2-NOANSWER-U018 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 시니어 요금제는 한 달에 얼마고 데이터는 몇 기가 줘요?

**저장된 답변**

요금제 안내예요.

[5G]
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: The claim is not supported by the provided FAQ sources, as no evidence is available to confirm the specific pricing and data allowance for the '5G 시니어' plan.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: The claim is not supported by the provided FAQ sources, as no evidence is available to confirm the specific pricing and data allowance for the 'LTE 시니어' plan.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: The claim is not supported by the provided FAQ sources, as no evidence is available to confirm the specific pricing and data allowance for the '알뜰 시니어' plan.

- UNSUPPORTED: 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: The claim is not supported by the provided FAQ sources, as no evidence is available to confirm the age requirement for eligibility of the '시니어 요금제'.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides the requested information about the monthly cost and data for each senior plan (5G, LTE, 알뜰). However, the user's question does not specify which plan they are asking about, and the answer does not explicitly clarify that it is providing information for multiple plans. Additionally, the age restriction is mentioned, which is not directly requested in the question.

## V2-NOANSWER-U022 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 5G 언리미티드 쓰는데 노트북에 핫스팟(테더링)으로 연결해서 쓰는 것도 무제한이에요?

**저장된 답변**

네, 5G 언리미티드 요금제는 데이터 무제한 혜택이 포함되어 있어 노트북 핫스팟(테더링)을 통한 데이터 사용도 무제한으로 이용 가능합니다. 다만, 핫스팟 사용 시 전체 데이터 사용량이 증가할 수 있으니 주의해 주세요.

- UNSUPPORTED: 5G 언리미티드 요금제는 데이터 무제한 혜택이 포함되어 있어 노트북 핫스팟(테더링)을 통한 데이터 사용도 무제한으로 이용 가능합니다
  - 판단 이유: The FAQ source only confirms that the 5G unlimited plan provides unlimited data, but it does not explicitly state that this includes usage via hotspot (tethering) on a laptop. The claim adds the specific condition of tethering, which is not addressed in the provided FAQ answer.

- UNSUPPORTED: 핫스팟 사용 시 전체 데이터 사용량이 증가할 수 있으니 주의해 주세요
  - 판단 이유: The FAQ source does not mention anything about data usage increasing when using a hotspot, nor does it provide any warning or caution related to hotspot usage. This claim introduces a new condition not supported by the provided FAQ answer.

- 하위 질문 1: MISSED
  - 판단 이유: The expected behavior is ABSTAIN, which means the assistant should not provide an answer. However, the assistant provided a complete answer, which is not appropriate for the expected behavior.

**RAG에 전달된 실제 근거**

- PLAN-0085: 데이터 무제한 요금제도 있나요

네, 5G 언리미티드 요금제가 월 105,000원에 데이터 무제한으로 제공됩니다.

## V2-NOANSWER-U026 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 넷플릭스나 유튜브 프리미엄 포함된 요금제 있어요?

**저장된 답변**

5G 프리미엄
- 월정액 85,000원
- 데이터 150GB
- 통화 무제한
- 문자 무제한

- UNSUPPORTED: 5G 프리미엄
  - 판단 이유: No FAQ source directly supports the claim '5G 프리미엄'.

- UNSUPPORTED: 월정액 85,000원
  - 판단 이유: No FAQ source directly supports the claim '월정액 85,000원'.

- UNSUPPORTED: 데이터 150GB
  - 판단 이유: No FAQ source directly supports the claim '데이터 150GB'.

- UNSUPPORTED: 통화 무제한
  - 판단 이유: No FAQ source directly supports the claim '통화 무제한'.

- UNSUPPORTED: 문자 무제한
  - 판단 이유: No FAQ source directly supports the claim '문자 무제한'.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not address whether the 5G 프리미엄 plan includes Netflix or YouTube Premium. It only provides general details about the plan, such as the monthly fee, data allowance, and unlimited calling and texting.

## V2-NOANSWER-U030 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 초등학생 키즈워치 전용 요금제 있어요? 월 얼마예요?

**저장된 답변**

요금제 안내예요.

[5G]
- 5G 언리미티드: 월 105,000원 · 데이터 무제한
- 5G 프리미엄: 월 85,000원 · 데이터 150GB
- 5G 스탠다드: 월 65,000원 · 데이터 50GB
- 5G 라이트: 월 45,000원 · 데이터 8GB
- 5G 청소년: 월 55,000원 · 데이터 80GB
- 5G 시니어: 월 49,000원 · 데이터 20GB

[LTE]
- LTE 맥스: 월 69,000원 · 데이터 100GB
- LTE 플러스: 월 49,000원 · 데이터 15GB
- LTE 베이직: 월 33,000원 · 데이터 4GB
- LTE 청소년: 월 39,000원 · 데이터 20GB
- LTE 시니어: 월 29,000원 · 데이터 8GB

[알뜰]
- 알뜰 세이브: 월 25,000원 · 데이터 3GB
- 알뜰 미니: 월 15,000원 · 데이터 1.5GB
- 알뜰 청소년: 월 20,000원 · 데이터 5GB
- 알뜰 시니어: 월 18,000원 · 데이터 3GB

청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.

- UNSUPPORTED: 5G 언리미티드: 월 105,000원 · 데이터 무제한
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 프리미엄: 월 85,000원 · 데이터 150GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 스탠다드: 월 65,000원 · 데이터 50GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 라이트: 월 45,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 청소년: 월 55,000원 · 데이터 80GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 5G 시니어: 월 49,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 맥스: 월 69,000원 · 데이터 100GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 플러스: 월 49,000원 · 데이터 15GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 베이직: 월 33,000원 · 데이터 4GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 청소년: 월 39,000원 · 데이터 20GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: LTE 시니어: 월 29,000원 · 데이터 8GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 세이브: 월 25,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 미니: 월 15,000원 · 데이터 1.5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 청소년: 월 20,000원 · 데이터 5GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 알뜰 시니어: 월 18,000원 · 데이터 3GB
  - 판단 이유: No FAQ source directly supports this claim.

- UNSUPPORTED: 청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요.
  - 판단 이유: No FAQ source directly supports this claim.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not address the specific question about a '초등학생 키즈워치 전용 요금제' (elementary student kids' watch dedicated plan). It provides general information about various 5G, LTE, and 알뜰 (budget) plans but does not mention any specific plan tailored for elementary students or kids' watches. The answer also does not provide the monthly cost for such a specific plan.

## V2-NOANSWER-U035 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 전화번호만 다른 번호로 바꾸고 싶은데 번호 변경은 어떻게 하고 수수료 있어요?

**저장된 답변**

1. 전화번호만 다른 번호로 바꾸고 싶은데
밀린 요금이 없다면 번호 변경이 가능합니다.

2. 수수료 있어요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.
  - 판단 이유: The provided FAQ answer does not mention anything about being unable to provide an exact answer based on currently available information. The claim is not supported by the given FAQ evidence.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the user's question by mentioning that number change is possible if there are no overdue charges, but it does not provide specific information on how to proceed with the number change. Additionally, it mentions that there is a fee but does not clarify the exact amount or conditions for the fee. The response also states that it is difficult to provide an exact answer based on the available information, which indicates a lack of complete resolution.

**RAG에 전달된 실제 근거**

- PORTING-0045: 다른 회사로 번호 가져가는 거 어떻게 하는 거예요

신분증 가지고 가서 옮긴다고 하시면 됩니다. 밀린 요금만 없으면 됩니다.

## V2-NOANSWER-U049 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 제 이번 달 요금 얼마 나왔어요?

**저장된 답변**

로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not directly supported by any provided FAQ sources. The statement about checking usage and estimated charges after logging in is not explicitly mentioned in the given FAQ answer text.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim is not directly supported by any provided FAQ sources. The statement about viewing all plans is not explicitly mentioned in the given FAQ answer text.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the actual monthly charge but instead directs the user to log in to check their usage and estimated cost. The user explicitly asked for the amount of their monthly charge, which was not answered.

## V2-NOANSWER-U050 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 저 지금 데이터 몇 기가 남았어요?

**저장된 답변**

로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not supported by any provided FAQ sources. The statement about checking usage and estimated charges after logging in is not confirmed by the available evidence.

- UNSUPPORTED: 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.
  - 판단 이유: The claim is not supported by any provided FAQ sources. The statement about viewing all plans is not confirmed by the available evidence.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the requested information about the remaining data usage in gigabytes. It only mentions that login is required to check usage and fees, which does not directly answer the question.

## V2-NOANSWER-U051 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 제 약정 몇 개월 남았는지랑 지금 해지하면 위약금 정확히 얼마인지 계산해 주세요

**저장된 답변**

1. 제 약정 몇 개월 남았는지
로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

2. 지금 해지하면 위약금 정확히 얼마인지
로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요. 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not supported because there is no FAQ evidence provided to confirm that logging in allows users to check their contract period and early termination fees.

- UNSUPPORTED: 로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.
  - 판단 이유: The claim is not supported because there is no FAQ evidence provided to confirm that logging in allows users to check their contract period and early termination fees.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the specific information requested (remaining contract months and exact penalty amount). It only directs the user to log in to check the information, which is not a concrete answer to the question.

## V2-NOANSWER-U056 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: SK텔레콤은 유심 재발급 비용이 얼마예요? 여기랑 비교해 보려고요

**저장된 답변**

SK텔레콤의 유심 재발급 비용에 대한 정보는 현재 제공된 근거에서 확인할 수 없습니다. LG U+의 경우 유심 재발급 비용이 7,700원입니다. SK텔레콤의 정확한 비용을 비교하려면 해당 통신사의 공식 정보를 확인해 보시는 것이 좋습니다.

- UNSUPPORTED: LG U+의 경우 유심 재발급 비용이 7,700원입니다.
  - 판단 이유: The claim refers to LG U+ but the FAQ source only mentions the cost of 7,700 won without specifying the carrier. The evidence does not explicitly state that this cost applies to LG U+.

- 하위 질문 1: MISSED
  - 판단 이유: The answer does not provide the requested factual information about SK Telecom's SIM reissue cost. It only states that the information is not available in the provided references and suggests checking the official information from the carrier, which does not resolve the user's question.

**RAG에 전달된 실제 근거**

- USIM-0001: 유심 재발급 비용이 얼마예요?

7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.

## REG-FAQ_FACT / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 가상계좌가 무엇인가요?

**저장된 답변**

가상계좌는 요금을 납부할 때 사용하는 입금 전용 계좌입니다. 주로 계좌이체 방식으로 요금을 쉽게 납부할 수 있게 해줍니다.

- UNSUPPORTED: 주로 계좌이체 방식으로 요금을 쉽게 납부할 수 있게 해줍니다.
  - 판단 이유: The claim '주로 계좌이체 방식으로 요금을 쉽게 납부할 수 있게 해줍니다.' is not supported by the FAQ answer. While the answer mentions that payments can be made via account transfer, credit card, and convenient payment methods, it does not state that account transfer is the primary method.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that a virtual account is a deposit-only account provided to users for bill payments, which aligns with the required fact that it is a deposit-only account provided for bill payments. The answer also mentions the primary method of payment (account transfer), which is consistent with the reference.

**RAG에 전달된 실제 근거**

- BILLING-0026: 가상계좌가 뭐예요

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 가상계좌가 무엇인가요?
  - BILLING-0026

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

## REG-MIXED / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 유심 재발급 비용은 얼마고 배송비도 내야 하나요?

**저장된 답변**

1. 유심 재발급 비용은 얼마
유심 재발급 비용은 7,700원입니다. 매장에서 즉시 받으실 수 있습니다.

2. 배송비도 내야 하나요
지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.

- 생성 기록: NO_EVIDENCE / None

- UNSUPPORTED: 지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.
  - 판단 이유: None of the provided FAQ answers mention this statement or imply that the information is insufficient to provide an accurate answer.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly provides the cost of 7,700 won for a SIM card reissue. However, it does not address the shipping fee question, stating that it cannot be accurately answered with the available information. This meets the requirement for a partial answer by providing one correct fact while withholding the other.

**RAG에 전달된 실제 근거**

- USIM-0001: 유심 재발급 비용이 얼마예요?

7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.

- USIM-0073: 유심이 안 읽혀서 새로 받아야 할 것 같은데 비용이 얼마나 드나요?

인식 오류가 계속되면 재발급을 받으시는 게 좋습니다. 매장에서는 즉시 발급되고, 비용은 7,700원입니다.

- USIM-0077: 유심이 부러졌는데 오늘 급하게 써야 해요

매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 유심 재발급 비용은 얼마고 배송비도 내야 하나요?
  - USIM-0001

7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.

## REG-FAQ_COMPOUND / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요?

**저장된 답변**

1. 요금제는 한 달에 몇 번 바꿀 수 있고
요금제는 한 달에 한 번만 변경 가능합니다. 변경 신청 후 다음 날 00:00부터 새로운 요금제가 적용됩니다.

2. 가상계좌는 뭐예요
가상계좌는 요금을 납부할 때 사용하는 입금 전용 계좌입니다. 주로 계좌이체를 통해 요금을 쉽게 납부할 수 있도록 설계되었습니다.

- UNSUPPORTED: 주로 계좌이체를 통해 요금을 쉽게 납부할 수 있도록 설계되었습니다.
  - 판단 이유: The claim introduces a new condition ('주로 계좌이체를 통해') that is not explicitly stated in the FAQ evidence. The FAQ only mentions that the virtual account can be used for account transfers, credit cards, and easy payments, but does not specify that it is primarily designed for account transfers.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the plan can be changed once a month, which matches the required fact. It also provides additional context about when the new plan is applied, which is not required but does not detract from the completeness of the answer.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer accurately conveys that the virtual account is a deposit-only account used for paying bills, which matches the required fact. It also provides additional context about how it is primarily used for account transfers, which is not required but does not detract from the completeness of the answer.

**RAG에 전달된 실제 근거**

- BILLING-0001: 요금제 바꾸는 거 한 달에 몇 번까지 되나요?

요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.

- BILLING-0085: 같은 달에 요금제를 두 번 바꿀 수 있나요?

월 1회만 변경할 수 있습니다.

- BILLING-0057: 요금제는 어떻게 바꿔요

신청하시면 다음 날 00:00부터 새 요금제로 바뀝니다. 한 달에 한 번만 바꾸실 수 있습니다.

- BILLING-0026: 가상계좌가 뭐예요

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- 질문 부분: 요금제 바꾸는 거 한 달에 몇 번까지 되나요?
  - BILLING-0001

요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.

- 질문 부분: 가상계좌가 뭐예요
  - BILLING-0026

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

