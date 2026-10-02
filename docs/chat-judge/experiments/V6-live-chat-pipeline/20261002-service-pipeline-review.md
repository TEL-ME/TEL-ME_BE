# 실제 채팅 평가의 오류와 검토 자료

실행 실패, Judge 출력 검증 실패, 모호한 판정과 근거 없는 주장을 구분한다.
아래 근거성 판정은 자동 Judge의 결과이며 사람 확정 라벨이 아니다.

- 실행 완료 후 Judge 검증 실패: 16건
- 검토 필요 판정: 7건
- 실행 실패는 위 Judge 검증 실패 수에 포함하지 않는다.

먼저 확인할 사례: PIPE-BILLING-0020, PIPE-BILLING-0102, PIPE-PLAN-0017, PIPE-PLAN-0121, PIPE-PLAN-0137, PIPE-DEVICE-0111, PIPE-SUBSCRIBE-0122, PIPE-PORTING-0049, PIPE-TERMINATE-0001, PIPE-TERMINATE-0107, PIPE-TERMINATE-0104, PIPE-USIM-0099, PIPE-COMPOUND-002, PIPE-COMPOUND-025, PIPE-COMPOUND-029, PIPE-COMPOUND-035, PIPE-BILLING-0148, PIPE-PLAN-0106, PIPE-SUBSCRIBE-0051, PIPE-TERMINATE-0092, PIPE-TERMINATE-0113, PIPE-COMPOUND-040, PIPE-COMPOUND-047

## PIPE-BILLING-0020 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요?

**저장된 답변**

네, 이중 납부 금액을 다음 달 요금에서 차감하는 방식으로 선택하실 수 있습니다. 차감 후 환불은 본인 명의 계좌로 5영업일 이내에 이루어집니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by confirming that the option to deduct the double payment from the next month's bill is available and provides the refund timeline of 5 business days to the user's account.

**RAG에 전달된 실제 근거**

- BILLING-0020: 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요?

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다. 환불은 본인 명의 계좌로 5영업일 이내입니다.

- BILLING-0076: 이중 납부 환불 대신 요금에서 빼는 것도 돼요?

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다.

- BILLING-0055: 청구서에서 이중 납부를 발견했을 때 다음 달 차감으로 처리하는 절차를 알려주세요.

고객센터 접수 시 다음 달 차감 방식을 선택하면 됩니다. 접수 후 3영업일 이내에 확인되고, 환불을 택하면 5영업일 이내에 본인 명의 계좌로 입금됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0020: 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요?

네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다. 환불은 본인 명의 계좌로 5영업일 이내입니다.

## PIPE-BILLING-0065 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 자동이체 두 번 나갔는데 어떻게 해요

**저장된 답변**

자동이체는 일반적으로 매월 25일에 한 번만 진행됩니다. 두 번 나간 경우는 오류일 가능성이 높습니다. 고객센터에 연락하여 상황을 접수하시면 정확한 문제 해결을 도와드리겠습니다.

- UNSUPPORTED: 두 번 나간 경우는 오류일 가능성이 높습니다.
  - 판단 이유: The FAQ source does not mention anything about the possibility of errors if the automatic transfer occurs twice.

- UNSUPPORTED: 고객센터에 연락하여 상황을 접수하시면 정확한 문제 해결을 도와드리겠습니다.
  - 판단 이유: The FAQ source mentions contacting the customer center but does not explicitly state that they will help resolve the issue accurately.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that automatic transfers typically occur once a month on the 25th, that two transfers may indicate an error, and that contacting the customer service center is the recommended course of action. This fully aligns with the reference provided.

**RAG에 전달된 실제 근거**

- BILLING-0065: 자동이체 두 번 나갔는데 어떻게 해요

고객센터에 접수하시면 됩니다. 자동이체는 원래 25일에 한 번만 나갑니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0065: 자동이체 두 번 나갔는데 어떻게 해요

고객센터에 접수하시면 됩니다. 자동이체는 원래 25일에 한 번만 나갑니다.

## PIPE-BILLING-0052 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 발신 정지 이후 재개까지의 절차를 알려주세요.

**저장된 답변**

발신 정지를 해제하려면 미납 요금이 완전히 납부되어야 합니다. 요금을 완납하시면 일반적으로 2시간 이내에 발신 기능이 재개됩니다. 요금을 납부하지 않으실 경우, 발신 정지 상태가 지속되며 30일 후에는 수신이 정지되고, 그 이후 60일이 경과하면 직권 해지될 수 있습니다.

- UNSUPPORTED: 발신 정지를 해제하려면 미납 요금이 완전히 납부되어야 합니다.
  - 판단 이유: The claim specifies that '미납 요금이 완전히 납부되어야 합니다' (the full outstanding payment must be made), but the FAQ source does not explicitly mention this condition. It only states that '미납 요금을 완납하면 2시간 이내에 재개됩니다' (if the outstanding payment is fully paid, the service resumes within 2 hours), which implies that full payment is required, but it is not explicitly stated as a condition for lifting the restriction.

- UNSUPPORTED: 요금을 납부하지 않으실 경우, 발신 정지 상태가 지속되며 30일 후에는 수신이 정지되고, 그 이후 60일이 경과하면 직권 해지될 수 있습니다.
  - 판단 이유: The claim mentions '발신 정지 상태가 지속되며' (the outgoing call restriction remains), but the FAQ source only mentions '수신 정지' (incoming call restriction) after 30 days and '직권 해지' (automatic termination) after 60 days. The claim adds the condition about the outgoing call restriction continuing, which is not explicitly mentioned in the source.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the required facts: the procedure for resuming service after a call block involves paying the outstanding fees, with service resuming within 2 hours upon full payment. It also explains the consequences of not paying, including continued call block, reception stop after 30 days, and potential termination after 60 days.

**RAG에 전달된 실제 근거**

- BILLING-0052: 발신 정지 이후 재개까지의 절차를 알려주세요.

미납 요금을 완납하면 2시간 이내에 재개됩니다. 완납하지 않으면 30일에 수신 정지, 60일에 직권 해지될 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0052: 발신 정지 이후 재개까지의 절차를 알려주세요.

미납 요금을 완납하면 2시간 이내에 재개됩니다. 완납하지 않으면 30일에 수신 정지, 60일에 직권 해지될 수 있습니다.

## PIPE-BILLING-0054 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 미납 요금을 가상계좌로 납부하는 절차를 알려주세요.

**저장된 답변**

그 후 해당 가상계좌로 지정된 금액을 입금하시면 됩니다.

- UNSUPPORTED: 그 후 해당 가상계좌로 지정된 금액을 입금하시면 됩니다.
  - 판단 이유: The claim specifies that the user should deposit the designated amount into the virtual account, but the FAQ source only mentions that the user can choose to pay via virtual account without explicitly stating that the amount is 'designated' or that the user should deposit it afterward.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the required fact from the reference, which is the procedure for paying the outstanding bill via a virtual account.

**RAG에 전달된 실제 근거**

- BILLING-0054: 미납 요금을 가상계좌로 납부하는 절차를 알려주세요.

납부 수단 4종(계좌이체·신용카드·가상계좌·간편결제) 중 가상계좌를 선택해 입금하시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0054: 미납 요금을 가상계좌로 납부하는 절차를 알려주세요.

납부 수단 4종(계좌이체·신용카드·가상계좌·간편결제) 중 가상계좌를 선택해 입금하시면 됩니다.

## PIPE-BILLING-0053 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 소액결제 한도를 다시 낮추는 절차를 알려주세요.

**저장된 답변**

소액결제 한도를 다시 낮추는 절차는 별도의 신청 과정이 필요하지 않습니다. 변경된 한도는 신청 즉시 적용되며, 기본 한도는 월 30만 원으로 설정되어 있습니다.

- UNSUPPORTED: 소액결제 한도를 다시 낮추는 절차는 별도의 신청 과정이 필요하지 않습니다.
  - 판단 이유: The claim states that there is no separate application process for lowering the limit, but the provided FAQ sources do not mention anything about lowering the limit or the process for doing so. Therefore, the claim cannot be supported by the given evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question by stating that there is no separate application process needed to lower the small payment limit, and that the changed limit is applied immediately upon application. It also mentions the default limit of 300,000 won per month, which aligns with the reference.

**RAG에 전달된 실제 근거**

- BILLING-0053: 소액결제 한도를 다시 낮추는 절차를 알려주세요.

한도 변경은 신청 즉시 적용됩니다. 기본 한도는 월 30만 원입니다.

- BILLING-0039: 소액결제 한도 올리는 방법 알려주세요.

본인 인증을 하시면 월 100만 원까지 상향되고 즉시 적용됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0053: 소액결제 한도를 다시 낮추는 절차를 알려주세요.

한도 변경은 신청 즉시 적용됩니다. 기본 한도는 월 30만 원입니다.

## PIPE-BILLING-0097 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 두 번 낸 거 자식 계좌로 받을 수 있나요

**저장된 답변**

자식 계좌로는 받으실 수 없습니다. 반드시 본인 명의의 계좌로만 가능합니다.

- UNSUPPORTED: 자식 계좌로는 받으실 수 없습니다.
  - 판단 이유: The claim '자식 계좌로는 받으실 수 없습니다.' is not directly supported by the FAQ evidence. The FAQ states '본인 명의 계좌로만 됩니다,' which supports the claim '본인 명의의 계좌로만 가능합니다,' but does not explicitly mention '자식 계좌' or its ineligibility.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the required fact that the child's account cannot be used, and only the account under the user's name is acceptable, which aligns with the reference.

**RAG에 전달된 실제 근거**

- BILLING-0097: 두 번 낸 거 자식 계좌로 받을 수 있나요

안 됩니다. 본인 명의 계좌로만 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0097: 두 번 낸 거 자식 계좌로 받을 수 있나요

안 됩니다. 본인 명의 계좌로만 됩니다.

## PIPE-BILLING-0074 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 미납이 있어도 소액결제 한도를 올릴 수 있어요?

**저장된 답변**

네, 미납이 있더라도 소액결제 한도를 올릴 수 있습니다. 한도는 본인 인증을 통해 최대 월 100만 원까지 상향 조정 가능합니다. 하지만 미납 상태에 따른 추가 제한 사항은 고객센터를 통해 확인해 보시는 것이 좋습니다.

- UNSUPPORTED: 미납이 있더라도 소액결제 한도를 올릴 수 있습니다
  - 판단 이유: FAQ에서 '미납 여부와 관련된 제한은 고객센터에서 확인해 주세요'라고 명시되어 있으므로, 미납 상태가 한도 상향에 영향을 줄 수 있다는 가능성이 제시되고 있어 '미납이 있더라도'라는 주장은 직접적으로 지원되지 않음.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the limit can be increased even with outstanding payments, mentions the maximum limit of 10 million won per month through personal authentication, and advises checking with the customer service for additional restrictions related to outstanding payments, which aligns with the reference.

**RAG에 전달된 실제 근거**

- BILLING-0074: 미납이 있어도 소액결제 한도를 올릴 수 있어요?

한도 상향은 본인 인증으로 진행되며 최대 월 100만 원입니다. 미납 여부와 관련된 제한은 고객센터에서 확인해 주세요.

- BILLING-0046: 미납 상태에서 소액결제 한도를 변경하는 절차를 알려주세요.

본인 인증을 거쳐 신청하면 즉시 적용됩니다. 기본 월 30만 원, 최대 월 100만 원입니다.

- BILLING-0116: 소액결제 한도를 올렸는데 여전히 결제가 안 됩니다

상향은 즉시 적용됩니다. 최대 한도인 월 100만 원을 초과했는지 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0074: 미납이 있어도 소액결제 한도를 올릴 수 있어요?

한도 상향은 본인 인증으로 진행되며 최대 월 100만 원입니다. 미납 여부와 관련된 제한은 고객센터에서 확인해 주세요.

## PIPE-BILLING-0087 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 청구서를 못 받았다는 이유로 정지가 유예되나요?

**저장된 답변**

청구서를 못 받으셨더라도 정지는 납부 기한이 지나고 15일 후에 발신 정지, 30일 후에 수신 정지가 적용됩니다. 따라서 청구서 수령 여부와 직접적인 유예 관계는 없습니다. 기한 내에 요금을 납부하시면 정지를 방지할 수 있습니다.

- UNSUPPORTED: 청구서를 못 받으셨더라도 정지는 납부 기한이 지나고 15일 후에 발신 정지, 30일 후에 수신 정지가 적용됩니다.
  - 판단 이유: The claim states that both 발신 정지 (outgoing call block) and 수신 정지 (incoming call block) occur 15 and 30 days after the due date, respectively. However, the FAQ sources only mention 15 days for 발신 정지 and do not mention a 30-day period for 수신 정지. This discrepancy makes the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the billing cutoff is based on the payment deadline, not the receipt of the invoice, and explains the 15-day and 30-day rules for service suspension. It also clarifies that the receipt of the invoice does not directly affect the suspension period.

**RAG에 전달된 실제 근거**

- BILLING-0087: 청구서를 못 받았다는 이유로 정지가 유예되나요?

정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.

- BILLING-0059: 청구서 못 받았는데 정지 안 되게 하려면 어떻게 해요

청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.

- BILLING-0115: 청구서를 못 받았는데 발신이 정지됐습니다

정지는 납부 기한 후 15일 기준입니다. 완납하시면 2시간 이내에 재개됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0087: 청구서를 못 받았다는 이유로 정지가 유예되나요?

정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.

## PIPE-BILLING-0084 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 모바일 알림 청구서로 바꾸면 요금이 붙나요?

**저장된 답변**

모바일 알림 청구서로 변경하실 경우 추가 요금은 발생하지 않습니다. 이메일이나 모바일 알림 모두 무료로 제공됩니다.

- UNSUPPORTED: 모바일 알림 청구서로 변경하실 경우 추가 요금은 발생하지 않습니다.
  - 판단 이유: The claim states that changing to a mobile alert bill does not incur additional charges. However, the FAQ source BILLING-0035 explicitly mentions that postal billing has a monthly cost of 500 won, but it does not directly confirm whether changing to mobile billing incurs any additional charges. The absence of explicit confirmation makes this claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that changing to a mobile alert billing statement does not incur additional charges and that both email and mobile alerts are free, which aligns with the reference.

**RAG에 전달된 실제 근거**

- BILLING-0084: 모바일 알림 청구서로 바꾸면 요금이 붙나요?

아니요, 모바일과 이메일은 무료입니다. 우편만 월 500원입니다.

- BILLING-0035: 청구서 받는 방법을 바꾸려면 어떻게 해야 하나요?

이메일, 모바일, 우편 중 원하시는 방법으로 변경 신청하시면 됩니다. 변경은 다음 달 청구분부터 적용되며, 우편은 월 500원의 비용이 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0084: 모바일 알림 청구서로 바꾸면 요금이 붙나요?

아니요, 모바일과 이메일은 무료입니다. 우편만 월 500원입니다.

## PIPE-BILLING-0123 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 청구서에 결제한 적 없는 소액결제가 있어요

**저장된 답변**

청구서에 결제 기록이 없는 소액결제 항목이 표시된 경우, 이는 소액결제 한도 내에서 발생한 것으로 보입니다. 기본 한도는 월 30만 원입니다. 정확한 내용 확인을 위해 고객센터에 문의해 주시기 바랍니다.

- UNSUPPORTED: 청구서에 결제 기록이 없는 소액결제 항목이 표시된 경우, 이는 소액결제 한도 내에서 발생한 것으로 보입니다.
  - 판단 이유: The claim discusses a scenario where a small payment item appears on the bill without a payment record, implying it is within the small payment limit. However, the FAQ source does not mention anything about this specific scenario or provide any reasoning about why an item might appear without a payment record. Therefore, the claim is not directly supported by the provided evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the requested facts from the reference, including the explanation about the small payment limit and the recommendation to contact the customer service for confirmation.

**RAG에 전달된 실제 근거**

- BILLING-0123: 청구서에 결제한 적 없는 소액결제가 있어요

소액결제 한도는 기본 월 30만 원입니다. 내역이 이상하면 고객센터에 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0123: 청구서에 결제한 적 없는 소액결제가 있어요

소액결제 한도는 기본 월 30만 원입니다. 내역이 이상하면 고객센터에 확인해 주세요.

## PIPE-BILLING-0102 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 휴대폰 결제가 갑자기 안 돼요

**저장된 답변**

휴대폰 결제가 갑자기 안 되시는군요. 기본 한도가 월 30만 원을 초과했을 가능성이 있습니다. 본인 인증을 통해 한도를 월 100만 원까지 즉시 올릴 수 있으니 이 방법을 시도해 보세요. 도움이 필요하시면 언제든지 다시 문의해 주세요.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining the possible reason (exceeding the monthly limit) and providing a clear solution (raising the limit through personal authentication). This matches the reference group's content accurately.

**RAG에 전달된 실제 근거**

- BILLING-0102: 휴대폰 결제가 갑자기 안 돼요

기본 한도 월 30만 원을 넘겼을 수 있습니다. 본인 인증으로 월 100만 원까지 즉시 올릴 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0102: 휴대폰 결제가 갑자기 안 돼요

기본 한도 월 30만 원을 넘겼을 수 있습니다. 본인 인증으로 월 100만 원까지 즉시 올릴 수 있습니다.

## PIPE-BILLING-0117 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 자동이체와 카드 결제가 같은 달에 둘 다 나갔습니다

**저장된 답변**

이 경우 고객센터에 연락하여 환불 접수를 진행해 주시기 바랍니다.

- UNSUPPORTED: 이 경우 고객센터에 연락하여 환불 접수를 진행해 주시기 바랍니다.
  - 판단 이유: The claim mentions contacting the customer service center to process a refund, but the FAQ source only states that double payments should be reported to the customer service center for refund processing. The exact wording and context of the claim are not directly supported by the FAQ source.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer directly addresses the user's question by instructing them to contact the customer service center to process a refund, which aligns with the reference group's answer. All requested facts are accurately conveyed.

**RAG에 전달된 실제 근거**

- BILLING-0117: 자동이체와 카드 결제가 같은 달에 둘 다 나갔습니다

납부 수단 변경은 다음 달 청구분부터 적용되어 겹칠 수 있습니다. 이중 납부는 고객센터에 환불 접수해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0117: 자동이체와 카드 결제가 같은 달에 둘 다 나갔습니다

납부 수단 변경은 다음 달 청구분부터 적용되어 겹칠 수 있습니다. 이중 납부는 고객센터에 환불 접수해 주세요.

## PIPE-BILLING-0135 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 청구서 나오는 날이랑 내는 날이랑 왜 달라요?

**저장된 답변**

청구서는 매월 10일에 발송되며, 납부 기한은 그 다음 달인 25일까지입니다. 이렇게 일정 시간 간격을 두어 고객님께서 청구서 내용을 확인하고 납부하실 수 있도록 하였습니다.

- UNSUPPORTED: 청구서는 매월 10일에 발송되며, 납부 기한은 그 다음 달인 25일까지입니다.
  - 판단 이유: The claim states that the payment deadline is 'the next month's 25th,' but the FAQ sources only mention '25th of the month' without specifying 'next month.' This introduces a potential ambiguity or contradiction.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested facts: the billing statement is sent on the 10th of each month and the payment deadline is the 25th of the following month, providing time for customers to review and pay.

**RAG에 전달된 실제 근거**

- BILLING-0135: 청구서 나오는 날이랑 내는 날이랑 왜 달라요?

청구서는 매월 10일에 발송되고 납부 기한은 매월 25일이라 확인할 시간을 두는 것입니다.

- BILLING-0156: 청구서 날짜랑 납부 날짜 뭐가 먼저예요

청구서가 10일에 먼저 오고, 25일까지 내는 겁니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0135: 청구서 나오는 날이랑 내는 날이랑 왜 달라요?

청구서는 매월 10일에 발송되고 납부 기한은 매월 25일이라 확인할 시간을 두는 것입니다.

## PIPE-BILLING-0148 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / HUMAN_REVIEW
- 질문: 요금제 변경 적용일과 청구 기준일은 어떻게 다른가요?

**저장된 답변**

요금제 변경은 신청하신 날 다음 날 자정(00:00)부터 적용됩니다. 반면에 청구 기준일은 변경된 요금제 기준으로 그 달 사용량에 따라 일할 계산되어 청구됩니다. 즉, 변경된 요금제의 혜택은 변경 적용일 이후 사용량부터 반영됩니다.

- REVIEW: 변경된 요금제의 혜택은 변경 적용일 이후 사용량부터 반영됩니다.
  - 판단 이유: 이 클레임은 '변경 적용일 이후 사용량'에 대한 명시적인 언급이 FAQ 소스에 없어 확인이 불가능합니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the difference between the application date of the plan change and the billing calculation date, as specified in the reference. It accurately conveys that the plan change takes effect the next day at midnight and that billing is calculated based on usage from the application date onward.

**RAG에 전달된 실제 근거**

- BILLING-0148: 요금제 변경 적용일과 청구 기준일은 어떻게 다른가요?

변경은 신청일 다음 날 00:00부터 적용되고, 그 달 요금은 일할 계산됩니다.

- BILLING-0141: 요금제 변경 신청일과 실제 적용일이 같은가요?

신청일 당일이 아니라 다음 날 00:00부터 적용됩니다. 변경은 월 1회로 제한됩니다.

- BILLING-0050: 미납이 있을 때 요금제 변경 절차가 달라지나요?

요금제 변경은 월 1회, 신청일 다음 날 00:00 적용이라는 기준이 그대로 적용됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0148: 요금제 변경 적용일과 청구 기준일은 어떻게 다른가요?

변경은 신청일 다음 날 00:00부터 적용되고, 그 달 요금은 일할 계산됩니다.

## PIPE-PLAN-0017 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 소진 후 속도 제한 값이 요금제마다 다른가요?

**저장된 답변**

네, 소진 후 속도 제한 값은 요금제에 따라 다릅니다. 일반 요금제의 경우 소진 후 속도가 400kbps로 제한되지만, 언리미티드 요금제에서는 데이터 소진 후에도 속도 제한이 없습니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that the speed limit after data exhaustion varies by plan, specifying that general plans have a 400kbps limit while unlimited plans have no limit. This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- PLAN-0017: 소진 후 속도 제한 값이 요금제마다 다른가요?

일반 요금제는 모두 400kbps로 같습니다. 언리미티드만 제한이 없습니다.

- PLAN-0042: 데이터를 다 쓴 뒤 속도 제한을 피하려면 어떻게 해야 하나요?

언리미티드 요금제로 변경하시면 소진 후에도 속도 제한 없이 이용하실 수 있습니다. 일반 요금제는 소진 후 400kbps로 제한됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0017: 소진 후 속도 제한 값이 요금제마다 다른가요?

일반 요금제는 모두 400kbps로 같습니다. 언리미티드만 제한이 없습니다.

## PIPE-PLAN-0011 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 요금제 몇 종류나 있어요? 이름 말고 그냥 개수만요

**저장된 답변**

총 9종류의 요금제를 운영하고 있습니다.

- UNSUPPORTED: 총 9종류의 요금제를 운영하고 있습니다.
  - 판단 이유: The claim states that there are a total of 9 types of plans, but the FAQ source only mentions 5G (4 types), LTE (3 types), and 알뜰 (2 types), which sum to 9. However, the claim explicitly states '총 9종류' (total of 9 types), while the FAQ source lists them as 4 + 3 + 2 = 9. This appears to be a direct match in terms of the total number, but the way it is phrased in the claim ('총 9종류') is not explicitly confirmed in the FAQ source. The source does not use the exact phrase '총 9종류', and the claim may be interpreted as a general statement that is not directly supported by the source's phrasing. Therefore, it is marked as UNSUPPORTED due to the lack of direct confirmation of the exact wording.

- 하위 질문 1: MISSED
  - 판단 이유: The reference states that there are 5G 4 types, LTE 3 types, and budget 2 types, totaling 9 types. However, the assistant's answer correctly states that there are 9 types of plans, but the reference answer provided in the reference group is different (4+3+2=9). The assistant's answer is accurate, but the reference answer is not aligned with the assistant's answer, which may indicate a conflict or inconsistency in the reference.

**RAG에 전달된 실제 근거**

- PLAN-0011: 요금제 몇 종류나 있어요? 이름 말고 그냥 개수만요

5G 4종, LTE 3종, 알뜰 2종을 운영하고 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0011: 요금제 몇 종류나 있어요? 이름 말고 그냥 개수만요

5G 4종, LTE 3종, 알뜰 2종을 운영하고 있습니다.

## PIPE-PLAN-0044 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 만 65세가 된 뒤 시니어 요금제로 전환하는 절차를 알려주세요.

**저장된 답변**

만 65세 생일이 지나셨다면, 생년월일을 확인할 수 있는 신분증을 준비해 매장이나 고객센터에 방문하시면 시니어 요금제로 쉽게 전환하실 수 있습니다.

- UNSUPPORTED: 시니어 요금제로 쉽게 전환하실 수 있습니다.
  - 판단 이유: The claim '시니어 요금제로 쉽게 전환하실 수 있습니다.' is not directly supported by any of the provided FAQ sources. The sources mention the process of changing plans but do not explicitly mention '시니어 요금제' or '쉽게 전환' in the same context.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully aligns with the reference, accurately conveying the required procedure for converting to the senior plan after turning 65, including the need for a valid ID and contacting a store or customer service.

**RAG에 전달된 실제 근거**

- PLAN-0044: 만 65세가 된 뒤 시니어 요금제로 전환하는 절차를 알려주세요.

생년월일 확인이 가능한 신분증을 지참해 매장이나 고객센터에 변경을 신청하시면 됩니다.

- PLAN-0059: 영감 요금제 시니어로 바꾸려면 어떻게 해요

만 65세 넘으셨으면 신분증 가지고 매장 오시면 바꿔드립니다.

- PLAN-0119: 시니어 요금제로 바꾸려는데 안 된대요

만 65세가 넘어야 됩니다. 신분증으로 확인합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0044: 만 65세가 된 뒤 시니어 요금제로 전환하는 절차를 알려주세요.

생년월일 확인이 가능한 신분증을 지참해 매장이나 고객센터에 변경을 신청하시면 됩니다.

## PIPE-PLAN-0036 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 나이 바뀌어서 요금제 바꾸려는데 어떤 게 있는지 알려주세요.

**저장된 답변**

나이가 변경되어 시니어 요금제 자격이 되셨다면, 만 65세 이상을 증명할 수 있는 신분증을 준비하시고 매장이나 고객센터를 통해 시니어 요금제로 변경 신청을 하실 수 있습니다. 다른 요금제로의 변경도 필요하시다면, 현재 나이와 상황에 맞는 요금제를 확인해 보시는 것이 좋습니다. 자세한 내용은 고객센터에 문의해 주시기 바랍니다.

- UNSUPPORTED: 나이가 변경되어 시니어 요금제 자격이 되셨다면,
  - 판단 이유: The claim implies that the user's age has changed to qualify for the senior plan, but the FAQ sources do not mention anything about age changes or eligibility based on age changes. The sources only mention age requirements for eligibility, not changes in age.

- UNSUPPORTED: 다른 요금제로의 변경도 필요하시다면,
  - 판단 이유: The claim suggests the possibility of changing to other plans, but the FAQ sources do not mention this explicitly. They only mention changing to the senior plan or the youth plan.

- UNSUPPORTED: 현재 나이와 상황에 맞는 요금제를 확인해 보시는 것이 좋습니다.
  - 판단 이유: The claim suggests checking for a plan that matches the user's current age and situation, but this is not directly supported by the FAQ sources.

- UNSUPPORTED: 자세한 내용은 고객센터에 문의해 주시기 바랍니다.
  - 판단 이유: The claim suggests contacting customer service for more details, but this is not explicitly mentioned in the FAQ sources.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides information on how to change to a senior plan and suggests consulting the customer service for other plans, but it does not explicitly list the available plans as requested in the question. The reference group mentions specific plan types (5G, LTE, 알뜰), which are not included in the assistant's answer.

**RAG에 전달된 실제 근거**

- PLAN-0114: 나이 때문에 요금제를 못 바꾼다고 하네요

시니어 요금제는 만 65세 이상만 가입할 수 있어 나이가 안 되시면 변경이 어렵습니다. 신분증으로 확인이 필요합니다.

- PLAN-0044: 만 65세가 된 뒤 시니어 요금제로 전환하는 절차를 알려주세요.

생년월일 확인이 가능한 신분증을 지참해 매장이나 고객센터에 변경을 신청하시면 됩니다.

- PLAN-0049: 청소년 요금제에서 데이터가 부족할 때 변경 절차를 알려주세요.

청소년 요금제는 만 18세 이하 대상입니다. 일반 요금제로 바꾸시려면 매장이나 앱에서 변경 신청을 하시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0036: 나이 바뀌어서 요금제 바꾸려는데 어떤 게 있는지 알려주세요.

5G 4종(라이트·스탠다드·프리미엄·언리미티드), LTE 3종(베이직·플러스·맥스), 알뜰 2종(세이브·미니)이 있습니다.

## PIPE-PLAN-0069 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 데이터 많이 쓰는 청소년도 청소년 요금제 되나요?

**저장된 답변**

네, 만 18세 이하의 청소년이면 데이터를 많이 써도 청소년 요금제에 가입할 수 있습니다. 가입을 위해서는 신분증을 통해 생년월일을 확인해야 합니다.

- UNSUPPORTED: 만 18세 이하의 청소년이면 데이터를 많이 써도 청소년 요금제에 가입할 수 있습니다
  - 판단 이유: The claim states that teenagers under 18 can join the youth plan even if they use a lot of data. However, the FAQ sources do not mention anything about data usage limits or eligibility based on data usage. The sources only confirm age eligibility, not data usage conditions.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that teenagers under 18 can join the youth plan even if they use a lot of data, and it mentions the requirement to confirm the birthdate with an ID. This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- PLAN-0129: 청소년 요금제랑 일반 요금제 중 데이터 많이 쓰면 뭐가 나아요?

청소년 요금제는 만 18세 이하만 가능합니다. 나이가 되면 두 가지를 비교해 보시고, 신분증으로 나이 확인이 필요합니다.

- PLAN-0109: 청소년 요금제인데 데이터가 매달 부족합니다

청소년 요금제는 만 18세 이하 대상입니다. 데이터가 부족하면 기본 제공량이 더 큰 요금제로 변경을 검토해 보세요.

- PLAN-0069: 데이터 많이 쓰는 청소년도 청소년 요금제 되나요?

만 18세 이하면 가입할 수 있습니다. 신분증으로 생년월일 확인이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0069: 데이터 많이 쓰는 청소년도 청소년 요금제 되나요?

만 18세 이하면 가입할 수 있습니다. 신분증으로 생년월일 확인이 필요합니다.

## PIPE-PLAN-0072 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 언리미티드로 바꾸면 속도 제한이 아예 없어지나요?

**저장된 답변**

네, 언리미티드 요금제로 변경하시면 속도 제한이 적용되지 않습니다. 기본 제공량에 따른 속도 제한이 없어지므로 무제한으로 이용 가능합니다.

- UNSUPPORTED: 기본 제공량에 따른 속도 제한이 없어지므로 무제한으로 이용 가능합니다.
  - 판단 이유: The claim mentions '기본 제공량에 따른 속도 제한' (speed limits based on the basic provided amount), which is not explicitly addressed in the FAQ source. The source only states that there are no speed limits, but does not mention the reason being the absence of a basic provided amount.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that switching to an unlimited plan removes speed limits, aligning with the reference that states unlimited plans have no speed restrictions due to the absence of a basic data allowance.

**RAG에 전달된 실제 근거**

- PLAN-0072: 언리미티드로 바꾸면 속도 제한이 아예 없어지나요?

네, 언리미티드 요금제는 기본 제공량 개념이 없어 속도 제한 자체가 적용되지 않습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0072: 언리미티드로 바꾸면 속도 제한이 아예 없어지나요?

네, 언리미티드 요금제는 기본 제공량 개념이 없어 속도 제한 자체가 적용되지 않습니다.

## PIPE-PLAN-0090 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 5만 원 안쪽 요금제 있나요

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 금액: [50000]

**RAG에 전달된 실제 근거**

- PLAN-0090: 5만 원 안쪽 요금제 있나요

네, 5G 라이트가 45,000원, LTE 플러스가 49,000원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0090: 5만 원 안쪽 요금제 있나요

네, 5G 라이트가 45,000원, LTE 플러스가 49,000원입니다.

## PIPE-PLAN-0066 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 알뜰 요금제로 5G를 쓸 수 있어요?

**저장된 답변**

네, 알뜰 요금제로도 5G 서비스를 이용하실 수 있습니다. 현재 세이브와 미니 두 가지 알뜰 요금제 중에서 5G를 지원하는 요금제가 별도로 4종이 준비되어 있습니다. 어떤 요금제가 적합하신지 자세히 알고 싶으시다면, 자세한 조건과 혜택을 확인해 보시는 것을 추천드립니다.

- UNSUPPORTED: 자세한 조건과 혜택을 확인해 보시는 것을 추천드립니다.
  - 판단 이유: The claim is not directly supported by the provided FAQ source, which does not mention anything about recommending users to check detailed conditions and benefits.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that 5G service can be used with the 'Save' and 'Mini' budget plans, and that there are 4 separate 5G plans available. This fully aligns with the reference group's information.

**RAG에 전달된 실제 근거**

- PLAN-0066: 알뜰 요금제로 5G를 쓸 수 있어요?

알뜰 요금제는 세이브·미니 2종이고, 5G 요금제는 별도로 4종이 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0066: 알뜰 요금제로 5G를 쓸 수 있어요?

알뜰 요금제는 세이브·미니 2종이고, 5G 요금제는 별도로 4종이 있습니다.

## PIPE-PLAN-0106 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 요금 절감을 위해 요금제 라인업을 봤는데 구분이 헷갈립니다

**저장된 답변**

데이터 사용량을 기준으로 요금제를 선택하시면 도움이 될 것입니다. 데이터 사용량에 맞는 요금제를 찾아보세요.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides general advice on selecting a plan based on data usage but does not fully address the user's confusion about the specific plan lineup. The reference group provides the specific plan categories (5G, LTE, 알뜰), which were not mentioned in the assistant's answer.

**RAG에 전달된 실제 근거**

- PLAN-0046: 요금 절감을 위해 요금제 라인업을 비교하는 절차를 알려주세요.

5G 4종, LTE 3종, 알뜰 2종의 구성을 먼저 파악하고, 데이터 사용량에 맞는 등급을 고르면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0106: 요금 절감을 위해 요금제 라인업을 봤는데 구분이 헷갈립니다

5G 4종(라이트·스탠다드·프리미엄·언리미티드), LTE 3종(베이직·플러스·맥스), 알뜰 2종(세이브·미니)으로 나뉩니다.

## PIPE-PLAN-0108 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 성인이 되면서 요금제를 바꿨더니 이월 데이터가 줄었습니다

**저장된 답변**

성인이 되면서 요금제를 변경하셨군요. 이월 데이터는 변경된 요금제 기준으로 잔여 데이터의 50%까지만 다음 달로 이어집니다. 따라서 요금제 변경으로 인해 이월 데이터가 줄어든 것으로 보입니다.

- UNSUPPORTED: 요금제 변경으로 인해 이월 데이터가 줄어든 것으로 보입니다.
  - 판단 이유: None of the provided FAQ sources mention anything about the amount of carried-over data decreasing due to a plan change. The claim is not directly supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that only 50% of the remaining data can be carried over to the next month under the new plan, which explains why the carried-over data has decreased after the plan change. This aligns with the reference provided.

**RAG에 전달된 실제 근거**

- PLAN-0108: 성인이 되면서 요금제를 바꿨더니 이월 데이터가 줄었습니다

이월은 잔여량의 50%까지입니다. 남은 양 전부가 넘어가지는 않습니다.

- PLAN-0068: 성인이 되면 이월 데이터가 사라지나요?

이월은 요금제 기준으로 잔여량의 50%까지 다음 달로 넘어갑니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0108: 성인이 되면서 요금제를 바꿨더니 이월 데이터가 줄었습니다

이월은 잔여량의 50%까지입니다. 남은 양 전부가 넘어가지는 않습니다.

## PIPE-PLAN-0150 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 싼 거랑 비싼 거랑 얼마 차이예요

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 금액: [90000]

**RAG에 전달된 실제 근거**

- PLAN-0150: 싼 거랑 비싼 거랑 얼마 차이예요

제일 싼 건 15,000원, 제일 비싼 건 105,000원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0150: 싼 거랑 비싼 거랑 얼마 차이예요

제일 싼 건 15,000원, 제일 비싼 건 105,000원입니다.

## PIPE-PLAN-0135 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 가족 4명이 5G 프리미엄과 LTE 맥스로 나눌 때 요금 차이는 어떻게 되나요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 금액: [16000, 64000]

**RAG에 전달된 실제 근거**

- PLAN-0135: 가족 4명이 5G 프리미엄과 LTE 맥스로 나눌 때 요금 차이는 어떻게 되나요?

5G 프리미엄은 85,000원에 150GB, LTE 맥스는 69,000원에 100GB입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0135: 가족 4명이 5G 프리미엄과 LTE 맥스로 나눌 때 요금 차이는 어떻게 되나요?

5G 프리미엄은 85,000원에 150GB, LTE 맥스는 69,000원에 100GB입니다.

## PIPE-PLAN-0121 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 5G랑 LTE랑 뭐가 달라요?

**저장된 답변**

5G와 LTE는 속도와 기술적 차이가 있습니다. - **지연 시간**: 5G는 저지연 특성을 갖추고 있어 실시간 응용 프로그램에 더 적합합니다. - **연결 밀도**: 5G는 더 많은 기기를 동시에 연결할 수 있는 능력이 있습니다. 요금제 구성도 다릅니다: 5G는 라이트, 스탠다드, 프리미엄, 언리미티드 등 4가지 종류가 있으며, LTE는 베이직, 플러스, 맥스 등 3가지 종류로 나뉩니다. 추가로 알뜰 요금제도 선택 가능합니다.

**grounding 검증 오류**: ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the differences between 5G and LTE, including technical aspects like latency and connection density, as well as the variety of plans available for each (4 for 5G and 3 for LTE, with additional budget plans). This matches the reference group's information.

**RAG에 전달된 실제 근거**

- PLAN-0121: 5G랑 LTE랑 뭐가 달라요?

5G는 라이트·스탠다드·프리미엄·언리미티드 4종, LTE는 베이직·플러스·맥스 3종으로 구성이 다릅니다. 알뜰 요금제 2종도 별도로 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0121: 5G랑 LTE랑 뭐가 달라요?

5G는 라이트·스탠다드·프리미엄·언리미티드 4종, LTE는 베이직·플러스·맥스 3종으로 구성이 다릅니다. 알뜰 요금제 2종도 별도로 있습니다.

## PIPE-PLAN-0145 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 제일 싼 거랑 제일 비싼 요금제 차이가 얼마나 나요

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 금액: [90000]

**RAG에 전달된 실제 근거**

- PLAN-0120: 요금제 값이 왜 이래요

제일 싼 게 15,000원, 제일 비싼 게 105,000원입니다. 어떤 걸 쓰시는지 확인해 보세요.

- PLAN-0145: 제일 싼 거랑 제일 비싼 요금제 차이가 얼마나 나요

가장 저렴한 알뜰 미니는 월 15,000원에 1.5GB이고, 가장 비싼 5G 언리미티드는 월 105,000원에 데이터 무제한입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0145: 제일 싼 거랑 제일 비싼 요금제 차이가 얼마나 나요

가장 저렴한 알뜰 미니는 월 15,000원에 1.5GB이고, 가장 비싼 5G 언리미티드는 월 105,000원에 데이터 무제한입니다.

## PIPE-PLAN-0137 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 400kbps 제한과 언리미티드의 실사용 차이가 큰가요?

**저장된 답변**

네, 차이가 큽니다. 400kbps 제한 상태에서는 대용량 파일 다운로드나 스트리밍 등 데이터를 많이 사용하는 활동이 느려질 수 있습니다. 반면에 언리미티드 플랜은 데이터 사용에 속도 제한이 없어 원활한 사용이 가능합니다.

**grounding 검증 오류**: ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the difference between 400kbps limitation and unlimited plans, including the impact on data-heavy activities and the absence of speed restrictions in unlimited plans, aligning with the reference.

**RAG에 전달된 실제 근거**

- PLAN-0137: 400kbps 제한과 언리미티드의 실사용 차이가 큰가요?

400kbps는 속도 제한 상태라 대용량 이용이 어렵고, 언리미티드는 제한 없이 이용됩니다.

- PLAN-0142: 느린 거랑 무제한이랑 뭐가 달라요

다 쓰면 400kbps로 느려지는데, 언리미티드는 안 느려집니다.

- PLAN-0127: 가족 중 언리미티드 쓰는 사람이랑 아닌 사람이랑 뭐가 달라요?

언리미티드는 속도 제한이 없고, 나머지는 기본 데이터 소진 후 400kbps로 제한됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0137: 400kbps 제한과 언리미티드의 실사용 차이가 큰가요?

400kbps는 속도 제한 상태라 대용량 이용이 어렵고, 언리미티드는 제한 없이 이용됩니다.

## PIPE-DEVICE-0004 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 배터리가 이상해서 새 폰 반품하려는데 기한이 얼마예요?

**저장된 답변**

개통 후 14일 이내이고 새 폰을 미개봉 상태로 유지하셨다면 반품이 가능합니다. 한 번에 한하여 적용됩니다.

- UNSUPPORTED: 개통 후 14일 이내이고 새 폰을 미개봉 상태로 유지하셨다면 반품이 가능합니다.
  - 판단 이유: The claim specifies '미개봉 상태로 유지하셨다면' (if kept unopened), but the FAQ sources mention '미개봉이거나 사용 흔적이 없는 상태여야 합니다' (must be unopened or have no usage traces) and '안 쓴 상태여야 바꿔드립니다' (must be unused). The condition in the claim is narrower than the FAQ evidence, which allows for unused but possibly opened devices. This discrepancy makes the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the required information about the 14-day return window and the condition of the phone being unopened, as specified in the reference.

**RAG에 전달된 실제 근거**

- DEVICE-0004: 배터리가 이상해서 새 폰 반품하려는데 기한이 얼마예요?

개통 후 14일 이내이고 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 1회에 한해 가능합니다.

- DEVICE-0104: 새 폰 배터리가 이상한데 안 바꿔줘요

14일 안에 안 쓴 상태여야 바꿔드립니다. 쓰셨으면 수리를 받으셔야 합니다.

- DEVICE-0024: 배터리 이상한 새 폰 바꿔줘요

개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0004: 배터리가 이상해서 새 폰 반품하려는데 기한이 얼마예요?

개통 후 14일 이내이고 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 1회에 한해 가능합니다.

## PIPE-DEVICE-0024 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 배터리 이상한 새 폰 바꿔줘요

**저장된 답변**

개통 후 14일 이내에 사용하지 않은 상태라면 배터리 문제로 새 폰으로 교체해 드릴 수 있습니다.

- UNSUPPORTED: 개통 후 14일 이내에 사용하지 않은 상태라면 배터리 문제로 새 폰으로 교체해 드릴 수 있습니다.
  - 판단 이유: The claim specifies that a new phone can be replaced due to battery issues if it is unused for 14 days after activation. However, the FAQ sources only mention that a replacement is provided if the device is unused for 14 days after activation, without explicitly stating that the reason for replacement is battery issues. The condition of 'battery issue' is not mentioned in the FAQ sources, making the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the required fact that if the device is unused for 14 days after activation, it can be replaced due to battery issues.

**RAG에 전달된 실제 근거**

- DEVICE-0024: 배터리 이상한 새 폰 바꿔줘요

개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

- DEVICE-0104: 새 폰 배터리가 이상한데 안 바꿔줘요

14일 안에 안 쓴 상태여야 바꿔드립니다. 쓰셨으면 수리를 받으셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0024: 배터리 이상한 새 폰 바꿔줘요

개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

## PIPE-DEVICE-0070 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요?

**저장된 답변**

개통 후 6개월이 지나더라도 할부금 완납 없이는 기기변경이 불가능합니다. 기기변경을 위해서는 기존 할부금을 완납하거나 새 할부 조건으로 승계해야 합니다.

- UNSUPPORTED: 개통 후 6개월이 지나더라도 할부금 완납 없이는 기기변경이 불가능합니다.
  - 판단 이유: The claim states that device change is impossible without full repayment of installments even after 6 months. However, the FAQ sources indicate that after 6 months, the user can change the device by either repaying the remaining installments or transferring to a new installment plan. The claim incorrectly implies that device change is impossible without full repayment, which contradicts the FAQ evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by stating that even after 6 months from activation, device change is not possible without full repayment of the installment or by transferring to a new installment plan. This aligns with the reference provided.

**RAG에 전달된 실제 근거**

- DEVICE-0070: 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요?

기존 할부금을 완납하거나 새 할부에 승계해야 합니다. 둘 중 하나는 필요합니다.

- DEVICE-0046: 기기 바꾸려면 어떻게 해요

개통하고 6개월이 지나면 바꾸실 수 있습니다. 남은 할부금은 다 내시거나 새 할부로 넘기시면 됩니다.

- DEVICE-0010: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요.

개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0070: 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요?

기존 할부금을 완납하거나 새 할부에 승계해야 합니다. 둘 중 하나는 필요합니다.

## PIPE-DEVICE-0072 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 액정 보호필름을 붙였는데 교환이 가능한가요?

**저장된 답변**

액정 보호필름을 붙였더라도 교환이 가능합니다. 하지만 교환을 위해서는 제품이 미개봉 상태이거나 사용 흔적이 전혀 없어야 합니다. 필름 부착 여부는 사용 흔적으로 간주될 수 있으니 매장에서 확인받으시는 것이 좋습니다.

- UNSUPPORTED: 액정 보호필름을 붙였더라도 교환이 가능합니다.
  - 판단 이유: FAQ sources indicate that film attachment is considered usage痕迹, which would make exchange difficult. The claim directly contradicts this.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the product can be exchanged even with a screen protector attached, but it must be unopened and have no signs of use. It also clarifies that the presence of a screen protector may be considered a sign of use and recommends checking with the store, which aligns with the reference.

**RAG에 전달된 실제 근거**

- DEVICE-0072: 액정 보호필름을 붙였는데 교환이 가능한가요?

교환은 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 필름 부착은 사용 흔적으로 볼 수 있어 매장 확인이 필요합니다.

- DEVICE-0092: 산 지 일주일 됐는데 액정에 문제가 있어서 교환하고 싶은데 이미 좀 썼습니다

교환은 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태에서만 가능합니다. 사용한 흔적이 있으면 교환이 어려울 수 있으니 매장에서 상태를 확인받아 보시기 바랍니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0072: 액정 보호필름을 붙였는데 교환이 가능한가요?

교환은 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 필름 부착은 사용 흔적으로 볼 수 있어 매장 확인이 필요합니다.

## PIPE-DEVICE-0080 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 바꾼 지 며칠 안 된 폰 다시 바꿀 수 있나요

**저장된 답변**

개통 후 14일 이내에 사용하지 않은 상태라면 한 번 더 기기 변경이 가능합니다.

- UNSUPPORTED: 개통 후 14일 이내에 사용하지 않은 상태라면 한 번 더 기기 변경이 가능합니다.
  - 판단 이유: The claim states that a device change is possible once if it is unused within 14 days of activation. However, the FAQ sources mention that a return or exchange is possible within 14 days if the device is unopened or shows no usage, but they do not explicitly state that a device change is allowed. Additionally, DEVICE-0086 explicitly states that device changes are only possible after 6 months, which contradicts the claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the required fact that a device can be changed once again within 14 days if it has not been used.

**RAG에 전달된 실제 근거**

- DEVICE-0080: 바꾼 지 며칠 안 된 폰 다시 바꿀 수 있나요

14일 안에 안 쓴 상태면 한 번 됩니다.

- DEVICE-0020: 산 지 얼마 안 된 폰 바꿀 수 있나요

개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태라면 반품이나 교환이 가능합니다. 다만 1회만 가능합니다.

- DEVICE-0086: 폰 잃어버려서 새 폰으로 바꾸려는데 6개월이 안 됐대요

기기 변경은 개통 후 6개월이 지나야 가능합니다. 그 전에는 새 회선 개통이나 수리 등 다른 방법을 안내받으세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0080: 바꾼 지 며칠 안 된 폰 다시 바꿀 수 있나요

14일 안에 안 쓴 상태면 한 번 됩니다.

## PIPE-DEVICE-0095 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기변경 전 수리를 받았는데 자기부담금이 30%보다 많이 나왔습니다

**저장된 답변**

자기부담금은 보험 가입 시 수리비의 30%로 정해져 있습니다. 청구 내역을 다시 한번 확인해 주시고, 만약 예상보다 높게 나왔다면 보험 가입 상태와 수리 내역을 제조사 서비스센터에 문의해 보시는 것을 권장드립니다.

- UNSUPPORTED: 만약 예상보다 높게 나왔다면 보험 가입 상태와 수리 내역을 제조사 서비스센터에 문의해 보시는 것을 권장드립니다.
  - 판단 이유: 이 주장은 FAQ 소스에 명시된 내용과 직접적으로 일치하지 않습니다. FAQ 소스는 보험 가입 상태와 수리 내역을 확인하라고 요청하지만, 제조사 서비스센터에 문의하라고 권장하는 내용은 없습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the deductible is 30% of the repair cost and advises the user to check their insurance status and repair details with the manufacturer's service center if the deductible is higher than expected. This fully addresses the user's question about the deductible being higher than 30%.

**RAG에 전달된 실제 근거**

- DEVICE-0095: 기기변경 전 수리를 받았는데 자기부담금이 30%보다 많이 나왔습니다

보험 가입 시 자기부담금은 수리비의 30%입니다. 보험 가입 여부와 청구 내역을 확인해 주세요.

- DEVICE-0015: 기기변경 전 파손 수리 시 자기부담금 비율을 알려주세요.

단말 보험 가입 시 수리비의 30%를 자기부담금으로 냅니다. 수리는 제조사 서비스센터에서 합니다.

- DEVICE-0059: 배터리 교체도 보험 자기부담금이 적용될까요?

보험 가입 시 수리비의 30%를 자기부담금으로 냅니다. 수리는 제조사 서비스센터에서 진행합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0095: 기기변경 전 수리를 받았는데 자기부담금이 30%보다 많이 나왔습니다

보험 가입 시 자기부담금은 수리비의 30%입니다. 보험 가입 여부와 청구 내역을 확인해 주세요.

## PIPE-DEVICE-0102 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 액정 깨진 폰 바꾸러 갔는데 안 된대요

**저장된 답변**

개통 후 6개월이 지나지 않아 교체가 어렵습니다. 현재는 수리를 받으셔야 합니다. 자세한 수리 절차는 가까운 서비스 센터를 방문하시거나 연락주시면 안내해 드리겠습니다.

- UNSUPPORTED: 개통 후 6개월이 지나지 않아 교체가 어렵습니다.
  - 판단 이유: The claim states that replacement is difficult within 6 months of activation, but the FAQ sources do not mention anything about replacement difficulty. The sources only mention repair requirements and the 6-month period for activation.

- UNSUPPORTED: 자세한 수리 절차는 가까운 서비스 센터를 방문하시거나 연락주시면 안내해 드리겠습니다.
  - 판단 이유: The claim provides specific instructions about contacting a service center for repair procedures, but none of the FAQ sources mention this information.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the phone cannot be replaced within 6 months of activation and that repair is necessary. It also provides the correct procedure for seeking repair assistance, which aligns with the reference.

**RAG에 전달된 실제 근거**

- DEVICE-0102: 액정 깨진 폰 바꾸러 갔는데 안 된대요

개통하고 6개월이 안 돼서 그렇습니다. 그 전엔 수리를 받으셔야 합니다.

- DEVICE-0022: 액정 깨진 폰 바꾸려면 언제 돼요

개통하고 6개월 지나야 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0102: 액정 깨진 폰 바꾸러 갔는데 안 된대요

개통하고 6개월이 안 돼서 그렇습니다. 그 전엔 수리를 받으셔야 합니다.

## PIPE-DEVICE-0096 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 분실 신고 후 찾은 새 폰을 반품하려는데 15일째입니다

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [15일]

**RAG에 전달된 실제 근거**

- DEVICE-0096: 분실 신고 후 찾은 새 폰을 반품하려는데 15일째입니다

반품은 개통 후 14일 이내에만 가능합니다. 기한이 지나면 반품이 어렵습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0096: 분실 신고 후 찾은 새 폰을 반품하려는데 15일째입니다

반품은 개통 후 14일 이내에만 가능합니다. 기한이 지나면 반품이 어렵습니다.

## PIPE-DEVICE-0105 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기 바꿨는데 이자가 붙어요

**저장된 답변**

기기 변경 시 할부 이용 시 연 5.9%의 이자가 적용될 수 있습니다.

- UNSUPPORTED: 기기 변경 시 할부 이용 시 연 5.9%의 이자가 적용될 수 있습니다.
  - 판단 이유: The claim specifies that the interest rate of 5.9% applies when using installment payments during a device change. However, the FAQ source only states that installment payments incur a 5.9% annual interest rate without mentioning the condition of a device change. Since the condition of 'device change' is not explicitly mentioned in the FAQ source, the claim cannot be fully supported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact about the interest rate (5.9%) applied when using installment payments for a device change, as stated in the reference.

**RAG에 전달된 실제 근거**

- DEVICE-0105: 기기 바꿨는데 이자가 붙어요

할부는 연 5.9% 이자가 붙습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0105: 기기 바꿨는데 이자가 붙어요

할부는 연 5.9% 이자가 붙습니다.

## PIPE-DEVICE-0119 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 배터리 수리 시 보험 가입자와 미가입자의 부담 차이는 얼마나 되나요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [70%]

**RAG에 전달된 실제 근거**

- DEVICE-0119: 배터리 수리 시 보험 가입자와 미가입자의 부담 차이는 얼마나 되나요?

가입자는 수리비의 30%만 부담하고, 미가입자는 전액을 부담합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0119: 배터리 수리 시 보험 가입자와 미가입자의 부담 차이는 얼마나 되나요?

가입자는 수리비의 30%만 부담하고, 미가입자는 전액을 부담합니다.

## PIPE-DEVICE-0111 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 분실이랑 파손이랑 처리가 어떻게 달라요?

**저장된 답변**

분실과 파손의 처리 방법이 다릅니다. 분실의 경우 즉시 이용 정지를 신청해야 하며, 파손의 경우에는 제조사의 서비스센터를 통해 수리를 진행하시면 됩니다. 만약 파손 시 보험에 가입되어 있다면 수리비의 30%만 부담하시면 됩니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the differences in handling between loss and damage, including the steps for each scenario and the insurance-related cost reduction for damage. All required facts from the reference are accurately conveyed.

**RAG에 전달된 실제 근거**

- DEVICE-0111: 분실이랑 파손이랑 처리가 어떻게 달라요?

분실은 즉시 이용 정지를 신청하고, 파손은 제조사 서비스센터에서 수리합니다. 보험이 있으면 수리비의 30%만 냅니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0111: 분실이랑 파손이랑 처리가 어떻게 달라요?

분실은 즉시 이용 정지를 신청하고, 파손은 제조사 서비스센터에서 수리합니다. 보험이 있으면 수리비의 30%만 냅니다.

## PIPE-DEVICE-0110 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기변경이랑 새 회선 개통이랑 뭐가 달라요?

**저장된 답변**

기기변경과 새 회선 개통의 주요 차이점은 다음과 같습니다. 기기변경은 기존에 사용 중인 단말기를 새로운 모델로 교체하는 것을 의미하며, 이 과정에서는 개통 후 6개월이 경과해야 하며, 기존 단말기의 할부금을 완납하거나 승계해야 합니다. 반면에 새 회선 개통은 완전히 새로운 통신 계약을 시작하는 것으로, 기존 단말기와 관련 없이 새로운 기기를 구매하고 개통하는 방식입니다. 따라서 기존 계약이나 단말기 상태에 따라 선택이 달라질 수 있습니다.

- UNSUPPORTED: 기기변경은 기존에 사용 중인 단말기를 새로운 모델로 교체하는 것을 의미하며
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ only mentions conditions for device change, not its definition.

- UNSUPPORTED: 새 회선 개통은 완전히 새로운 통신 계약을 시작하는 것으로
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ does not mention new line activation or its definition.

- UNSUPPORTED: 기존 단말기와 관련 없이 새로운 기기를 구매하고 개통하는 방식입니다
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ does not mention this specific method of new device purchase.

- UNSUPPORTED: 기존 계약이나 단말기 상태에 따라 선택이 달라질 수 있습니다
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ does not mention that choices depend on existing contracts or device status.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the difference between device change and new line activation, including the required conditions such as waiting 6 months after activation and settling the existing installment payments.

**RAG에 전달된 실제 근거**

- DEVICE-0110: 기기변경이랑 새 회선 개통이랑 뭐가 달라요?

기기변경은 개통 후 6개월이 지나야 하고 기존 할부금을 완납하거나 승계해야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0110: 기기변경이랑 새 회선 개통이랑 뭐가 달라요?

기기변경은 개통 후 6개월이 지나야 하고 기존 할부금을 완납하거나 승계해야 합니다.

## PIPE-DEVICE-0117 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?

**저장된 답변**

액정 파손 후 새 단말을 구매할 때, 24개월과 30개월 할부 모두 연 수수료율은 동일하게 5.9%입니다. 하지만 할부 기간이 더 긴 30개월의 경우 총 수수료가 더 많아집니다. 구체적인 금액 차이는 구매 금액에 따라 달라지므로 정확한 수치는 제공된 정보로는 안내드릴 수 없습니다. 할부 기간 선택 시 총 비용을 고려하시는 것이 좋습니다.

- UNSUPPORTED: 구체적인 금액 차이는 구매 금액에 따라 달라지므로 정확한 수치는 제공된 정보로는 안내드릴 수 없습니다
  - 판단 이유: FAQ에서 구체적인 금액 차이에 대한 언급이 없으며, 제공된 정보로는 안내가 불가능하다는 내용도 없음.

- UNSUPPORTED: 할부 기간 선택 시 총 비용을 고려하시는 것이 좋습니다
  - 판단 이유: FAQ에서 총 비용을 고려하는 것이 좋다는 조언은 없음.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the annual interest rate is the same (5.9%) for both 24-month and 30-month installment plans, and correctly notes that the total interest is higher for the longer 30-month period. It also appropriately states that the exact amount depends on the purchase price and cannot be specified with the provided information.

**RAG에 전달된 실제 근거**

- DEVICE-0117: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?

수수료율은 둘 다 연 5.9%로 같지만, 30개월이 기간이 길어 총 수수료가 더 많습니다.

- DEVICE-0017: 액정 파손 후 새 단말 할부 수수료율을 알려주세요.

연 5.9%입니다. 할부 기간은 24개월 또는 30개월입니다.

- DEVICE-0097: 액정 파손으로 새 폰을 샀는데 30개월 할부가 안 된다고 합니다

단말 할부는 24개월 또는 30개월 중 선택할 수 있습니다. 매장에서 조건을 다시 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0117: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?

수수료율은 둘 다 연 5.9%로 같지만, 30개월이 기간이 길어 총 수수료가 더 많습니다.

## PIPE-SUBSCRIBE-0012 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 자녀 명의 회선을 포함해 성인 1인이 개통 가능한 회선 수를 알려주세요.

**저장된 답변**

성인 1인이 본인 명의로 개통 가능한 회선 수는 최대 5회선입니다. 자녀 명의 회선은 이 제한 수에 포함되지 않습니다.

- UNSUPPORTED: 자녀 명의 회선은 이 제한 수에 포함되지 않습니다.
  - 판단 이유: The provided FAQ sources do not mention anything about child-named lines or whether they are excluded from the limit. Therefore, the claim cannot be supported by the given evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that an adult can open up to 5 lines under their own name, and clarifies that child-named lines are not included in this limit. This fully addresses the user's question.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0012: 자녀 명의 회선을 포함해 성인 1인이 개통 가능한 회선 수를 알려주세요.

성인은 본인 명의로 최대 5회선까지 개통할 수 있습니다. 법인 명의는 별도 심사입니다.

- SUBSCRIBE-0072: 자녀 회선을 제 명의 회선 수에 포함해 계산하나요?

본인 명의로 개통한 회선만 성인 1인 최대 5회선에 포함됩니다.

- SUBSCRIBE-0056: 회선을 하나 더 만들 수 있을까요?

성인 1인당 최대 5회선까지 개통할 수 있어 그 한도 안이라면 가능합니다. 법인 명의로 하시는 경우에는 별도 심사를 거칩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0012: 자녀 명의 회선을 포함해 성인 1인이 개통 가능한 회선 수를 알려주세요.

성인은 본인 명의로 최대 5회선까지 개통할 수 있습니다. 법인 명의는 별도 심사입니다.

## PIPE-SUBSCRIBE-0019 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 가입하면 언제부터 되나요

**저장된 답변**

가입하신 방법에 따라 다릅니다. 매장에서 가입하시면 가입 즉시 서비스 이용이 가능합니다. 인터넷을 통해 신청하신 경우에는 다음 날부터 서비스 이용이 가능하며, 신청 시간은 오전 9시부터 오후 8시까지입니다.

- UNSUPPORTED: 매장에서 가입하시면 가입 즉시 서비스 이용이 가능합니다.
  - 판단 이유: FAQ 답변에 따르면 매장에서 가입할 경우 '30분 안에 됩니다'라고 되어 있으며, '즉시' 서비스 이용이 가능하다는 명시는 없습니다. 따라서 '가입 즉시 서비스 이용이 가능합니다'라는 주장은 FAQ의 정보와 일치하지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question by specifying the service availability based on the subscription method. It accurately conveys that in-store sign-up allows immediate service, while online sign-up enables service the next day between 9 AM and 8 PM, which aligns with the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0019: 가입하면 언제부터 되나요

매장에서 하시면 30분 안에 됩니다. 인터넷으로 신청하시면 다음 날 되고, 09:00부터 20:00까지 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0019: 가입하면 언제부터 되나요

매장에서 하시면 30분 안에 됩니다. 인터넷으로 신청하시면 다음 날 되고, 09:00부터 20:00까지 처리됩니다.

## PIPE-SUBSCRIBE-0005 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 두 번째 폰 만들 때도 신분증 가져가야 해요?

**저장된 답변**

네, 두 번째 폰을 만들 때도 본인 명의의 신분증(주민등록증, 운전면허증, 여권 등)을 가져가셔야 합니다.

- UNSUPPORTED: 두 번째 폰을 만들 때도 본인 명의의 신분증(주민등록증, 운전면허증, 여권 등)을 가져가셔야 합니다.
  - 판단 이유: The claim specifies that a second phone requires bringing a personal ID (resident registration, driver's license, passport, etc.), but the FAQ sources do not mention anything about a 'second phone' or any distinction between first and second devices. The FAQ sources only state that a personal ID is required, but they do not confirm that this applies specifically to a second phone.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the required fact that a personal ID (such as a resident registration card, driver's license, or passport) is needed when making a second phone, as stated in the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0005: 두 번째 폰 만들 때도 신분증 가져가야 해요?

네, 본인 명의 신분증(주민등록증, 운전면허증, 여권 중 하나)이 필요합니다.

- SUBSCRIBE-0085: 세컨드폰 만들러 갔는데 신분증이 만료됐대요

유효한 본인 명의 신분증이 필요합니다. 운전면허증이나 여권 등 다른 유효한 신분증을 가져가시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0005: 두 번째 폰 만들 때도 신분증 가져가야 해요?

네, 본인 명의 신분증(주민등록증, 운전면허증, 여권 중 하나)이 필요합니다.

## PIPE-SUBSCRIBE-0036 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 첫 개통인데 회선 한도는 어떻게 확인해요?

**저장된 답변**

첫 개통이시라면 회선 한도 걱정 없이 개통하실 수 있습니다. 성인 기준으로는 1인당 최대 5회선까지 개통 가능합니다. 현재 본인 명의의 개통 이력이 없다면 한도 내에서 자유롭게 개통하실 수 있습니다.

- UNSUPPORTED: 현재 본인 명의의 개통 이력이 없다면 한도 내에서 자유롭게 개통하실 수 있습니다.
  - 판단 이유: FAQ 답변 중 '첫 개통이면 한도 걱정은 없습니다.'라는 문장은 '한도 내에서 자유롭게 개통'이라는 의미와 유사하지만, '본인 명의의 개통 이력이 없다면'이라는 조건은 명시적으로 언급되지 않았습니다. 따라서 클레임의 조건이 FAQ에서 명확히 지원되지 않아 UNSUPPORTED로 판단됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question about how to check the line limit for a first-time opening. It states that there is no limit concern for first-time openings and specifies the maximum number of lines (5) for adults, which aligns with the reference group's information.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0036: 첫 개통인데 회선 한도는 어떻게 확인해요?

성인은 1인당 최대 5회선까지 개통할 수 있습니다. 첫 개통이면 한도 걱정은 없습니다.

- SUBSCRIBE-0096: 첫 개통인데 회선 한도 초과라고 나옵니다

성인은 1인당 최대 5회선입니다. 본인 명의로 이미 개통된 회선이 있는지 확인해 보세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0036: 첫 개통인데 회선 한도는 어떻게 확인해요?

성인은 1인당 최대 5회선까지 개통할 수 있습니다. 첫 개통이면 한도 걱정은 없습니다.

## PIPE-SUBSCRIBE-0052 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 자식들 폰 다 만들려면 몇 개까지 돼요

**저장된 답변**

자식들 폰을 만들 수 있는 최대 회선 수는 한 사람 이름으로 5개입니다.

- UNSUPPORTED: 자식들 폰을 만들 수 있는 최대 회선 수는 한 사람 이름으로 5개입니다.
  - 판단 이유: The claim specifies '자식들 폰' (children's phones) and mentions '한 사람 이름으로 5개' (5 lines under one person's name). The FAQ source states '한 사람 이름으로 5회선까지 됩니다' (up to 5 lines under one person's name), but it does not explicitly mention '자식들 폰' or children's phones. The difference in wording and the specific context of 'children's phones' being absent in the FAQ source makes the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact that the maximum number of lines for children's phones under one person's name is 5, as stated in the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0052: 자식들 폰 다 만들려면 몇 개까지 돼요

한 사람 이름으로 5회선까지 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0052: 자식들 폰 다 만들려면 몇 개까지 돼요

한 사람 이름으로 5회선까지 됩니다.

## PIPE-SUBSCRIBE-0051 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS, ROUTING_MISMATCH
- 질문: 처음 만드는 건데 매장 가면 바로 돼요

**저장된 답변**

어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- 하위 질문 1: PARTIAL
  - 판단 이유: The assistant's answer does not fully resolve the user's question. While it asks for the location, it does not provide the information that the user is seeking, which is the time required to complete the process. The reference group provides the answer that it takes 30 minutes, but the assistant's response does not include this information.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0051: 처음 만드는 건데 매장 가면 바로 돼요

네, 30분 안에 됩니다.

## PIPE-SUBSCRIBE-0077 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 아들 대신 제가 가서 아들 폰 만들 수 있나요

**저장된 답변**

아들 명의로 폰을 만들기 위해서는 아드님의 신분증이 필요합니다. 현재 아드님의 신분증을 직접 확인하실 수 없다면, 해당 절차를 진행하기 어렵습니다. 아드님께서 직접 방문하시거나, 필요한 경우 아드님의 동의를 얻어 진행하시는 것을 권장드립니다.

- UNSUPPORTED: 현재 아드님의 신분증을 직접 확인하실 수 없다면, 해당 절차를 진행하기 어렵습니다.
  - 판단 이유: The FAQ source does not mention anything about the difficulty of proceeding if the son's ID cannot be verified directly.

- UNSUPPORTED: 아드님께서 직접 방문하시거나, 필요한 경우 아드님의 동의를 얻어 진행하시는 것을 권장드립니다.
  - 판단 이유: The FAQ source does not mention anything about recommending the son to visit in person or obtaining consent.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the required fact that the son's ID is needed to create a phone under his name and explains the necessary steps if the son cannot be present, aligning with the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0077: 아들 대신 제가 가서 아들 폰 만들 수 있나요

아들 명의로 하려면 아들 본인 신분증이 있어야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0077: 아들 대신 제가 가서 아들 폰 만들 수 있나요

아들 명의로 하려면 아들 본인 신분증이 있어야 합니다.

## PIPE-SUBSCRIBE-0068 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 외국인 법인 대표도 별도 심사 대상인가요?

**저장된 답변**

외국인 법인 대표라도 법인 명의 가입 시 별도의 심사를 받게 됩니다. 국적에 관계없이 동일한 절차가 적용됩니다.

- UNSUPPORTED: 국적에 관계없이 동일한 절차가 적용됩니다.
  - 판단 이유: The FAQ source mentions a separate review regardless of the representative's nationality, but it does not explicitly state that the same procedure is applied regardless of nationality. The claim about '동일한 절차' (same procedure) is not directly supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that foreign corporate representatives are subject to separate review when joining under the company's name, and that the process is the same regardless of nationality, which aligns with the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0068: 외국인 법인 대표도 별도 심사 대상인가요?

법인 명의 가입은 대표 국적과 관계없이 별도 심사를 거칩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0068: 외국인 법인 대표도 별도 심사 대상인가요?

법인 명의 가입은 대표 국적과 관계없이 별도 심사를 거칩니다.

## PIPE-SUBSCRIBE-0105 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 폰 하나 더 하려는데 신분증 때문에 안 된대요

**저장된 답변**

본인 확인을 위해 주민등록증, 운전면허증, 여권 중 하나를 지참해 주시면 됩니다. 해당 서류로 확인이 가능하니 다시 방문해 주세요.

- UNSUPPORTED: 해당 서류로 확인이 가능하니 다시 방문해 주세요.
  - 판단 이유: The claim is not present in the provided FAQ source and cannot be directly supported by it.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by specifying the required documents (resident registration certificate, driver's license, passport) for identity verification. This matches the reference group's answer, which also lists the same documents. The answer is accurate and complete, providing the necessary information for the user to resolve the issue.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0105: 폰 하나 더 하려는데 신분증 때문에 안 된대요

본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나를 가져가셔야 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0105: 폰 하나 더 하려는데 신분증 때문에 안 된대요

본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나를 가져가셔야 됩니다.

## PIPE-SUBSCRIBE-0088 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 외국인인데 회선을 추가하려니 한도라고 해요

**저장된 답변**

외국인 고객님께서도 성인 기준으로 1인당 최대 5회선까지 개통이 가능합니다. 이미 5회선을 사용 중이시라면 추가 개통이 제한될 수 있습니다. 추가 개통이 필요하시다면 현재 사용 중인 회선 수를 확인해 주시기 바랍니다.

- UNSUPPORTED: 외국인 고객님께서도 성인 기준으로 1인당 최대 5회선까지 개통이 가능합니다.
  - 판단 이유: The claim specifies '외국인 고객님' (foreign customers) and explicitly mentions '성인 기준' (adult standard). The FAQ source only states '성인은 1인당 최대 5회선까지입니다' (Adults can have up to 5 lines per person), but does not mention foreign customers or explicitly confirm that they are included under the '성인 기준'. The additional wording in the claim introduces a condition not directly supported by the FAQ.

- UNSUPPORTED: 추가 개통이 필요하시다면 현재 사용 중인 회선 수를 확인해 주시기 바랍니다.
  - 판단 이유: The claim introduces a recommendation or action ('확인해 주시기 바랍니다') that is not present in the FAQ source. The source only states a condition, not a recommendation to check the number of lines.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question about the limit on additional lines for a foreigner, explaining that up to 5 lines can be opened per adult and that if they are already using 5 lines, additional openings may be restricted. It also advises checking the current number of lines in use, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0088: 외국인인데 회선을 추가하려니 한도라고 해요

성인은 1인당 최대 5회선까지입니다. 이미 5회선이면 추가 개통이 어렵습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0088: 외국인인데 회선을 추가하려니 한도라고 해요

성인은 1인당 최대 5회선까지입니다. 이미 5회선이면 추가 개통이 어렵습니다.

## PIPE-SUBSCRIBE-0095 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 세컨드폰을 20:00 넘어서 신청했는데 처리가 안 됩니다

**저장된 답변**

세컨드폰 신청은 오후 8시 이후에는 처리되지 않고, 다음 날 처리 시간에 진행됩니다.

- UNSUPPORTED: 세컨드폰 신청은 오후 8시 이후에는 처리되지 않고, 다음 날 처리 시간에 진행됩니다.
  - 판단 이유: The claim states that '세컨드폰 신청' (second phone application) is not processed after 8 PM and is handled during the next day's processing time. However, the provided FAQ source (SUBSCRIBE-0095) mentions that '개통 처리' (activation processing) is only conducted from 09:00 to 20:00 and that it is handled during the next day's processing time. The term '개통 처리' (activation processing) is not explicitly equivalent to '세컨드폰 신청' (second phone application), and the claim introduces a specific time restriction (after 8 PM) that is not explicitly mentioned in the FAQ. Therefore, the claim is not directly supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact that second phone applications made after 8 PM are processed the next day during business hours, as stated in the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0095: 세컨드폰을 20:00 넘어서 신청했는데 처리가 안 됩니다

개통 처리는 09:00부터 20:00까지만 진행됩니다. 다음 날 처리 시간에 진행됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0095: 세컨드폰을 20:00 넘어서 신청했는데 처리가 안 됩니다

개통 처리는 09:00부터 20:00까지만 진행됩니다. 다음 날 처리 시간에 진행됩니다.

## PIPE-SUBSCRIBE-0101 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 처음 만드는데 신분증이 안 된대요

**저장된 답변**

주민등록증, 운전면허증, 여권 중 본인 이름으로 된 것을 준비해 주시면 됩니다. 다른 종류의 신분증은 현재 사용이 제한될 수 있으니 해당 서류들을 확인해 주세요.

- UNSUPPORTED: 다른 종류의 신분증은 현재 사용이 제한될 수 있으니 해당 서류들을 확인해 주세요.
  - 판단 이유: The claim mentions restrictions on other types of identification, but this information is not present in the provided FAQ source.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully conveys the required information from the reference, specifying that a resident registration certificate, driver's license, or passport with the individual's name is needed, and that other types of identification may currently be restricted.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0101: 처음 만드는데 신분증이 안 된대요

본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나여야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0101: 처음 만드는데 신분증이 안 된대요

본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나여야 합니다.

## PIPE-SUBSCRIBE-0112 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 애 명의 회선이랑 제 명의 회선이랑 한도가 달라요?

**저장된 답변**

개인 명의 회선의 경우 최대 5회선까지 허용됩니다. 애 명의 회선의 경우 별도의 심사 기준이 적용되어 한도가 다를 수 있습니다.

- UNSUPPORTED: 애 명의 회선의 경우 별도의 심사 기준이 적용되어 한도가 다를 수 있습니다.
  - 판단 이유: The claim '애 명의 회선의 경우 별도의 심사 기준이 적용되어 한도가 다를 수 있습니다.' is not supported by the provided FAQ evidence. The FAQ source mentions '법인 명의는 별도 심사로 정해집니다.' (Corporate lines are determined by separate review), but there is no mention of '애 명의' (child's name) or any specific conditions for it.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that personal lines have a maximum limit of 5 lines, while child lines may have different limits based on separate review criteria, which matches the reference group's information.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0112: 애 명의 회선이랑 제 명의 회선이랑 한도가 달라요?

성인은 1인당 최대 5회선입니다. 법인 명의는 별도 심사로 정해집니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0112: 애 명의 회선이랑 제 명의 회선이랑 한도가 달라요?

성인은 1인당 최대 5회선입니다. 법인 명의는 별도 심사로 정해집니다.

## PIPE-SUBSCRIBE-0122 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 자녀 명의와 부모 명의 중 어느 쪽으로 개통하는 게 서류가 간단한가요?

**저장된 답변**

부모 명의로 개통하는 것이 서류가 더 간단합니다. 부모 본인 명의로 진행할 경우 신분증 하나만으로 가능하지만, 자녀 명의로 개통할 경우 법정대리인 동의서, 가족관계증명서, 그리고 법정대리인의 신분증이 필요합니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by clearly stating that opening under a parent's name is simpler in terms of required documents. It specifies that only one ID is needed for a parent's name, while a child's name requires additional documents such as a legal guardian consent form, family relationship certificate, and the legal guardian's ID. This matches the reference group's answer, which also states that a parent's name is simpler with just one ID, while a child's name requires additional documents.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0122: 자녀 명의와 부모 명의 중 어느 쪽으로 개통하는 게 서류가 간단한가요?

부모 본인 명의가 신분증 하나로 끝나 간단합니다. 자녀 명의는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0122: 자녀 명의와 부모 명의 중 어느 쪽으로 개통하는 게 서류가 간단한가요?

부모 본인 명의가 신분증 하나로 끝나 간단합니다. 자녀 명의는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다.

## PIPE-PORTING-0022 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 밀린 요금 있으면 못 옮겨요

**저장된 답변**

밀린 요금이 있으시다면 먼저 해당 요금을 정산하셔야 서비스를 옮길 수 있습니다. 요금을 모두 납부하신 후에야 서비스 변경이 가능합니다.

- UNSUPPORTED: 밀린 요금이 있으시다면 먼저 해당 요금을 정산하셔야 서비스를 옮길 수 있습니다.
  - 판단 이유: The claim specifies that if there are overdue fees, they must be settled before switching services. The FAQ source only states that 'you must pay first to move,' but does not explicitly mention 'overdue fees' or the need to settle them before switching. The wording and condition differ.

- UNSUPPORTED: 요금을 모두 납부하신 후에야 서비스 변경이 가능합니다.
  - 판단 이유: The claim states that service change is only possible after paying all fees. The FAQ source says 'you must pay first to move,' but does not explicitly confirm that all fees must be paid before the change. The condition is not fully aligned.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully conveys the required fact that unsettled fees must be paid before service transfer is possible, aligning with the reference.

**RAG에 전달된 실제 근거**

- PORTING-0022: 밀린 요금 있으면 못 옮겨요

네, 먼저 다 내셔야 옮길 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0022: 밀린 요금 있으면 못 옮겨요

네, 먼저 다 내셔야 옮길 수 있습니다.

## PIPE-PORTING-0024 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 옮기고 나서 무를 수 있는 기간이 얼마예요

**저장된 답변**

14일입니다. 그 기간 내에 변경을 무르시면 원래 통신사로 돌아갈 수 있습니다.

- UNSUPPORTED: 그 기간 내에 변경을 무르시면 원래 통신사로 돌아갈 수 있습니다.
  - 판단 이유: The claim states '원래 통신사로 돌아갈 수 있습니다' (can return to the original carrier), but the FAQ sources use '자동으로 돌아갑니다' (will automatically return) and '새로 옮기셔야 합니다' (will need to move again). The wording and nuance differ, making the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact that the period is 14 days and explains the consequence of reversing the change within that period.

**RAG에 전달된 실제 근거**

- PORTING-0024: 옮기고 나서 무를 수 있는 기간이 얼마예요

14일입니다. 그 안에 무르면 원래 통신사로 돌아갑니다.

- PORTING-0112: 무르는 거랑 새로 옮기는 거랑 뭐가 달라요

14일 안에 무르면 원래 통신사로 자동으로 돌아갑니다. 14일 지나면 새로 옮기셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0024: 옮기고 나서 무를 수 있는 기간이 얼마예요

14일입니다. 그 안에 무르면 원래 통신사로 돌아갑니다.

## PIPE-PORTING-0029 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 번호이동 신청하면 그다음에 뭘 해야 돼요?

**저장된 답변**

번호이동 신청 후에는 기존 통신사에 미납 요금이 없는지 확인하시고, 만약 미납 요금이 있다면 먼저 완납하셔야 합니다. 이후 새로운 통신사의 안내에 따라 필요한 절차를 진행하시면 됩니다.

- UNSUPPORTED: 새로운 통신사의 안내에 따라 필요한 절차를 진행하시면 됩니다
  - 판단 이유: None of the provided FAQ sources mention following the new carrier's instructions for the process.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer mentions checking for outstanding fees and paying them if necessary, which is accurate. However, it does not mention the processing time (2 hours on average within 09:00-20:00, or 24 hours maximum) provided in the reference. This is a key detail that was requested in the question.

**RAG에 전달된 실제 근거**

- PORTING-0026: 번호이동은 어떻게 신청해요?

본인 신분증을 지참해 신청하시면 됩니다. 기존 통신사에 미납 요금이 있으면 먼저 완납하셔야 진행됩니다.

- PORTING-0030: 결합 해지하고 번호이동하려면 순서가 어떻게 돼요?

기존 통신사 미납 요금이 없는지 먼저 확인하고, 본인 신분증을 지참해 번호이동을 신청하시면 됩니다.

- PORTING-0042: 번호이동 신청하려면 뭐 가져가요

신분증만 있으면 됩니다. 밀린 요금이 있으면 먼저 내셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0029: 번호이동 신청하면 그다음에 뭘 해야 돼요?

신청 후에는 기다리시면 됩니다. 09:00~20:00 사이 신청 기준으로 평균 2시간 이내에 완료되고, 늦어도 24시간 안에 처리됩니다.

## PIPE-PORTING-0060 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 철회 후 원래 통신사 요금제도 그대로 복원되나요?

**저장된 답변**

네, 철회 후에는 원래 통신사의 요금제가 자동으로 복원됩니다. 하지만 철회는 개통 후 14일 이내에만 가능합니다.

- UNSUPPORTED: 철회 후에는 원래 통신사의 요금제가 자동으로 복원됩니다
  - 판단 이유: FAQ에서 '회선은 원래 통신사로 자동 복원됩니다'라고 명시되어 있지만, '요금제'라는 구체적인 항목은 언급되지 않았습니다. '요금제'와 '회선'은 의미가 다르므로 이 클레임은 직접적으로 지원되지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by confirming that the original carrier's plan is automatically restored after withdrawal and specifying that withdrawal is only possible within 14 days of activation. This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- PORTING-0060: 철회 후 원래 통신사 요금제도 그대로 복원되나요?

회선은 원래 통신사로 자동 복원됩니다. 철회는 개통 후 14일 이내에 가능합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0060: 철회 후 원래 통신사 요금제도 그대로 복원되나요?

회선은 원래 통신사로 자동 복원됩니다. 철회는 개통 후 14일 이내에 가능합니다.

## PIPE-PORTING-0065 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 토요일에도 번호이동 되나요

**저장된 답변**

네, 토요일에도 번호이동이 가능합니다. 매일 오전 9시부터 오후 8시까지 운영되고 있습니다.

- UNSUPPORTED: 네, 토요일에도 번호이동이 가능합니다.
  - 판단 이유: The claim states that number porting is possible on Saturdays, but the FAQ source only mentions operating hours (09:00 to 20:00) without specifying days of the week. There is no explicit confirmation that Saturday is included.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that number porting is possible on Saturdays and specifies the operating hours from 9 AM to 8 PM daily, which matches the reference information.

**RAG에 전달된 실제 근거**

- PORTING-0065: 토요일에도 번호이동 되나요

네, 매일 09:00부터 20:00까지 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0065: 토요일에도 번호이동 되나요

네, 매일 09:00부터 20:00까지 처리됩니다.

## PIPE-PORTING-0053 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 밤에 신청해도 다음 날 아침에 바로 될까요?

**저장된 답변**

밤에 신청하시더라도 처리는 다음 날 오전 9시부터 시작되므로, 당일 완료보다는 다음 날 아침에 완료될 가능성이 높습니다. 평균 처리 시간은 약 2시간 이내입니다.

- UNSUPPORTED: 밤에 신청하시더라도 처리는 다음 날 오전 9시부터 시작되므로, 당일 완료보다는 다음 날 아침에 완료될 가능성이 높습니다.
  - 판단 이유: The claim states that processing starts the next morning at 9 AM and is more likely to be completed the next morning rather than on the same day. However, the FAQ sources do not mention that processing starts the next day for night applications. Source PORTING-0097 mentions processing from 09:00 to 20:00, implying that applications made in the evening may still be processed the same day, but it does not explicitly state that night applications are processed the next day.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that processing starts at 9 AM the next day and takes approximately 2 hours, aligning with the reference group's information.

**RAG에 전달된 실제 근거**

- PORTING-0053: 밤에 신청해도 다음 날 아침에 바로 될까요?

처리는 09:00부터 시작됩니다. 처리 시작 후 평균 2시간 이내에 완료됩니다.

- PORTING-0097: 아침에 신청하는 거랑 저녁에 신청하는 거랑 뭐가 빨라요?

처리는 09:00부터 20:00까지라 아침 신청이 당일에 끝날 가능성이 큽니다. 평균 2시간 이내 처리입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0053: 밤에 신청해도 다음 날 아침에 바로 될까요?

처리는 09:00부터 시작됩니다. 처리 시작 후 평균 2시간 이내에 완료됩니다.

## PIPE-PORTING-0049 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?

**저장된 답변**

저녁 늦게 신청하시더라도 번호 이동 처리 시간은 매일 오후 9시부터 저녁 8시까지 운영됩니다. 신청하신 경우 평균 2시간 이내에 완료되며, 늦어도 하루 이내에 처리될 예정입니다. 다만, 처리 시간을 고려하시어 가능한 한 저녁 시간대 이전에 신청하시는 것을 권장드립니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question about whether the number porting can be done the same day if applied late in the evening. It confirms that processing is available until 8 PM, provides the average completion time of 2 hours, and mentions that it will be completed within a day at the latest. It also includes the recommendation to apply earlier if possible, which aligns with the reference.

**RAG에 전달된 실제 근거**

- PORTING-0049: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?

번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0049: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?

번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

## PIPE-PORTING-0085 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 일요일 오후에 신청했는데 진행이 안 됩니다

**저장된 답변**

일요일 오후에 신청하셨더라도 처리는 평일 오전 9시부터 오후 8시까지 운영됩니다. 현재 시간이 처리 시간 외라면 진행이 일시적으로 중단될 수 있습니다. 평일로 재개되면 평균 2시간 이내에 처리될 예정입니다. 불편을 드려 죄송합니다.

- UNSUPPORTED: 일요일 오후에 신청하셨더라도 처리는 평일 오전 9시부터 오후 8시까지 운영됩니다
  - 판단 이유: The claim states that processing occurs from Monday to Friday between 9 AM and 8 PM, even if the application was made on a Sunday afternoon. However, the FAQ source only states that processing occurs daily from 9 AM to 8 PM, without specifying that processing is only done on weekdays. This implies that processing may occur on weekends as well, which contradicts the claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested facts: that processing occurs from 9 AM to 8 PM on weekdays, that processing may be temporarily paused outside of these hours, and that it will take approximately 2 hours to complete once processing resumes on a weekday.

**RAG에 전달된 실제 근거**

- PORTING-0085: 일요일 오후에 신청했는데 진행이 안 됩니다

처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안이라면 평균 2시간 이내에 완료됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0085: 일요일 오후에 신청했는데 진행이 안 됩니다

처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안이라면 평균 2시간 이내에 완료됩니다.

## PIPE-PORTING-0081 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다

**저장된 답변**

신청하신 작업이 평균적으로 2시간 내에 완료되지만, 최대 24시간까지 소요될 수 있습니다. 현재 시간이 점심 이후이므로, 아직 완료되지 않은 경우 정상적인 범위 내에 있습니다. 하지만 24시간이 경과해도 완료되지 않는다면 고객센터로 연락주시기 바랍니다.

- UNSUPPORTED: 현재 시간이 점심 이후이므로, 아직 완료되지 않은 경우 정상적인 범위 내에 있습니다.
  - 판단 이유: The claim introduces new information about the current time and lunch hours, which is not mentioned or supported in the FAQ source.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by providing the average and maximum processing times (2 hours and 24 hours, respectively) and includes the appropriate action if the task is not completed within 24 hours (contacting the customer service). The answer also contextualizes the current situation (lunchtime has passed) and reassures the user that the delay is within the normal range.

**RAG에 전달된 실제 근거**

- PORTING-0081: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다

평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0081: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다

평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.

## PIPE-PORTING-0087 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기변경한 지 얼마 안 됐다고 번호이동이 안 된답니다

**저장된 답변**

기기변경 후 번호이동이 제한되는 기간은 개통일 기준으로 3개월입니다. 현재 기간이 이 기준을 충족하지 못하고 있으니, 3개월이 경과하신 후에 다시 시도해 주시기 바랍니다.

- UNSUPPORTED: 현재 기간이 이 기준을 충족하지 못하고 있으니, 3개월이 경과하신 후에 다시 시도해 주시기 바랍니다.
  - 판단 이유: The claim implies a current period that does not meet the criteria, but no FAQ source explicitly confirms this condition or provides context about the current period.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the 3-month restriction period based on the activation date, which is the key fact requested in the reference. It also provides the appropriate guidance for the user.

**RAG에 전달된 실제 근거**

- PORTING-0087: 기기변경한 지 얼마 안 됐다고 번호이동이 안 된답니다

제한은 개통일 기준 3개월, 명의변경일 기준 1개월입니다. 두 날짜를 확인해 보세요.

- PORTING-0091: 새 폰 샀는데 번호를 못 옮긴대요

개통한 지 3개월이 안 돼서 그렇습니다. 3개월 지나면 됩니다.

- PORTING-0079: 명의 바꾼 지 얼마 안 됐는데 번호이동이 안 돼요

명의변경일로부터 1개월이 지나야 번호이동이 가능합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0087: 기기변경한 지 얼마 안 됐다고 번호이동이 안 된답니다

제한은 개통일 기준 3개월, 명의변경일 기준 1개월입니다. 두 날짜를 확인해 보세요.

## PIPE-PORTING-0107 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 개통 후 3개월과 명의변경 후 1개월 중 어느 조건이 더 자주 걸리나요?

**저장된 답변**

명의변경 후 1개월 조건이 추가로 적용되는 경우에만 해당 조건이 발생하므로, 일반적인 경우 개통 후 3개월 조건만 확인하시면 됩니다. 따라서 대부분의 경우 개통 후 3개월 조건이 더 자주 적용됩니다.

- UNSUPPORTED: 명의변경 후 1개월 조건이 추가로 적용되는 경우에만 해당 조건이 발생하므로
  - 판단 이유: The claim implies that the 1-month condition after name change is applied only in specific cases, but the FAQ sources do not explicitly state this conditional limitation. The sources only mention that the 1-month condition applies when there is a name change, but do not clarify that it is only applied in certain cases.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the 3-month condition after opening is more commonly applied, and that the 1-month condition after name change only applies in specific cases. This aligns with the reference provided.

**RAG에 전달된 실제 근거**

- PORTING-0107: 개통 후 3개월과 명의변경 후 1개월 중 어느 조건이 더 자주 걸리나요?

명의변경을 한 경우에만 1개월 조건이 추가로 붙습니다. 대부분은 개통 후 3개월 조건만 확인하시면 됩니다.

- PORTING-0103: 개통 기준이랑 명의변경 기준 중 어느 쪽이 더 짧은가요?

명의변경 기준은 1개월, 개통 기준은 3개월이라 명의변경 기준이 더 짧습니다.

- PORTING-0099: 3개월 기다리는 거랑 명의변경 후 1개월 기다리는 거랑 뭐가 먼저예요?

둘 다 지나야 합니다. 개통 후 3개월과 명의변경 후 1개월 중 늦게 끝나는 쪽까지 기다리셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0107: 개통 후 3개월과 명의변경 후 1개월 중 어느 조건이 더 자주 걸리나요?

명의변경을 한 경우에만 1개월 조건이 추가로 붙습니다. 대부분은 개통 후 3개월 조건만 확인하시면 됩니다.

## PIPE-PORTING-0105 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 평일과 주말의 번호이동 처리 시간이 다른가요?

**저장된 답변**

평일과 주말의 번호이동 처리 시간은 동일합니다. 매일 09:00부터 20:00까지 평균 2시간 이내에 처리됩니다.

- UNSUPPORTED: 평일과 주말의 번호이동 처리 시간은 동일합니다.
  - 판단 이유: The claim states that processing times are the same on weekdays and weekends, but none of the provided FAQ sources mention anything about weekends. The sources only mention processing times during 09:00-20:00 daily but do not clarify if this applies to weekends or if there is a difference between weekdays and weekends.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that the processing time for number porting is the same on weekdays and weekends, and specifies the operating hours (09:00 to 20:00) and average processing time (within 2 hours). This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- PORTING-0105: 평일과 주말의 번호이동 처리 시간이 다른가요?

같습니다. 매일 09:00부터 20:00까지 처리되고 평균 2시간 이내입니다.

- PORTING-0065: 토요일에도 번호이동 되나요

네, 매일 09:00부터 20:00까지 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0105: 평일과 주말의 번호이동 처리 시간이 다른가요?

같습니다. 매일 09:00부터 20:00까지 처리되고 평균 2시간 이내입니다.

## PIPE-TERMINATE-0013 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 분실 후 해지 시 요금 정산 기준을 알려주세요.

**저장된 답변**

분실 후 해지 시 요금은 해지 신청이 처리된 달부터 정산됩니다. 해당 달의 요금은 일할 계산되어 청구됩니다.

- UNSUPPORTED: 분실 후 해지 시 요금은 해지 신청이 처리된 달부터 정산됩니다.
  - 판단 이유: The claim specifies '분실 후 해지' (cancellation after loss), which is not mentioned in the FAQ source. The source only mentions '해지 신청 즉시 처리되며 해지한 달의 요금은 일할 계산됩니다' (cancellation is processed immediately, and the fee for the month of cancellation is prorated). The condition of '분실 후 해지' is not addressed in the source, making the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the required fact that the fee is calculated from the month the termination request is processed and that the fee for that month is prorated.

**RAG에 전달된 실제 근거**

- TERMINATE-0013: 분실 후 해지 시 요금 정산 기준을 알려주세요.

해지 신청 즉시 처리되며 해지한 달의 요금은 일할 계산됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0013: 분실 후 해지 시 요금 정산 기준을 알려주세요.

해지 신청 즉시 처리되며 해지한 달의 요금은 일할 계산됩니다.

## PIPE-TERMINATE-0001 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 번호 해지는 어떻게 해요?

**저장된 답변**

번호 해지는 매장을 방문하시거나, 고객센터에 전화를 주시거나, 저희 홈페이지에서 신청하실 수 있습니다. 편하신 방법을 선택하시면 됩니다. 신청이 접수되면 즉시 해지 처리되며, 요금은 일할 계산됩니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by providing all the required methods for terminating the service (visiting a store, calling the customer center, or applying via the website) and explains that the fee will be calculated on a daily basis once the application is received. This matches the reference group's answer exactly.

**RAG에 전달된 실제 근거**

- TERMINATE-0001: 번호 해지는 어떻게 해요?

매장 방문, 고객센터 전화, 홈페이지 신청 중 편하신 방법으로 하시면 됩니다. 신청 즉시 해지되고 요금은 일할 계산됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0001: 번호 해지는 어떻게 해요?

매장 방문, 고객센터 전화, 홈페이지 신청 중 편하신 방법으로 하시면 됩니다. 신청 즉시 해지되고 요금은 일할 계산됩니다.

## PIPE-TERMINATE-0058 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 11개월째에 해지하면 위약금을 다 물어야 하나요?

**저장된 답변**

할인 혜택을 받으셨던 기간이 남아 있어 할인 반환금의 전체 금액을 반환해야 합니다.

- UNSUPPORTED: 할인 혜택을 받으셨던 기간이 남아 있어 할인 반환금의 전체 금액을 반환해야 합니다.
  - 판단 이유: The claim states that the customer must return the full amount of the discount refund because there is a remaining period of the discount benefit. However, the FAQ source (TERMINATE-0058) only mentions that if the period is less than 12 months, the full 100% of the discount refund must be returned. It does not mention anything about a remaining period of the discount benefit being the reason for returning the full amount. Therefore, the claim is not directly supported by the provided FAQ evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user must return the full discount refund amount because they received the discount benefit during the remaining period, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- TERMINATE-0058: 11개월째에 해지하면 위약금을 다 물어야 하나요?

네, 12개월 미만이라 할인 반환금의 100%를 반환하셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0058: 11개월째에 해지하면 위약금을 다 물어야 하나요?

네, 12개월 미만이라 할인 반환금의 100%를 반환하셔야 합니다.

## PIPE-TERMINATE-0069 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 외국에서 전화로 해지할 수 있나요

**저장된 답변**

네, 외국에서도 고객센터에 전화하시면 즉시 해지 처리가 가능합니다.

- UNSUPPORTED: 외국에서도 고객센터에 전화하시면 즉시 해지 처리가 가능합니다.
  - 판단 이유: The claim specifies that the customer can call the customer service center in foreign countries to immediately process the cancellation. However, none of the provided FAQ sources mention anything about calling the customer service center from abroad. The sources only state that calling the customer service center or applying through the website allows for immediate cancellation, but they do not specify that this is possible from foreign countries. Therefore, the claim is not directly supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact that termination is possible by calling the customer service center abroad, aligning with the reference.

**RAG에 전달된 실제 근거**

- TERMINATE-0069: 외국에서 전화로 해지할 수 있나요

네, 고객센터에 전화하시면 바로 해지됩니다.

- TERMINATE-0081: 급하게 해외로 나가게 됐는데 매장 갈 시간이 없어요

고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

- TERMINATE-0049: 해외 나가 있는 동안 홈페이지로 해지할 수 있을까요?

네, 홈페이지에서 신청하시면 즉시 해지됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0069: 외국에서 전화로 해지할 수 있나요

네, 고객센터에 전화하시면 바로 해지됩니다.

## PIPE-TERMINATE-0061 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 입대 당일에 해지 신청해도 바로 처리되나요?

**저장된 답변**

네, 입대 당일에 해지 신청하시면 즉시 처리됩니다. 해지 요금은 실제 사용 기간에 따라 일할 계산됩니다.

- UNSUPPORTED: 입대 당일에 해지 신청하시면 즉시 처리됩니다
  - 판단 이유: FAQ에서 '신청 즉시 해지됩니다'라고 명시되어 있지만, '입대 당일'이라는 조건은 FAQ에 언급되지 않았습니다. 조건이 다르므로 지원되지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question by confirming that the termination can be processed immediately on the day of enlistment and explains that the termination fee is calculated on a daily basis according to the actual usage period. This aligns with the reference provided.

**RAG에 전달된 실제 근거**

- TERMINATE-0061: 입대 당일에 해지 신청해도 바로 처리되나요?

네, 신청 즉시 해지됩니다. 해지월 요금은 일할 계산됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0061: 입대 당일에 해지 신청해도 바로 처리되나요?

네, 신청 즉시 해지됩니다. 해지월 요금은 일할 계산됩니다.

## PIPE-TERMINATE-0090 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 요금 부담돼서 해지했는데 위약금이 더 나와요

**저장된 답변**

약정 기간 내에 해지를 진행하셨다면 반환금이 발생할 수 있습니다. 만약 해지 시점이 약정 기간의 절반을 초과했다면, 반환금은 절반만 적용될 수 있습니다.

- UNSUPPORTED: 해지 시점이 약정 기간의 절반을 초과했다면, 반환금은 절반만 적용될 수 있습니다.
  - 판단 이유: The FAQ source mentions that if termination occurs after 12 months, only half applies, but it does not mention the 'half of the agreement period' as a condition for applying half the refund. The condition in the claim differs from the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question about the refund policy when terminating the agreement. It accurately conveys that a refund may be issued if the termination occurs within the agreement period, and that if the termination occurs after half of the agreement period has passed, only half of the refund applies. This matches the reference group's information.

**RAG에 전달된 실제 근거**

- TERMINATE-0090: 요금 부담돼서 해지했는데 위약금이 더 나와요

약정 기간 안에 해지하면 반환금이 있습니다. 12개월 전이면 전부, 넘었으면 절반입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0090: 요금 부담돼서 해지했는데 위약금이 더 나와요

약정 기간 안에 해지하면 반환금이 있습니다. 12개월 전이면 전부, 넘었으면 절반입니다.

## PIPE-TERMINATE-0085 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 해지 신청했는데 다음 달 요금이 청구됐습니다

**저장된 답변**

해지 신청 후에도 다음 달 요금이 청구되는 것은 해지 처리 과정에서 일시적인 청구일 수 있습니다. 해지 신청이 접수되면 해당 월의 남은 기간에 대한 요금이 일할 계산되어 청구되며, 이후 달부터는 요금이 발생하지 않습니다. 청구 내역을 고객센터에서 자세히 확인해 보시는 것을 권장드립니다.

- UNSUPPORTED: 해지 신청 후에도 다음 달 요금이 청구되는 것은 해지 처리 과정에서 일시적인 청구일 수 있습니다.
  - 판단 이유: The claim suggests that the next month's fee might be temporarily charged during the termination process, but none of the FAQ sources mention this. Instead, they state that the termination month's fee is prorated and that no fees are charged after the termination month.

- UNSUPPORTED: 이후 달부터는 요금이 발생하지 않습니다.
  - 판단 이유: The claim states that no fees occur in subsequent months, but the FAQ sources do not explicitly confirm this. One source (SERVICE-0051) says that the fee for the termination month is not prorated and is charged in full, but it does not confirm that no fees occur in subsequent months.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining that the next month's fee is a temporary charge during the termination process and that only the remaining days of the termination month are billed. It also advises checking the billing details with the customer service center, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- TERMINATE-0085: 해지 신청했는데 다음 달 요금이 청구됐습니다

해지는 신청 즉시 처리되고 해지월 요금만 일할 계산됩니다. 청구 내역을 고객센터에서 확인해 보세요.

- SERVICE-0051: 부가서비스 해지했는데 요금이 또 나왔어요

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

- TERMINATE-0089: 오래 안 썼는데 해지하면 요금 어떻게 나와요

해지 신청 즉시 처리되고, 그 달 요금은 사용한 날수만큼 일할 계산되어 청구됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0085: 해지 신청했는데 다음 달 요금이 청구됐습니다

해지는 신청 즉시 처리되고 해지월 요금만 일할 계산됩니다. 청구 내역을 고객센터에서 확인해 보세요.

## PIPE-TERMINATE-0092 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 정지 중인데 청구서가 왔어요

**저장된 답변**

정지 중에 청구서가 도착하셨다니 다행입니다. 청구서를 확인하시고 요금을 정산하시면 서비스 정지 해제에 필요한 시간 내에 정상화될 수 있습니다. 추가 도움이 필요하시면 언제든지 연락 주세요.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer mentions that the service can be restored once the bill is settled, which is accurate. However, it does not explicitly state that 50% of the basic fee is charged during the suspension period, as indicated in the reference.

**RAG에 전달된 실제 근거**

- BILLING-0059: 청구서 못 받았는데 정지 안 되게 하려면 어떻게 해요

청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0092: 정지 중인데 청구서가 왔어요

정지 기간에도 기본료의 절반(50%)은 청구됩니다. 전액 면제가 아닙니다.

## PIPE-TERMINATE-0107 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 장기 미사용 회선 해지 시 할부 유지와 일시 납부는 절차가 다른가요?

**저장된 답변**

해지 신청 시 할부 유지와 일시 납부는 선택 방법이 다릅니다. 할부 유지를 선택하면 기존 할부 계획이 그대로 유지되며, 일시 납부를 선택하면 해지 시 요금을 한 번에 정산하게 됩니다. 따라서 절차의 본질적인 차이는 선택에 따른 요금 정산 방식에 있습니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining the difference in procedures between '할부 유지' and '일시 납부' during the termination of a long-term unused line. It clearly states that the difference lies in the payment settlement method based on the user's choice, which aligns with the reference provided.

**RAG에 전달된 실제 근거**

- TERMINATE-0107: 장기 미사용 회선 해지 시 할부 유지와 일시 납부는 절차가 다른가요?

해지 신청 시 선택만 다르고, 유지를 고르면 기존 일정이 이어지고 일시 납부를 고르면 한 번에 정산됩니다.

- TERMINATE-0039: 장기 미사용 회선 해지 시 할부 유지 신청 절차를 알려주세요.

해지 신청 시 할부 유지를 선택하시면 기존 할부 일정이 그대로 이어집니다. 선택하지 않으면 일시 납부가 원칙입니다.

- TERMINATE-0087: 장기 미사용으로 해지하면서 할부 유지를 선택했는데 일시 납부로 처리됐습니다

일시 납부가 원칙이라 명시적으로 할부 유지를 선택해야 합니다. 고객센터에서 처리 내역을 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0107: 장기 미사용 회선 해지 시 할부 유지와 일시 납부는 절차가 다른가요?

해지 신청 시 선택만 다르고, 유지를 고르면 기존 일정이 이어지고 일시 납부를 고르면 한 번에 정산됩니다.

## PIPE-TERMINATE-0113 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / HUMAN_REVIEW
- 질문: 폰 잃어버렸는데 매장 가는 거랑 전화하는 거랑 뭐가 빨라요

**저장된 답변**

둘 다 즉시 처리가 가능하니 편하신 방법으로 선택하시면 됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that both options (going to the store or calling) can be handled immediately, which matches the reference group's answer that both methods are equally quick and allows the user to choose their preferred method.

**RAG에 전달된 실제 근거**

- TERMINATE-0113: 폰 잃어버렸는데 매장 가는 거랑 전화하는 거랑 뭐가 빨라요

똑같이 바로 됩니다. 전화가 편하시면 전화로 하세요.

- TERMINATE-0109: 매장 가는 거랑 전화하는 거랑 뭐가 더 빨라요

셋 다(매장·전화·홈페이지) 신청 즉시 해지되어 처리 속도는 같습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0113: 폰 잃어버렸는데 매장 가는 거랑 전화하는 거랑 뭐가 빨라요

똑같이 바로 됩니다. 전화가 편하시면 전화로 하세요.

## PIPE-TERMINATE-0095 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 한 번에 내는 거랑 나눠 내는 거랑 뭐가 더 나아요?

**저장된 답변**

반면에 할부 납부는 금액 부담을 줄여주며, 일정 기간 동안 나눠서 납부할 수 있어 경제적 부담을 덜 수 있습니다. 고객님의 재정 상황과 편의성에 따라 선택하시면 됩니다.

- UNSUPPORTED: 할부 납부는 금액 부담을 줄여주며, 일정 기간 동안 나눠서 납부할 수 있어 경제적 부담을 덜 수 있습니다.
  - 판단 이유: The claim mentions that installment payments reduce financial burden by allowing payment over time, but the provided FAQ source (TERMINATE-0095) only discusses the option to choose between one-time payment and installment payments, without explicitly stating that installment payments reduce financial burden or allow payment over time.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining the benefits of installment payments (reducing financial burden and allowing payment over time) and mentions that the choice depends on the customer's financial situation and convenience. This aligns with the reference group's answer, which also discusses the flexibility of installment payments and the option to choose based on personal preference.

**RAG에 전달된 실제 근거**

- TERMINATE-0095: 한 번에 내는 거랑 나눠 내는 거랑 뭐가 더 나아요?

일시 납부는 그 자리에서 정리가 끝나고, 할부 유지는 기존 일정대로 계속 나눠 내실 수 있습니다. 편하신 쪽으로 선택하시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0095: 한 번에 내는 거랑 나눠 내는 거랑 뭐가 더 나아요?

일시 납부는 그 자리에서 정리가 끝나고, 할부 유지는 기존 일정대로 계속 나눠 내실 수 있습니다. 편하신 쪽으로 선택하시면 됩니다.

## PIPE-TERMINATE-0096 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 정지하는 거랑 아예 해지하는 거랑 뭐가 더 나아요?

**저장된 답변**

일시 정지는 기본료의 절반만 내면 되고 번호를 유지할 수 있어 추후 재가입이 용이합니다. 반면에 해지는 요금 부담이 완전히 사라지지만, 사용하던 번호와 계약이 종료되므로 다시 개통할 때는 새로운 절차가 필요합니다. 번호 유지와 재사용 가능성을 고려하신다면 일시 정지를, 완전히 요금 부담을 없애고 싶으시다면 해지를 추천드립니다.

- UNSUPPORTED: 해지는 요금 부담이 완전히 사라지지만, 사용하던 번호와 계약이 종료되므로 다시 개통할 때는 새로운 절차가 필요합니다.
  - 판단 이유: FAQ에서 '해지는 요금이 아예 안 나가지만 번호와 계약이 없어집니다.'라고 명시되어 있다. '요금 부담이 완전히 사라지지만'은 지원되지만, '다시 개통할 때는 새로운 절차가 필요합니다'는 FAQ에서 명시되지 않았으며, '계약이 없어진다'는 표현과 '새로운 절차가 필요하다'는 의미가 다르므로 직접적인 지원이 되지 않는다.

- UNSUPPORTED: 번호 유지와 재사용 가능성을 고려하신다면 일시 정지를, 완전히 요금 부담을 없애고 싶으시다면 해지를 추천드립니다.
  - 판단 이유: FAQ에서 '일시 정지는 기본료의 50%만 내고 번호를 유지할 수 있습니다. 해지는 요금이 아예 안 나가지만 번호와 계약이 없어집니다.'라고 명시되어 있다. '번호 유지와 재사용 가능성을 고려하신다면 일시 정지를'은 지원되지만, '완전히 요금 부담을 없애고 싶으시다면 해지를 추천드립니다'는 명시적으로 언급되지 않았으며, 추천 내용은 FAQ에서 직접적으로 언급되지 않았다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining the differences between temporary suspension and permanent cancellation, including the cost implications and the ability to retain or reuse the number. All required facts from the reference are accurately conveyed.

**RAG에 전달된 실제 근거**

- TERMINATE-0096: 정지하는 거랑 아예 해지하는 거랑 뭐가 더 나아요?

일시 정지는 기본료의 50%만 내고 번호를 유지할 수 있습니다. 해지는 요금이 아예 안 나가지만 번호와 계약이 없어집니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0096: 정지하는 거랑 아예 해지하는 거랑 뭐가 더 나아요?

일시 정지는 기본료의 50%만 내고 번호를 유지할 수 있습니다. 해지는 요금이 아예 안 나가지만 번호와 계약이 없어집니다.

## PIPE-TERMINATE-0104 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 장기 미사용이면 일시 정지와 해지 중 어느 쪽이 유리한가요?

**저장된 답변**

장기 미사용 시 번호를 계속 유지하고 싶으시다면 일시 정지를 추천드립니다. 일시 정지 시 기본료가 50% 감면되며, 1회에 30일, 연 최대 90일까지 가능합니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the key facts from the reference: that temporary suspension is recommended for long-term non-use if the user wants to keep the number, it reduces the basic fee by 50%, and it allows for up to 30 days per session with a maximum of 90 days per year. This fully addresses the user's question about which option is more favorable between temporary suspension and cancellation.

**RAG에 전달된 실제 근거**

- TERMINATE-0104: 장기 미사용이면 일시 정지와 해지 중 어느 쪽이 유리한가요?

번호를 유지하려면 일시 정지(기본료 50%, 1회 30일, 연 90일)가 낫고, 다시 쓸 계획이 없으면 해지가 낫습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0104: 장기 미사용이면 일시 정지와 해지 중 어느 쪽이 유리한가요?

번호를 유지하려면 일시 정지(기본료 50%, 1회 30일, 연 90일)가 낫고, 다시 쓸 계획이 없으면 해지가 낫습니다.

## PIPE-USIM-0020 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 유심 없어지면 어떻게 되나요

**저장된 답변**

유심이 없어지면 서비스가 정지됩니다. 새로운 유심을 발급받으시면 기존 유심은 자동으로 무효화되어 서비스가 재개될 수 있습니다. 신고 절차를 통해 유심 분실을 등록하시면 즉시 정지 처리가 이루어집니다.

- UNSUPPORTED: 유심이 없어지면 서비스가 정지됩니다.
  - 판단 이유: The claim states that if the SIM card is lost, the service will be suspended. However, the FAQ source only mentions that reporting the loss will result in immediate suspension, not that losing the SIM card automatically suspends the service.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question by explaining that the service will be suspended if the SIM card is lost, how to reissue a SIM card to resume service, and how to report the loss to immediately stop the service. All required facts from the reference are accurately conveyed.

**RAG에 전달된 실제 근거**

- USIM-0020: 유심 없어지면 어떻게 되나요

신고하시면 바로 정지됩니다. 새로 받으시면 예전 유심은 자동으로 없어집니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0020: 유심 없어지면 어떻게 되나요

신고하시면 바로 정지됩니다. 새로 받으시면 예전 유심은 자동으로 없어집니다.

## PIPE-USIM-0041 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 재발급 신청 절차를 순서대로 알려주세요.

**저장된 답변**

재발급 신청은 주로 두 가지 방법으로 진행됩니다. 둘째, 온라인으로 신청하시면 택배로 수령하실 수 있으며, 이 경우 처리 기간은 약 2~3 영업일이 소요됩니다. 매장 방문이 어려우시다면 온라인 신청을 추천드립니다.

- UNSUPPORTED: 재발급 신청은 주로 두 가지 방법으로 진행됩니다.
  - 판단 이유: The claim mentions two main methods for reissuing, but the FAQ only explicitly mentions two options (in-store and online), without explicitly stating that these are the 'main' or 'primary' methods. The claim's phrasing introduces a generalization not directly supported by the evidence.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer mentions the two methods (online and in-store) and the processing time for online applications (2-3 business days). However, it does not explicitly outline the step-by-step procedure in a clear sequential order as requested in the question.

**RAG에 전달된 실제 근거**

- USIM-0041: 재발급 신청 절차를 순서대로 알려주세요.

매장을 방문하시면 즉시 발급받으실 수 있습니다. 매장 방문이 어려우시면 온라인으로 신청해 택배로 받으실 수 있으며 2~3 영업일이 걸립니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0041: 재발급 신청 절차를 순서대로 알려주세요.

매장을 방문하시면 즉시 발급받으실 수 있습니다. 매장 방문이 어려우시면 온라인으로 신청해 택배로 받으실 수 있으며 2~3 영업일이 걸립니다.

## PIPE-USIM-0046 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 유심 잃어버려서 아들이 대신 받으려면 뭐 줘야 해요

**저장된 답변**

위임장을 작성해 주시고, 제 신분증 사본과 아들의 신분증 사본을 함께 제출해 주시면 됩니다.

- UNSUPPORTED: 위임장을 작성해 주시고, 제 신분증 사본과 아들의 신분증 사본을 함께 제출해 주시면 됩니다.
  - 판단 이유: The claim mentions submitting both the user's ID copy and their son's ID copy, but the FAQ source (USIM-0046) only states that the son's ID is required, not that it must be submitted along with the user's ID. The exact wording and conditions differ.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys all requested facts from the reference, including the need for a power of attorney, the user's ID copy, and the child's ID copy.

**RAG에 전달된 실제 근거**

- USIM-0046: 유심 잃어버려서 아들이 대신 받으려면 뭐 줘야 해요

위임장 써 주시고, 제 신분증 사본을 주시면 됩니다. 아들 신분증도 있어야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0046: 유심 잃어버려서 아들이 대신 받으려면 뭐 줘야 해요

위임장 써 주시고, 제 신분증 사본을 주시면 됩니다. 아들 신분증도 있어야 합니다.

## PIPE-USIM-0067 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 제 폰에서도 eSIM 될까요

**저장된 답변**

네, 가능합니다. 단, 귀하의 폰 모델이 eSIM을 지원해야 합니다. 지원 여부는 제조사에 문의하시거나, 제품 사양을 확인해 보시는 것이 좋습니다. 비용은 2,750원입니다.

- UNSUPPORTED: 지원 여부는 제조사에 문의하시거나, 제품 사양을 확인해 보시는 것이 좋습니다.
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ does not mention consulting the manufacturer or checking product specifications.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that eSIM is possible if the phone model supports it, mentions checking with the manufacturer or product specifications, and provides the cost of 2,750 won, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0067: 제 폰에서도 eSIM 될까요

eSIM을 지원하는 단말이면 가능합니다. 비용은 2,750원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0067: 제 폰에서도 eSIM 될까요

eSIM을 지원하는 단말이면 가능합니다. 비용은 2,750원입니다.

## PIPE-USIM-0058 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 유심이 파손돼서 대리인이 재발급받으려면 어떤 서류가 필요한가요?

**저장된 답변**

대리인이 유심을 재발급받으려면 위임장, 대리인의 신분증, 그리고 가입자의 신분증 사본이 필요합니다. 본인의 신분증은 필요하지 않습니다.

- UNSUPPORTED: 본인의 신분증은 필요하지 않습니다.
  - 판단 이유: The FAQ sources state that when the subscriber themselves visits, they only need their ID. This implies that the subscriber's ID is required, contradicting the claim that it is not needed.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys all the required facts from the reference: the documents needed for a proxy to reissue a SIM card (power of attorney, proxy's ID, and a copy of the subscriber's ID), and clarifies that the subscriber's ID is not required. This fully aligns with the reference provided.

**RAG에 전달된 실제 근거**

- USIM-0058: 유심이 파손돼서 대리인이 재발급받으려면 어떤 서류가 필요한가요?

본인이 방문하시는 경우에는 신분증만 지참하시면 됩니다. 대리인이 방문할 때는 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.

- USIM-0038: 유심 재발급을 대리인에게 맡길 때 절차를 알려주세요.

대리인이 위임장, 대리인 신분증, 가입자 신분증 사본을 지참해 매장을 방문하면 됩니다.

- USIM-0034: 기기변경 시 유심 재발급 구비 서류를 알려주세요.

본인 신분증만 지참하시면 됩니다. 대리인이 방문하면 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0058: 유심이 파손돼서 대리인이 재발급받으려면 어떤 서류가 필요한가요?

본인이 방문하시는 경우에는 신분증만 지참하시면 됩니다. 대리인이 방문할 때는 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.

## PIPE-USIM-0049 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기변경 때 유심을 택배로 받을 수 있을까요?

**저장된 답변**

네, 기기변경 시 유심을 택배로 받으실 수 있습니다. 온라인으로 신청하시면 2~3 영업일 내에 배송받으실 수 있습니다.

- UNSUPPORTED: 기기변경 시 유심을 택배로 받으실 수 있습니다.
  - 판단 이유: The claim mentions '기기변경 시' (when changing devices), but none of the FAQ sources explicitly mention this condition. The sources only mention '온라인으로 신청하시면' (when applying online), which is not the same as changing devices.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question about receiving a SIM card via快递 during a device change, including the method of online application and the delivery time of 2-3 business days, which matches the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0049: 기기변경 때 유심을 택배로 받을 수 있을까요?

네, 온라인으로 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다.

- USIM-0029: 새 폰에 쓸 유심을 택배로 받는 방법 알려주세요.

온라인으로 재발급을 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다. 비용은 7,700원입니다.

- USIM-0009: 기기변경 시 유심 재발급 택배 소요 기간을 알려주세요.

온라인 신청 시 택배로 2~3 영업일이 걸립니다. 매장 방문은 즉시 발급이며 비용은 7,700원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0049: 기기변경 때 유심을 택배로 받을 수 있을까요?

네, 온라인으로 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다.

## PIPE-USIM-0060 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 해외에서 홈페이지로 분실 신고가 가능한가요?

**저장된 답변**

네, 해외에서도 홈페이지를 통해 분실 신고가 가능합니다. 신고 후 즉시 이용 정지 처리가 이루어집니다.

- UNSUPPORTED: 해외에서도 홈페이지를 통해 분실 신고가 가능합니다.
  - 판단 이유: The claim mentions '해외에서도' (overseas as well), but the FAQ source does not mention anything about overseas access or availability. The FAQ only states that reporting via the homepage results in immediate service suspension, but does not confirm if the homepage is accessible or usable overseas.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that overseas users can report a lost card via the website and that the service will be immediately suspended, which matches the reference.

**RAG에 전달된 실제 근거**

- USIM-0060: 해외에서 홈페이지로 분실 신고가 가능한가요?

네, 홈페이지에서 신고하시면 즉시 이용 정지됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0060: 해외에서 홈페이지로 분실 신고가 가능한가요?

네, 홈페이지에서 신고하시면 즉시 이용 정지됩니다.

## PIPE-USIM-0065 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 외국 가기 전에 유심 바로 받을 수 있나요

**저장된 답변**

네, 외국으로 떠나시기 전에 매장을 방문하시면 바로 유심을 받으실 수 있습니다.

- UNSUPPORTED: 외국으로 떠나시기 전에 매장을 방문하시면 바로 유심을 받으실 수 있습니다.
  - 판단 이유: The claim mentions visiting the store before leaving for abroad to immediately receive a SIM card. However, the FAQ sources only state that visiting the store allows one to receive the SIM card immediately but do not mention anything about traveling abroad or the context of leaving for foreign travel. The condition of 'before leaving for abroad' is not addressed in the FAQ sources, making the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested fact that a user can obtain a SIM card immediately by visiting the store before traveling abroad, as stated in the reference.

**RAG에 전달된 실제 근거**

- USIM-0065: 외국 가기 전에 유심 바로 받을 수 있나요

네, 매장에 가시면 그 자리에서 받으실 수 있습니다.

- USIM-0045: 외국 가기 전에 유심 새로 받으려면 어떻게 해요

매장에 가시면 바로 받으실 수 있습니다. 7,700원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0065: 외국 가기 전에 유심 바로 받을 수 있나요

네, 매장에 가시면 그 자리에서 받으실 수 있습니다.

## PIPE-USIM-0086 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 분실 재발급을 대리인에게 부탁했는데 가입자 신분증 원본이 없어 거절됐습니다

**저장된 답변**

대리인을 통한 분실 재발급 시, 가입자 신분증의 원본이 아닌 사본이 필요합니다. 위임장과 함께 대리인의 신분증도 함께 제출하셨는지 확인해 주세요. 필요한 서류가 모두 준비되었다면 다시 신청해 주시기 바랍니다.

- UNSUPPORTED: 필요한 서류가 모두 준비되었다면 다시 신청해 주시기 바랍니다.
  - 판단 이유: The provided FAQ source does not mention anything about resubmitting the application after preparing all required documents, so the claim is not supported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that a copy of the insured's ID is required instead of the original, and also mentions checking if the power of attorney and the agent's ID are submitted. This aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0086: 분실 재발급을 대리인에게 부탁했는데 가입자 신분증 원본이 없어 거절됐습니다

대리인 방문은 가입자 신분증 사본이면 됩니다. 위임장과 대리인 신분증을 함께 지참했는지 확인해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0086: 분실 재발급을 대리인에게 부탁했는데 가입자 신분증 원본이 없어 거절됐습니다

대리인 방문은 가입자 신분증 사본이면 됩니다. 위임장과 대리인 신분증을 함께 지참했는지 확인해 주세요.

## PIPE-USIM-0083 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기존 유심이 자꾸 오류가 나서 eSIM으로 바꾸고 싶은데 제 폰이 지원하는지 모르겠어요

**저장된 답변**

eSIM 지원 여부는 매장에서 확인해 드릴 수 있습니다. 폰이 eSIM을 지원한다면 전환이 가능하며, 발급 비용은 2,750원입니다. 매장 방문을 권장드립니다.

- UNSUPPORTED: 매장 방문을 권장드립니다.
  - 판단 이유: The claim '매장 방문을 권장드립니다.' is not explicitly supported by any of the provided FAQ sources. While the sources mention that device support can be checked at a store, they do not explicitly recommend visiting a store.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user should check eSIM support at a store, mentions the possibility of conversion if supported, and provides the issuance cost of 2,750 won. This fully aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0083: 기존 유심이 자꾸 오류가 나서 eSIM으로 바꾸고 싶은데 제 폰이 지원하는지 모르겠어요

eSIM은 지원 단말에서만 전환하실 수 있습니다. 단말 지원 여부는 매장에서 확인해 드리며, 발급 비용은 2,750원입니다.

- USIM-0087: 유심이 파손돼 eSIM으로 바꾸려는데 제 폰이 안 된다고 합니다

eSIM은 지원 단말에서만 발급할 수 있습니다. 지원하지 않는 단말이면 물리 유심을 재발급받으셔야 합니다.

- USIM-0091: 유심이 자꾸 인식이 안 됩니다. eSIM으로 바꿀 수 있나요.

eSIM을 지원하는 단말이라면 바꾸실 수 있습니다. 발급 비용은 2,750원이고, 듀얼심으로 최대 2개 회선을 동시에 쓰실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0083: 기존 유심이 자꾸 오류가 나서 eSIM으로 바꾸고 싶은데 제 폰이 지원하는지 모르겠어요

eSIM은 지원 단말에서만 전환하실 수 있습니다. 단말 지원 여부는 매장에서 확인해 드리며, 발급 비용은 2,750원입니다.

## PIPE-USIM-0092 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 유심이 깨져서 새로 받았는데 예전 게 아직 되나요

**저장된 답변**

아니요, 유심이 깨져서 새로 받으셨다면 이전 유심은 자동으로 해지되어 더 이상 작동하지 않습니다. 새로운 유심으로 서비스가 제공됩니다.

- UNSUPPORTED: 새로운 유심으로 서비스가 제공됩니다.
  - 판단 이유: None of the faqSources mention that service is provided with the new SIM; they only mention the old SIM being canceled.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys that the old SIM card is automatically canceled and no longer works after receiving a new one, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0092: 유심이 깨져서 새로 받았는데 예전 게 아직 되나요

아닙니다. 새로 받으면 예전 건 자동으로 없어집니다.

- USIM-0088: 유심이 안 되는데 새로 받으면 예전 것도 계속 되는 거예요?

아닙니다. 새 유심을 재발급받으면 기존 유심은 자동으로 해지됩니다.

- USIM-0084: 기기변경 후 예전 유심이 아직 살아 있는 것 같습니다

새 유심을 재발급받으면 기존 유심은 자동으로 해지됩니다. 재발급이 정상 완료됐는지 확인해 보세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0092: 유심이 깨져서 새로 받았는데 예전 게 아직 되나요

아닙니다. 새로 받으면 예전 건 자동으로 없어집니다.

## PIPE-USIM-0075 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 외국에서 산 폰인데 eSIM이 안 잡혀요

**저장된 답변**

eSIM 기능은 지원 가능한 단말 모델에서만 사용 가능합니다. 외국에서 구매하신 폰이 eSIM을 지원하는지 매장이나 제품 설명서를 통해 확인해 주시기 바랍니다.

- UNSUPPORTED: 외국에서 구매하신 폰이 eSIM을 지원하는지 매장이나 제품 설명서를 통해 확인해 주시기 바랍니다.
  - 판단 이유: The FAQ source 'USIM-0075' mentions checking with a store but does not mention checking the product manual or specifically address phones purchased abroad.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that eSIM is only available on supported devices and directs the user to check with a store or product manual, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0075: 외국에서 산 폰인데 eSIM이 안 잡혀요

eSIM은 지원 단말에서만 쓸 수 있습니다. 단말 지원 여부를 매장에서 확인해 보세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0075: 외국에서 산 폰인데 eSIM이 안 잡혀요

eSIM은 지원 단말에서만 쓸 수 있습니다. 단말 지원 여부를 매장에서 확인해 보세요.

## PIPE-USIM-0097 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 유심 깨졌을 때 매장이랑 택배 중 뭐가 나아요?

**저장된 답변**

비용은 두 방법 모두 7,700원으로 동일합니다. 상황에 맞게 선택하시면 됩니다.

- UNSUPPORTED: 상황에 맞게 선택하시면 됩니다.
  - 판단 이유: The claim is not directly supported by the FAQ source. The source mentions the cost and time difference but does not explicitly recommend choosing based on the situation.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the cost information (7,700원 for both methods) and provides a recommendation to choose based on the situation, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- USIM-0097: 유심 깨졌을 때 매장이랑 택배 중 뭐가 나아요?

급하면 매장이 즉시 발급이라 낫습니다. 택배는 2~3 영업일 걸리고 비용은 둘 다 7,700원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0097: 유심 깨졌을 때 매장이랑 택배 중 뭐가 나아요?

급하면 매장이 즉시 발급이라 낫습니다. 택배는 2~3 영업일 걸리고 비용은 둘 다 7,700원입니다.

## PIPE-USIM-0104 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 기기변경 시 분실 신고를 하는 것과 재발급만 하는 것은 어떻게 다른가요?

**저장된 답변**

분실 신고는 즉시 기기의 사용을 정지시키는 절차로, 유심의 분실이나 도난 상황에서 보안을 강화하기 위해 이루어집니다. 반면에 재발급은 새로운 유심을 발급받으면서 기존 유심이 자동으로 해지되는 과정으로, 유심만 교체하는 경우에 해당합니다. 따라서 분실이 의심되는 경우에는 분실 신고를, 단순히 유심만 교체하려는 경우에는 재발급 절차를 진행하시면 됩니다.

- UNSUPPORTED: 분실 신고는 즉시 기기의 사용을 정지시키는 절차로, 유심의 분실이나 도난 상황에서 보안을 강화하기 위해 이루어집니다.
  - 판단 이유: The claim mentions '기기의 사용을 정지시키는 절차' (stopping device usage) and '보안을 강화하기 위해' (to enhance security), which are not explicitly stated in the FAQ source. The source only mentions '즉시 정지 조치' (immediate stop measure) but does not specify that it stops device usage or mentions security enhancement.

- UNSUPPORTED: 따라서 분실이 의심되는 경우에는 분실 신고를, 단순히 유심만 교체하려는 경우에는 재발급 절차를 진행하시면 됩니다.
  - 판단 이유: The claim introduces a conditional recommendation ('분실이 의심되는 경우' and '유심만 교체하려는 경우') which is not explicitly stated in the source. The source only states '잃어버린 게 아니라면 재발급만 하시면 됩니다' (if it's not lost, just reissue), but does not provide the full conditional logic presented in the claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully explains the difference between reporting a lost SIM card and reissuing a new one, including the security implications of each and when to use each procedure.

**RAG에 전달된 실제 근거**

- USIM-0104: 기기변경 시 분실 신고를 하는 것과 재발급만 하는 것은 어떻게 다른가요?

분실 신고는 즉시 정지 조치이고, 재발급은 새 유심을 받으면서 기존 유심이 자동 해지되는 절차입니다. 잃어버린 게 아니라면 재발급만 하시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0104: 기기변경 시 분실 신고를 하는 것과 재발급만 하는 것은 어떻게 다른가요?

분실 신고는 즉시 정지 조치이고, 재발급은 새 유심을 받으면서 기존 유심이 자동 해지되는 절차입니다. 잃어버린 게 아니라면 재발급만 하시면 됩니다.

## PIPE-USIM-0099 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / 
- 질문: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?

**저장된 답변**

물리 유심을 그대로 사용하는 경우 기존 유심을 새 기기에 삽입하여 사용할 수 있습니다. 반면에 eSIM을 새로 받는 경우, 2,750원의 비용이 발생하며, 지원하는 단말에서만 활성화 가능합니다. eSIM의 장점은 듀얼심 기능을 통해 최대 2개의 회선을 동시에 사용할 수 있다는 점입니다. 어떤 방식이 더 적합한지는 사용 패턴과 기기 호환성을 고려해 결정하시면 됩니다.

**grounding 검증 오류**: ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the differences between using a physical SIM and getting a new eSIM, including the cost, device compatibility, and the dual SIM functionality of eSIM. All required facts are accurately conveyed.

**RAG에 전달된 실제 근거**

- USIM-0099: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?

eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0099: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?

eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.

## PIPE-NAME_CHANGE-0015 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 남편 폰 제 이름으로 하는데 할부 남았으면 어떻게 돼요

**저장된 답변**

심사 결과에 따라 결정됩니다.

- UNSUPPORTED: 심사 결과에 따라 결정됩니다.
  - 판단 이유: The claim '심사 결과에 따라 결정됩니다.' (The decision is made based on the review result) is not directly supported by the provided FAQ evidence. The FAQ source states '통과하면 됩니다' (If it passes, it's okay), which implies a binary outcome (pass/fail) rather than a decision based on the review result. The claim is not explicitly mentioned in the source, and the meaning does not fully align.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the question by stating that the decision depends on the credit review result. However, it does not fully convey the specific information from the reference that the person whose name is on the phone must undergo a credit check and pass it.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0015: 남편 폰 제 이름으로 하는데 할부 남았으면 어떻게 돼요

제 이름으로 받는 분이 신용 심사를 받으셔야 합니다. 통과하면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0015: 남편 폰 제 이름으로 하는데 할부 남았으면 어떻게 돼요

제 이름으로 받는 분이 신용 심사를 받으셔야 합니다. 통과하면 됩니다.

## PIPE-NAME_CHANGE-0020 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 미성년 자녀가 성인이 됐는데 명의를 자녀 앞으로 바꾸는 순서를 알려주세요.

**저장된 답변**

미성년 자녀가 성인이 되어 명의 변경을 원하시는군요. 이 과정에서 별도의 수수료는 발생하지 않습니다.

- UNSUPPORTED: 미성년 자녀가 성인이 되어 명의 변경을 원하시는군요.
  - 판단 이유: The claim mentions a minor child becoming an adult and wanting to change the name, but the FAQ source does not mention anything about the age of the child or the reason for the name change. The FAQ only discusses the process and lack of fee, not the eligibility or conditions related to the child's age.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested facts, including the process of changing the name to the adult child and the absence of additional fees.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0020: 미성년 자녀가 성인이 됐는데 명의를 자녀 앞으로 바꾸는 순서를 알려주세요.

두 분이 신분증을 챙겨 매장을 방문하시면 접수되고, 미납 요금이 없으면 30분 이내에 처리됩니다. 수수료는 없습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0020: 미성년 자녀가 성인이 됐는데 명의를 자녀 앞으로 바꾸는 순서를 알려주세요.

두 분이 신분증을 챙겨 매장을 방문하시면 접수되고, 미납 요금이 없으면 30분 이내에 처리됩니다. 수수료는 없습니다.

## PIPE-NAME_CHANGE-0025 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 가족 간 명의변경 시 방문과 서류 절차를 정리해 주세요.

**저장된 답변**

가족 간 명의변경을 위해서는 양도인과 양수인 모두 신분증을 지참하고 함께 매장을 방문해야 합니다. 만약 한 분이 직접 방문이 어려우시다면, 위임장을 준비하여 대리로 진행할 수 있습니다. 방문 시 필요한 서류는 구체적인 상황에 따라 다를 수 있으나, 일반적으로 신분증 외에 추가적인 서류 요청이 있을 수 있으니 매장 직원에게 안내를 받으시는 것이 좋습니다.

- UNSUPPORTED: 양도인과 양수인 모두 신분증을 지참하고 함께 매장을 방문해야 합니다
  - 판단 이유: The claim states that both the transferor and transferee must bring their ID and visit the store together. However, the FAQ source indicates that if one party cannot visit, a power of attorney can be used for proxy processing. This contradicts the claim's requirement that both must be present together.

- UNSUPPORTED: 신분증 외에 추가적인 서류 요청이 있을 수 있으니 매장 직원에게 안내를 받으시는 것이 좋습니다
  - 판단 이유: The claim mentions that additional documents may be requested, and it is recommended to consult the store staff. However, the FAQ source does not mention anything about additional documents or consulting store staff, so this claim is not supported by the evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the required facts about the visit and document procedures for family name changes, including the need for both parties to visit the store with ID, the option to use a power of attorney if one cannot visit, and the possibility of additional documents depending on the situation.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0025: 가족 간 명의변경 시 방문과 서류 절차를 정리해 주세요.

양도인과 양수인이 각각 신분증을 지참하고 함께 매장을 방문합니다. 한쪽이 방문하지 못하면 위임장으로 대리 진행이 가능합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0025: 가족 간 명의변경 시 방문과 서류 절차를 정리해 주세요.

양도인과 양수인이 각각 신분증을 지참하고 함께 매장을 방문합니다. 한쪽이 방문하지 못하면 위임장으로 대리 진행이 가능합니다.

## PIPE-NAME_CHANGE-0054 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 미성년자끼리 명의변경 되나요

**저장된 답변**

미성년자끼리의 명의변경은 불가능합니다. 관련 규정에 따라 미성년자 간의 명의 변경은 지원되지 않습니다.

- UNSUPPORTED: 관련 규정에 따라 미성년자 간의 명의 변경은 지원되지 않습니다.
  - 판단 이유: FAQ 답변에서는 '지원되지 않습니다.'라는 표현이 없으며, '불가능합니다.' 또는 '진행할 수 없습니다.'라는 표현만 사용되었습니다. 이는 '지원되지 않습니다.'와 의미가 다릅니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer directly addresses the user's question by stating that name changes between minors are not possible, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0054: 미성년자끼리 명의변경 되나요

안 됩니다. 미성년자 사이의 명의변경은 불가능합니다.

- NAME_CHANGE-0027: 미성년자 간 명의변경 절차를 알고 싶습니다.

미성년자 사이의 명의변경은 애초에 진행할 수 없습니다.

- NAME_CHANGE-0003: 중학생 애들끼리 폰 명의 주고받을 수 있어요?

미성년자 사이의 명의변경은 불가능합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0054: 미성년자끼리 명의변경 되나요

안 됩니다. 미성년자 사이의 명의변경은 불가능합니다.

## PIPE-NAME_CHANGE-0048 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-2
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 만 17세 동생에게 제 회선을 넘길 수 있나요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / None

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0048: 만 17세 동생에게 제 회선을 넘길 수 있나요?

본인이 성인이라면 가능합니다. 양도인과 양수인이 모두 미성년자인 경우에만 불가능합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0048: 만 17세 동생에게 제 회선을 넘길 수 있나요?

본인이 성인이라면 가능합니다. 양도인과 양수인이 모두 미성년자인 경우에만 불가능합니다.

## PIPE-NAME_CHANGE-0039 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 핸드폰 값 남았는데 이름 바꿀 수 있어요?

**저장된 답변**

네, 핸드폰 값이 남아 있으셔도 이름 변경이 가능합니다. 하지만 변경을 위해서는 양수인의 신용 심사를 통과해야 합니다.

- UNSUPPORTED: 핸드폰 값이 남아 있으셔도 이름 변경이 가능합니다
  - 판단 이유: The claim mentions '핸드폰 값이 남아 있으셔도' (even if the phone value remains), which is not addressed in the FAQ evidence. The FAQ only states that the name change is possible if the buyer passes the credit check, but it does not mention anything about phone value remaining.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully aligns with the reference, confirming that name change is possible even if phone payments remain, but requires the buyer's credit approval.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0039: 핸드폰 값 남았는데 이름 바꿀 수 있어요?

가능합니다. 다만 양수인이 신용 심사를 통과해야 진행됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0039: 핸드폰 값 남았는데 이름 바꿀 수 있어요?

가능합니다. 다만 양수인이 신용 심사를 통과해야 진행됩니다.

## PIPE-NAME_CHANGE-0044 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 당일에 바로 처리가 가능한가요?

**저장된 답변**

네, 당일에 바로 처리가 가능합니다. 매장 방문 시 일반적으로 30분 이내에 완료되며, 추가 수수료는 발생하지 않습니다.

- UNSUPPORTED: 당일에 바로 처리가 가능합니다.
  - 판단 이유: The FAQ source 'PORTING-0061' states that processing may be difficult to complete on the same day and may take up to 24 hours, which contradicts the claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question about same-day processing, confirming it is possible, stating the time frame (30 minutes or less), and noting there are no additional fees. This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0044: 당일에 바로 처리가 가능한가요?

가능합니다. 매장 방문 시 30분 이내에 처리되며 수수료도 없습니다.

- PORTING-0061: 20:00 직전에 신청하면 당일 처리가 가능한가요?

처리 시간이 20:00까지라 당일 완료가 어려울 수 있습니다. 늦어도 24시간 이내에는 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0044: 당일에 바로 처리가 가능한가요?

가능합니다. 매장 방문 시 30분 이내에 처리되며 수수료도 없습니다.

## PIPE-NAME_CHANGE-0042 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?

**저장된 답변**

네, 가능합니다. 하지만 현재 남아 있는 할부금을 모두 정산하거나, 양수인인 본인이 신용 심사를 통과해야 합니다.

- UNSUPPORTED: 현재 남아 있는 할부금을 모두 정산하거나
  - 판단 이유: The claim about settling all remaining installments is not directly supported by any of the provided FAQ sources. The FAQ sources mention that the buyer must pass a credit check, but they do not mention anything about settling remaining installments.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully aligns with the reference, accurately conveying that it is possible to transfer the phone, but the buyer must settle any remaining installments or pass a credit check.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0042: 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?

가능합니다. 다만 할부금이 남아 있어 양수인인 본인이 신용 심사를 통과해야 합니다.

- NAME_CHANGE-0018: 회사 그만두면서 폰 받으려는데 할부 있으면 안 되나요

됩니다. 다만 받는 분이 신용 심사를 통과해야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0042: 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?

가능합니다. 다만 할부금이 남아 있어 양수인인 본인이 신용 심사를 통과해야 합니다.

## PIPE-NAME_CHANGE-0051 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 할부금이 남아 있어도 명의 바꿀 수 있나요.

**저장된 답변**

네, 할부금이 남아 있어도 명의 변경이 가능합니다. 하지만 양수인의 신용 심사를 통과해야 합니다. 심사를 거쳐 승인되면 명의 변경이 이루어질 수 있습니다. 미성년자 간의 명의 변경은 불가능합니다.

- UNSUPPORTED: 할부금이 남아 있어도 명의 변경이 가능합니다
  - 판단 이유: FAQ sources indicate that if there is remaining installment payment, credit check is required for the new owner. This implies that it is not automatically possible to change the name if there is remaining installment payment, but rather requires additional steps.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by confirming that name change is possible even with remaining installment payments, provided the new owner passes a credit check, and explicitly states that it is not possible between minors. This matches the reference group's answer.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0051: 할부금이 남아 있어도 명의 바꿀 수 있나요.

단말 할부금이 남아 있으면 양수인의 신용 심사를 거쳐야 합니다. 심사를 통과하시면 명의변경이 가능합니다. 미성년자 사이의 명의변경은 불가능합니다.

- NAME_CHANGE-0090: 할부 남은 거랑 다 낸 거랑 명의 바꾸는 게 달라요

남아 있으면 받는 사람이 신용 심사를 받아야 합니다. 다 냈으면 그냥 됩니다.

- NAME_CHANGE-0087: 할부 있는 거랑 없는 거 명의변경 차이가 뭐예요

할부금이 없으면 바로 되고, 남아 있으면 신용 심사를 거쳐야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0051: 할부금이 남아 있어도 명의 바꿀 수 있나요.

단말 할부금이 남아 있으면 양수인의 신용 심사를 거쳐야 합니다. 심사를 통과하시면 명의변경이 가능합니다. 미성년자 사이의 명의변경은 불가능합니다.

## PIPE-NAME_CHANGE-0063 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 명의변경 신청했는데 신용 심사에서 거절됐습니다

**저장된 답변**

신용 심사에서 거절된 경우, 양수인의 신용 상태가 명의변경 조건을 충족하지 못한 것으로 보입니다. 할부금이 남아 있거나 신용 점수가 기준에 미치지 못한 경우가 거절 사유일 수 있습니다.

- UNSUPPORTED: 신용 심사에서 거절된 경우, 양수인의 신용 상태가 명의변경 조건을 충족하지 못한 것으로 보입니다.
  - 판단 이유: The claim states that if the credit check is rejected, the buyer's credit status does not meet the name change conditions. However, the FAQ source only mentions that if there are remaining installment payments, the buyer must pass the credit check for the name change to be possible. It does not explicitly state that a rejected credit check means the buyer's credit status does not meet the conditions.

- UNSUPPORTED: 할부금이 남아 있거나 신용 점수가 기준에 미치지 못한 경우가 거절 사유일 수 있습니다.
  - 판단 이유: The FAQ source mentions that if there are remaining installment payments, the buyer must pass the credit check for the name change to be possible. However, it does not explicitly state that a low credit score is a reason for rejection. The claim adds information not directly supported by the source.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested facts from the reference, including the reasons for rejection (remaining installments or insufficient credit score) and the implication that the buyer's credit status does not meet the conditions for name change.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0063: 명의변경 신청했는데 신용 심사에서 거절됐습니다

할부금이 남아 있는 경우 양수인의 신용 심사를 통과해야 명의변경이 가능합니다. 심사 결과에 따라 진행이 어려울 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0063: 명의변경 신청했는데 신용 심사에서 거절됐습니다

할부금이 남아 있는 경우 양수인의 신용 심사를 통과해야 명의변경이 가능합니다. 심사 결과에 따라 진행이 어려울 수 있습니다.

## PIPE-NAME_CHANGE-0076 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 엄마가 오는 거랑 위임장 받아오는 거랑 뭐가 더 확실해요?

**저장된 답변**

따라서 확실성 측면에서는 두 방법 모두 동등합니다.

- UNSUPPORTED: 따라서 확실성 측면에서는 두 방법 모두 동등합니다.
  - 판단 이유: The claim discusses '확실성 측면' (certainty aspect) and states that both methods are equal, but the provided FAQ source only mentions that the processing result is the same, not the certainty aspect. The source does not directly support the claim's specific wording or meaning.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that both methods are equally certain in terms of outcome, as stated in the reference.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0076: 엄마가 오는 거랑 위임장 받아오는 거랑 뭐가 더 확실해요?

두 분이 함께 오시면 신분증만으로 바로 됩니다. 위임장 대리는 서류가 하나 더 필요하지만 처리 결과는 같습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0076: 엄마가 오는 거랑 위임장 받아오는 거랑 뭐가 더 확실해요?

두 분이 함께 오시면 신분증만으로 바로 됩니다. 위임장 대리는 서류가 하나 더 필요하지만 처리 결과는 같습니다.

## PIPE-ROAMING-0018 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 로밍 차단 풀려면 뭐 해야 돼요

**저장된 답변**

본인 인증을 진행하시면 로밍 차단이 해제됩니다.

- UNSUPPORTED: 본인 인증을 진행하시면 로밍 차단이 해제됩니다.
  - 판단 이유: The claim states that 'performing self-authentication will解除 roaming block,' but the FAQ source only mentions 'you need to perform self-authentication' without explicitly stating that it will解除 the roaming block. The evidence does not directly support the specific claim about the roaming block being解除.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer accurately conveys the required fact from the reference, which states that performing a self-authentication will解除 the roaming block. The answer is complete and matches the reference.

**RAG에 전달된 실제 근거**

- ROAMING-0018: 로밍 차단 풀려면 뭐 해야 돼요

본인 인증을 하시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0018: 로밍 차단 풀려면 뭐 해야 돼요

본인 인증을 하시면 됩니다.

## PIPE-ROAMING-0034 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 손목시계도 로밍 신청해요

**저장된 답변**

손목시계도 휴대폰과 별도로 로밍 신청이 필요합니다. 워치 전용 로밍 서비스를 확인해 주세요.

- UNSUPPORTED: 워치 전용 로밍 서비스를 확인해 주세요.
  - 판단 이유: The claim is not directly supported by the provided FAQ evidence. The FAQ mentions that the watch requires a separate application, but it does not mention a 'watch-specific roaming service' or instruct the user to check for one.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that a separate roaming application is needed for the smartwatch and directs the user to check the watch-specific roaming service, which aligns with the reference group's answer.

**RAG에 전달된 실제 근거**

- ROAMING-0034: 손목시계도 로밍 신청해요

네, 워치는 휴대폰과 다른 회선이라 따로 신청하셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0034: 손목시계도 로밍 신청해요

네, 워치는 휴대폰과 다른 회선이라 따로 신청하셔야 합니다.

## PIPE-ROAMING-0041 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 3일만 가는데 일 단위 요금제로도 충분할까요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [3일]

**RAG에 전달된 실제 근거**

- ROAMING-0041: 3일만 가는데 일 단위 요금제로도 충분할까요?

가능합니다. 일 단위 9,900원 요금제를 필요하신 날짜만큼 결제해서 이용하실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0041: 3일만 가는데 일 단위 요금제로도 충분할까요?

가능합니다. 일 단위 9,900원 요금제를 필요하신 날짜만큼 결제해서 이용하실 수 있습니다.

## PIPE-ROAMING-0060 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 로밍 켰는데 10분이 지나도 연결이 안 돼요

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [10분]

**RAG에 전달된 실제 근거**

- ROAMING-0060: 로밍 켰는데 10분이 지나도 연결이 안 돼요

현지 망 자동 연결은 보통 3~5분 걸립니다. 그 이상 지연되면 단말을 재부팅한 뒤 데이터 로밍을 다시 켜 보세요.

- ROAMING-0055: 도착했는데 로밍이 안 잡혀요

단말의 데이터 로밍 설정이 켜져 있는지 확인해 주세요. 자동 연결에 3~5분이 걸리니 그래도 안 되면 재부팅 후 다시 시도해 보세요.

- ROAMING-0030: 로밍이 안 잡힐 때 확인 순서를 알려주세요.

먼저 앱이나 홈페이지에서 가입 상태를 확인하고, 단말의 데이터 로밍이 켜져 있는지 보세요. 자동 연결까지 3~5분이 걸리며, 그래도 안 되면 재부팅 후 다시 시도합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0060: 로밍 켰는데 10분이 지나도 연결이 안 돼요

현지 망 자동 연결은 보통 3~5분 걸립니다. 그 이상 지연되면 단말을 재부팅한 뒤 데이터 로밍을 다시 켜 보세요.

## PIPE-ROAMING-0056 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 보름 동안 로밍을 썼는데 요금이 생각보다 많이 나왔습니다

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 금액: [130000, 78000]

**RAG에 전달된 실제 근거**

- ROAMING-0056: 보름 동안 로밍을 썼는데 요금이 생각보다 많이 나왔습니다

일 단위는 하루 9,900원, 무제한은 하루 12,100원이 매일 부과됩니다. 7일 기간권 39,000원을 두 번 쓰는 편이 더 저렴할 수 있습니다.

- ROAMING-0061: 로밍 요금이 너무 많이 나와요

하루짜리는 9,900원, 무제한은 12,100원씩 매일 나갑니다. 일주일 넘게 쓰시면 7일권 39,000원이 더 쌉니다.

- ROAMING-0031: 오래 나가 있을 건데 로밍은 뭘로 해요

일주일씩 쓰시면 7일 기간권 39,000원이 있습니다. 하루짜리는 9,900원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0056: 보름 동안 로밍을 썼는데 요금이 생각보다 많이 나왔습니다

일 단위는 하루 9,900원, 무제한은 하루 12,100원이 매일 부과됩니다. 7일 기간권 39,000원을 두 번 쓰는 편이 더 저렴할 수 있습니다.

## PIPE-ROAMING-0071 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 한 달 정도 있을 예정인데 일 단위랑 무제한 중 뭐가 유리한가요?

**저장된 답변**

데이터 사용량이 많지 않으실 것 같다면 일 단위 데이터 요금제(9,900원)가 경제적입니다. 무제한 요금제는 12,100원으로 더 비싸지만 데이터 제한 없이 사용하고 싶으시다면 고려해볼 만합니다. 한 달 동안의 사용 패턴을 고려해 선택하시는 것이 좋겠습니다.

- UNSUPPORTED: 한 달 동안의 사용 패턴을 고려해 선택하시는 것이 좋겠습니다
  - 판단 이유: The FAQ source does not mention considering usage patterns over a month when choosing a plan, so this claim is not supported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the requested facts: the cost of the daily plan (9,900원), the cost of the unlimited plan (12,100원), and the recommendation to consider usage patterns. It aligns with the reference group's answer and provides a complete resolution to the user's question.

**RAG에 전달된 실제 근거**

- ROAMING-0071: 한 달 정도 있을 예정인데 일 단위랑 무제한 중 뭐가 유리한가요?

일 단위는 9,900원, 무제한은 12,100원으로 무제한이 더 비쌉니다. 데이터를 많이 쓰실 계획이 아니면 일 단위가 유리합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0071: 한 달 정도 있을 예정인데 일 단위랑 무제한 중 뭐가 유리한가요?

일 단위는 9,900원, 무제한은 12,100원으로 무제한이 더 비쌉니다. 데이터를 많이 쓰실 계획이 아니면 일 단위가 유리합니다.

## PIPE-ROAMING-0076 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 하루짜리 며칠 쓰는 거랑 일주일권이랑 뭐가 싸요

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [7일]

**RAG에 전달된 실제 근거**

- ROAMING-0076: 하루짜리 며칠 쓰는 거랑 일주일권이랑 뭐가 싸요

일주일권은 39,000원이고 하루짜리는 9,900원입니다. 며칠 이상 쓰시면 일주일권이 쌉니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0076: 하루짜리 며칠 쓰는 거랑 일주일권이랑 뭐가 싸요

일주일권은 39,000원이고 하루짜리는 9,900원입니다. 며칠 이상 쓰시면 일주일권이 쌉니다.

## PIPE-ROAMING-0074 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 워치와 태블릿 로밍은 각각 따로 신청해야 하나요, 하나로 묶이나요?

**저장된 답변**

워치와 태블릿은 각각 별개의 회선으로 운영되므로, 로밍을 위해서는 각각 따로 신청하셔야 합니다.

- UNSUPPORTED: 워치와 태블릿은 각각 별개의 회선으로 운영되므로, 로밍을 위해서는 각각 따로 신청하셔야 합니다.
  - 판단 이유: The claim mentions both '워치' (watch) and '태블릿' (tablet) as separate circuits requiring individual roaming applications. However, the provided FAQ sources only mention '워치' (watch) being a separate circuit and requiring separate roaming applications. There is no mention of '태블릿' (tablet) in any of the FAQ sources, making the claim unsupported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that watches and tablets operate on separate lines and must be applied for separately for roaming, which matches the reference.

**RAG에 전달된 실제 근거**

- ROAMING-0074: 워치와 태블릿 로밍은 각각 따로 신청해야 하나요, 하나로 묶이나요?

각각 별개 회선이라 따로 신청하셔야 합니다.

- ROAMING-0069: 폰 로밍만 하면 워치도 같이 되는 거예요?

아닙니다. 워치는 별개 회선이라 로밍을 따로 신청하셔야 합니다.

- ROAMING-0034: 손목시계도 로밍 신청해요

네, 워치는 휴대폰과 다른 회선이라 따로 신청하셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0074: 워치와 태블릿 로밍은 각각 따로 신청해야 하나요, 하나로 묶이나요?

각각 별개 회선이라 따로 신청하셔야 합니다.

## PIPE-SERVICE-0005 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 가입한 거 증명하는 서류 뗄 수 있어요?

**저장된 답변**

가입사실확인서를 홈페이지에서 무료로 즉시 발급받으실 수 있습니다. 필요하시면 바로 확인해 보세요.

- UNSUPPORTED: 필요하시면 바로 확인해 보세요.
  - 판단 이유: The claim is not present in the provided FAQ sources and cannot be confirmed.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user can obtain a '가입사실확인서' (proof of enrollment) for free and immediately through the website, which directly answers the user's question about obtaining documentation to prove their enrollment.

**RAG에 전달된 실제 근거**

- SERVICE-0005: 가입한 거 증명하는 서류 뗄 수 있어요?

가입사실확인서를 홈페이지에서 무료로 즉시 발급받으실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0005: 가입한 거 증명하는 서류 뗄 수 있어요?

가입사실확인서를 홈페이지에서 무료로 즉시 발급받으실 수 있습니다.

## PIPE-SERVICE-0019 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요.

**저장된 답변**

명의도용으로 의심되는 회선에 대해 신고를 원하시는군요. 고객센터로 직접 연락하시어 명의도용 신고를 접수하시면 됩니다. 접수 후에는 3영업일 이내에 조사가 진행되며, 도용이 확인되면 해당 요금은 전액 취소될 예정입니다. 신고 방법에 대한 자세한 안내는 고객센터로 문의해 주시기 바랍니다.

- UNSUPPORTED: 명의도용으로 의심되는 회선에 대해 신고를 원하시는군요.
  - 판단 이유: The claim is not directly supported by the FAQ evidence. The evidence does not mention anything about suspecting a line of identity theft or the intent to report it.

- UNSUPPORTED: 신고 방법에 대한 자세한 안내는 고객센터로 문의해 주시기 바랍니다.
  - 판단 이유: The claim is not directly supported by the FAQ evidence. The evidence does not mention contacting the customer service center for detailed reporting instructions.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer provides all the requested facts: the procedure to report suspected identity theft (contacting the customer service center), the investigation timeline (within 3 business days), and the outcome if the theft is confirmed (full cancellation of the charges).

**RAG에 전달된 실제 근거**

- SERVICE-0019: 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요.

고객센터로 명의도용 신고를 접수하시면 됩니다. 접수 후 3영업일 이내에 조사가 진행되고, 도용으로 확인되면 해당 요금은 전액 취소됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0019: 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요.

고객센터로 명의도용 신고를 접수하시면 됩니다. 접수 후 3영업일 이내에 조사가 진행되고, 도용으로 확인되면 해당 요금은 전액 취소됩니다.

## PIPE-SERVICE-0041 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 부가서비스 해지하고 다시 가입할 수 있나요

**저장된 답변**

네, 부가서비스를 해지한 후 다시 가입하실 수 있습니다. 신청하시면 즉시 새로운 부가서비스가 적용됩니다.

- UNSUPPORTED: 부가서비스를 해지한 후 다시 가입하실 수 있습니다.
  - 판단 이유: The claim about being able to re-subscribe after cancellation is not directly supported by any of the provided FAQ sources. The sources mention cancellation and immediate application of new subscriptions but do not explicitly confirm the possibility of re-subscription after cancellation.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the user can cancel and re-subscribe to the add-on service, and that the new service will be applied immediately upon application, which matches the reference group's answer.

**RAG에 전달된 실제 근거**

- SERVICE-0041: 부가서비스 해지하고 다시 가입할 수 있나요

네, 가입은 신청하시면 즉시 적용됩니다.

- SERVICE-0016: 부가서비스 가입이랑 해지는 어떻게 해요?

가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0041: 부가서비스 해지하고 다시 가입할 수 있나요

네, 가입은 신청하시면 즉시 적용됩니다.

## PIPE-SERVICE-0035 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 해지한 지 2년 된 회선도 증명서 발급되나요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [2년]

**RAG에 전달된 실제 근거**

- SERVICE-0035: 해지한 지 2년 된 회선도 증명서 발급되나요?

가능합니다. 해지일로부터 최근 3년분까지는 발급받으실 수 있습니다.

- SERVICE-0040: 해지한 번호 증명서도 나오나요

네, 해지일로부터 3년 이내면 발급됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0035: 해지한 지 2년 된 회선도 증명서 발급되나요?

가능합니다. 해지일로부터 최근 3년분까지는 발급받으실 수 있습니다.

## PIPE-SERVICE-0046 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 해지 신청했는데 아직도 서비스가 이용되고 있어요

**저장된 답변**

해지 신청을 하셨더라도 신청일이 속한 달의 말일까지는 서비스 이용이 계속됩니다. 정상적인 절차이므로 걱정하지 않으셔도 됩니다.

- UNSUPPORTED: 정상적인 절차이므로 걱정하지 않으셔도 됩니다.
  - 판단 이유: The FAQ source does not mention anything about the procedure being normal or about not worrying. It only confirms the service continuation until the end of the month.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant's answer fully addresses the user's question by explaining that service continues until the end of the month in which the cancellation was requested, which aligns with the reference provided.

**RAG에 전달된 실제 근거**

- SERVICE-0046: 해지 신청했는데 아직도 서비스가 이용되고 있어요

정상입니다. 해지를 신청하셔도 신청일이 속한 달 말일까지는 계속 이용하실 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0046: 해지 신청했는데 아직도 서비스가 이용되고 있어요

정상입니다. 해지를 신청하셔도 신청일이 속한 달 말일까지는 계속 이용하실 수 있습니다.

## PIPE-SERVICE-0051 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 부가서비스 해지했는데 요금이 또 나왔어요

**저장된 답변**

해지한 달의 요금은 일할 계산되어 청구되므로, 해지 후에도 해당 달의 요금이 발생할 수 있습니다. 다음 달부터는 더 이상 요금이 청구되지 않을 것입니다.

- UNSUPPORTED: 해지한 달의 요금은 일할 계산되어 청구되므로, 해지 후에도 해당 달의 요금이 발생할 수 있습니다.
  - 판단 이유: The FAQ source states that the monthly fee for the month of cancellation is not prorated and is charged in full, which directly contradicts the claim that it is prorated.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly mentions that the fee for the month of cancellation is calculated on a daily basis and may be charged after cancellation, but it incorrectly states that the fee is calculated on a daily basis, whereas the reference states that the monthly fee is not calculated on a daily basis and is charged in full.

**RAG에 전달된 실제 근거**

- SERVICE-0051: 부가서비스 해지했는데 요금이 또 나왔어요

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0051: 부가서비스 해지했는데 요금이 또 나왔어요

해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

## PIPE-SERVICE-0052 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 멤버십 등급이 왜 바뀌었어요

**저장된 답변**

멤버십 등급은 매년 1월에 직전 1년간의 납부 금액을 기준으로 갱신됩니다. 따라서 납부 금액의 변화에 따라 등급이 조정될 수 있습니다. 만약 최근 납부 금액이 감소하여 등급이 내려갔다면, 이는 자연스러운 등급 갱신 과정의 일환입니다.

- UNSUPPORTED: 만약 최근 납부 금액이 감소하여 등급이 내려갔다면, 이는 자연스러운 등급 갱신 과정의 일환입니다.
  - 판단 이유: The provided FAQ sources do not mention anything about the downgrade being a 'natural part of the renewal process.'

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the user's question by explaining that membership levels are updated annually in January based on the previous year's payment amount, which can lead to changes in the level. This aligns with the reference provided.

**RAG에 전달된 실제 근거**

- SERVICE-0052: 멤버십 등급이 왜 바뀌었어요

1년치 납부액을 기준으로 매년 1월에 등급이 다시 매겨집니다. 그래서 바뀔 수 있습니다.

- SERVICE-0042: 멤버십 등급이 왜 갑자기 내려갔는지 모르겠어요

멤버십은 직전 1년간 납부 금액을 기준으로 매년 1월에 갱신되어, 사용 금액이 줄면 등급이 낮아질 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0052: 멤버십 등급이 왜 바뀌었어요

1년치 납부액을 기준으로 매년 1월에 등급이 다시 매겨집니다. 그래서 바뀔 수 있습니다.

## PIPE-SERVICE-0058 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE
- 질문: 평일이랑 토요일 운영시간이 어떻게 다른가요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [2시간]

**RAG에 전달된 실제 근거**

- SERVICE-0058: 평일이랑 토요일 운영시간이 어떻게 다른가요?

평일은 10:00부터 19:00까지, 토요일은 10:00부터 17:00까지로 토요일이 두 시간 짧습니다.

- SERVICE-0063: 토요일에 가도 됩니까. 평일이랑 시간이 다른가요.

평일은 10:00부터 19:00까지, 토요일은 10:00부터 17:00까지 운영합니다. 토요일이 평일보다 두 시간 일찍 닫습니다. 일요일과 공휴일은 휴무입니다. 매장별로 운영 시간이 다를 수 있습니다.

- SERVICE-0018: 매장에 갈 건데 시간을 어떻게 맞추면 돼요?

평일은 10:00부터 19:00, 토요일은 10:00부터 17:00 사이에 방문하시면 됩니다. 일요일과 공휴일은 휴무이니 피해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0058: 평일이랑 토요일 운영시간이 어떻게 다른가요?

평일은 10:00부터 19:00까지, 토요일은 10:00부터 17:00까지로 토요일이 두 시간 짧습니다.

## PIPE-COMPOUND-002 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / RETRIEVAL_MISS
- 질문: 요금이 부담돼서 일부만 내도 정지가 풀리나요 그리고 일시 정지 중에도 요금이 부과되나요?

**저장된 답변**

일시 정지 중에도 요금은 부과되며, 정지 기간에는 기본료의 50%가 청구됩니다. 전액 면제는 아닙니다. 일부만 내셔도 정지 상태가 자동으로 해제되는 것은 아니며, 정지 해제를 위해서는 전체 요금을 정산해야 합니다. 요금 부담을 줄이기 위해서는 다른 요금제나 할인 프로그램을 확인해 보시는 것도 좋은 방법입니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately addresses both subquestions: it states that fees are charged during the temporary suspension (50% of the base fee) and clarifies that partial payment does not automatically lift the suspension, requiring full payment for resolution.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer correctly states that fees are charged during the temporary suspension (50% of the base fee), aligning with the reference.

**RAG에 전달된 실제 근거**

- TERMINATE-0060: 일시 정지 중에도 요금이 부과되나요?

네, 정지 기간에는 기본료의 50%가 부과됩니다.

- TERMINATE-0080: 정지시켰는데 요금이 반이나 나와요

정지 중에는 기본료의 50%가 부과됩니다. 전액 면제는 아닙니다.

- TERMINATE-0088: 정지했더니 요금이 나왔어요 왜 그래요

일시 정지 기간에도 기본료의 50%는 부과됩니다. 전액 면제가 아니라는 점 참고해 주세요.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0080: 요금이 부담돼서 일부만 내도 정지가 풀리나요?

재개는 미납 요금을 완납해야 2시간 이내에 이루어집니다.

- TERMINATE-0060: 일시 정지 중에도 요금이 부과되나요?

네, 정지 기간에는 기본료의 50%가 부과됩니다.

## PIPE-COMPOUND-003 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-1
- 판정: UNSCORED / PROCESSING_FAILURE, RETRIEVAL_MISS
- 질문: 본인 인증 안 하면 휴대폰 결제 얼마까지 돼요 그리고 1회 정지 기간을 30일보다 길게 설정할 수 있나요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / 근거에 없는 수치: [1회, 30일]

**RAG에 전달된 실제 근거**

- BILLING-0011: 본인 인증 안 하면 휴대폰 결제 얼마까지 돼요?

월 30만 원까지입니다. 본인 인증을 하면 월 100만 원까지 올릴 수 있고 바로 적용됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0011: 본인 인증 안 하면 휴대폰 결제 얼마까지 돼요?

월 30만 원까지입니다. 본인 인증을 하면 월 100만 원까지 올릴 수 있고 바로 적용됩니다.

- TERMINATE-0064: 1회 정지 기간을 30일보다 길게 설정할 수 있나요?

1회 최대 30일까지만 가능합니다. 연간 합산은 90일까지입니다.

## PIPE-COMPOUND-004 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 요금 밀렸는데 청구서 방법 바꿀 수 있나요 그리고 위약금 얼마 나오는지 미리 알려면 어떻게 해요?

**저장된 답변**

요금 밀렸더라도 청구서 방법은 바꿀 수 있으며, 다음 달부터 변경된 방법으로 적용됩니다.

- UNSUPPORTED: 요금 밀렸더라도 청구서 방법은 바꿀 수 있으며, 다음 달부터 변경된 방법으로 적용됩니다.
  - 판단 이유: The claim states that the billing method can be changed even if there is an overdue balance, and the change will take effect from the next month. However, the provided FAQ source (BILLING-0098) only mentions that changing the method will take effect from the next month's bill but does not mention anything about being able to change the method if there is an overdue balance. Since the condition about an overdue balance is not addressed in the source, the claim is not fully supported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the billing method can be changed even if there are outstanding payments, and that the change will take effect from the next month. This matches the reference provided in the reference group.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not address the second part of the question regarding the early termination fee. The reference group provides information about calculating the early termination fee based on the length of the contract, but this information is not included in the assistant's answer.

**RAG에 전달된 실제 근거**

- BILLING-0098: 요금 밀렸는데 청구서 방법 바꿀 수 있나요

네, 바꾸면 다음 달 청구서부터 적용됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0098: 요금 밀렸는데 청구서 방법 바꿀 수 있나요

네, 바꾸면 다음 달 청구서부터 적용됩니다.

- TERMINATE-0030: 위약금 얼마 나오는지 미리 알려면 어떻게 해요?

가입일 기준으로 12개월이 지났는지 보시면 됩니다. 12개월 전 해지는 할인 반환금 100%, 12개월 이상 24개월 미만은 50%입니다.

## PIPE-COMPOUND-005 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 요금제 바꾸면 그 달 요금은 어떻게 계산돼요 그리고 폰 잃어버려서 해지하면 위약금 있어요

**저장된 답변**

요금제를 변경하시면 변경된 요금제에 따라 그 달의 요금이 계산됩니다. 정확한 금액은 변경된 요금제의 세부 내용에 따라 달라지므로, 변경 후 안내된 요금 안내를 참고하시면 됩니다.

- UNSUPPORTED: 요금제를 변경하시면 변경된 요금제에 따라 그 달의 요금이 계산됩니다.
  - 판단 이유: The provided FAQ sources discuss termination fees and return amounts upon early termination of a contract, but they do not mention anything about changing a plan or how billing is calculated after a plan change. Therefore, there is no direct evidence to support this claim.

- UNSUPPORTED: 정확한 금액은 변경된 요금제의 세부 내용에 따라 달라지므로, 변경 후 안내된 요금 안내를 참고하시면 됩니다.
  - 판단 이유: The provided FAQ sources do not mention anything about plan changes, detailed billing calculations, or post-change fee notifications. Therefore, there is no direct evidence to support this claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that the monthly fee is calculated based on the new plan and that the exact amount depends on the details of the new plan, which aligns with the reference.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not address the penalty for early termination due to losing the phone, which is a separate subquestion.

**RAG에 전달된 실제 근거**

- TERMINATE-0098: 폰 잃어버렸을 때 해지하는 거랑 그냥 두는 거랑 위약금이 달라요?

해지하면 약정 24개월 기준 반환금(12개월 미만 100%, 이상 50%)이 발생합니다. 두면 반환금은 없지만 요금이 계속 나옵니다.

- TERMINATE-0018: 폰 잃어버려서 해지하면 위약금 있어요

약정 24개월 안에 해지하시면 있습니다. 12개월 전이면 할인 받은 거 전부(100%), 넘었으면 절반(50%)입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0008: 요금제 바꾸면 그 달 요금은 어떻게 계산돼요?

변경 신청일 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다.

- TERMINATE-0018: 폰 잃어버려서 해지하면 위약금 있어요

약정 24개월 안에 해지하시면 있습니다. 12개월 전이면 할인 받은 거 전부(100%), 넘었으면 절반(50%)입니다.

## PIPE-COMPOUND-010 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요.

**저장된 답변**

청소년 요금제와 시니어 요금제는 각각 만 18세 이하와 만 65세 이상만 가입 가능합니다. 가입 시에는 생년월일 확인이 가능한 신분증이 필요합니다. 기기 변경 시 유심 재발급을 위한 구비 서류는 다음과 같습니다: - 신분증 사본 - 기존 통신사 이용 약관 해지 증명서 (기존 통신사에서 발급)

- UNSUPPORTED: 기기 변경 시 유심 재발급을 위한 구비 서류는 다음과 같습니다: - 신분증 사본 - 기존 통신사 이용 약관 해지 증명서 (기존 통신사에서 발급)
  - 판단 이유: The claim is not mentioned in the provided FAQ source, so it cannot be confirmed as supported.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses both subquestions: it specifies the age requirements for the youth and senior plans and lists the required documents for a SIM card reissue during a device change.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer partially addresses the second subquestion about the required documents for a SIM card reissue. It mentions the need for a copy of an ID and a proof of termination of the previous carrier's service agreement, but it does not mention the additional documents required if someone else is acting on behalf of the user (e.g., a power of attorney, the agent's ID, and a copy of the user's ID).

**RAG에 전달된 실제 근거**

- PLAN-0004: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요?

청소년 요금제는 만 18세 이하, 시니어 요금제는 만 65세 이상만 가입할 수 있습니다. 가입 시 생년월일 확인이 가능한 신분증이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PLAN-0004: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요?

청소년 요금제는 만 18세 이하, 시니어 요금제는 만 65세 이상만 가입할 수 있습니다. 가입 시 생년월일 확인이 가능한 신분증이 필요합니다.

- USIM-0034: 기기변경 시 유심 재발급 구비 서류를 알려주세요.

본인 신분증만 지참하시면 됩니다. 대리인이 방문하면 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.

## PIPE-COMPOUND-011 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 이미 한 번 교환했는데 다시 교환할 수 있을까요 그리고 대학생인 제가 부모님 명의 폰을 제 명의로 바꿀 수 있을까요?

**저장된 답변**

이미 한 번 교환하신 경우에도 다시 교환이 가능합니다. 대학생이시더라도 성인이시므로 부모님 명의의 폰을 본인 명의로 변경하실 수 있습니다. 부모님과 함께 신분증을 지참하거나 부모님의 위임장을 가져오시면 절차를 진행할 수 있습니다.

- UNSUPPORTED: 이미 한 번 교환하신 경우에도 다시 교환이 가능합니다.
  - 판단 이유: The claim about being able to exchange again after an initial exchange is not mentioned in the provided FAQ evidence.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states that a second exchange is possible, which contradicts the reference stating that only one exchange is allowed. However, it accurately addresses the second part about changing the phone to the user's name as an adult.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer accurately conveys that as an adult, the user can change the phone to their name by providing identification with their parent or a power of attorney from the parent.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0040: 대학생인 제가 부모님 명의 폰을 제 명의로 바꿀 수 있을까요?

성인이시면 가능합니다. 부모님과 함께 신분증을 지참해 매장을 방문하시거나, 부모님 위임장을 받아 오시면 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0064: 이미 한 번 교환했는데 다시 교환할 수 있을까요?

반품·교환은 1회에 한해서만 가능해, 이미 한 번 교환하셨다면 추가 교환은 어렵습니다.

- NAME_CHANGE-0040: 대학생인 제가 부모님 명의 폰을 제 명의로 바꿀 수 있을까요?

성인이시면 가능합니다. 부모님과 함께 신분증을 지참해 매장을 방문하시거나, 부모님 위임장을 받아 오시면 됩니다.

## PIPE-COMPOUND-013 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요?

**저장된 답변**

기기변경 신청은 개통일로부터 6개월이 경과한 후에 가능합니다. 할부금 처리는 기존 할부금을 완납하거나 새 할부 계약으로 승계하는 방식으로 이루어집니다. 자녀 명의 변경과 관련해서는 현재 제공된 정보로는 구체적인 준비물을 안내드리기 어렵습니다.

- UNSUPPORTED: 자녀 명의 변경과 관련해서는 현재 제공된 정보로는 구체적인 준비물을 안내드리기 어렵습니다.
  - 판단 이유: The provided FAQ source does not mention anything about changing the name of a child or the specific preparations required for such a change.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the first part of the question regarding the device change application timing and installment payment processing. It also acknowledges the lack of specific information about the required documents for transferring ownership to a child, which aligns with the reference.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not provide the specific documents required for transferring ownership to a child, which was explicitly requested in the second part of the question. The reference provides this information, but it is not included in the answer.

**RAG에 전달된 실제 근거**

- DEVICE-0010: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요.

개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- DEVICE-0010: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요.

개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.

- NAME_CHANGE-0028: 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요?

양도인인 부모님의 신분증이 필요하고, 자녀와 함께 매장을 방문하셔야 합니다. 동행이 어려우면 위임장을 자녀에게 맡기시면 됩니다.

## PIPE-COMPOUND-016 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 자녀 명의로 가입하려는데 나이 제한이 어떻게 되나요 그리고 가족이랑 로밍 데이터 나눠 쓰려면 어떻게 신청해요?

**저장된 답변**

자녀 명의로 가입하실 경우, 만 14세 미만은 가입이 불가능하며, 만 14세 이상 18세 이하의 경우 법정대리인 동의서, 가족관계증명서, 그리고 법정대리인의 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통 가능합니다. 로밍 데이터를 가족과 나눠 쓰는 방법에 대해서는 현재 제공하는 서비스 내에서 직접적인 가족 간 데이터 공유 기능이 명시적으로 안내되어 있지 않습니다.

- UNSUPPORTED: 로밍 데이터를 가족과 나눠 쓰는 방법에 대해서는 현재 제공하는 서비스 내에서 직접적인 가족 간 데이터 공유 기능이 명시적으로 안내되어 있지 않습니다
  - 판단 이유: This claim is not mentioned in any of the provided FAQ sources.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the age restrictions for a child's name registration, including the specific requirements for minors aged 14-18 and the maximum number of lines that can be opened under a minor's name. It also accurately states that there is no explicit guidance on sharing roaming data with family members within the current service.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer correctly states that there is no explicit guidance on sharing roaming data with family members within the current service. However, it does not provide the specific method for applying for roaming data sharing, which was part of the user's question.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0010: 자녀 명의로 가입하려는데 나이 제한이 어떻게 되나요?

만 14세 미만은 가입하실 수 없습니다. 만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통할 수 있습니다.

- SUBSCRIBE-0002: 우리 애 이름으로 폰 만들려는데 나이 제한 있어요?

만 14세 미만은 가입할 수 없습니다. 만 14세 이상 18세 이하라면 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통할 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0010: 자녀 명의로 가입하려는데 나이 제한이 어떻게 되나요?

만 14세 미만은 가입하실 수 없습니다. 만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통할 수 있습니다.

- ROAMING-0024: 가족이랑 로밍 데이터 나눠 쓰려면 어떻게 신청해요?

로밍 데이터 나눠쓰기를 신청하시면 되고, 최대 2회선까지 함께 쓸 수 있습니다.

## PIPE-COMPOUND-020 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 법인 명의 개통 시 담당자 여권만으로 가능한가요 그리고 공항 도착 후에 신청해도 늦지 않나요?

**저장된 답변**

법인 명의 개통 시 담당자의 여권만으로는 개통이 불가능합니다. 본인 확인을 위해 여권 외에도 법인 관련 서류나 추가적인 증빙 자료가 필요할 수 있습니다.

- UNSUPPORTED: 법인 명의 개통 시 담당자의 여권만으로는 개통이 불가능합니다.
  - 판단 이유: The claim states that a passport alone is insufficient for corporate account opening, but the FAQ source does not mention anything about corporate account opening requirements. It only discusses personal account opening with a passport or additional documents for foreigners.

- UNSUPPORTED: 본인 확인을 위해 여권 외에도 법인 관련 서류나 추가적인 증빙 자료가 필요할 수 있습니다.
  - 판단 이유: The FAQ source does not mention anything about corporate-related documents or additional verification materials for corporate account opening. It only discusses personal identification requirements.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that a passport alone is insufficient for corporate account opening and that additional documents may be required for verification, aligning with the reference.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not address the second part of the question regarding applying after arriving at the airport. The reference indicates that it is possible but with a 1-hour delay, which was not mentioned in the assistant's response.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0069: 법인 명의 개통 시 담당자 여권만으로 가능한가요?

본인 명의 신분증이면 여권도 가능합니다. 외국인이면 외국인등록증과 여권을 함께 제시해야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SUBSCRIBE-0069: 법인 명의 개통 시 담당자 여권만으로 가능한가요?

본인 명의 신분증이면 여권도 가능합니다. 외국인이면 외국인등록증과 여권을 함께 제시해야 합니다.

- ROAMING-0042: 공항 도착 후에 신청해도 늦지 않나요?

현지 도착 후에도 신청하실 수 있습니다. 다만 적용까지 1시간이 걸리니 출국 전 신청을 권장드립니다.

## PIPE-COMPOUND-021 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 번호이동 최대 소요 시간을 알려주세요. 그리고 부가서비스 해지하면 언제까지 쓸 수 있어요

**저장된 답변**

번호 이동의 최대 소요 시간은 24시간 이내입니다. 부가서비스 해지와 관련된 정확한 기간은 현재 제공된 정보로는 안내드릴 수 없습니다.

- UNSUPPORTED: 부가서비스 해지와 관련된 정확한 기간은 현재 제공된 정보로는 안내드릴 수 없습니다.
  - 판단 이유: FAQ 소스에서 부가서비스 해지와 관련된 기간에 대한 정보가 전혀 제공되지 않았기 때문에 해당 클레임은 지원되지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the maximum time for number porting (24 hours) and acknowledges that the exact period for using the service after cancellation is not available in the provided information.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer correctly states that the service can be used until the end of the month after cancellation, as per the reference.

**RAG에 전달된 실제 근거**

- PORTING-0009: 번호이동 최대 소요 시간을 알려주세요.

최대 24시간 이내에 처리됩니다. 평균은 2시간 이내입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0009: 번호이동 최대 소요 시간을 알려주세요.

최대 24시간 이내에 처리됩니다. 평균은 2시간 이내입니다.

- SERVICE-0011: 부가서비스 해지하면 언제까지 쓸 수 있어요

해지를 신청하신 달 말일까지 쓰실 수 있습니다.

## PIPE-COMPOUND-025 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / RETRIEVAL_MISS
- 질문: 번호이동 하고 싶은데 지금 조건이 되는지 어떻게 확인해요 그리고 4년 전 요금 낸 기록이 필요한데 안 나와요

**저장된 답변**

번호이동 가능 여부를 확인하려면 개통일로부터 3개월이 경과했는지 먼저 확인해 주세요. 만약 명의변경 경험이 있다면, 그 변경일로부터도 1개월이 경과했는지 함께 확인해 보시는 것이 좋습니다.

**quality 검증 오류**: ValueError: Answered subquestion has no answer citation

**RAG에 전달된 실제 근거**

- PORTING-0027: 번호이동 하고 싶은데 지금 조건이 되는지 어떻게 확인해요?

개통일로부터 3개월이 지났는지 확인하시면 됩니다. 명의변경을 하신 적이 있다면 변경일로부터 1개월이 지났는지도 함께 확인하셔야 합니다.

- PORTING-0082: 번호이동을 신청했는데 진행이 안 된다고 합니다. 원인이 뭘까요?

기존 통신사에 미납 요금이 있으면 번호이동이 진행되지 않습니다. 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청하실 때는 본인 신분증이 필요합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- PORTING-0027: 번호이동 하고 싶은데 지금 조건이 되는지 어떻게 확인해요?

개통일로부터 3개월이 지났는지 확인하시면 됩니다. 명의변경을 하신 적이 있다면 변경일로부터 1개월이 지났는지도 함께 확인하셔야 합니다.

- SERVICE-0045: 4년 전 요금 낸 기록이 필요한데 안 나와요

요금납부확인서는 최근 3년분까지만 발급됩니다. 그 이전 기록은 발급 범위 밖입니다.

## PIPE-COMPOUND-026 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 일시 정지 연간 한도가 어떻게 되나요 그리고 청구서를 이메일이랑 우편 둘 다 받을 수 있어요?

**저장된 답변**

일시 정지 연간 한도는 최대 90일입니다. 1회 신청 시 최대 30일까지 가능합니다. 청구서 수령 방법에 대해서는 현재 제공하는 옵션으로 이메일과 우편 둘 다 받는 것은 지원하지 않습니다. 이메일 또는 우편 중 하나를 선택하여 수령하실 수 있습니다.

- UNSUPPORTED: 청구서 수령 방법에 대해서는 현재 제공하는 옵션으로 이메일과 우편 둘 다 받는 것은 지원하지 않습니다.
  - 판단 이유: The provided FAQ sources do not mention anything about invoice receipt methods or limitations on receiving via both email and mail.

- UNSUPPORTED: 이메일 또는 우편 중 하나를 선택하여 수령하실 수 있습니다.
  - 판단 이유: The provided FAQ sources do not mention anything about invoice receipt methods or the ability to choose between email or mail.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses both subquestions: it correctly states the annual limit for temporary suspension (90 days, with a maximum of 30 days per application) and clarifies that users can only choose one method (email or postal) for receiving bills, not both.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer correctly states that users cannot receive bills via both email and postal mail, aligning with the reference that only one method can be selected.

**RAG에 전달된 실제 근거**

- TERMINATE-0012: 일시 정지 연간 한도가 어떻게 되나요?

연간 최대 90일입니다. 1회에는 최대 30일까지 신청할 수 있습니다.

- TERMINATE-0036: 일시 정지는 어떻게 신청하나요?

고객센터나 매장에서 신청하시면 됩니다. 1회 최대 30일, 연간 누적 최대 90일까지 가능하며 정지 기간 기본료는 50%로 줄어듭니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0012: 일시 정지 연간 한도가 어떻게 되나요?

연간 최대 90일입니다. 1회에는 최대 30일까지 신청할 수 있습니다.

- BILLING-0077: 청구서를 이메일이랑 우편 둘 다 받을 수 있어요?

이메일, 모바일, 우편 중 한 가지를 선택하는 방식입니다. 우편은 월 500원입니다.

## PIPE-COMPOUND-027 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 오래 안 쓸 건데 정지 석 달 되나요 그리고 청구서 안 왔는데 요금 안 내면 어떻게 돼요

**저장된 답변**

석 달 동안 정지하시려면 기본료의 절반(50%)만 청구됩니다. 청구서를 받지 못하셨더라도 요금을 기한 내에 납부하시면 서비스 정지는 이루어지지 않습니다. 만약 청구서를 받지 못하셨다면, 요금 납부 기한을 확인하시고 그 안에 요금을 내주시면 됩니다. 기한이 지나면 15일 후부터는 추가 요금 청구나 서비스 제한이 있을 수 있으니 주의하시기 바랍니다.

- UNSUPPORTED: 청구서를 받지 못하셨더라도 요금을 기한 내에 납부하시면 서비스 정지는 이루어지지 않습니다.
  - 판단 이유: The claim states that service suspension will not occur if the bill is paid on time even if the bill was not received. However, the FAQ source 'BILLING-0059' states that if the bill is not received, the service will be suspended after 15 days if the payment is not made on time, which contradicts the claim.

- UNSUPPORTED: 만약 청구서를 받지 못하셨다면, 요금 납부 기한을 확인하시고 그 안에 요금을 내주시면 됩니다.
  - 판단 이유: The claim suggests that if the bill is not received, the user should check the payment deadline and pay within that time. However, the FAQ source 'BILLING-0059' states that if the bill is not received, the service will be suspended after 15 days if the payment is not made on time, which contradicts the claim.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the user's question about the 3-month suspension and the consequences of not paying the bill. However, it does not explicitly mention the 30-day limit per suspension or the 90-day annual limit from the reference. Additionally, the reference mentions specific consequences (service restriction after 15 days and possible termination after 60 days), which are not fully detailed in the answer.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer mentions the consequences of not paying the bill, such as additional charges and service restrictions after 15 days. However, it does not explicitly mention the 60-day period after which the service may be terminated, as stated in the reference.

**RAG에 전달된 실제 근거**

- BILLING-0031: 청구서 안 왔는데 요금 안 내면 어떻게 돼요

기한 지나고 15일이면 전화가 안 걸리고, 60일이면 해지될 수 있습니다.

- TERMINATE-0024: 오래 안 쓸 건데 정지하면 요금 얼마 나와요

기본료의 절반(50%)만 나옵니다. 한 번에 30일, 일 년에 90일까지 됩니다.

- BILLING-0059: 청구서 못 받았는데 정지 안 되게 하려면 어떻게 해요

청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0072: 오래 안 쓸 건데 정지 석 달 되나요

한 번에 30일까지만 되고, 일 년에 다 합쳐 90일까지 됩니다.

- BILLING-0031: 청구서 안 왔는데 요금 안 내면 어떻게 돼요

기한 지나고 15일이면 전화가 안 걸리고, 60일이면 해지될 수 있습니다.

## PIPE-COMPOUND-029 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / INCOMPLETE_ANSWER, RETRIEVAL_MISS
- 질문: 전화나 홈페이지로도 해지 신청이 가능한가요 그리고 요금 내는 방법이 네 가지라는데 뭐뭐예요?

**저장된 답변**

네, 전화나 홈페이지를 통해 해지 신청이 가능합니다. 요금 납부 방법은 주로 다음 네 가지로 이루어집니다: 1. **일시불 결제**: 한 번에 요금을 납부합니다. 2. 3. **자동이체**: 지정된 계좌에서 자동으로 요금이 이체됩니다. 4. 해지 신청 시 요금은 사용한 일수만큼 청구됩니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states that termination can be done via phone or website, but the four billing methods are not fully listed. The reference specifies '계좌이체, 신용카드, 가상계좌, 간편결제' (account transfer, credit card, virtual account, easy payment), but the answer only mentions '일시불 결제' (one-time payment) and '자동이체' (automatic transfer), missing the other two methods.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer partially addresses the billing methods by mentioning '일시불 결제' and '자동이체' but does not fully list the four methods specified in the reference ('계좌이체, 신용카드, 가상계좌, 간편결제').

**RAG에 전달된 실제 근거**

- TERMINATE-0045: 요금 부담돼서 해지하려면 어떻게 해요

매장이나 고객센터, 홈페이지에서 신청하시면 바로 됩니다. 그 달 요금은 쓴 날짜만큼 나옵니다.

- TERMINATE-0001: 번호 해지는 어떻게 해요?

매장 방문, 고객센터 전화, 홈페이지 신청 중 편하신 방법으로 하시면 됩니다. 신청 즉시 해지되고 요금은 일할 계산됩니다.

- TERMINATE-0081: 급하게 해외로 나가게 됐는데 매장 갈 시간이 없어요

고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- TERMINATE-0057: 전화나 홈페이지로도 해지 신청이 가능한가요?

네, 매장 방문 없이 고객센터 전화나 홈페이지로도 신청하실 수 있습니다.

- BILLING-0075: 요금 내는 방법이 네 가지라는데 뭐뭐예요?

계좌이체, 신용카드, 가상계좌, 간편결제입니다.

## PIPE-COMPOUND-031 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요.

**저장된 답변**

해외 체류 중 유심을 분실하셨군요. 먼저 홈페이지에서 분실 신고를 진행하시면 즉시 이용이 정지됩니다. 귀국 후에는 새 유심을 발급받으시면 기존 유심은 자동으로 해지됩니다. 가족에게 데이터를 나눠주는 절차에 대해서는 현재 제공된 정보로는 안내드릴 수 있는 내용이 없습니다.

- UNSUPPORTED: 해외 체류 중 유심을 분실하셨군요.
  - 판단 이유: The claim about the user losing a SIM card while abroad is not mentioned or supported in the provided FAQ evidence.

- UNSUPPORTED: 가족에게 데이터를 나눠주는 절차에 대해서는 현재 제공된 정보로는 안내드릴 수 있는 내용이 없습니다.
  - 판단 이유: The claim about sharing data with family is not mentioned or supported in the provided FAQ evidence.

- 하위 질문 1: COMPLETE
  - 판단 이유: The assistant accurately conveyed the required facts from the reference for the first subquestion about the procedure for a lost SIM card while abroad. The reference explicitly states that reporting the loss on the homepage stops the service immediately and that a new SIM card will automatically cancel the old one upon reissuance after returning home. For the second subquestion about sharing data with family, the assistant correctly stated that there is no information available to provide guidance, which aligns with the reference not providing any information on this topic.

- 하위 질문 2: COMPLETE
  - 판단 이유: The assistant accurately conveyed the required facts from the reference for the second subquestion about sharing data with family. The reference explicitly states that the data sharing procedure can be done via the app or website's data gift menu, with specific limits. However, the assistant correctly noted that there is no information available to provide guidance on this topic, which aligns with the reference not providing any information on this topic.

**RAG에 전달된 실제 근거**

- USIM-0040: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요.

홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0040: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요.

홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.

- PLAN-0043: 가족에게 데이터를 나눠주는 절차를 알려주세요.

앱 또는 홈페이지의 데이터 선물 메뉴에서 신청합니다. 월 2회, 회당 최대 2GB까지 가능합니다.

## PIPE-COMPOUND-033 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 외국 가기 전에 유심 새로 받으려면 어떻게 해요 그리고 부모님 시니어 요금제 가입 시 필요한 서류를 알려주세요.

**저장된 답변**

유심을 새로 받으시기 위해서는 부모님께서 직접 방문하시거나, 대리인 방문을 통해 처리하실 수 있습니다. 대리인 방문 시에는 위임장, 대리인의 신분증 사본, 그리고 가입자(본인)의 신분증 사본을 준비하셔야 합니다. 시니어 요금제 가입 시에는 일반적으로 가입자의 신분증 사본과 함께 시니어 증빙 서류가 필요할 수 있습니다.

- REVIEW: 부모님께서 직접 방문하시거나, 대리인 방문을 통해 처리하실 수 있습니다.
  - 판단 이유: The claim mentions both direct visits by parents and proxy visits, but the FAQ only mentions proxy visits and the required documents. There is no explicit confirmation about parents being able to visit directly.

- UNSUPPORTED: 시니어 요금제 가입 시에는 일반적으로 가입자의 신분증 사본과 함께 시니어 증빙 서류가 필요할 수 있습니다.
  - 판단 이유: The claim mentions senior plan enrollment requiring a senior verification document, but the FAQ does not mention anything about senior plans or related documents.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides information about how to obtain a new SIM card (visiting in person or with an agent, required documents for an agent), but it does not mention the cost (7,700 won) or the exact process of obtaining it at a store, which is covered in the reference. Additionally, the answer mentions the need for a senior verification document for a senior plan, but the reference only mentions the age requirement and a valid ID with birthdate.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer mentions the need for a senior verification document and a valid ID for a senior plan, but it does not specify the age requirement (65 years or older) or the exact requirement of a valid ID with a birthdate, which is covered in the reference.

**RAG에 전달된 실제 근거**

- USIM-0030: 해외에서 부모님이 대신 유심을 받아 보내려면 뭘 준비해야 해요?

대리인 방문이라 위임장, 대리인 신분증, 가입자 신분증 사본을 준비하셔야 합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0045: 외국 가기 전에 유심 새로 받으려면 어떻게 해요

매장에 가시면 바로 받으실 수 있습니다. 7,700원입니다.

- PLAN-0019: 부모님 시니어 요금제 가입 시 필요한 서류를 알려주세요.

만 65세 이상이어야 하며, 생년월일 확인이 가능한 신분증이 필요합니다.

## PIPE-COMPOUND-035 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: UNSCORED / INCOMPLETE_ANSWER, RETRIEVAL_MISS
- 질문: 새 단말로 바꾸면서 eSIM으로 전환하는 절차를 알려주세요. 그리고 남은 데이터 다음 달에 쓸 수 있어요

**저장된 답변**

새 단말을 eSIM으로 전환하려면 먼저 새 단말이 eSIM을 지원하는지 확인해 주세요. 그 후 매장이나 온라인을 통해 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원이며, 이 과정을 통해 기존 데이터는 다음 달에 계속 사용 가능합니다.

**grounding 검증 오류**: ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the procedure for converting to eSIM, including checking device compatibility, applying for eSIM issuance through a store or online, the cost of 2,750 won, and the fact that remaining data can be used next month. All required facts from the reference group are accurately conveyed.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer mentions that remaining data can be used next month, which partially addresses the subquestion. However, it does not specify that only 50% of the remaining data can be carried over, as indicated in the reference. This detail is missing, making the answer partial.

**RAG에 전달된 실제 근거**

- USIM-0039: 새 단말로 바꾸면서 eSIM으로 전환하는 절차를 알려주세요.

새 단말이 eSIM을 지원하는지 확인한 뒤 매장이나 온라인에서 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원입니다.

- USIM-0099: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?

eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0039: 새 단말로 바꾸면서 eSIM으로 전환하는 절차를 알려주세요.

새 단말이 eSIM을 지원하는지 확인한 뒤 매장이나 온라인에서 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원입니다.

- PLAN-0028: 남은 데이터 다음 달에 쓸 수 있어요

네, 남은 것의 절반(50%)까지 넘어갑니다.

## PIPE-COMPOUND-036 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 법인 회선을 개인 명의로 전환할 때 구비 서류를 알려주세요. 그리고 사용하다가 마음에 안 들면 교환할 수 있을까요?

**저장된 답변**

법인 회선을 개인 명의로 전환할 때 필요한 구비 서류는 양도인과 양수인의 신분증입니다. 원칙적으로는 두 분이 함께 방문하시는 것이 좋으나, 한 분이 방문이 어려우시다면 위임장을 준비하셔야 합니다. 교환 가능 여부에 대해서는 현재 제공 중인 서비스 정책에 따라 다를 수 있습니다.

- UNSUPPORTED: 교환 가능 여부에 대해서는 현재 제공 중인 서비스 정책에 따라 다를 수 있습니다.
  - 판단 이유: The claim is not mentioned or supported by the provided FAQ source. The FAQ source does not discuss the exchange possibility or service policies.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the required facts about the necessary documents for transferring a corporate line to an individual's name, including the need for both parties' ID and the option of a power of attorney if one cannot attend. It also mentions the exchange policy, which is covered in the reference.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer mentions that exchange is possible depending on the service policy, which is partially addressed. However, it does not provide specific details about the exchange conditions, such as the 14-day period and the one-time exchange limit mentioned in the reference.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0010: 법인 회선을 개인 명의로 전환할 때 구비 서류를 알려주세요.

양도인과 양수인의 신분증이 각각 필요하며, 원칙적으로 양측이 함께 방문해야 합니다. 한쪽이 방문할 수 없으면 위임장을 준비합니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0010: 법인 회선을 개인 명의로 전환할 때 구비 서류를 알려주세요.

양도인과 양수인의 신분증이 각각 필요하며, 원칙적으로 양측이 함께 방문해야 합니다. 한쪽이 방문할 수 없으면 위임장을 준비합니다.

- DEVICE-0056: 사용하다가 마음에 안 들면 교환할 수 있을까요?

개통 후 14일 이내이고 미개봉이거나 사용 흔적이 없는 상태라면 가능합니다. 다만 1회에 한해서만 교환하실 수 있습니다.

## PIPE-COMPOUND-038 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 자식한테 폰 넘기는데 할부가 남았으면 어떻게 해요 그리고 분실 신고 후 다시 찾으면 정지를 풀 수 있나요?

**저장된 답변**

분실 신고 후 폰을 다시 찾으신 경우, 일반적으로 정지 해제 절차를 다시 진행하셔야 합니다.

- UNSUPPORTED: 분실 신고 후 폰을 다시 찾으신 경우, 일반적으로 정지 해제 절차를 다시 진행하셔야 합니다.
  - 판단 이유: The claim is about retrieving a lost phone and restarting the suspension解除 procedure, but the provided FAQ source (NAME_CHANGE-0033) discusses a child's credit check process for a name change, which is unrelated to phone recovery or suspension procedures. There is no direct evidence in the provided FAQ sources to support this claim.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys the required fact that after recovering a lost phone, the user generally needs to go through the process to lift the suspension again.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not address the specific question about the process for transferring a phone with remaining installments to a child. The reference group provides a relevant answer, but it is not included in the assistant's response.

**RAG에 전달된 실제 근거**

- NAME_CHANGE-0033: 자식한테 폰 넘기는데 할부가 남았으면 어떻게 해요

받는 자녀분이 신용 심사를 받으시면 됩니다. 통과하면 넘어갑니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0033: 자식한테 폰 넘기는데 할부가 남았으면 어떻게 해요

받는 자녀분이 신용 심사를 받으시면 됩니다. 통과하면 넘어갑니다.

- DEVICE-0071: 분실 신고 후 다시 찾으면 정지를 풀 수 있나요?

분실 신고로 즉시 정지된 회선은 고객센터에서 정지 해제를 요청하실 수 있습니다.

## PIPE-COMPOUND-040 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS
- 질문: 회사 담당자가 대리로 와서 양도 처리를 해도 되나요 그리고 배터리 교체 비용도 할부에 포함할 수 있나요?

**저장된 답변**

안내드릴 수 있는 정보가 없습니다.

- 생성 기록: NO_EVIDENCE / None

- 하위 질문 1: PARTIAL
  - 판단 이유: The first subquestion about whether a company representative can act as a proxy for the transfer is partially answered in the reference (it is possible with a power of attorney). However, the assistant's answer does not address this and instead states that there is no information to provide, which is incomplete. The second subquestion about including battery replacement costs in installments is not addressed in the reference, and the assistant's answer also does not address it.

- 하위 질문 2: MISSED
  - 판단 이유: The second subquestion about including battery replacement costs in installments is not addressed in the reference, and the assistant's answer does not provide any information on this matter.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- NAME_CHANGE-0046: 회사 담당자가 대리로 와서 양도 처리를 해도 되나요?

네, 양도인 측 위임장을 지참하면 대리 방문으로 진행할 수 있습니다. 양수인 신분증은 그대로 필요합니다.

- DEVICE-0069: 배터리 교체 비용도 할부에 포함할 수 있나요?

단말 할부는 단말 구매 금액을 24개월 또는 30개월로 나누는 것이며 수수료는 연 5.9%입니다.

## PIPE-COMPOUND-041 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 해외에서 번호 두 개 쓸 수 있어요 그리고 외국인인데 개통하면 얼마 만에 쓸 수 있어요?

**저장된 답변**

외국인 고객님께서 두 개의 번호를 사용하는 것은 일반적으로 제한될 수 있으며, 정확한 조건은 현재 제공된 정보로는 확인이 어렵습니다. 하지만 개통 시간에 대해 말씀드리자면, 매장에서 가입하시면 개통 후 즉시 사용 가능하며, 온라인으로 가입하실 경우 다음 날 개통되어 사용 가능합니다. 처리 시간은 오전 9시부터 오후 8시까지입니다.

- UNSUPPORTED: 외국인 고객님께서 두 개의 번호를 사용하는 것은 일반적으로 제한될 수 있으며
  - 판단 이유: The claim about restrictions on foreigners using two numbers is not mentioned in the provided FAQ evidence.

- UNSUPPORTED: 매장에서 가입하시면 개통 후 즉시 사용 가능하며
  - 판단 이유: The FAQ states that in-store sign-up results in activation within 30 minutes, but it does not explicitly state that it is 'immediately usable after activation.'

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer partially addresses the question by mentioning the general limitation on using two numbers for foreigners and provides the activation time for both in-store and online registration. However, it does not explicitly mention the cost of the dual-number service, which is a key fact from the reference.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer accurately conveys the activation time for both in-store and online registration, including the processing hours, which matches the reference.

**RAG에 전달된 실제 근거**

- SUBSCRIBE-0003: 외국인인데 개통하면 얼마 만에 쓸 수 있어요?

매장에서 가입하시면 30분 이내에 개통됩니다. 온라인은 다음 날 개통되고, 처리 시간은 09:00부터 20:00까지입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0004: 해외에서 번호 두 개 쓸 수 있어요?

듀얼번호 서비스를 월 3,300원에 이용하실 수 있습니다.

- SUBSCRIBE-0003: 외국인인데 개통하면 얼마 만에 쓸 수 있어요?

매장에서 가입하시면 30분 이내에 개통됩니다. 온라인은 다음 날 개통되고, 처리 시간은 09:00부터 20:00까지입니다.

## PIPE-COMPOUND-044 / 질문 1

- 실행: FAILED
- 실행 오류 코드: LLM502-2
- 판정: UNSCORED / PROCESSING_FAILURE, RETRIEVAL_MISS
- 질문: 로밍 요금 자동 차단 해제 절차를 알려주세요. 그리고 부모님 동의 없이 고등학생이 개통할 수 있을까요?

**저장된 답변**

답변 없음

- 생성 기록: MODEL_ERROR / None

**RAG에 전달된 실제 근거**

- ROAMING-0028: 로밍 요금 자동 차단 해제 절차를 알려주세요.

본인 인증을 완료하시면 해제됩니다. 차단은 로밍 요금이 20만 원에 도달했을 때 적용된 것입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- ROAMING-0028: 로밍 요금 자동 차단 해제 절차를 알려주세요.

본인 인증을 완료하시면 해제됩니다. 차단은 로밍 요금이 20만 원에 도달했을 때 적용된 것입니다.

- SUBSCRIBE-0062: 부모님 동의 없이 고등학생이 개통할 수 있을까요?

만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 있어야 가능합니다.

## PIPE-COMPOUND-047 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS, ROUTING_MISMATCH
- 질문: 매장에 갈 건데 시간을 어떻게 맞추면 돼요 그리고 결합 해지하고 번호이동하려면 순서가 어떻게 돼요?

**저장된 답변**

한 번에 여러 내용을 요청하셨어요. 질문을 하나씩 나누어 보내주세요.

- 하위 질문 1: PARTIAL
  - 판단 이유: The assistant provided a response that acknowledges the user's request but does not fully address the specific subquestions about store hours and the process for porting. The answer quotes indicate a refusal to handle multiple questions at once, but the reference groups contain the necessary information to answer both subquestions.

- 하위 질문 2: PARTIAL
  - 판단 이유: The assistant's response does not directly address the specific subquestion about the process for porting and cancellation. The reference group provides the necessary information, but the assistant's answer does not convey it.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0018: 매장에 갈 건데 시간을 어떻게 맞추면 돼요?

평일은 10:00부터 19:00, 토요일은 10:00부터 17:00 사이에 방문하시면 됩니다. 일요일과 공휴일은 휴무이니 피해 주세요.

- PORTING-0030: 결합 해지하고 번호이동하려면 순서가 어떻게 돼요?

기존 통신사 미납 요금이 없는지 먼저 확인하고, 본인 신분증을 지참해 번호이동을 신청하시면 됩니다.

## PIPE-COMPOUND-048 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요 그리고 다른 회사로 번호 가져가는 거 어떻게 하는 거예요

**저장된 답변**

신고 접수 후 3영업일 이내에 조사가 진행되며, 도용이 확인되면 해당 요금은 전액 취소됩니다. 번호를 다른 회사로 이전하는 절차에 대해서는 현재 제공된 정보로는 자세한 안내를 드리기 어렵습니다.

- UNSUPPORTED: 번호를 다른 회사로 이전하는 절차에 대해서는 현재 제공된 정보로는 자세한 안내를 드리기 어렵습니다.
  - 판단 이유: The claim is not mentioned in any of the provided FAQ sources. There is no evidence to support this statement.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer fully addresses the first subquestion about the investigation process and refund policy after reporting. It also acknowledges the inability to provide detailed guidance on porting the number to another company, which aligns with the reference.

- 하위 질문 2: PARTIAL
  - 판단 이유: The answer does not directly address the second subquestion about porting the number to another company. It only mentions that detailed guidance cannot be provided, but does not include the specific procedure mentioned in the reference (e.g., bringing ID and having no outstanding fees).

**RAG에 전달된 실제 근거**

- SERVICE-0004: 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요?

신고 접수 후 3영업일 이내에 조사가 진행됩니다. 도용이 확인되면 해당 요금은 전액 취소됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0004: 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요?

신고 접수 후 3영업일 이내에 조사가 진행됩니다. 도용이 확인되면 해당 요금은 전액 취소됩니다.

- PORTING-0045: 다른 회사로 번호 가져가는 거 어떻게 하는 거예요

신분증 가지고 가서 옮긴다고 하시면 됩니다. 밀린 요금만 없으면 됩니다.

## PIPE-COMPOUND-049 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 증명서 인터넷으로 바로 뗄 수 있어요 그리고 개통하고 보름 지났는데 철회할 수 있을까요?

**저장된 답변**

네, 증명서는 홈페이지에서 즉시 무료로 발급 가능합니다. 정확한 철회 정책은 해당 페이지의 안내를 참고해 주세요.

- UNSUPPORTED: 정확한 철회 정책은 해당 페이지의 안내를 참고해 주세요.
  - 판단 이유: FAQ 답변에서 철회 정책에 대한 언급이 전혀 없으므로 해당 클레임은 지원되지 않습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately addresses both subquestions: it confirms that the certificate can be issued immediately and for free via the homepage, and it directs the user to check the policy details on the page for the withdrawal policy. The reference groups also confirm these points.

- 하위 질문 2: COMPLETE
  - 판단 이유: The answer correctly states that the certificate can be issued immediately and for free via the homepage, which aligns with the reference group. It also mentions that the withdrawal policy should be checked on the page, which is consistent with the reference group's answer about the 14-day withdrawal period.

**RAG에 전달된 실제 근거**

- SERVICE-0030: 증명서 인터넷으로 바로 뗄 수 있어요?

네, 홈페이지에서 신청하시면 즉시 무료로 발급됩니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0030: 증명서 인터넷으로 바로 뗄 수 있어요?

네, 홈페이지에서 신청하시면 즉시 무료로 발급됩니다.

- PORTING-0056: 개통하고 보름 지났는데 철회할 수 있을까요?

철회는 개통 후 14일 이내에만 가능합니다. 그 기한을 넘겼다면 어렵습니다.

## PIPE-COMPOUND-050 / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS
- 질문: 내 멤버십 등급이 어떻게 정해지는지 순서대로 설명해 주세요. 그리고 번호이동 신청부터 완료까지 흐름을 알려주세요.

**저장된 답변**

멤버십 등급은 주로 직전 1년간 납부하신 금액을 기준으로 정해집니다. 순서대로 설명드리면 다음과 같습니다: 1. **납부 금액 집계**: 직전 1년간 사용하신 서비스에 대한 금액을 합산합니다. 2. **등급 산정**: 합산된 금액에 따라 다음과 같은 등급으로 결정됩니다: VVIP, VIP, 골드, 실버. 3. **갱신 처리**: 매년 1월에 등급이 갱신됩니다. 번호이동 신청부터 완료까지의 일반적인 흐름은 다음과 같습니다: 1. 2. **인증 및 확인**: 신청 후 필요한 인증 절차를 거치고, 신청 내용을 확인합니다. 3. **심사**: 통신사에서 신청 내용을 심사합니다. 4. **승인 및 안내**: 심사 후 승인되면 안내 메시지를 통해 완료 안내를 받습니다. 5. **서비스 전환**: 승인 후 지정된 날짜에 새로운 번호로 서비스가 전환됩니다.

- UNSUPPORTED: 1. **납부 금액 집계**: 직전 1년간 사용하신 서비스에 대한 금액을 합산합니다.
  - 판단 이유: None of the provided sources mention the specific process of summing the amount for services used in the previous year. The sources only mention the basis for determining the grade, not the detailed process of summing the amount.

- UNSUPPORTED: 2. **인증 및 확인**: 신청 후 필요한 인증 절차를 거치고, 신청 내용을 확인합니다.
  - 판단 이유: None of the provided sources mention the authentication and confirmation process after applying for a membership grade.

- UNSUPPORTED: 3. **심사**: 통신사에서 신청 내용을 심사합니다.
  - 판단 이유: None of the provided sources mention the review process by the telecommunications company.

- UNSUPPORTED: 4. **승인 및 안내**: 심사 후 승인되면 안내 메시지를 통해 완료 안내를 받습니다.
  - 판단 이유: None of the provided sources mention the approval and notification process.

- UNSUPPORTED: 5. **서비스 전환**: 승인 후 지정된 날짜에 새로운 번호로 서비스가 전환됩니다.
  - 판단 이유: None of the provided sources mention the service transition process.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer provides a complete explanation of how the membership grade is determined, including the steps of payment amount aggregation, grade calculation, and renewal process. However, the flow of the number porting process is partially answered, as it skips the first step and only partially describes the process.

- 하위 질문 2: MISSED
  - 판단 이유: The answer does not provide the requested information about the flow of the number porting process. It skips the first step and only partially describes the process.

**RAG에 전달된 실제 근거**

- SERVICE-0017: 내 멤버십 등급이 어떻게 정해지는지 순서대로 설명해 주세요.

먼저 직전 1년간 납부하신 금액을 집계하고, 그 금액에 따라 VVIP·VIP·골드·실버 중 하나로 정해집니다. 이 산정은 매년 1월에 한 번 이루어집니다.

- SERVICE-0022: 멤버십 등급 산정과 갱신 절차를 알려주세요.

직전 1년간 납부하신 금액을 기준으로 등급이 산정되고, 매년 1월에 갱신 처리됩니다. 등급은 VVIP·VIP·골드·실버 4단계입니다.

- SERVICE-0027: 멤버십 등급 확인은 어떻게 해요

앱이나 홈페이지에서 확인하실 수 있습니다. 등급은 직전 1년 납부액으로 매년 1월에 정해집니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- SERVICE-0017: 내 멤버십 등급이 어떻게 정해지는지 순서대로 설명해 주세요.

먼저 직전 1년간 납부하신 금액을 집계하고, 그 금액에 따라 VVIP·VIP·골드·실버 중 하나로 정해집니다. 이 산정은 매년 1월에 한 번 이루어집니다.

- PORTING-0037: 번호이동 신청부터 완료까지 흐름을 알려주세요.

09:00~20:00 사이에 신청하면 평균 2시간 이내에 완료됩니다. 최대 24시간까지 걸릴 수 있습니다.

## REG-FAQ_FACT / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, SHOULD_ABSTAIN
- 질문: 가상계좌가 무엇인가요?

**저장된 답변**

가상계좌는 요금을 납부할 때 사용하는 입금 전용 계좌입니다. 주로 계좌이체를 통해 요금을 쉽게 납부할 수 있도록 설계되었습니다. 신용카드나 간편결제 방법 외에도 이 가상계좌를 통해 편리하게 요금을 낼 수 있습니다.

- UNSUPPORTED: 주로 계좌이체를 통해 요금을 쉽게 납부할 수 있도록 설계되었습니다.
  - 판단 이유: FAQ 답변은 '계좌이체, 신용카드, 간편결제로도 낼 수 있습니다'라고 명시하지만, '주로 계좌이체를 통해 설계되었다'는 구체적인 정보는 제공되지 않았습니다.

- 하위 질문 1: COMPLETE
  - 판단 이유: The answer accurately conveys that a virtual account is a deposit-only account used for paying bills, and it mentions that it can be used via account transfer, credit card, or convenient payment methods. This aligns with the required fact that it is a deposit-only account provided to users for bill payments.

**RAG에 전달된 실제 근거**

- BILLING-0026: 가상계좌가 뭐예요

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- BILLING-0026: 가상계좌가 뭐예요

요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.

## REG-MIXED / 질문 1

- 실행: COMPLETED
- 실행 오류 코드: 없음
- 판정: SCORED / UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN
- 질문: 유심 재발급 비용은 얼마고 배송비도 내야 하나요?

**저장된 답변**

유심 재발급 비용은 7,700원입니다. 온라인으로 신청하신 경우에는 택배로 배송되며, 배송비는 별도로 발생하지 않습니다. 매장 방문 시에는 즉시 받으실 수 있습니다.

- UNSUPPORTED: 온라인으로 신청하신 경우에는 택배로 배송되며, 배송비는 별도로 발생하지 않습니다.
  - 판단 이유: The FAQ sources mention that online applications take 2-3 business days via courier, but they do not explicitly state that there is no additional shipping fee. The absence of confirmation about shipping fees makes this claim unsupported.

- 하위 질문 1: PARTIAL
  - 판단 이유: The answer correctly states the cost of 7,700 won for a SIM card reissue. However, the reference group does not provide information about whether shipping fees are charged, and the answer assumes that shipping fees are not charged, which is not explicitly stated in the reference group.

**RAG에 전달된 실제 근거**

- USIM-0001: 유심 재발급 비용이 얼마예요?

7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.

- USIM-0073: 유심이 안 읽혀서 새로 받아야 할 것 같은데 비용이 얼마나 드나요?

인식 오류가 계속되면 재발급을 받으시는 게 좋습니다. 매장에서는 즉시 발급되고, 비용은 7,700원입니다.

- USIM-0077: 유심이 부러졌는데 오늘 급하게 써야 해요

매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.

**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**

- USIM-0001: 유심 재발급 비용이 얼마예요?

7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.

