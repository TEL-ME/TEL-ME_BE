# V7 세 모델 교차 검증 사람 검토 목록

Qwen3 235B, GPT-OSS-120B, Sonnet 4.6의 판정 불일치, 채점 누락, 기존 사람 판정 자료와의 불일치가 포함된 답변입니다. 모델 다수결을 정답으로 간주하지 말고, 질문·답변·FAQ 근거를 보고 판정해 주세요.

## PIPE-BILLING-0020 (turn 0)

- 질문: 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요?
- 기대 동작: ANSWER
- 실제 답변: 네, 이중 납부 금액을 다음 달 요금에서 차감하는 방식으로 선택하실 수 있습니다. 차감 후 환불은 본인 명의 계좌로 5영업일 이내에 이루어집니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0020`: 네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다. 환불은 본인 명의 계좌로 5영업일 이내입니다.
  - `BILLING-0076`: 네, 다음 달 요금에서 차감하는 방식도 선택할 수 있습니다.
  - `BILLING-0055`: 고객센터 접수 시 다음 달 차감 방식을 선택하면 됩니다. 접수 후 3영업일 이내에 확인되고, 환불을 택하면 5영업일 이내에 본인 명의 계좌로 입금됩니다.

## PIPE-BILLING-0021 (turn 0)

- 질문: 청구서 수령 방법 종류를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 청구서 수령 방법은 이메일, 모바일 알림, 우편 총 3가지입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0021`: 이메일, 모바일 알림, 우편 3종입니다. 우편만 월 500원이 부과됩니다.
  - `BILLING-0105`: 수령 방법은 이메일, 모바일, 우편 중 하나입니다. 중복 발송이면 고객센터에 확인해 주세요.
  - `BILLING-0028`: 이메일, 모바일 알림, 우편 중에서 받으실 수 있습니다. 우편은 월 500원이 붙고 나머지는 무료입니다.

## PIPE-BILLING-0052 (turn 0)

- 질문: 발신 정지 이후 재개까지의 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 발신 정지를 해제하려면 미납 요금이 완전히 납부되어야 합니다. 요금을 완납하시면 일반적으로 2시간 이내에 발신 기능이 재개됩니다. 요금을 납부하지 않으실 경우, 발신 정지 상태가 지속되며 30일 후에는 수신이 정지되고, 그 이후 60일이 경과하면 직권 해지될 수 있습니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0052`: 미납 요금을 완납하면 2시간 이내에 재개됩니다. 완납하지 않으면 30일에 수신 정지, 60일에 직권 해지될 수 있습니다.

## PIPE-BILLING-0054 (turn 0)

- 질문: 미납 요금을 가상계좌로 납부하는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 그 후 해당 가상계좌로 지정된 금액을 입금하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0054`: 납부 수단 4종(계좌이체·신용카드·가상계좌·간편결제) 중 가상계좌를 선택해 입금하시면 됩니다.

## PIPE-BILLING-0065 (turn 0)

- 질문: 자동이체 두 번 나갔는데 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 자동이체는 일반적으로 매월 25일에 한 번만 진행됩니다. 두 번 나간 경우는 오류일 가능성이 높습니다. 고객센터에 연락하여 상황을 접수하시면 정확한 문제 해결을 도와드리겠습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0065`: 고객센터에 접수하시면 됩니다. 자동이체는 원래 25일에 한 번만 나갑니다.

## PIPE-BILLING-0069 (turn 0)

- 질문: 이중 납부 환불을 배우자 계좌로 받을 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0069`: 환불은 가입자 본인 명의 계좌로만 가능합니다.
  - `BILLING-0006`: 가입자 본인 명의 계좌로 환불됩니다. 접수 후 3영업일 이내 확인, 5영업일 이내 환불입니다.
  - `BILLING-0048`: 환불은 가입자 본인 명의 계좌로만 가능하며, 접수 시 계좌를 알려주시면 3영업일 이내 확인 후 5영업일 이내에 입금됩니다.

## PIPE-BILLING-0072 (turn 0)

- 질문: 25일 전에 미리 낼 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-BILLING-0074 (turn 0)

- 질문: 미납이 있어도 소액결제 한도를 올릴 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 네, 미납이 있더라도 소액결제 한도를 올릴 수 있습니다. 한도는 본인 인증을 통해 최대 월 100만 원까지 상향 조정 가능합니다. 하지만 미납 상태에 따른 추가 제한 사항은 고객센터를 통해 확인해 보시는 것이 좋습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `BILLING-0074`: 한도 상향은 본인 인증으로 진행되며 최대 월 100만 원입니다. 미납 여부와 관련된 제한은 고객센터에서 확인해 주세요.
  - `BILLING-0046`: 본인 인증을 거쳐 신청하면 즉시 적용됩니다. 기본 월 30만 원, 최대 월 100만 원입니다.
  - `BILLING-0116`: 상향은 즉시 적용됩니다. 최대 한도인 월 100만 원을 초과했는지 확인해 주세요.

## PIPE-BILLING-0087 (turn 0)

- 질문: 청구서를 못 받았다는 이유로 정지가 유예되나요?
- 기대 동작: ANSWER
- 실제 답변: 청구서를 못 받으셨더라도 정지는 납부 기한이 지나고 15일 후에 발신 정지, 30일 후에 수신 정지가 적용됩니다. 따라서 청구서 수령 여부와 직접적인 유예 관계는 없습니다. 기한 내에 요금을 납부하시면 정지를 방지할 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-BILLING-0087","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `BILLING-0087`: 정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.
  - `BILLING-0059`: 청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.
  - `BILLING-0115`: 정지는 납부 기한 후 15일 기준입니다. 완납하시면 2시간 이내에 재개됩니다.

## PIPE-BILLING-0092 (turn 0)

- 질문: 요금제 이번 달에 바꿀 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 네, 이번 달에 아직 요금제를 변경하지 않으셨다면 변경이 가능합니다. 한 달에 한 번만 변경할 수 있습니다. 변경을 원하시면 신청하시면 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0092`: 이번 달에 아직 안 바꾸셨으면 됩니다. 한 달에 한 번입니다.
  - `BILLING-0106`: 요금제 변경은 월 1회이며, 이번 달에 이미 변경했다면 다음 달에 가능합니다. 가입한 달에도 변경할 수 없습니다.
  - `BILLING-0001`: 요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.

## PIPE-BILLING-0097 (turn 0)

- 질문: 두 번 낸 거 자식 계좌로 받을 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 자식 계좌로는 받으실 수 없습니다. 반드시 본인 명의의 계좌로만 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0097`: 안 됩니다. 본인 명의 계좌로만 됩니다.

## PIPE-BILLING-0100 (turn 0)

- 질문: 요금이 너무 많이 나왔는데 25일까지 다 못 낼 것 같아요
- 기대 동작: ANSWER
- 실제 답변: 납부 기한은 매월 25일까지입니다. 현재 요금 납부에 어려움이 있으시다면, 고객센터에 연락하여 상황을 설명해 주시면 도움을 받으실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0100`: 납부 기한은 매월 25일입니다. 기한 조정은 고객센터에 상담해 보세요.
  - `BILLING-0128`: 청구서는 10일에 나오고 25일까지 내시면 됩니다. 내역은 고객센터에서 확인해 드립니다.

## PIPE-BILLING-0102 (turn 0)

- 질문: 휴대폰 결제가 갑자기 안 돼요
- 기대 동작: ANSWER
- 실제 답변: 휴대폰 결제가 갑자기 안 되시는군요. 기본 한도가 월 30만 원을 초과했을 가능성이 있습니다. 본인 인증을 통해 한도를 월 100만 원까지 즉시 올릴 수 있으니 이 방법을 시도해 보세요. 도움이 필요하시면 언제든지 다시 문의해 주세요.

- qwen: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0102`: 기본 한도 월 30만 원을 넘겼을 수 있습니다. 본인 인증으로 월 100만 원까지 즉시 올릴 수 있습니다.

## PIPE-BILLING-0113 (turn 0)

- 질문: 요금제를 바꿨는데 청구서에 두 요금제가 다 찍혀 있습니다
- 기대 동작: ANSWER
- 실제 답변: 요금제 변경 후 청구된 청구서에 두 가지 요금제가 함께 표시되는 것은 정상적인 현상입니다. 변경된 요금제는 변경일 다음 날 00:00부터 적용되며, 해당 달의 요금은 일할 계산되어 두 요금제 금액이 함께 표시됩니다. 걱정하지 마시고, 정확한 요금 내역은 청구서를 자세히 확인해 주세요.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0113`: 변경일 다음 날 00:00부터 새 요금제가 적용되고 그 달 요금은 일할 계산되어 두 요금제가 나눠 표시됩니다.

## PIPE-BILLING-0115 (turn 0)

- 질문: 청구서를 못 받았는데 발신이 정지됐습니다
- 기대 동작: ANSWER
- 실제 답변: 청구서를 받지 못하셨더라도 정지는 납부 기한이 지난 후 15일이 경과하면 발신이 정지될 수 있습니다. 재개까지는 대략 2시간 이내가 소요될 것입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0115`: 정지는 납부 기한 후 15일 기준입니다. 완납하시면 2시간 이내에 재개됩니다.
  - `BILLING-0087`: 정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.

## PIPE-BILLING-0117 (turn 0)

- 질문: 자동이체와 카드 결제가 같은 달에 둘 다 나갔습니다
- 기대 동작: ANSWER
- 실제 답변: 이 경우 고객센터에 연락하여 환불 접수를 진행해 주시기 바랍니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0117`: 납부 수단 변경은 다음 달 청구분부터 적용되어 겹칠 수 있습니다. 이중 납부는 고객센터에 환불 접수해 주세요.

## PIPE-BILLING-0123 (turn 0)

- 질문: 청구서에 결제한 적 없는 소액결제가 있어요
- 기대 동작: ANSWER
- 실제 답변: 청구서에 결제 기록이 없는 소액결제 항목이 표시된 경우, 이는 소액결제 한도 내에서 발생한 것으로 보입니다. 기본 한도는 월 30만 원입니다. 정확한 내용 확인을 위해 고객센터에 문의해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0123`: 소액결제 한도는 기본 월 30만 원입니다. 내역이 이상하면 고객센터에 확인해 주세요.

## PIPE-BILLING-0129 (turn 0)

- 질문: 요금 두 번 냈는데 전화가 끊겼어요
- 기대 동작: ANSWER
- 실제 답변: 밀린 요금이 있어 전화가 끊겼을 가능성이 있습니다. 두 번의 요금 납부는 별도로 접수되어야 하며, 해당 사항을 고객센터에 연락하여 확인 및 처리를 요청하시는 것이 좋습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0129`: 밀린 게 있으면 15일 지나서 끊깁니다. 두 번 낸 건 따로 접수하셔야 돌려드립니다.
  - `BILLING-0066`: 밀린 요금 내시면 2시간 안에 다시 됩니다.
  - `BILLING-0108`: 완납 후 2시간 이내에 재개됩니다. 그 이상 지나도 안 되면 고객센터에 확인해 주세요.

## PIPE-BILLING-0135 (turn 0)

- 질문: 청구서 나오는 날이랑 내는 날이랑 왜 달라요?
- 기대 동작: ANSWER
- 실제 답변: 청구서는 매월 10일에 발송되며, 납부 기한은 그 다음 달인 25일까지입니다. 이렇게 일정 시간 간격을 두어 고객님께서 청구서 내용을 확인하고 납부하실 수 있도록 하였습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0135`: 청구서는 매월 10일에 발송되고 납부 기한은 매월 25일이라 확인할 시간을 두는 것입니다.
  - `BILLING-0156`: 청구서가 10일에 먼저 오고, 25일까지 내는 겁니다.

## PIPE-BILLING-0138 (turn 0)

- 질문: 자동이체랑 가상계좌랑 뭐가 나아요?
- 기대 동작: ANSWER
- 실제 답변: 반면에 가상계좌는 매번 입금해야 하는 방식이라 관리가 조금 더 번거로울 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0138`: 자동이체는 등록에 3영업일이 걸리지만 매달 자동 출금되고, 가상계좌는 그때그때 입금하는 방식입니다.

## PIPE-BILLING-0146 (turn 0)

- 질문: 미납 상태에서 환불과 차감 중 어느 쪽이 유리한가요?
- 기대 동작: ANSWER
- 실제 답변: 미납 상태에서 선택하실 경우, 환불은 본인 명의 계좌로 5영업일 이내에 이루어지며, 차감은 다음 달 요금에서 자동으로 반영됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0146`: 환불은 본인 명의 계좌로 5영업일 이내 지급되고, 차감은 다음 달 요금에 반영됩니다.

## PIPE-BILLING-0148 (turn 0)

- 질문: 요금제 변경 적용일과 청구 기준일은 어떻게 다른가요?
- 기대 동작: ANSWER
- 실제 답변: 요금제 변경은 신청하신 날 다음 날 자정(00:00)부터 적용됩니다. 반면에 청구 기준일은 변경된 요금제 기준으로 그 달 사용량에 따라 일할 계산되어 청구됩니다. 즉, 변경된 요금제의 혜택은 변경 적용일 이후 사용량부터 반영됩니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0148`: 변경은 신청일 다음 날 00:00부터 적용되고, 그 달 요금은 일할 계산됩니다.
  - `BILLING-0141`: 신청일 당일이 아니라 다음 날 00:00부터 적용됩니다. 변경은 월 1회로 제한됩니다.
  - `BILLING-0050`: 요금제 변경은 월 1회, 신청일 다음 날 00:00 적용이라는 기준이 그대로 적용됩니다.

## PIPE-BILLING-0154 (turn 0)

- 질문: 종이랑 이메일이랑 뭐가 달라요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-BILLING-0155 (turn 0)

- 질문: 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요
- 기대 동작: ANSWER
- 실제 답변: 청구서 발행 전이든 후든 요금제 변경은 다음 날 00:00부터 적용됩니다. 변경 후에는 해당 달의 사용량에 따라 계산됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-BILLING-0155","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `BILLING-0155`: 똑같이 다음 날 00:00부터 됩니다. 한 달에 한 번입니다.
  - `BILLING-0127`: 요금제를 바꾸셨으면 다음 날 00:00부터 바뀌고 그 달은 날짜대로 나눠서 계산됩니다.
  - `BILLING-0099`: 네, 바꾸면 다음 날 00:00부터 적용됩니다.

## PIPE-COMPOUND-001 (turn 0)

- 질문: 한도를 월 100만 원 이상으로 올릴 수 있나요 그리고 분실로 해지할 때 위약금 확인 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-002 (turn 0)

- 질문: 요금이 부담돼서 일부만 내도 정지가 풀리나요 그리고 일시 정지 중에도 요금이 부과되나요?
- 기대 동작: ANSWER
- 실제 답변: 일시 정지 중에도 요금은 부과되며, 정지 기간에는 기본료의 50%가 청구됩니다. 전액 면제는 아닙니다. 일부만 내셔도 정지 상태가 자동으로 해제되는 것은 아니며, 정지 해제를 위해서는 전체 요금을 정산해야 합니다. 요금 부담을 줄이기 위해서는 다른 요금제나 할인 프로그램을 확인해 보시는 것도 좋은 방법입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0060`: 네, 정지 기간에는 기본료의 50%가 부과됩니다.
  - `TERMINATE-0080`: 정지 중에는 기본료의 50%가 부과됩니다. 전액 면제는 아닙니다.
  - `TERMINATE-0088`: 일시 정지 기간에도 기본료의 50%는 부과됩니다. 전액 면제가 아니라는 점 참고해 주세요.

## PIPE-COMPOUND-004 (turn 0)

- 질문: 요금 밀렸는데 청구서 방법 바꿀 수 있나요 그리고 위약금 얼마 나오는지 미리 알려면 어떻게 해요?
- 기대 동작: ANSWER
- 실제 답변: 요금 밀렸더라도 청구서 방법은 바꿀 수 있으며, 다음 달부터 변경된 방법으로 적용됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["UNCERTAIN","INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0098`: 네, 바꾸면 다음 달 청구서부터 적용됩니다.

## PIPE-COMPOUND-005 (turn 0)

- 질문: 요금제 바꾸면 그 달 요금은 어떻게 계산돼요 그리고 폰 잃어버려서 해지하면 위약금 있어요
- 기대 동작: ANSWER
- 실제 답변: 요금제를 변경하시면 변경된 요금제에 따라 그 달의 요금이 계산됩니다. 정확한 금액은 변경된 요금제의 세부 내용에 따라 달라지므로, 변경 후 안내된 요금 안내를 참고하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0098`: 해지하면 약정 24개월 기준 반환금(12개월 미만 100%, 이상 50%)이 발생합니다. 두면 반환금은 없지만 요금이 계속 나옵니다.
  - `TERMINATE-0018`: 약정 24개월 안에 해지하시면 있습니다. 12개월 전이면 할인 받은 거 전부(100%), 넘었으면 절반(50%)입니다.

## PIPE-COMPOUND-006 (turn 0)

- 질문: 데이터 다 썼다고 문자 오면 어떻게 해요 그리고 해외 단말도 eSIM 발급이 되나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-007 (turn 0)

- 질문: 가족 모두 5G 언리미티드로 바꿀 수 있나요 그리고 유심 재발급을 대리인에게 맡길 때 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-008 (turn 0)

- 질문: 안 쓴 데이터가 다음 달로 얼마나 넘어가요 그리고 eSIM으로 회선 두 개를 동시에 쓸 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-009 (turn 0)

- 질문: 연령 제한 없는 요금제만 따로 있나요 그리고 폰을 잃어버려서 eSIM으로 새로 받고 싶은데 어떻게 해요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-010 (turn 0)

- 질문: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 청소년 요금제와 시니어 요금제는 각각 만 18세 이하와 만 65세 이상만 가입 가능합니다. 가입 시에는 생년월일 확인이 가능한 신분증이 필요합니다. 기기 변경 시 유심 재발급을 위한 구비 서류는 다음과 같습니다: - 신분증 사본 - 기존 통신사 이용 약관 해지 증명서 (기존 통신사에서 발급)

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-COMPOUND-010","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":"SHOULD_ABSTAIN","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `PLAN-0004`: 청소년 요금제는 만 18세 이하, 시니어 요금제는 만 65세 이상만 가입할 수 있습니다. 가입 시 생년월일 확인이 가능한 신분증이 필요합니다.

## PIPE-COMPOUND-012 (turn 0)

- 질문: 기기변경 전에 파손 수리를 받으려면 어떻게 해요 그리고 명의변경 신청하고 나서 얼마나 기다리면 돼요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-013 (turn 0)

- 질문: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요?
- 기대 동작: ANSWER
- 실제 답변: 기기변경 신청은 개통일로부터 6개월이 경과한 후에 가능합니다. 할부금 처리는 기존 할부금을 완납하거나 새 할부 계약으로 승계하는 방식으로 이루어집니다. 자녀 명의 변경과 관련해서는 현재 제공된 정보로는 구체적인 준비물을 안내드리기 어렵습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-COMPOUND-013","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `DEVICE-0010`: 개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.

## PIPE-COMPOUND-014 (turn 0)

- 질문: 기기변경 시 새 할부 계약 절차를 알려주세요. 그리고 미성년자 명의 회선을 다른 미성년자에게 넘길 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-015 (turn 0)

- 질문: 액정 깨졌을 때 수리는 어떻게 받나요 그리고 성인 된 자녀 명의로 폰을 넘기려면 뭐가 필요해요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-016 (turn 0)

- 질문: 자녀 명의로 가입하려는데 나이 제한이 어떻게 되나요 그리고 가족이랑 로밍 데이터 나눠 쓰려면 어떻게 신청해요?
- 기대 동작: ANSWER
- 실제 답변: 자녀 명의로 가입하실 경우, 만 14세 미만은 가입이 불가능하며, 만 14세 이상 18세 이하의 경우 법정대리인 동의서, 가족관계증명서, 그리고 법정대리인의 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통 가능합니다. 로밍 데이터를 가족과 나눠 쓰는 방법에 대해서는 현재 제공하는 서비스 내에서 직접적인 가족 간 데이터 공유 기능이 명시적으로 안내되어 있지 않습니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE","MISSED"],"abstention":["REVIEW",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0010`: 만 14세 미만은 가입하실 수 없습니다. 만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통할 수 있습니다.
  - `SUBSCRIBE-0002`: 만 14세 미만은 가입할 수 없습니다. 만 14세 이상 18세 이하라면 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 필요합니다. 미성년자 명의로는 최대 3회선까지 개통할 수 있습니다.

## PIPE-COMPOUND-017 (turn 0)

- 질문: 자녀 명의 개통 절차를 단계별로 알려주세요. 그리고 요금 부담돼서 그런데 하루만 로밍 쓸 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-018 (turn 0)

- 질문: 첫 개통을 온라인으로 하면 처리 시간이 늦어지나요 그리고 듀얼번호 신청 절차와 비용을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 온라인 개통은 다음 날에 이루어지므로 처리 시간이 다소 늦어질 수 있습니다. 듀얼번호 신청 절차에 대해서는 현재 제공된 정보로는 구체적인 안내를 드리기 어렵습니다. 매장 방문을 통해 자세한 정보와 신청을 진행하시는 것도 좋은 방법입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0071`: 온라인은 다음 날 개통됩니다. 매장은 30분 이내라 당일 이용이 필요하면 매장이 낫습니다.

## PIPE-COMPOUND-019 (turn 0)

- 질문: 법정대리인 동의가 필요한 연령 구간을 알려주세요. 그리고 로밍 데이터 나눠쓰기는 몇 회선까지 가능한가요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-020 (turn 0)

- 질문: 법인 명의 개통 시 담당자 여권만으로 가능한가요 그리고 공항 도착 후에 신청해도 늦지 않나요?
- 기대 동작: ANSWER
- 실제 답변: 법인 명의 개통 시 담당자의 여권만으로는 개통이 불가능합니다. 본인 확인을 위해 여권 외에도 법인 관련 서류나 추가적인 증빙 자료가 필요할 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":null,"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0069`: 본인 명의 신분증이면 여권도 가능합니다. 외국인이면 외국인등록증과 여권을 함께 제시해야 합니다.

## PIPE-COMPOUND-021 (turn 0)

- 질문: 번호이동 최대 소요 시간을 알려주세요. 그리고 부가서비스 해지하면 언제까지 쓸 수 있어요
- 기대 동작: ANSWER
- 실제 답변: 번호 이동의 최대 소요 시간은 24시간 이내입니다. 부가서비스 해지와 관련된 정확한 기간은 현재 제공된 정보로는 안내드릴 수 없습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0009`: 최대 24시간 이내에 처리됩니다. 평균은 2시간 이내입니다.

## PIPE-COMPOUND-022 (turn 0)

- 질문: 개통한 지 두 달 됐는데 옮길 수 있나요 그리고 명의도용 신고 후 요금 취소까지 어떤 단계를 거치나요?
- 기대 동작: ANSWER
- 실제 답변: 개통한 지 두 달이 되셨지만, 아직 번호 이동 신청은 불가능합니다. 번호 이동은 개통 후 3개월이 경과해야 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0111`: 개통한 지 3개월이 지나야 다른 통신사로 번호이동이 가능합니다. 두 달째라면 아직 신청하실 수 없고, 3개월이 지난 뒤에 신청하셔야 합니다. 명의를 변경하신 경우에는 변경일로부터 1개월이 지나야 합니다.

## PIPE-COMPOUND-023 (turn 0)

- 질문: 철회 가능 기간의 기산일은 언제인가요 그리고 부가서비스 신청하면 오늘부터 바로 되는 거예요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-024 (turn 0)

- 질문: 번호이동 철회 시 회선은 어떻게 처리되나요 그리고 멤버십 등급 산정 기간을 제가 지정할 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-025 (turn 0)

- 질문: 번호이동 하고 싶은데 지금 조건이 되는지 어떻게 확인해요 그리고 4년 전 요금 낸 기록이 필요한데 안 나와요
- 기대 동작: ANSWER
- 실제 답변: 번호이동 가능 여부를 확인하려면 개통일로부터 3개월이 경과했는지 먼저 확인해 주세요. 만약 명의변경 경험이 있다면, 그 변경일로부터도 1개월이 경과했는지 함께 확인해 보시는 것이 좋습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0027`: 개통일로부터 3개월이 지났는지 확인하시면 됩니다. 명의변경을 하신 적이 있다면 변경일로부터 1개월이 지났는지도 함께 확인하셔야 합니다.
  - `PORTING-0082`: 기존 통신사에 미납 요금이 있으면 번호이동이 진행되지 않습니다. 미납 요금을 완납하신 뒤 다시 신청해 주세요. 신청하실 때는 본인 신분증이 필요합니다.

## PIPE-COMPOUND-026 (turn 0)

- 질문: 일시 정지 연간 한도가 어떻게 되나요 그리고 청구서를 이메일이랑 우편 둘 다 받을 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 일시 정지 연간 한도는 최대 90일입니다. 1회 신청 시 최대 30일까지 가능합니다. 청구서 수령 방법에 대해서는 현재 제공하는 옵션으로 이메일과 우편 둘 다 받는 것은 지원하지 않습니다. 이메일 또는 우편 중 하나를 선택하여 수령하실 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `TERMINATE-0012`: 연간 최대 90일입니다. 1회에는 최대 30일까지 신청할 수 있습니다.
  - `TERMINATE-0036`: 고객센터나 매장에서 신청하시면 됩니다. 1회 최대 30일, 연간 누적 최대 90일까지 가능하며 정지 기간 기본료는 50%로 줄어듭니다.

## PIPE-COMPOUND-027 (turn 0)

- 질문: 오래 안 쓸 건데 정지 석 달 되나요 그리고 청구서 안 왔는데 요금 안 내면 어떻게 돼요
- 기대 동작: ANSWER
- 실제 답변: 석 달 동안 정지하시려면 기본료의 절반(50%)만 청구됩니다. 청구서를 받지 못하셨더라도 요금을 기한 내에 납부하시면 서비스 정지는 이루어지지 않습니다. 만약 청구서를 받지 못하셨다면, 요금 납부 기한을 확인하시고 그 안에 요금을 내주시면 됩니다. 기한이 지나면 15일 후부터는 추가 요금 청구나 서비스 제한이 있을 수 있으니 주의하시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["MISSED","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- FAQ 근거:
  - `BILLING-0031`: 기한 지나고 15일이면 전화가 안 걸리고, 60일이면 해지될 수 있습니다.
  - `TERMINATE-0024`: 기본료의 절반(50%)만 나옵니다. 한 번에 30일, 일 년에 90일까지 됩니다.
  - `BILLING-0059`: 청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.

## PIPE-COMPOUND-029 (turn 0)

- 질문: 전화나 홈페이지로도 해지 신청이 가능한가요 그리고 요금 내는 방법이 네 가지라는데 뭐뭐예요?
- 기대 동작: ANSWER
- 실제 답변: 네, 전화나 홈페이지를 통해 해지 신청이 가능합니다. 요금 납부 방법은 주로 다음 네 가지로 이루어집니다: 1. **일시불 결제**: 한 번에 요금을 납부합니다. 2. 3. **자동이체**: 지정된 계좌에서 자동으로 요금이 이체됩니다. 4. 해지 신청 시 요금은 사용한 일수만큼 청구됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE","MISSED"],"abstention":[null,false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `TERMINATE-0045`: 매장이나 고객센터, 홈페이지에서 신청하시면 바로 됩니다. 그 달 요금은 쓴 날짜만큼 나옵니다.
  - `TERMINATE-0001`: 매장 방문, 고객센터 전화, 홈페이지 신청 중 편하신 방법으로 하시면 됩니다. 신청 즉시 해지되고 요금은 일할 계산됩니다.
  - `TERMINATE-0081`: 고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

## PIPE-COMPOUND-030 (turn 0)

- 질문: 오래 안 쓸 건데 정지하면 요금 얼마 나와요 그리고 청구서에서 이중 납부를 발견했을 때 다음 달 차감으로 처리하는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 정지 시 요금은 기본료의 절반(50%)이 부과되며, 한 번에 최대 30일까지 정지 가능하며, 일 년에 최대 90일까지 정지할 수 있습니다. 청구서에서 이중 납부를 발견하셨다면, 고객센터에 연락하여 다음 달 차감 방식을 선택하시면 됩니다. 접수 후 3영업일 이내에 확인되며, 환불이 필요하다면 추가로 5영업일 이내에 계좌로 입금될 예정입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE","COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0024`: 기본료의 절반(50%)만 나옵니다. 한 번에 30일, 일 년에 90일까지 됩니다.
  - `BILLING-0055`: 고객센터 접수 시 다음 달 차감 방식을 선택하면 됩니다. 접수 후 3영업일 이내에 확인되고, 환불을 택하면 5영업일 이내에 본인 명의 계좌로 입금됩니다.
  - `BILLING-0073`: 납부 기한 후 15일이 지나면 발신이 정지됩니다. 이중 납부는 별도로 환불 접수를 하셔야 합니다.

## PIPE-COMPOUND-031 (turn 0)

- 질문: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 해외 체류 중 유심을 분실하셨군요. 먼저 홈페이지에서 분실 신고를 진행하시면 즉시 이용이 정지됩니다. 귀국 후에는 새 유심을 발급받으시면 기존 유심은 자동으로 해지됩니다. 가족에게 데이터를 나눠주는 절차에 대해서는 현재 제공된 정보로는 안내드릴 수 있는 내용이 없습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-COMPOUND-031","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `USIM-0040`: 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.

## PIPE-COMPOUND-032 (turn 0)

- 질문: 새 폰 사서 유심 바꾸면 옛날 건 어떻게 해요 그리고 손주 데이터가 느려졌다는데 왜 그래요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-033 (turn 0)

- 질문: 외국 가기 전에 유심 새로 받으려면 어떻게 해요 그리고 부모님 시니어 요금제 가입 시 필요한 서류를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 유심을 새로 받으시기 위해서는 부모님께서 직접 방문하시거나, 대리인 방문을 통해 처리하실 수 있습니다. 대리인 방문 시에는 위임장, 대리인의 신분증 사본, 그리고 가입자(본인)의 신분증 사본을 준비하셔야 합니다. 시니어 요금제 가입 시에는 일반적으로 가입자의 신분증 사본과 함께 시니어 증빙 서류가 필요할 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","UNCERTAIN"]]}`
- FAQ 근거:
  - `USIM-0030`: 대리인 방문이라 위임장, 대리인 신분증, 가입자 신분증 사본을 준비하셔야 합니다.

## PIPE-COMPOUND-034 (turn 0)

- 질문: 유심 잃어버렸는데 폰 없이도 정지시킬 수 있나요 그리고 가족이 다 같이 쓸 요금제를 고르려면 어떻게 봐야 해요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-035 (turn 0)

- 질문: 새 단말로 바꾸면서 eSIM으로 전환하는 절차를 알려주세요. 그리고 남은 데이터 다음 달에 쓸 수 있어요
- 기대 동작: ANSWER
- 실제 답변: 새 단말을 eSIM으로 전환하려면 먼저 새 단말이 eSIM을 지원하는지 확인해 주세요. 그 후 매장이나 온라인을 통해 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원이며, 이 과정을 통해 기존 데이터는 다음 달에 계속 사용 가능합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `USIM-0039`: 새 단말이 eSIM을 지원하는지 확인한 뒤 매장이나 온라인에서 eSIM 발급을 신청하시면 됩니다. 발급 비용은 2,750원입니다.
  - `USIM-0099`: eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.

## PIPE-COMPOUND-036 (turn 0)

- 질문: 법인 회선을 개인 명의로 전환할 때 구비 서류를 알려주세요. 그리고 사용하다가 마음에 안 들면 교환할 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 법인 회선을 개인 명의로 전환할 때 필요한 구비 서류는 양도인과 양수인의 신분증입니다. 원칙적으로는 두 분이 함께 방문하시는 것이 좋으나, 한 분이 방문이 어려우시다면 위임장을 준비하셔야 합니다. 교환 가능 여부에 대해서는 현재 제공 중인 서비스 정책에 따라 다를 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0010`: 양도인과 양수인의 신분증이 각각 필요하며, 원칙적으로 양측이 함께 방문해야 합니다. 한쪽이 방문할 수 없으면 위임장을 준비합니다.

## PIPE-COMPOUND-037 (turn 0)

- 질문: 퇴사하면서 회사폰 명의를 제 앞으로 가져오려는데 할부가 남아 있어요. 조건이 뭐예요 그리고 액정 깨져서 새로 사면 30개월 되나요
- 기대 동작: ANSWER
- 실제 답변: 퇴사 후 회사폰 명의를 개인 앞으로 변경하려면 남아 있는 할부금에 대한 신용 심사를 통과해야 합니다. 심사를 거쳐 승인되면 명의 변경이 가능합니다. 또한, 액정 파손으로 인한 새 폰 구매 시 단말 할부는 24개월 또는 30개월 중 선택 가능하니, 매장에서 원하시는 기간으로 진행할 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH","ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0057`: 가능합니다. 새 할부는 24개월 또는 30개월, 연 5.9%이며 기존 할부금은 완납하거나 승계합니다.
  - `NAME_CHANGE-0006`: 단말 할부금이 남아 있으면 양수인인 본인이 신용 심사를 거쳐야 합니다. 통과하면 명의변경이 진행됩니다.
  - `DEVICE-0097`: 단말 할부는 24개월 또는 30개월 중 선택할 수 있습니다. 매장에서 조건을 다시 확인해 주세요.

## PIPE-COMPOUND-038 (turn 0)

- 질문: 자식한테 폰 넘기는데 할부가 남았으면 어떻게 해요 그리고 분실 신고 후 다시 찾으면 정지를 풀 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 분실 신고 후 폰을 다시 찾으신 경우, 일반적으로 정지 해제 절차를 다시 진행하셔야 합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["MISSED","COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0033`: 받는 자녀분이 신용 심사를 받으시면 됩니다. 통과하면 넘어갑니다.

## PIPE-COMPOUND-039 (turn 0)

- 질문: 회사 폰 내 걸로 만들려면 어떻게 해요 그리고 액정 파손 후 새 단말 할부 수수료율을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["REVIEW","REVIEW"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-040 (turn 0)

- 질문: 회사 담당자가 대리로 와서 양도 처리를 해도 되나요 그리고 배터리 교체 비용도 할부에 포함할 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-041 (turn 0)

- 질문: 해외에서 번호 두 개 쓸 수 있어요 그리고 외국인인데 개통하면 얼마 만에 쓸 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 외국인 고객님께서 두 개의 번호를 사용하는 것은 일반적으로 제한될 수 있으며, 정확한 조건은 현재 제공된 정보로는 확인이 어렵습니다. 하지만 개통 시간에 대해 말씀드리자면, 매장에서 가입하시면 개통 후 즉시 사용 가능하며, 온라인으로 가입하실 경우 다음 날 개통되어 사용 가능합니다. 처리 시간은 오전 9시부터 오후 8시까지입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":null,"abstention":[null,false,"INSUFFICIENT",["INSUFFICIENT","ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["MISSED","PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT","UNCERTAIN"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0003`: 매장에서 가입하시면 30분 이내에 개통됩니다. 온라인은 다음 날 개통되고, 처리 시간은 09:00부터 20:00까지입니다.

## PIPE-COMPOUND-042 (turn 0)

- 질문: 해외 나갈 때 데이터 하루에 얼마예요 그리고 법인 명의로 미성년 직원 회선을 만들 수 있는 절차가 있어요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-043 (turn 0)

- 질문: 출국하기 전에 로밍 가입됐는지 확인하려면 어떻게 해요 그리고 자녀 대신 제 신분증으로 자녀 폰을 만들 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-045 (turn 0)

- 질문: 앱 없이도 로밍 됐는지 알 수 있나요 그리고 법인 회선 개통 소요 시간과 처리 시간대를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-047 (turn 0)

- 질문: 매장에 갈 건데 시간을 어떻게 맞추면 돼요 그리고 결합 해지하고 번호이동하려면 순서가 어떻게 돼요?
- 기대 동작: ANSWER
- 실제 답변: 한 번에 여러 내용을 요청하셨어요. 질문을 하나씩 나누어 보내주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-COMPOUND-048 (turn 0)

- 질문: 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요 그리고 다른 회사로 번호 가져가는 거 어떻게 하는 거예요
- 기대 동작: ANSWER
- 실제 답변: 신고 접수 후 3영업일 이내에 조사가 진행되며, 도용이 확인되면 해당 요금은 전액 취소됩니다. 번호를 다른 회사로 이전하는 절차에 대해서는 현재 제공된 정보로는 자세한 안내를 드리기 어렵습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SERVICE-0004`: 신고 접수 후 3영업일 이내에 조사가 진행됩니다. 도용이 확인되면 해당 요금은 전액 취소됩니다.

## PIPE-COMPOUND-049 (turn 0)

- 질문: 증명서 인터넷으로 바로 뗄 수 있어요 그리고 개통하고 보름 지났는데 철회할 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 네, 증명서는 홈페이지에서 즉시 무료로 발급 가능합니다. 정확한 철회 정책은 해당 페이지의 안내를 참고해 주세요.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SERVICE-0030`: 네, 홈페이지에서 신청하시면 즉시 무료로 발급됩니다.

## PIPE-COMPOUND-050 (turn 0)

- 질문: 내 멤버십 등급이 어떻게 정해지는지 순서대로 설명해 주세요. 그리고 번호이동 신청부터 완료까지 흐름을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 멤버십 등급은 주로 직전 1년간 납부하신 금액을 기준으로 정해집니다. 순서대로 설명드리면 다음과 같습니다: 1. **납부 금액 집계**: 직전 1년간 사용하신 서비스에 대한 금액을 합산합니다. 2. **등급 산정**: 합산된 금액에 따라 다음과 같은 등급으로 결정됩니다: VVIP, VIP, 골드, 실버. 3. **갱신 처리**: 매년 1월에 등급이 갱신됩니다. 번호이동 신청부터 완료까지의 일반적인 흐름은 다음과 같습니다: 1. 2. **인증 및 확인**: 신청 후 필요한 인증 절차를 거치고, 신청 내용을 확인합니다. 3. **심사**: 통신사에서 신청 내용을 심사합니다. 4. **승인 및 안내**: 심사 후 승인되면 안내 메시지를 통해 완료 안내를 받습니다. 5. **서비스 전환**: 승인 후 지정된 날짜에 새로운 번호로 서비스가 전환됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":null}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE","MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["ENOUGH","INSUFFICIENT"]]}`
- FAQ 근거:
  - `SERVICE-0017`: 먼저 직전 1년간 납부하신 금액을 집계하고, 그 금액에 따라 VVIP·VIP·골드·실버 중 하나로 정해집니다. 이 산정은 매년 1월에 한 번 이루어집니다.
  - `SERVICE-0022`: 직전 1년간 납부하신 금액을 기준으로 등급이 산정되고, 매년 1월에 갱신 처리됩니다. 등급은 VVIP·VIP·골드·실버 4단계입니다.
  - `SERVICE-0027`: 앱이나 홈페이지에서 확인하실 수 있습니다. 등급은 직전 1년 납부액으로 매년 1월에 정해집니다.

## PIPE-DEVICE-0004 (turn 0)

- 질문: 배터리가 이상해서 새 폰 반품하려는데 기한이 얼마예요?
- 기대 동작: ANSWER
- 실제 답변: 개통 후 14일 이내이고 새 폰을 미개봉 상태로 유지하셨다면 반품이 가능합니다. 한 번에 한하여 적용됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `DEVICE-0004`: 개통 후 14일 이내이고 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 1회에 한해 가능합니다.
  - `DEVICE-0104`: 14일 안에 안 쓴 상태여야 바꿔드립니다. 쓰셨으면 수리를 받으셔야 합니다.
  - `DEVICE-0024`: 개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

## PIPE-DEVICE-0008 (turn 0)

- 질문: 침수된 폰은 교환 대상이 돼요?
- 기대 동작: ANSWER
- 실제 답변: 침수된 폰은 교환 대상이 아닙니다. 침수된 단말은 수리 대상으로, 제조사의 서비스센터에서 점검 및 수리를 받으셔야 합니다. 교환은 미개봉 상태이거나 사용 흔적이 전혀 없는 경우에만 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-DEVICE-0008","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":"SHOULD_ABSTAIN","answerability":"ENOUGH"}`
- FAQ 근거:
  - `DEVICE-0008`: 반품·교환은 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 침수 단말은 수리 대상입니다.
  - `DEVICE-0088`: 교환은 미개봉이거나 사용 흔적이 없어야 합니다. 침수 단말은 제조사 서비스센터 수리 대상입니다.
  - `DEVICE-0068`: 교환은 미개봉 또는 사용 흔적이 없는 상태여야 합니다. 침수 흔적이 있으면 교환 대신 수리로 진행됩니다.

## PIPE-DEVICE-0012 (turn 0)

- 질문: 액정 불량 단말의 교환 가능 조건을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 액정 불량 단말의 교환은 개통 후 14일 이내에 이루어져야 하며, 단말이 미개봉 상태이거나 사용 흔적이 없는 경우에 가능합니다. 또한, 이 교환은 1회에 한정됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `DEVICE-0012`: 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태, 1회에 한해 가능합니다.

## PIPE-DEVICE-0024 (turn 0)

- 질문: 배터리 이상한 새 폰 바꿔줘요
- 기대 동작: ANSWER
- 실제 답변: 개통 후 14일 이내에 사용하지 않은 상태라면 배터리 문제로 새 폰으로 교체해 드릴 수 있습니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0024`: 개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.
  - `DEVICE-0104`: 14일 안에 안 쓴 상태여야 바꿔드립니다. 쓰셨으면 수리를 받으셔야 합니다.

## PIPE-DEVICE-0033 (turn 0)

- 질문: 침수돼서 새 폰 할부로 사려는데 어떻게 진행돼요?
- 기대 동작: ANSWER
- 실제 답변: 새 폰을 할부로 구매하시려면 매장에서 24개월 또는 30개월 중 원하시는 할부 기간을 선택하시면 됩니다. 할부 이용 시 연 5.9%의 수수료가 적용됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0033`: 매장에서 24개월 또는 30개월 중 할부 기간을 고르시면 됩니다. 수수료는 연 5.9%가 적용됩니다.

## PIPE-DEVICE-0034 (turn 0)

- 질문: 배터리가 오래돼서 기기변경하려면 뭘 준비해요?
- 기대 동작: ANSWER
- 실제 답변: 배터리가 오래되어 기기 변경을 원하신다면, 개통 후 6개월이 경과했는지 확인하시고, 기존 기기의 할부금이 완납되었거나 승계 절차가 완료되었는지 준비하시면 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0034`: 개통 후 6개월 경과 여부와 기존 할부금 완납 또는 승계 여부를 준비하시면 됩니다.

## PIPE-DEVICE-0040 (turn 0)

- 질문: 기기변경 직후 새 단말 교환 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 개통 후 14일 이내에 미개봉 또는 미사용 상태의 새 단말을 매장이나 고객센터에 신청하시면 1회에 한해 교환이 가능합니다.

- qwen: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0040`: 개통 후 14일 이내에 미개봉 또는 미사용 상태로 매장이나 고객센터에 신청하면 1회에 한해 교환됩니다.
  - `PORTING-0039`: 기존 회선 개통일로부터 3개월, 명의변경이 있었다면 변경일로부터 1개월 경과 여부를 확인한 뒤 진행하시면 됩니다.
  - `DEVICE-0038`: 매장에서 기기변경을 신청하시면 되고, 이때 기존 단말 할부금을 완납하시거나 새 할부로 승계하는 방식을 선택하실 수 있습니다.

## PIPE-DEVICE-0048 (turn 0)

- 질문: 물에 빠뜨린 새 폰 교환하려면 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 물에 빠뜨린 새 폰의 경우, 개통 후 14일 이내에 사용 흔적이 거의 없는 상태여야 교환이 가능합니다. 만약 침수된 상태라면 제조사의 서비스 센터를 통해 수리를 받아야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0048`: 개통하고 14일 안에 안 쓴 상태여야 바꿔드립니다. 침수됐으면 수리를 받으셔야 합니다.
  - `DEVICE-0088`: 교환은 미개봉이거나 사용 흔적이 없어야 합니다. 침수 단말은 제조사 서비스센터 수리 대상입니다.

## PIPE-DEVICE-0054 (turn 0)

- 질문: 배터리가 금방 닳아서 폰 바꾸고 싶은데 조건이 뭐예요
- 기대 동작: ANSWER
- 실제 답변: 배터리 문제로 새 폰을 원하시는 경우, 개통 후 6개월이 경과해야 조건에 부합합니다. 현재 남은 할부 금액을 모두 상환하시거나, 새 할부 계약으로 전환하실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0054`: 개통하고 6개월이 지나야 됩니다. 남은 할부는 다 내거나 새 할부로 넘기시면 됩니다.
  - `DEVICE-0024`: 개통하고 14일 안에 안 쓴 상태면 한 번 바꿔드립니다.

## PIPE-DEVICE-0062 (turn 0)

- 질문: 액정만 깨졌는데 기기변경 대신 수리로 갈 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 액정이 깨졌을 경우, 기기 변경 기간이 경과하지 않았다면 수리로 진행하실 수 있습니다. 현재 조건으로는 개통 후 6개월 이내이므로 수리를 추천드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0062`: 기기 변경은 개통 후 6개월이 지나야 하므로, 그 전이라면 수리로 진행하시면 됩니다.

## PIPE-DEVICE-0063 (turn 0)

- 질문: 침수 폰도 보험 처리가 될까요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-DEVICE-0070 (turn 0)

- 질문: 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요?
- 기대 동작: ANSWER
- 실제 답변: 개통 후 6개월이 지나더라도 할부금 완납 없이는 기기변경이 불가능합니다. 기기변경을 위해서는 기존 할부금을 완납하거나 새 할부 조건으로 승계해야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0070`: 기존 할부금을 완납하거나 새 할부에 승계해야 합니다. 둘 중 하나는 필요합니다.
  - `DEVICE-0046`: 개통하고 6개월이 지나면 바꾸실 수 있습니다. 남은 할부금은 다 내시거나 새 할부로 넘기시면 됩니다.
  - `DEVICE-0010`: 개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.

## PIPE-DEVICE-0072 (turn 0)

- 질문: 액정 보호필름을 붙였는데 교환이 가능한가요?
- 기대 동작: ANSWER
- 실제 답변: 액정 보호필름을 붙였더라도 교환이 가능합니다. 하지만 교환을 위해서는 제품이 미개봉 상태이거나 사용 흔적이 전혀 없어야 합니다. 필름 부착 여부는 사용 흔적으로 간주될 수 있으니 매장에서 확인받으시는 것이 좋습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0072`: 교환은 미개봉이거나 사용 흔적이 없는 상태여야 합니다. 필름 부착은 사용 흔적으로 볼 수 있어 매장 확인이 필요합니다.
  - `DEVICE-0092`: 교환은 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태에서만 가능합니다. 사용한 흔적이 있으면 교환이 어려울 수 있으니 매장에서 상태를 확인받아 보시기 바랍니다.

## PIPE-DEVICE-0076 (turn 0)

- 질문: 잃어버렸다가 찾은 새 폰 반품되나요
- 기대 동작: ANSWER
- 실제 답변: 잃어버렸다가 다시 찾은 새 폰의 경우, 14일 이내이고 사용하지 않은 상태라면 반품이 가능합니다. 단, 이는 한 번에 한정됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `DEVICE-0076`: 14일 안이고 안 쓴 상태면 됩니다. 한 번만 됩니다.

## PIPE-DEVICE-0080 (turn 0)

- 질문: 바꾼 지 며칠 안 된 폰 다시 바꿀 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 개통 후 14일 이내에 사용하지 않은 상태라면 한 번 더 기기 변경이 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0080`: 14일 안에 안 쓴 상태면 한 번 됩니다.
  - `DEVICE-0020`: 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태라면 반품이나 교환이 가능합니다. 다만 1회만 가능합니다.
  - `DEVICE-0086`: 기기 변경은 개통 후 6개월이 지나야 가능합니다. 그 전에는 새 회선 개통이나 수리 등 다른 방법을 안내받으세요.

## PIPE-DEVICE-0084 (turn 0)

- 질문: 배터리가 하루도 안 가는 새 폰인데 교환 거절당했어요
- 기대 동작: ANSWER
- 실제 답변: 배터리 성능 문제로 인해 교환을 원하시는 경우, 개통 후 14일 이내에 새 제품이 미개봉 상태여야 교환이 가능합니다. 만약 해당 기간이 지났거나 제품에 사용 흔적이 있다면 교환이 어려울 수 있습니다. 서비스센터에 방문하시어 정확한 상태를 확인해 보시는 것을 권장드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0084`: 교환은 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 기간이 지났거나 사용 흔적이 있으면 서비스센터 수리로 안내됩니다.

## PIPE-DEVICE-0089 (turn 0)

- 질문: 배터리 때문에 새로 샀는데 할부 기간을 잘못 골랐어요
- 기대 동작: ANSWER
- 실제 답변: 개통 시 선택 가능한 할부 기간은 24개월 또는 30개월입니다. 이미 선택하신 할부 기간을 변경하고 싶으시다면 매장에 직접 문의해 주시기 바랍니다. 변경 가능 여부와 관련된 자세한 내용은 매장 직원에게 확인해 보시는 것이 좋습니다. 수수료는 연 5.9%입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0089`: 할부 기간은 24개월 또는 30개월 중 개통 시 선택합니다. 변경 가능 여부는 매장에 문의해 주세요. 수수료는 연 5.9%입니다.

## PIPE-DEVICE-0095 (turn 0)

- 질문: 기기변경 전 수리를 받았는데 자기부담금이 30%보다 많이 나왔습니다
- 기대 동작: ANSWER
- 실제 답변: 자기부담금은 보험 가입 시 수리비의 30%로 정해져 있습니다. 청구 내역을 다시 한번 확인해 주시고, 만약 예상보다 높게 나왔다면 보험 가입 상태와 수리 내역을 제조사 서비스센터에 문의해 보시는 것을 권장드립니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `DEVICE-0095`: 보험 가입 시 자기부담금은 수리비의 30%입니다. 보험 가입 여부와 청구 내역을 확인해 주세요.
  - `DEVICE-0015`: 단말 보험 가입 시 수리비의 30%를 자기부담금으로 냅니다. 수리는 제조사 서비스센터에서 합니다.
  - `DEVICE-0059`: 보험 가입 시 수리비의 30%를 자기부담금으로 냅니다. 수리는 제조사 서비스센터에서 진행합니다.

## PIPE-DEVICE-0098 (turn 0)

- 질문: 침수로 기기변경하려는데 할부 승계가 거절됐습니다
- 기대 동작: ANSWER
- 실제 답변: 기존 할부금은 완납하거나 새 할부 계약으로 승계해야 합니다. 승계가 어려우시다면 완납을 통해 진행할 수 있습니다. 추가로 변경을 위해서는 개통 후 6개월이 경과해야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0098`: 기존 할부금은 완납하거나 새 할부에 승계해야 합니다. 승계가 어려우면 완납으로 진행하시면 됩니다.
  - `DEVICE-0018`: 완납하거나 새 할부에 승계해야 합니다. 개통 후 6개월이 지나야 변경할 수 있습니다.

## PIPE-DEVICE-0099 (turn 0)

- 질문: 물에 빠뜨렸습니다. 어디로 가야 합니까.
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-DEVICE-0102 (turn 0)

- 질문: 액정 깨진 폰 바꾸러 갔는데 안 된대요
- 기대 동작: ANSWER
- 실제 답변: 개통 후 6개월이 지나지 않아 교체가 어렵습니다. 현재는 수리를 받으셔야 합니다. 자세한 수리 절차는 가까운 서비스 센터를 방문하시거나 연락주시면 안내해 드리겠습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0102`: 개통하고 6개월이 안 돼서 그렇습니다. 그 전엔 수리를 받으셔야 합니다.
  - `DEVICE-0022`: 개통하고 6개월 지나야 됩니다.

## PIPE-DEVICE-0105 (turn 0)

- 질문: 기기 바꿨는데 이자가 붙어요
- 기대 동작: ANSWER
- 실제 답변: 기기 변경 시 할부 이용 시 연 5.9%의 이자가 적용될 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0105`: 할부는 연 5.9% 이자가 붙습니다.

## PIPE-DEVICE-0116 (turn 0)

- 질문: 반품과 교환은 조건이 서로 다른가요?
- 기대 동작: ANSWER
- 실제 답변: 반품과 교환 모두 개통 후 14일 이내에 미개봉 제품이거나 사용 흔적이 없는 상태에서 1회에 한해 동일한 조건으로 적용됩니다. 따라서 조건은 서로 다릅니다만, 실제로는 동일한 기준을 따릅니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `DEVICE-0116`: 반품과 교환 모두 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태에서 1회에 한해 적용되는 동일한 조건입니다.
  - `DEVICE-0124`: 네, 반품과 교환 모두 개통 후 14일 이내, 미개봉이거나 사용 흔적이 없는 상태에서 1회에 한해 가능한 동일한 조건이라 언제든 원하시는 쪽으로 처리해 드릴 수 있습니다.

## PIPE-DEVICE-0117 (turn 0)

- 질문: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?
- 기대 동작: ANSWER
- 실제 답변: 액정 파손 후 새 단말을 구매할 때, 24개월과 30개월 할부 모두 연 수수료율은 동일하게 5.9%입니다. 하지만 할부 기간이 더 긴 30개월의 경우 총 수수료가 더 많아집니다. 구체적인 금액 차이는 구매 금액에 따라 달라지므로 정확한 수치는 제공된 정보로는 안내드릴 수 없습니다. 할부 기간 선택 시 총 비용을 고려하시는 것이 좋습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-DEVICE-0117","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `DEVICE-0117`: 수수료율은 둘 다 연 5.9%로 같지만, 30개월이 기간이 길어 총 수수료가 더 많습니다.
  - `DEVICE-0017`: 연 5.9%입니다. 할부 기간은 24개월 또는 30개월입니다.
  - `DEVICE-0097`: 단말 할부는 24개월 또는 30개월 중 선택할 수 있습니다. 매장에서 조건을 다시 확인해 주세요.

## PIPE-DEVICE-0129 (turn 0)

- 질문: 배터리 때문에 새로 사는데 할부 길게 하는 게 나아요
- 기대 동작: ANSWER
- 실제 답변: 연 이자율은 5.9%로 동일합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `DEVICE-0129`: 30개월은 매달 적게 내지만 이자를 더 냅니다. 이자율은 연 5.9%로 같습니다.
  - `DEVICE-0049`: 24개월이나 30개월 중에 고르시면 됩니다. 이자는 연 5.9%입니다.

## PIPE-NAME_CHANGE-0015 (turn 0)

- 질문: 남편 폰 제 이름으로 하는데 할부 남았으면 어떻게 돼요
- 기대 동작: ANSWER
- 실제 답변: 심사 결과에 따라 결정됩니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0015`: 제 이름으로 받는 분이 신용 심사를 받으셔야 합니다. 통과하면 됩니다.

## PIPE-NAME_CHANGE-0020 (turn 0)

- 질문: 미성년 자녀가 성인이 됐는데 명의를 자녀 앞으로 바꾸는 순서를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 미성년 자녀가 성인이 되어 명의 변경을 원하시는군요. 이 과정에서 별도의 수수료는 발생하지 않습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["PARTIAL"],"abstention":[null,false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0020`: 두 분이 신분증을 챙겨 매장을 방문하시면 접수되고, 미납 요금이 없으면 30분 이내에 처리됩니다. 수수료는 없습니다.

## PIPE-NAME_CHANGE-0024 (turn 0)

- 질문: 미성년자한테 폰 명의 넘기는 절차가 어떻게 돼요?
- 기대 동작: ANSWER
- 실제 답변: 미성년자 간의 휴대폰 명의 이전은 현재 규정상 허용되지 않습니다. 따라서 미성년자에게 휴대폰 명의를 넘기는 절차는 진행하실 수 없습니다. 성인 간의 거래에 대해서는 일반적인 절차를 따르게 됩니다. 자세한 절차에 대해 궁금하시다면 성인 간의 휴대폰 명의 이전 방법을 문의해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0024`: 양수인이 미성년자이고 양도인도 미성년자라면 진행할 수 없습니다. 성인 간 또는 성인이 관여하는 경우에만 일반 절차로 진행됩니다.

## PIPE-NAME_CHANGE-0025 (turn 0)

- 질문: 가족 간 명의변경 시 방문과 서류 절차를 정리해 주세요.
- 기대 동작: ANSWER
- 실제 답변: 가족 간 명의변경을 위해서는 양도인과 양수인 모두 신분증을 지참하고 함께 매장을 방문해야 합니다. 만약 한 분이 직접 방문이 어려우시다면, 위임장을 준비하여 대리로 진행할 수 있습니다. 방문 시 필요한 서류는 구체적인 상황에 따라 다를 수 있으나, 일반적으로 신분증 외에 추가적인 서류 요청이 있을 수 있으니 매장 직원에게 안내를 받으시는 것이 좋습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0025`: 양도인과 양수인이 각각 신분증을 지참하고 함께 매장을 방문합니다. 한쪽이 방문하지 못하면 위임장으로 대리 진행이 가능합니다.

## PIPE-NAME_CHANGE-0027 (turn 0)

- 질문: 미성년자 간 명의변경 절차를 알고 싶습니다.
- 기대 동작: ANSWER
- 실제 답변: 미성년자 간의 명의변경은 불가능합니다. 다른 문의 사항이 있으시면 알려주세요.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0027`: 미성년자 사이의 명의변경은 애초에 진행할 수 없습니다.
  - `NAME_CHANGE-0020`: 두 분이 신분증을 챙겨 매장을 방문하시면 접수되고, 미납 요금이 없으면 30분 이내에 처리됩니다. 수수료는 없습니다.
  - `NAME_CHANGE-0054`: 안 됩니다. 미성년자 사이의 명의변경은 불가능합니다.

## PIPE-NAME_CHANGE-0030 (turn 0)

- 질문: 퇴사자에게 회사 회선을 넘길 때 할부 잔액이 있으면 어떻게 진행되나요?
- 기대 동작: ANSWER
- 실제 답변: 퇴사자가 회사 회선을 넘기려면 먼저 신용 심사를 받아야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0030`: 양수인인 퇴사자가 신용 심사를 받고, 통과하면 명의변경이 진행됩니다.

## PIPE-NAME_CHANGE-0036 (turn 0)

- 질문: 미성년자 손주한테 넘길 수 있어요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-NAME_CHANGE-0044 (turn 0)

- 질문: 당일에 바로 처리가 가능한가요?
- 기대 동작: ANSWER
- 실제 답변: 네, 당일에 바로 처리가 가능합니다. 매장 방문 시 일반적으로 30분 이내에 완료되며, 추가 수수료는 발생하지 않습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0044`: 가능합니다. 매장 방문 시 30분 이내에 처리되며 수수료도 없습니다.
  - `PORTING-0061`: 처리 시간이 20:00까지라 당일 완료가 어려울 수 있습니다. 늦어도 24시간 이내에는 처리됩니다.

## PIPE-NAME_CHANGE-0049 (turn 0)

- 질문: 아들이 대신 가서 명의 바꿔올 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 네, 위임장을 작성하셔서 아드님께 제출하시면 됩니다. 아드님과 본인의 신분증도 함께 지참해 주세요.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0049`: 네, 위임장을 써 주시면 됩니다. 두 사람 신분증도 챙겨야 합니다.

## PIPE-NAME_CHANGE-0058 (turn 0)

- 질문: 퇴사했는데 회사 담당자가 매장에 같이 못 온대요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-NAME_CHANGE-0061 (turn 0)

- 질문: 아버지 신분증만 가지고 갔는데 처리가 안 됐습니다
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-NAME_CHANGE-0068 (turn 0)

- 질문: 수수료를 내라고 하는데 원래 그런가요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-NAME_CHANGE-0070 (turn 0)

- 질문: 회사가 폐업해서 양도인이 없어요
- 기대 동작: ANSWER
- 실제 답변: 양도인이 없는 경우에는 원칙적으로 절차 진행이 어렵습니다. 매장에 직접 방문하셔서 현재 상황을 설명하시고 필요한 대리 서류나 추가적인 안내를 받으시는 것이 좋습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0070`: 양도인 측 서류 없이는 원칙적으로 진행이 어렵습니다. 매장에 사정을 말씀하시고 필요한 대리 서류를 안내받으세요.

## PIPE-NAME_CHANGE-0075 (turn 0)

- 질문: 할부 있는 폰이랑 없는 폰이랑 명의변경 시간이 달라요?
- 기대 동작: ANSWER
- 실제 답변: 네, 그렇습니다. 할부가 남아 있는 폰의 경우 신용 심사 절차가 추가되어 명의변경 시간이 더 걸릴 수 있습니다. 반면에 할부가 없는 폰은 심사 절차 없이 바로 명의변경이 가능합니다. 정확한 시간 차이는 상황에 따라 다를 수 있으니 참고하시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0075`: 할부금이 남아 있으면 양수인 신용 심사를 거쳐야 해서 절차가 하나 더 있습니다. 없으면 심사 없이 바로 진행됩니다.
  - `PORTING-0095`: 명의변경을 안 하셨다면 개통 후 3개월이 기준이고, 명의변경을 하셨다면 변경일로부터 1개월이 기준입니다.

## PIPE-NAME_CHANGE-0076 (turn 0)

- 질문: 엄마가 오는 거랑 위임장 받아오는 거랑 뭐가 더 확실해요?
- 기대 동작: ANSWER
- 실제 답변: 따라서 확실성 측면에서는 두 방법 모두 동등합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0076`: 두 분이 함께 오시면 신분증만으로 바로 됩니다. 위임장 대리는 서류가 하나 더 필요하지만 처리 결과는 같습니다.

## PIPE-NAME_CHANGE-0083 (turn 0)

- 질문: 미납 요금을 먼저 내고 명의변경하는 것과 명의변경 후에 내는 것 중 어느 쪽인가요?
- 기대 동작: ANSWER
- 실제 답변: 미납 요금을 먼저 완납하신 후에 명의변경을 진행하셔야 합니다. 미납 요금이 남아 있으면 명의변경이 불가능합니다. 완납 후에는 명의변경 절차가 원활하게 진행될 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0083`: 먼저 완납해야 합니다. 미납 요금이 남아 있으면 명의변경이 진행되지 않습니다.
  - `NAME_CHANGE-0056`: 미납 요금이 있으면 명의변경이 진행되지 않습니다. 먼저 미납 요금을 완납하신 뒤 다시 신청해 주세요.
  - `NAME_CHANGE-0011`: 네, 미납 요금이 있으면 완납해야 명의변경이 진행됩니다. 완납 후에는 30분 이내에 처리되고 수수료는 없습니다.

## PIPE-NAME_CHANGE-0088 (turn 0)

- 질문: 내가 가는 거랑 위임장 주는 거랑 뭐가 빨라요
- 기대 동작: ANSWER
- 실제 답변: 처리 시간은 동일합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `NAME_CHANGE-0088`: 처리 시간은 같습니다. 직접 가시면 서류가 하나 덜 필요합니다.

## PIPE-OUT-001 (turn 0)

- 질문: 이번 주 나스닥 지수는 얼마나 올랐나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-002 (turn 0)

- 질문: 김치찌개를 맛있게 끓이는 방법 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-003 (turn 0)

- 질문: 고려 시대의 수도는 어디였나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-004 (turn 0)

- 질문: x의 제곱을 미분하면 무엇인가요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-006 (turn 0)

- 질문: 주말에 볼 만한 영화 추천해줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-007 (turn 0)

- 질문: 피아노를 혼자 조율하는 법을 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-008 (turn 0)

- 질문: 베란다 화분에 물을 얼마나 자주 줘야 해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-009 (turn 0)

- 질문: 고양이 사료를 하루에 얼마나 줘야 할까?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-012 (turn 0)

- 질문: 야간 사진을 찍을 때 셔터 속도는 어떻게 잡아?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-013 (turn 0)

- 질문: 마라톤 준비를 위한 러닝 계획을 짜줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-014 (turn 0)

- 질문: 최근 지진이 발생한 지역을 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-015 (turn 0)

- 질문: 게이밍 노트북 GPU는 어떻게 골라?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-016 (turn 0)

- 질문: 장거리 비행기 좌석은 어디가 편해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-017 (turn 0)

- 질문: 아파트 전세 계약할 때 주의할 점은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-018 (turn 0)

- 질문: 주식 매도 시점은 어떻게 정하면 좋아?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-019 (turn 0)

- 질문: 한국사 시험 공부 순서를 추천해줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-020 (turn 0)

- 질문: 체스에서 퀸을 잘 활용하는 방법은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-OUT-020","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-021 (turn 0)

- 질문: 북극성과 다른 별을 구분하는 방법은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-022 (turn 0)

- 질문: 파스타 면은 보통 몇 분 삶아야 해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-023 (turn 0)

- 질문: 조선 세종대왕의 즉위 연도는 언제야?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-025 (turn 0)

- 질문: 올해 프로농구 우승팀은 누구야?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-026 (turn 0)

- 질문: 수채화 물감의 번짐을 줄이는 방법은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-027 (turn 0)

- 질문: 기타 줄을 새로 교체하는 순서를 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-028 (turn 0)

- 질문: 실내 바질 잎이 노랗게 변하는 이유는?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-029 (turn 0)

- 질문: 강아지 산책은 하루에 몇 번 하는 게 좋아?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-030 (turn 0)

- 질문: 세계에서 가장 높은 산의 높이는 얼마야?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-031 (turn 0)

- 질문: 독일어로 감사합니다를 어떻게 말해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-032 (turn 0)

- 질문: 카메라 조리개 값은 뭘 뜻하나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-033 (turn 0)

- 질문: 등산화는 발에 어떻게 맞춰 골라야 해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-035 (turn 0)

- 질문: 가정용 커피 원두는 어떻게 보관해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-036 (turn 0)

- 질문: 해외여행 여권 유효기간은 얼마나 남아야 해?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-OUT-036","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-037 (turn 0)

- 질문: 집에서 벽지 얼룩을 지우는 방법은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-038 (turn 0)

- 질문: 채권 가격과 금리의 관계를 설명해줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-039 (turn 0)

- 질문: 영어 듣기 공부를 매일 어떻게 하면 좋아?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-040 (turn 0)

- 질문: 바둑에서 포석을 연습하는 방법을 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-041 (turn 0)

- 질문: 달 표면의 중력은 지구와 얼마나 다른가요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-042 (turn 0)

- 질문: 떡볶이 양념을 덜 맵게 만드는 방법은?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-043 (turn 0)

- 질문: 로마 제국의 수도는 어디였나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-044 (turn 0)

- 질문: 원기둥의 부피는 어떻게 구하나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-045 (turn 0)

- 질문: 배구 경기의 세트 승리 조건은 뭐예요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-046 (turn 0)

- 질문: 붓글씨를 처음 배울 때 필요한 도구는?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-047 (turn 0)

- 질문: 드럼 스틱 잡는 방법을 알려줘
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-048 (turn 0)

- 질문: 장미 화분의 가지치기는 언제 하나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-049 (turn 0)

- 질문: 반려견 목욕 주기는 어느 정도가 좋아요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-OUT-049","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-OUT-050 (turn 0)

- 질문: 아마존강의 길이는 얼마나 되나요?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0001 (turn 0)

- 질문: 데이터 많이 주는 5G 요금제는 몇 개나 있어요?
- 기대 동작: ANSWER
- 실제 답변: 데이터 많이 제공하는 5G 요금제는 총 4종이 있습니다. 라이트, 스탠다드, 프리미엄, 언리미티드 요금제가 해당됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `PLAN-0001`: 5G 요금제는 라이트·스탠다드·프리미엄·언리미티드 4종입니다. LTE는 3종, 알뜰은 2종이 더 있습니다.
  - `PLAN-0091`: 크게 세 갈래로 보시면 됩니다. 5G 요금제 4종(라이트·스탠다드·프리미엄·언리미티드), LTE 요금제 3종(베이직·플러스·맥스), 알뜰 요금제 2종(세이브·미니)을 운영하고 있습니다.
  - `PLAN-0101`: 5G 4종, LTE 3종, 알뜰 2종 중에서 기본 제공 데이터가 더 큰 상위 요금제로 변경하시면 됩니다. 상세 제공량은 매장이나 앱에서 비교해 보실 수 있습니다.

## PIPE-PLAN-0003 (turn 0)

- 질문: 가족한테 데이터 한 번에 얼마까지 보낼 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 가족에게 한 번에 최대 2GB의 데이터를 보낼 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0003`: 한 번에 최대 2GB, 한 달에 2회까지 보낼 수 있습니다.
  - `PLAN-0063`: 월 2회까지만 가능합니다. 회당 최대 2GB입니다.
  - `PLAN-0103`: 데이터 선물은 한 달에 2회, 회당 최대 2GB까지 가능합니다. 이번 달에 이미 2회를 보내셨다면 다음 달까지 기다리셔야 합니다.

## PIPE-PLAN-0017 (turn 0)

- 질문: 소진 후 속도 제한 값이 요금제마다 다른가요?
- 기대 동작: ANSWER
- 실제 답변: 네, 소진 후 속도 제한 값은 요금제에 따라 다릅니다. 일반 요금제의 경우 소진 후 속도가 400kbps로 제한되지만, 언리미티드 요금제에서는 데이터 소진 후에도 속도 제한이 없습니다.

- qwen: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0017`: 일반 요금제는 모두 400kbps로 같습니다. 언리미티드만 제한이 없습니다.
  - `PLAN-0042`: 언리미티드 요금제로 변경하시면 소진 후에도 속도 제한 없이 이용하실 수 있습니다. 일반 요금제는 소진 후 400kbps로 제한됩니다.

## PIPE-PLAN-0022 (turn 0)

- 질문: 데이터 다 쓰면 아예 안 되나요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0036 (turn 0)

- 질문: 나이 바뀌어서 요금제 바꾸려는데 어떤 게 있는지 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 나이가 변경되어 시니어 요금제 자격이 되셨다면, 만 65세 이상을 증명할 수 있는 신분증을 준비하시고 매장이나 고객센터를 통해 시니어 요금제로 변경 신청을 하실 수 있습니다. 다른 요금제로의 변경도 필요하시다면, 현재 나이와 상황에 맞는 요금제를 확인해 보시는 것이 좋습니다. 자세한 내용은 고객센터에 문의해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PLAN-0114`: 시니어 요금제는 만 65세 이상만 가입할 수 있어 나이가 안 되시면 변경이 어렵습니다. 신분증으로 확인이 필요합니다.
  - `PLAN-0044`: 생년월일 확인이 가능한 신분증을 지참해 매장이나 고객센터에 변경을 신청하시면 됩니다.
  - `PLAN-0049`: 청소년 요금제는 만 18세 이하 대상입니다. 일반 요금제로 바꾸시려면 매장이나 앱에서 변경 신청을 하시면 됩니다.

## PIPE-PLAN-0038 (turn 0)

- 질문: 데이터 선물은 어떻게 보내요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0047 (turn 0)

- 질문: 가족 회선의 속도 제한 상태를 확인하는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 가족 회선의 속도 제한 상태를 확인하려면 먼저 앱을 통해 해당 회선의 잔여 데이터 사용량을 확인해 보세요. 만약 기본 데이터 사용량을 모두 사용한 경우, 속도가 400kbps로 제한된 상태임을 알 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0047`: 앱에서 회선별 잔여 데이터를 확인합니다. 기본 데이터를 다 쓴 회선은 400kbps로 제한된 상태입니다.

## PIPE-PLAN-0050 (turn 0)

- 질문: 예산 5만 원 안에서 요금제 고르는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 필요한 데이터량에 맞게 선택하시면 예산 내에서 최적의 서비스를 이용하실 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0050`: 월정액 기준으로 알뜰 미니(15,000원), 알뜰 세이브(25,000원), LTE 베이직(33,000원), 5G 라이트(45,000원), LTE 플러스(49,000원)가 해당됩니다. 필요한 데이터양으로 최종 결정하시면 됩니다.

## PIPE-PLAN-0058 (turn 0)

- 질문: 남은 데이터 손주한테 어떻게 줘요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0060 (turn 0)

- 질문: 요금제 뭐가 있는지 어떻게 알아요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-PLAN-0060","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0066 (turn 0)

- 질문: 알뜰 요금제로 5G를 쓸 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 네, 알뜰 요금제로도 5G 서비스를 이용하실 수 있습니다. 현재 세이브와 미니 두 가지 알뜰 요금제 중에서 5G를 지원하는 요금제가 별도로 4종이 준비되어 있습니다. 어떤 요금제가 적합하신지 자세히 알고 싶으시다면, 자세한 조건과 혜택을 확인해 보시는 것을 추천드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0066`: 알뜰 요금제는 세이브·미니 2종이고, 5G 요금제는 별도로 4종이 있습니다.

## PIPE-PLAN-0069 (turn 0)

- 질문: 데이터 많이 쓰는 청소년도 청소년 요금제 되나요?
- 기대 동작: ANSWER
- 실제 답변: 네, 만 18세 이하의 청소년이면 데이터를 많이 써도 청소년 요금제에 가입할 수 있습니다. 가입을 위해서는 신분증을 통해 생년월일을 확인해야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0129`: 청소년 요금제는 만 18세 이하만 가능합니다. 나이가 되면 두 가지를 비교해 보시고, 신분증으로 나이 확인이 필요합니다.
  - `PLAN-0109`: 청소년 요금제는 만 18세 이하 대상입니다. 데이터가 부족하면 기본 제공량이 더 큰 요금제로 변경을 검토해 보세요.
  - `PLAN-0069`: 만 18세 이하면 가입할 수 있습니다. 신분증으로 생년월일 확인이 필요합니다.

## PIPE-PLAN-0072 (turn 0)

- 질문: 언리미티드로 바꾸면 속도 제한이 아예 없어지나요?
- 기대 동작: ANSWER
- 실제 답변: 네, 언리미티드 요금제로 변경하시면 속도 제한이 적용되지 않습니다. 기본 제공량에 따른 속도 제한이 없어지므로 무제한으로 이용 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0072`: 네, 언리미티드 요금제는 기본 제공량 개념이 없어 속도 제한 자체가 적용되지 않습니다.

## PIPE-PLAN-0081 (turn 0)

- 질문: 데이터 조금 쓰는데 싼 거 있나요
- 기대 동작: ANSWER
- 실제 답변: 데이터를 조금씩 사용하시는 경우 알뜰 요금제 중 세이브나 미니 요금제를 고려해 보시는 것이 좋습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-PLAN-0081","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `PLAN-0081`: 알뜰 요금제가 2가지 있습니다. 세이브랑 미니예요.

## PIPE-PLAN-0092 (turn 0)

- 질문: 데이터가 갑자기 느려졌어요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0103 (turn 0)

- 질문: 가족한테 데이터를 나눠주고 싶은데 한도를 넘겼는지 확인이 안 됩니다
- 기대 동작: ANSWER
- 실제 답변: 데이터 선물은 한 달에 최대 2회, 회당 2GB까지 가능합니다. 이미 이번 달에 2회 데이터 선물을 하셨다면 한도를 넘으셨으니 다음 달을 기다리셔야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PLAN-0103`: 데이터 선물은 한 달에 2회, 회당 최대 2GB까지 가능합니다. 이번 달에 이미 2회를 보내셨다면 다음 달까지 기다리셔야 합니다.

## PIPE-PLAN-0108 (turn 0)

- 질문: 성인이 되면서 요금제를 바꿨더니 이월 데이터가 줄었습니다
- 기대 동작: ANSWER
- 실제 답변: 성인이 되면서 요금제를 변경하셨군요. 이월 데이터는 변경된 요금제 기준으로 잔여 데이터의 50%까지만 다음 달로 이어집니다. 따라서 요금제 변경으로 인해 이월 데이터가 줄어든 것으로 보입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0108`: 이월은 잔여량의 50%까지입니다. 남은 양 전부가 넘어가지는 않습니다.
  - `PLAN-0068`: 이월은 요금제 기준으로 잔여량의 50%까지 다음 달로 넘어갑니다.

## PIPE-PLAN-0115 (turn 0)

- 질문: 요금이 너무 많이 나와서 낮은 요금제로 바꾸고 싶어요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0121 (turn 0)

- 질문: 5G랑 LTE랑 뭐가 달라요?
- 기대 동작: ANSWER
- 실제 답변: 5G와 LTE는 속도와 기술적 차이가 있습니다. - **지연 시간**: 5G는 저지연 특성을 갖추고 있어 실시간 응용 프로그램에 더 적합합니다. - **연결 밀도**: 5G는 더 많은 기기를 동시에 연결할 수 있는 능력이 있습니다. 요금제 구성도 다릅니다: 5G는 라이트, 스탠다드, 프리미엄, 언리미티드 등 4가지 종류가 있으며, LTE는 베이직, 플러스, 맥스 등 3가지 종류로 나뉩니다. 추가로 알뜰 요금제도 선택 가능합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0121`: 5G는 라이트·스탠다드·프리미엄·언리미티드 4종, LTE는 베이직·플러스·맥스 3종으로 구성이 다릅니다. 알뜰 요금제 2종도 별도로 있습니다.

## PIPE-PLAN-0127 (turn 0)

- 질문: 가족 중 언리미티드 쓰는 사람이랑 아닌 사람이랑 뭐가 달라요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PLAN-0136 (turn 0)

- 질문: 연령 제한 요금제와 일반 라인업의 종류 수 차이를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 연령 제한 요금제는 별도의 정책으로 운영되고 있어 정확한 종류 수가 일반 라인업과 비교해 명시되어 있지 않습니다. 일반 라인업의 경우 5G 4종, LTE 3종, 알뜰폰 2종으로 구성되어 있습니다. 연령 제한 요금제의 구체적인 종류 수에 대해서는 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PLAN-0136`: 일반 라인업은 5G 4종, LTE 3종, 알뜰 2종입니다. 연령 제한 요금제는 별도 정책으로 운영됩니다.

## PIPE-PLAN-0143 (turn 0)

- 질문: 손주한테 주는 거랑 넘기는 거랑 뭐가 나아요
- 기대 동작: ANSWER
- 실제 답변: 손주에게 데이터를 제공하는 방법으로는 '넘기기'와 '주기'가 있습니다. 반면 주기 방식은 한 달에 최대 2번, 각 2GB씩 데이터를 제공할 수 있습니다. 어떤 방법이 더 적합한지는 손주 사용 패턴과 필요 데이터량에 따라 달라질 수 있으니, 상황에 맞게 선택하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PLAN-0143`: 넘기는 건 절반(50%)이 자동으로 넘어가고, 주는 건 한 달에 2번 2GB씩 줄 수 있습니다.

## PIPE-PORTING-0001 (turn 0)

- 질문: 번호이동 신청하면 얼마나 걸려요?
- 기대 동작: ANSWER
- 실제 답변: 번호이동 신청은 평균 2시간 이내에 완료되며, 늦어도 24시간 안에는 처리됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0025`: 신분증을 가지고 신청하시면 09:00~20:00 사이에 처리됩니다. 보통 2시간 이내, 늦어도 24시간 안에 완료됩니다.
  - `PORTING-0037`: 09:00~20:00 사이에 신청하면 평균 2시간 이내에 완료됩니다. 최대 24시간까지 걸릴 수 있습니다.
  - `PORTING-0001`: 평균 2시간 이내에 완료되고, 늦어도 24시간 안에는 처리됩니다.

## PIPE-PORTING-0015 (turn 0)

- 질문: 번호이동 제한 기간 기준일이 개통일인가요, 명의변경일인가요?
- 기대 동작: ANSWER
- 실제 답변: 번호이동 제한 기간 기준일은 개통일과 명의변경일 모두에 따라 달라집니다. 개통일로부터 3개월이 경과해야 하며, 만약 명의변경을 진행하셨다면 명의변경일로부터 1개월이 지나야 번호이동이 가능합니다. 두 날짜 중 더 늦은 시점을 기준으로 제한 기간이 적용됩니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0015`: 둘 다 봅니다. 개통일로부터 3개월, 명의변경을 했다면 변경일로부터 1개월이 지나야 합니다.
  - `PORTING-0087`: 제한은 개통일 기준 3개월, 명의변경일 기준 1개월입니다. 두 날짜를 확인해 보세요.
  - `PORTING-0095`: 명의변경을 안 하셨다면 개통 후 3개월이 기준이고, 명의변경을 하셨다면 변경일로부터 1개월이 기준입니다.

## PIPE-PORTING-0023 (turn 0)

- 질문: 명의 바꾸고 바로 옮길 수 있어요
- 기대 동작: ANSWER
- 실제 답변: 명의를 바꾸신 후 바로 이동이 가능한 것이 아니라, 변경 후 1개월이 경과해야 이동이 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0023`: 명의 바꾸고 1개월 지나야 됩니다.

## PIPE-PORTING-0029 (turn 0)

- 질문: 번호이동 신청하면 그다음에 뭘 해야 돼요?
- 기대 동작: ANSWER
- 실제 답변: 번호이동 신청 후에는 기존 통신사에 미납 요금이 없는지 확인하시고, 만약 미납 요금이 있다면 먼저 완납하셔야 합니다. 이후 새로운 통신사의 안내에 따라 필요한 절차를 진행하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0026`: 본인 신분증을 지참해 신청하시면 됩니다. 기존 통신사에 미납 요금이 있으면 먼저 완납하셔야 진행됩니다.
  - `PORTING-0030`: 기존 통신사 미납 요금이 없는지 먼저 확인하고, 본인 신분증을 지참해 번호이동을 신청하시면 됩니다.
  - `PORTING-0042`: 신분증만 있으면 됩니다. 밀린 요금이 있으면 먼저 내셔야 합니다.

## PIPE-PORTING-0032 (turn 0)

- 질문: 옮긴 걸 없던 일로 하려면 누구한테 말해요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PORTING-0033 (turn 0)

- 질문: 번호이동 처리 절차와 소요 시간을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 번호이동 처리는 매일 오전 9시부터 오후 8시까지 가능하며, 신청 후 평균 2시간 이내에 완료됩니다. 늦어지더라도 최대 24시간 이내에 처리될 예정입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0033`: 매일 09:00부터 20:00까지 접수하시면 되고, 접수 후 평균 2시간 이내, 늦어도 24시간 이내에 처리됩니다.
  - `PORTING-0025`: 신분증을 가지고 신청하시면 09:00~20:00 사이에 처리됩니다. 보통 2시간 이내, 늦어도 24시간 안에 완료됩니다.
  - `PORTING-0037`: 09:00~20:00 사이에 신청하면 평균 2시간 이내에 완료됩니다. 최대 24시간까지 걸릴 수 있습니다.

## PIPE-PORTING-0044 (turn 0)

- 질문: 옮긴 거 무르려면 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PORTING-0048 (turn 0)

- 질문: 옮겼는데 다시 돌아가려면 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-PORTING-0049 (turn 0)

- 질문: 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요?
- 기대 동작: ANSWER
- 실제 답변: 저녁 늦게 신청하시더라도 번호 이동 처리 시간은 매일 오후 9시부터 저녁 8시까지 운영됩니다. 신청하신 경우 평균 2시간 이내에 완료되며, 늦어도 하루 이내에 처리될 예정입니다. 다만, 처리 시간을 고려하시어 가능한 한 저녁 시간대 이전에 신청하시는 것을 권장드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0049`: 번호이동 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안에 신청하시면 평균 2시간 이내에 완료되고, 늦어도 24시간 이내에 처리됩니다.

## PIPE-PORTING-0052 (turn 0)

- 질문: 이동한 지 일주일 됐는데 취소할 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 네, 개통 후 14일 이내이므로 취소하실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0052`: 네, 개통 후 14일 이내이므로 철회하실 수 있습니다.

## PIPE-PORTING-0053 (turn 0)

- 질문: 밤에 신청해도 다음 날 아침에 바로 될까요?
- 기대 동작: ANSWER
- 실제 답변: 밤에 신청하시더라도 처리는 다음 날 오전 9시부터 시작되므로, 당일 완료보다는 다음 날 아침에 완료될 가능성이 높습니다. 평균 처리 시간은 약 2시간 이내입니다.

- qwen: `{"grounding":"SUPPORTED","quality":null,"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0053`: 처리는 09:00부터 시작됩니다. 처리 시작 후 평균 2시간 이내에 완료됩니다.
  - `PORTING-0097`: 처리는 09:00부터 20:00까지라 아침 신청이 당일에 끝날 가능성이 큽니다. 평균 2시간 이내 처리입니다.

## PIPE-PORTING-0060 (turn 0)

- 질문: 철회 후 원래 통신사 요금제도 그대로 복원되나요?
- 기대 동작: ANSWER
- 실제 답변: 네, 철회 후에는 원래 통신사의 요금제가 자동으로 복원됩니다. 하지만 철회는 개통 후 14일 이내에만 가능합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0060`: 회선은 원래 통신사로 자동 복원됩니다. 철회는 개통 후 14일 이내에 가능합니다.

## PIPE-PORTING-0065 (turn 0)

- 질문: 토요일에도 번호이동 되나요
- 기대 동작: ANSWER
- 실제 답변: 네, 토요일에도 번호이동이 가능합니다. 매일 오전 9시부터 오후 8시까지 운영되고 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0065`: 네, 매일 09:00부터 20:00까지 처리됩니다.

## PIPE-PORTING-0071 (turn 0)

- 질문: 개통 두 달인데 되나요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0071`: 안 됩니다. 3개월이 지나야 됩니다.

## PIPE-PORTING-0077 (turn 0)

- 질문: 신청한 지 하루가 넘었는데 아직도 안 됐어요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-PORTING-0077","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `ROAMING-0062`: 신청하면 1시간 안에 됩니다. 조금만 기다려 주세요.

## PIPE-PORTING-0078 (turn 0)

- 질문: 결합 해지했는데 번호이동이 막혔어요
- 기대 동작: ANSWER
- 실제 답변: 기존 통신사의 미납 요금이 모두 정산되었는지 확인 부탁드립니다. 요금을 완납하신 후 본인의 신분증을 지참하여 다시 번호 이동을 신청해 주시면 됩니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0078`: 기존 통신사 미납 요금이 남아 있는지 확인해 주세요. 완납 후 본인 신분증으로 다시 신청하시면 됩니다.

## PIPE-PORTING-0081 (turn 0)

- 질문: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다
- 기대 동작: ANSWER
- 실제 답변: 신청하신 작업이 평균적으로 2시간 내에 완료되지만, 최대 24시간까지 소요될 수 있습니다. 현재 시간이 점심 이후이므로, 아직 완료되지 않은 경우 정상적인 범위 내에 있습니다. 하지만 24시간이 경과해도 완료되지 않는다면 고객센터로 연락주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-PORTING-0081","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `PORTING-0081`: 평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.

## PIPE-PORTING-0085 (turn 0)

- 질문: 일요일 오후에 신청했는데 진행이 안 됩니다
- 기대 동작: ANSWER
- 실제 답변: 일요일 오후에 신청하셨더라도 처리는 평일 오전 9시부터 오후 8시까지 운영됩니다. 현재 시간이 처리 시간 외라면 진행이 일시적으로 중단될 수 있습니다. 평일로 재개되면 평균 2시간 이내에 처리될 예정입니다. 불편을 드려 죄송합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0085`: 처리는 매일 09:00부터 20:00까지만 진행됩니다. 처리 시간 안이라면 평균 2시간 이내에 완료됩니다.

## PIPE-PORTING-0087 (turn 0)

- 질문: 기기변경한 지 얼마 안 됐다고 번호이동이 안 된답니다
- 기대 동작: ANSWER
- 실제 답변: 기기변경 후 번호이동이 제한되는 기간은 개통일 기준으로 3개월입니다. 현재 기간이 이 기준을 충족하지 못하고 있으니, 3개월이 경과하신 후에 다시 시도해 주시기 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0087`: 제한은 개통일 기준 3개월, 명의변경일 기준 1개월입니다. 두 날짜를 확인해 보세요.
  - `PORTING-0091`: 개통한 지 3개월이 안 돼서 그렇습니다. 3개월 지나면 됩니다.
  - `PORTING-0079`: 명의변경일로부터 1개월이 지나야 번호이동이 가능합니다.

## PIPE-PORTING-0092 (turn 0)

- 질문: 옮겼는데 전화가 잘 안 돼요
- 기대 동작: ANSWER
- 실제 답변: 전화 연결에 문제가 있으시군요. 이동 후 14일 이내라면 서비스 변경을 취소하실 수 있습니다. 원래 통신사로 다시 돌아갈 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `PORTING-0092`: 14일 안이면 무를 수 있습니다. 원래 통신사로 돌아갑니다.

## PIPE-PORTING-0097 (turn 0)

- 질문: 아침에 신청하는 거랑 저녁에 신청하는 거랑 뭐가 빨라요?
- 기대 동작: ANSWER
- 실제 답변: 아침에 신청하시는 것이 보통 당일에 처리가 완료될 가능성이 높습니다. 평균적으로 2시간 이내에 처리되므로, 아침 신청이 더 빠를 것으로 예상됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0097`: 처리는 09:00부터 20:00까지라 아침 신청이 당일에 끝날 가능성이 큽니다. 평균 2시간 이내 처리입니다.

## PIPE-PORTING-0107 (turn 0)

- 질문: 개통 후 3개월과 명의변경 후 1개월 중 어느 조건이 더 자주 걸리나요?
- 기대 동작: ANSWER
- 실제 답변: 명의변경 후 1개월 조건이 추가로 적용되는 경우에만 해당 조건이 발생하므로, 일반적인 경우 개통 후 3개월 조건만 확인하시면 됩니다. 따라서 대부분의 경우 개통 후 3개월 조건이 더 자주 적용됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `PORTING-0107`: 명의변경을 한 경우에만 1개월 조건이 추가로 붙습니다. 대부분은 개통 후 3개월 조건만 확인하시면 됩니다.
  - `PORTING-0103`: 명의변경 기준은 1개월, 개통 기준은 3개월이라 명의변경 기준이 더 짧습니다.
  - `PORTING-0099`: 둘 다 지나야 합니다. 개통 후 3개월과 명의변경 후 1개월 중 늦게 끝나는 쪽까지 기다리셔야 합니다.

## PIPE-PORTING-0113 (turn 0)

- 질문: 빨리 되는 날이랑 늦는 날이랑 있어요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0022 (turn 0)

- 질문: 로밍은 어떻게 신청해요?
- 기대 동작: ANSWER
- 실제 답변: 출국 전에 신청하시는 것이 좋지만, 현지 도착 후에도 신청하실 수 있습니다. 신청 후에는 약 1시간 이내에 로밍 서비스가 적용됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":null}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-ROAMING-0022","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `ROAMING-0022`: 홈페이지나 고객센터에서 신청하시면 됩니다. 출국 전에 신청하시는 걸 권장드리지만, 현지 도착 후에도 신청하실 수 있습니다.
  - `ROAMING-0032`: 홈페이지나 고객센터에서 하시면 됩니다. 신청하고 1시간 안에 적용됩니다.
  - `ROAMING-0027`: 고객센터에 전화해 로밍 요금제와 이용 기간을 말씀하시면 접수됩니다. 접수 후 1시간 이내에 적용되며, 홈페이지에서 직접 신청하셔도 됩니다.

## PIPE-ROAMING-0025 (turn 0)

- 질문: 현지 도착 후 로밍 설정 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 자동으로 현지 통신사 네트워크에 연결되기까지 약 3~5분 정도 소요될 예정입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0025`: 단말에서 데이터 로밍을 켜시면 3~5분 안에 현지 망에 자동으로 연결됩니다. 가입 상태는 앱이나 홈페이지에서 미리 확인하실 수 있습니다.
  - `ROAMING-0080`: 먼저 앱이나 홈페이지에서 가입 상태를 확인하시고, 현지에 도착하면 단말에서 데이터 로밍을 켜시면 됩니다. 연결까지 3~5분 걸립니다.
  - `ROAMING-0015`: 현지에 도착하시면 단말에서 데이터 로밍을 켜셔야 합니다. 자동으로 연결되기까지 3~5분 걸립니다.

## PIPE-ROAMING-0027 (turn 0)

- 질문: 고객센터 전화로 로밍을 신청하는 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 고객센터에 전화하셔서 로밍 요금제와 이용 기간을 알려주시면 로밍 신청이 접수됩니다. 접수 후 약 1시간 이내에 로밍 서비스가 자동으로 적용됩니다. 추가로 홈페이지에서도 직접 로밍을 신청하실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0027`: 고객센터에 전화해 로밍 요금제와 이용 기간을 말씀하시면 접수됩니다. 접수 후 1시간 이내에 적용되며, 홈페이지에서 직접 신청하셔도 됩니다.
  - `ROAMING-0032`: 홈페이지나 고객센터에서 하시면 됩니다. 신청하고 1시간 안에 적용됩니다.
  - `ROAMING-0022`: 홈페이지나 고객센터에서 신청하시면 됩니다. 출국 전에 신청하시는 걸 권장드리지만, 현지 도착 후에도 신청하실 수 있습니다.

## PIPE-ROAMING-0030 (turn 0)

- 질문: 로밍이 안 잡힐 때 확인 순서를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 로밍 서비스가 제대로 작동하지 않을 때 확인해 보실 순서는 다음과 같습니다: 1. **앱 또는 홈페이지**에서 현재 로밍 가입 상태가 활성화되어 있는지 확인해 주세요. 2. **단말기 설정**에서 데이터 로밍 기능이 켜져 있는지 확인하세요. 3. 자동 연결 설정까지 약 3~5분 정도 소요될 수 있으니 잠시 기다려 주세요. 4. 문제가 지속된다면 **기기 재부팅** 후 다시 시도해 보세요. 이 단계들을 따라 확인해 보시면 도움이 될 것입니다.

- qwen: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0030`: 먼저 앱이나 홈페이지에서 가입 상태를 확인하고, 단말의 데이터 로밍이 켜져 있는지 보세요. 자동 연결까지 3~5분이 걸리며, 그래도 안 되면 재부팅 후 다시 시도합니다.

## PIPE-ROAMING-0031 (turn 0)

- 질문: 오래 나가 있을 건데 로밍은 뭘로 해요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0032 (turn 0)

- 질문: 로밍 신청은 어디서 해요
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-ROAMING-0032","turnIndex":0,"pipelineFailure":"MISROUTED_CLARIFICATION","grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":"NOT_APPLICABLE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0034 (turn 0)

- 질문: 손목시계도 로밍 신청해요
- 기대 동작: ANSWER
- 실제 답변: 손목시계도 휴대폰과 별도로 로밍 신청이 필요합니다. 워치 전용 로밍 서비스를 확인해 주세요.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0034`: 네, 워치는 휴대폰과 다른 회선이라 따로 신청하셔야 합니다.

## PIPE-ROAMING-0046 (turn 0)

- 질문: 일주일 여행인데 무제한으로 할 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["REVIEW"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0053 (turn 0)

- 질문: 현지에서 영상 보다가 갑자기 인터넷만 안 되고 전화는 돼요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0062 (turn 0)

- 질문: 신청했는데 아직 안 돼요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0064 (turn 0)

- 질문: 스마트워치 샀는데 인터넷이 안 잡혀요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-ROAMING-0071 (turn 0)

- 질문: 한 달 정도 있을 예정인데 일 단위랑 무제한 중 뭐가 유리한가요?
- 기대 동작: ANSWER
- 실제 답변: 데이터 사용량이 많지 않으실 것 같다면 일 단위 데이터 요금제(9,900원)가 경제적입니다. 무제한 요금제는 12,100원으로 더 비싸지만 데이터 제한 없이 사용하고 싶으시다면 고려해볼 만합니다. 한 달 동안의 사용 패턴을 고려해 선택하시는 것이 좋겠습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0071`: 일 단위는 9,900원, 무제한은 12,100원으로 무제한이 더 비쌉니다. 데이터를 많이 쓰실 계획이 아니면 일 단위가 유리합니다.

## PIPE-ROAMING-0074 (turn 0)

- 질문: 워치와 태블릿 로밍은 각각 따로 신청해야 하나요, 하나로 묶이나요?
- 기대 동작: ANSWER
- 실제 답변: 워치와 태블릿은 각각 별개의 회선으로 운영되므로, 로밍을 위해서는 각각 따로 신청하셔야 합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0074`: 각각 별개 회선이라 따로 신청하셔야 합니다.
  - `ROAMING-0069`: 아닙니다. 워치는 별개 회선이라 로밍을 따로 신청하셔야 합니다.
  - `ROAMING-0034`: 네, 워치는 휴대폰과 다른 회선이라 따로 신청하셔야 합니다.

## PIPE-ROAMING-0077 (turn 0)

- 질문: 집에서 신청하는 거랑 공항에서 신청하는 거랑 달라요
- 기대 동작: ANSWER
- 실제 답변: 집에서 신청하시든 공항에서 신청하시든 신청 완료까지 걸리는 시간은 동일하게 1시간 이내입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `ROAMING-0077`: 똑같이 1시간 안에 됩니다. 집에서 미리 하시는 게 편합니다.

## PIPE-SERVICE-0003 (turn 0)

- 질문: 매장 문 여는 시간이 몇 시예요?
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0008 (turn 0)

- 질문: 직영 매장 토요일 영업시간을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 현재 가까운 매장 정보를 바로 확인하기 어려워요. 잠시 후 다시 시도하거나 고객센터를 이용해 주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0013 (turn 0)

- 질문: 매장 몇 시까지 해요
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0014 (turn 0)

- 질문: 신고하면 확인하는 데 며칠 걸려요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0016 (turn 0)

- 질문: 부가서비스 가입이랑 해지는 어떻게 해요?
- 기대 동작: ANSWER
- 실제 답변: 부가서비스 가입은 신청하시면 바로 적용됩니다. 반면에 해지는 신청하신 달의 말일까지 해당 서비스를 이용하실 수 있으며, 해지 신청 후에도 그 달의 이용료는 일할 계산 없이 전액 청구됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":null}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0016`: 가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.
  - `SERVICE-0056`: 가입은 신청 즉시 적용되고, 해지는 그 달 말일까지 이용하면서 월 이용료가 일할 계산 없이 전액 청구됩니다.
  - `SERVICE-0041`: 네, 가입은 신청하시면 즉시 적용됩니다.

## PIPE-SERVICE-0019 (turn 0)

- 질문: 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 명의도용으로 의심되는 회선에 대해 신고를 원하시는군요. 고객센터로 직접 연락하시어 명의도용 신고를 접수하시면 됩니다. 접수 후에는 3영업일 이내에 조사가 진행되며, 도용이 확인되면 해당 요금은 전액 취소될 예정입니다. 신고 방법에 대한 자세한 안내는 고객센터로 문의해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0019`: 고객센터로 명의도용 신고를 접수하시면 됩니다. 접수 후 3영업일 이내에 조사가 진행되고, 도용으로 확인되면 해당 요금은 전액 취소됩니다.

## PIPE-SERVICE-0020 (turn 0)

- 질문: 증명서는 어떻게 발급받아요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0026 (turn 0)

- 질문: 부가서비스 없애려면 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 부가서비스를 없애려면 신청 절차를 진행하시면 됩니다. 변경 신청 후 그 달 말까지는 기존 부가서비스를 계속 이용하실 수 있으며, 요금에도 변화가 없습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `SERVICE-0026`: 신청만 하시면 됩니다. 그 달 말일까지는 계속 쓰실 수 있고, 요금도 그대로 나갑니다.

## PIPE-SERVICE-0033 (turn 0)

- 질문: 평일 저녁 7시 넘어서 매장에 갈 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0038 (turn 0)

- 질문: 공휴일에도 매장 열어요?
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0041 (turn 0)

- 질문: 부가서비스 해지하고 다시 가입할 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 네, 부가서비스를 해지한 후 다시 가입하실 수 있습니다. 신청하시면 즉시 새로운 부가서비스가 적용됩니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0041`: 네, 가입은 신청하시면 즉시 적용됩니다.
  - `SERVICE-0016`: 가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.

## PIPE-SERVICE-0043 (turn 0)

- 질문: 일요일에 갔는데 매장이 닫혀 있었어요
- 기대 동작: ANSWER
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0044 (turn 0)

- 질문: 신고했는데 며칠째 연락이 없어요
- 기대 동작: ANSWER
- 실제 답변: 명의도용 조사는 접수 후 일반적으로 3영업일 이내에 진행됩니다. 주말과 공휴일은 포함되지 않으니, 그 기간이 경과했다면 조금 더 기다려 주시기 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-SERVICE-0044","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":"SHOULD_ABSTAIN","answerability":"ENOUGH"}`
- FAQ 근거:
  - `SERVICE-0044`: 명의도용 조사는 접수 후 3영업일 이내에 진행됩니다. 영업일 기준이라 주말과 공휴일은 빠지니 그 안이라면 조금 더 기다려 주세요.

## PIPE-SERVICE-0046 (turn 0)

- 질문: 해지 신청했는데 아직도 서비스가 이용되고 있어요
- 기대 동작: ANSWER
- 실제 답변: 해지 신청을 하셨더라도 신청일이 속한 달의 말일까지는 서비스 이용이 계속됩니다. 정상적인 절차이므로 걱정하지 않으셔도 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0046`: 정상입니다. 해지를 신청하셔도 신청일이 속한 달 말일까지는 계속 이용하실 수 있습니다.

## PIPE-SERVICE-0049 (turn 0)

- 질문: 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다
- 기대 동작: ANSWER
- 실제 답변: 도용이 확인되기 전까지는 청구서에서 해당 요금이 취소되지 않을 수 있습니다. 접수 후 3영업일 이내에 조사가 진행되므로, 그 결과를 확인해 주시기 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-SERVICE-0049","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `SERVICE-0049`: 조사에서 도용이 확인되어야 요금이 전액 취소됩니다. 접수 후 3영업일 이내에 조사가 진행되니 결과를 확인해 주세요.

## PIPE-SERVICE-0051 (turn 0)

- 질문: 부가서비스 해지했는데 요금이 또 나왔어요
- 기대 동작: ANSWER
- 실제 답변: 해지한 달의 요금은 일할 계산되어 청구되므로, 해지 후에도 해당 달의 요금이 발생할 수 있습니다. 다음 달부터는 더 이상 요금이 청구되지 않을 것입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0051`: 해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.

## PIPE-SERVICE-0055 (turn 0)

- 질문: 증명서 종류마다 발급 방법이 달라요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0056 (turn 0)

- 질문: 부가서비스 가입할 때랑 해지할 때랑 요금 계산이 달라요?
- 기대 동작: ANSWER
- 실제 답변: 네, 맞습니다. 부가서비스 가입 시에는 신청 즉시 해당 서비스 요금이 적용됩니다. 반면에 해지 시에는 해지 신청일이 속한 달 말일까지 서비스를 이용하게 되며, 그 달의 요금은 일할 계산 없이 전액 청구됩니다. 따라서 가입과 해지 시 요금 청구 방식에 차이가 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SERVICE-0056`: 가입은 신청 즉시 적용되고, 해지는 그 달 말일까지 이용하면서 월 이용료가 일할 계산 없이 전액 청구됩니다.
  - `SERVICE-0016`: 가입은 신청하시면 바로 적용됩니다. 해지를 신청하시면 신청일이 속한 달 말일까지는 계속 이용하실 수 있고, 그 달 요금은 일할 계산 없이 그대로 청구됩니다.
  - `SERVICE-0036`: 네, 월 이용료는 일할 계산되지 않아 가입 당일 해지하셔도 그 달 요금은 전액 청구됩니다.

## PIPE-SERVICE-0063 (turn 0)

- 질문: 토요일에 가도 됩니까. 평일이랑 시간이 다른가요.
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SERVICE-0065 (turn 0)

- 질문: 홈페이지로 받는 거랑 매장에서 받는 거랑 증명서가 달라요
- 기대 동작: ANSWER
- 실제 답변: 홈페이지를 통해 증명서를 발급받으실 경우 즉시 무료로 받으실 수 있으며, 최근 3년분까지 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `SERVICE-0065`: 홈페이지에서 신청하시면 즉시 무료로 발급됩니다. 최근 3년분까지 가능합니다.

## PIPE-SUBSCRIBE-0004 (turn 0)

- 질문: 회사 이름으로 폰 몇 개까지 만들 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 법인 명의로는 별도의 심사를 거쳐 결정되므로 정확한 회선 수는 안내드리기 어렵습니다. 개인의 경우 최대 5회선까지 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0004`: 법인 명의는 별도 심사를 거쳐 정해집니다. 개인은 1인당 최대 5회선입니다.

## PIPE-SUBSCRIBE-0005 (turn 0)

- 질문: 두 번째 폰 만들 때도 신분증 가져가야 해요?
- 기대 동작: ANSWER
- 실제 답변: 네, 두 번째 폰을 만들 때도 본인 명의의 신분증(주민등록증, 운전면허증, 여권 등)을 가져가셔야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0005`: 네, 본인 명의 신분증(주민등록증, 운전면허증, 여권 중 하나)이 필요합니다.
  - `SUBSCRIBE-0085`: 유효한 본인 명의 신분증이 필요합니다. 운전면허증이나 여권 등 다른 유효한 신분증을 가져가시면 됩니다.

## PIPE-SUBSCRIBE-0012 (turn 0)

- 질문: 자녀 명의 회선을 포함해 성인 1인이 개통 가능한 회선 수를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 성인 1인이 본인 명의로 개통 가능한 회선 수는 최대 5회선입니다. 자녀 명의 회선은 이 제한 수에 포함되지 않습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0012`: 성인은 본인 명의로 최대 5회선까지 개통할 수 있습니다. 법인 명의는 별도 심사입니다.
  - `SUBSCRIBE-0072`: 본인 명의로 개통한 회선만 성인 1인 최대 5회선에 포함됩니다.
  - `SUBSCRIBE-0056`: 성인 1인당 최대 5회선까지 개통할 수 있어 그 한도 안이라면 가능합니다. 법인 명의로 하시는 경우에는 별도 심사를 거칩니다.

## PIPE-SUBSCRIBE-0015 (turn 0)

- 질문: 세컨드폰 매장 개통 소요 시간을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SUBSCRIBE-0019 (turn 0)

- 질문: 가입하면 언제부터 되나요
- 기대 동작: ANSWER
- 실제 답변: 가입하신 방법에 따라 다릅니다. 매장에서 가입하시면 가입 즉시 서비스 이용이 가능합니다. 인터넷을 통해 신청하신 경우에는 다음 날부터 서비스 이용이 가능하며, 신청 시간은 오전 9시부터 오후 8시까지입니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["REVIEW"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0019`: 매장에서 하시면 30분 안에 됩니다. 인터넷으로 신청하시면 다음 날 되고, 09:00부터 20:00까지 처리됩니다.

## PIPE-SUBSCRIBE-0022 (turn 0)

- 질문: 손주가 열세 살인데 폰 만들어줄 수 있어요
- 기대 동작: ANSWER
- 실제 답변: 손주가 열세 살이시군요. 하지만 현재 만 14세 미만 고객은 가입이 불가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0022`: 만 14세 미만은 가입이 안 됩니다.

## PIPE-SUBSCRIBE-0029 (turn 0)

- 질문: 휴대폰 새로 만들려는데 신분증 말고 또 뭐 챙겨야 하나요?
- 기대 동작: ANSWER
- 실제 답변: 휴대폰을 새로 만들 때는 본인 명의의 신분증 외에 추가로 다른 서류는 필요하지 않습니다. 주민등록증, 운전면허증, 여권 중 하나만 가져오시면 됩니다. 외국인이시라면 외국인등록증과 여권을 함께 준비하시면 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0029`: 본인 명의 신분증만 있으면 됩니다. 주민등록증, 운전면허증, 여권 중 하나를 지참하시면 되고, 외국인이신 경우에는 외국인등록증과 여권을 함께 준비하시면 됩니다.
  - `SUBSCRIBE-0005`: 네, 본인 명의 신분증(주민등록증, 운전면허증, 여권 중 하나)이 필요합니다.
  - `SUBSCRIBE-0001`: 본인 명의 신분증이면 주민등록증, 운전면허증, 여권 중 아무거나 가져오시면 됩니다. 외국인이시면 외국인등록증과 여권을 함께 지참하셔야 합니다.

## PIPE-SUBSCRIBE-0036 (turn 0)

- 질문: 첫 개통인데 회선 한도는 어떻게 확인해요?
- 기대 동작: ANSWER
- 실제 답변: 첫 개통이시라면 회선 한도 걱정 없이 개통하실 수 있습니다. 성인 기준으로는 1인당 최대 5회선까지 개통 가능합니다. 현재 본인 명의의 개통 이력이 없다면 한도 내에서 자유롭게 개통하실 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0036`: 성인은 1인당 최대 5회선까지 개통할 수 있습니다. 첫 개통이면 한도 걱정은 없습니다.
  - `SUBSCRIBE-0096`: 성인은 1인당 최대 5회선입니다. 본인 명의로 이미 개통된 회선이 있는지 확인해 보세요.

## PIPE-SUBSCRIBE-0049 (turn 0)

- 질문: 회사 폰 만들 때 뭐 가져가요
- 기대 동작: ANSWER
- 실제 답변: 회사 폰을 만들 때는 신분증을 가져가시면 됩니다. 주민등록증, 운전면허증, 여권 중 하나를 준비하시면 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0049`: 신분증 가져가시면 됩니다. 주민등록증, 운전면허증, 여권 중 하나면 됩니다.

## PIPE-SUBSCRIBE-0051 (turn 0)

- 질문: 처음 만드는 건데 매장 가면 바로 돼요
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SUBSCRIBE-0056 (turn 0)

- 질문: 회선을 하나 더 만들 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 네, 성인 한 분이시라면 최대 5회선까지 개통이 가능합니다. 추가로 개통을 원하신다면 해당 한도 내에서는 가능합니다. 다른 조건이나 법인 명의로의 개통에 대해서는 별도의 심사 절차가 필요할 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0056`: 성인 1인당 최대 5회선까지 개통할 수 있어 그 한도 안이라면 가능합니다. 법인 명의로 하시는 경우에는 별도 심사를 거칩니다.

## PIPE-SUBSCRIBE-0066 (turn 0)

- 질문: 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요?
- 기대 동작: ANSWER
- 실제 답변: 네, 만 18세 생일이 지나면 법정대리인 서류 없이 본인 신분증만으로 개통이 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-SUBSCRIBE-0066","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":"SHOULD_ABSTAIN","answerability":"ENOUGH"}`
- FAQ 근거:
  - `SUBSCRIBE-0066`: 법정대리인 서류는 만 14세 이상 18세 이하에 필요합니다. 성인이 되면 본인 신분증만으로 가능합니다.
  - `SUBSCRIBE-0062`: 만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 있어야 가능합니다.

## PIPE-SUBSCRIBE-0077 (turn 0)

- 질문: 아들 대신 제가 가서 아들 폰 만들 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 아들 명의로 폰을 만들기 위해서는 아드님의 신분증이 필요합니다. 현재 아드님의 신분증을 직접 확인하실 수 없다면, 해당 절차를 진행하기 어렵습니다. 아드님께서 직접 방문하시거나, 필요한 경우 아드님의 동의를 얻어 진행하시는 것을 권장드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0077`: 아들 명의로 하려면 아들 본인 신분증이 있어야 합니다.

## PIPE-SUBSCRIBE-0079 (turn 0)

- 질문: 회사 폰 오늘 바로 되나요
- 기대 동작: ANSWER
- 실제 답변: 매장에서 바로 진행하시면 오늘 안에 완료될 수 있으며, 대략 30분 정도 소요될 것입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0079`: 매장에서 하시면 30분이면 됩니다.

## PIPE-SUBSCRIBE-0081 (turn 0)

- 질문: 운전면허증만 있는데 되나요
- 기대 동작: ANSWER
- 실제 답변: 네, 운전면허증만으로도 개통이 가능합니다. 다른 신분증으로는 주민등록증이나 여권도 인정됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0081`: 네, 됩니다.
  - `SUBSCRIBE-0065`: 가능합니다. 본인 명의 신분증이면 주민등록증, 운전면허증, 여권 중 무엇이든 인정됩니다.

## PIPE-SUBSCRIBE-0084 (turn 0)

- 질문: 회사 이름으로 폰 만들려는데 심사 때문에 안 된대요
- 기대 동작: ANSWER
- 실제 답변: 법인 명의로 폰을 개통하려면 추가적인 심사 과정을 거쳐야 합니다. 심사 결과에 따라 개통 여부가 결정되므로, 심사 결과를 확인해 주시기 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0084`: 법인 명의 가입은 별도 심사를 거치기 때문에 결과가 나와야 개통됩니다. 심사 결과를 확인해 주세요.

## PIPE-SUBSCRIBE-0088 (turn 0)

- 질문: 외국인인데 회선을 추가하려니 한도라고 해요
- 기대 동작: ANSWER
- 실제 답변: 외국인 고객님께서도 성인 기준으로 1인당 최대 5회선까지 개통이 가능합니다. 이미 5회선을 사용 중이시라면 추가 개통이 제한될 수 있습니다. 추가 개통이 필요하시다면 현재 사용 중인 회선 수를 확인해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"PIPE-SUBSCRIBE-0088","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":"SHOULD_ABSTAIN","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `SUBSCRIBE-0088`: 성인은 1인당 최대 5회선까지입니다. 이미 5회선이면 추가 개통이 어렵습니다.

## PIPE-SUBSCRIBE-0094 (turn 0)

- 질문: 법인 명의로 미성년 인턴 회선을 만들려다 거절됐습니다
- 기대 동작: ANSWER
- 실제 답변: 법인 명의로 미성년자 인턴 회선을 개설하려는 경우, 미성년자의 법정대리인 서류가 필요하며, 미성년자의 나이는 만 14세 이상 18세 이하여야 합니다. 만 14세 미만의 경우 회선 개설이 불가능합니다. 따라서 거절된 이유는 미성년자의 나이 조건을 충족하지 못했거나 필요한 법정대리인 서류가 미비했을 가능성이 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0094`: 미성년자 명의 가입은 만 14세 이상 18세 이하이고 법정대리인 서류가 갖춰져야 합니다. 만 14세 미만은 불가능합니다.

## PIPE-SUBSCRIBE-0099 (turn 0)

- 질문: 신청했는데 왜 오늘 안 되나요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-SUBSCRIBE-0101 (turn 0)

- 질문: 처음 만드는데 신분증이 안 된대요
- 기대 동작: ANSWER
- 실제 답변: 주민등록증, 운전면허증, 여권 중 본인 이름으로 된 것을 준비해 주시면 됩니다. 다른 종류의 신분증은 현재 사용이 제한될 수 있으니 해당 서류들을 확인해 주세요.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0101`: 본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나여야 합니다.

## PIPE-SUBSCRIBE-0105 (turn 0)

- 질문: 폰 하나 더 하려는데 신분증 때문에 안 된대요
- 기대 동작: ANSWER
- 실제 답변: 본인 확인을 위해 주민등록증, 운전면허증, 여권 중 하나를 지참해 주시면 됩니다. 해당 서류로 확인이 가능하니 다시 방문해 주세요.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0105`: 본인 이름으로 된 주민등록증, 운전면허증, 여권 중 하나를 가져가셔야 됩니다.

## PIPE-SUBSCRIBE-0112 (turn 0)

- 질문: 애 명의 회선이랑 제 명의 회선이랑 한도가 달라요?
- 기대 동작: ANSWER
- 실제 답변: 개인 명의 회선의 경우 최대 5회선까지 허용됩니다. 애 명의 회선의 경우 별도의 심사 기준이 적용되어 한도가 다를 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0112`: 성인은 1인당 최대 5회선입니다. 법인 명의는 별도 심사로 정해집니다.

## PIPE-SUBSCRIBE-0123 (turn 0)

- 질문: 매장이랑 인터넷 중 어디가 빨리 되나요
- 기대 동작: ANSWER
- 실제 답변: 매장에서 신청하시면 30분 안에 처리됩니다. 인터넷을 통한 신청은 다음 날 완료되므로, 시간이 더 걸립니다. 따라서 매장 방문이 더 빠릅니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `SUBSCRIBE-0123`: 매장은 30분이면 됩니다. 인터넷은 다음 날 되니 매장이 더 빠릅니다.
  - `SUBSCRIBE-0107`: 매장에서 만드시면 30분 안에 바로 됩니다. 인터넷으로 신청하시면 다음 날 되니, 급하시면 매장 방문이 더 빠릅니다.
  - `SUBSCRIBE-0127`: 매장이 빠릅니다. 30분이면 됩니다. 인터넷은 다음 날 됩니다.

## PIPE-SUBSCRIBE-0127 (turn 0)

- 질문: 아들 폰 매장이 빨라요 인터넷이 빨라요
- 기대 동작: ANSWER
- 실제 답변: 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-TERMINATE-0004 (turn 0)

- 질문: 한동안 안 쓸 건데 정지시킬 수 있어요?
- 기대 동작: ANSWER
- 실제 답변: 네, 한동안 서비스를 정지하실 수 있습니다. 일시 정지는 연간 최대 90일까지 가능하며, 한 번에 최대 30일까지 정지하실 수 있습니다. 정지 기간 동안에는 기본료의 50%가 부과됩니다. 정지를 원하신다면 고객센터에 연락 주시면 도와드리겠습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0004`: 일시 정지는 연간 최대 90일, 1회 최대 30일까지 가능합니다. 정지 기간에는 기본료의 50%가 부과됩니다.
  - `TERMINATE-0044`: 고객센터에 전화하시면 됩니다. 한 번에 30일까지, 일 년에 90일까지 됩니다.

## PIPE-TERMINATE-0010 (turn 0)

- 질문: 약정 중간에 해지하면 위약금이 얼마나 나오나요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-TERMINATE-0010","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":"OVER_REFUSAL","answerability":"ENOUGH"}`
- FAQ 근거:
  - `TERMINATE-0010`: 약정은 24개월이며, 12개월 미만에 해지하면 할인 반환금의 100%, 12개월 이상 24개월 미만이면 50%를 반환합니다.
  - `TERMINATE-0026`: 24개월 약정 기준으로, 12개월이 되기 전에 해지하면 할인 반환금 100%, 12개월 이상 24개월 미만이면 50%를 내시면 됩니다.
  - `TERMINATE-0102`: 약정 기간은 24개월입니다. 12개월이 되기 전에 해지하시면 받으신 요금 할인액의 100%를 반환해야 하고, 12개월 이상 24개월 미만이면 50%를 반환합니다. 같은 해지라도 12개월을 넘겼는지에 따라 반환금이 절반으로 줄어듭니다.

## PIPE-TERMINATE-0013 (turn 0)

- 질문: 분실 후 해지 시 요금 정산 기준을 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 분실 후 해지 시 요금은 해지 신청이 처리된 달부터 정산됩니다. 해당 달의 요금은 일할 계산되어 청구됩니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0013`: 해지 신청 즉시 처리되며 해지한 달의 요금은 일할 계산됩니다.

## PIPE-TERMINATE-0029 (turn 0)

- 질문: 해외에 오래 있을 건데 해지는 어떤 순서로 해요?
- 기대 동작: ANSWER
- 실제 답변: 해지 신청 후 즉시 처리되며, 해지된 달의 요금은 일할 계산되어 청구됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0029`: 홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 해지한 달 요금은 일할 계산되어 청구됩니다.

## PIPE-TERMINATE-0034 (turn 0)

- 질문: 약정 반환금 계산 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 약정 반환금은 가입 기간에 따라 다르게 계산됩니다. 가입일부터 해지일까지의 총 개월 수를 확인하신 후, 12개월 미만이면 할인받은 금액의 100%를, 12개월 이상 24개월 미만이면 50%를 반환받으실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0034`: 가입일부터 해지일까지의 개월 수를 확인해 12개월 미만이면 할인액의 100%, 12개월 이상 24개월 미만이면 50%를 반환합니다.

## PIPE-TERMINATE-0043 (turn 0)

- 질문: 폰 잃어버려서 해지하는데 할부는 어떻게 해요
- 기대 동작: ANSWER
- 실제 답변: 폰을 잃어버리신 상황에서 해지를 진행하시더라도, 남은 할부금은 일시에 납부하시거나, 원하시는 경우 해지 후에도 기존 할부 일정을 유지하며 나눠 납부하실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":null}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-TERMINATE-0043","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `TERMINATE-0023`: 한 번에 내시는 게 원칙입니다. 원하시면 해지하고도 나눠 내실 수 있습니다.
  - `TERMINATE-0043`: 남은 할부를 한 번에 내시거나, 원하시면 계속 나눠 내실 수 있습니다.
  - `TERMINATE-0003`: 남은 할부금은 일시 납부가 원칙이고, 원하시면 해지 후에도 기존 할부 일정을 유지하실 수 있습니다.

## PIPE-TERMINATE-0049 (turn 0)

- 질문: 해외 나가 있는 동안 홈페이지로 해지할 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 신청 즉시 해지 처리가 이루어집니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0049`: 네, 홈페이지에서 신청하시면 즉시 해지됩니다.
  - `TERMINATE-0077`: 홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 매장에 가지 않아도 됩니다.
  - `TERMINATE-0081`: 고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.

## PIPE-TERMINATE-0053 (turn 0)

- 질문: 폰을 잃어버렸는데 매장 안 가고 해지할 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 신청 즉시 해지 처리가 완료됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0053`: 네, 고객센터 전화나 홈페이지로도 해지 신청이 됩니다. 신청 즉시 해지됩니다.
  - `TERMINATE-0077`: 홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 매장에 가지 않아도 됩니다.
  - `TERMINATE-0041`: 매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 신청하시면 바로 해지됩니다.

## PIPE-TERMINATE-0069 (turn 0)

- 질문: 외국에서 전화로 해지할 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 네, 외국에서도 고객센터에 전화하시면 즉시 해지 처리가 가능합니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `TERMINATE-0069`: 네, 고객센터에 전화하시면 바로 해지됩니다.
  - `TERMINATE-0081`: 고객센터 전화나 홈페이지로도 해지 신청이 가능합니다. 신청하시면 즉시 해지되고 해당 월 요금은 일할 계산됩니다.
  - `TERMINATE-0049`: 네, 홈페이지에서 신청하시면 즉시 해지됩니다.

## PIPE-TERMINATE-0071 (turn 0)

- 질문: 군대 가는 아들 폰 해지하면 할부 계속 나눠 낼 수 있나요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0071`: 네, 원하시면 그대로 나눠 내실 수 있습니다.
  - `TERMINATE-0091`: 해지할 때 나눠 내는 걸로 하셨으면 계속 나옵니다. 한 번에 정리하시려면 남은 걸 다 내시면 됩니다.

## PIPE-TERMINATE-0073 (turn 0)

- 질문: 군대를 가는데 쓰던 번호를 어떻게 해야 할지 모르겠어요
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-TERMINATE-0085 (turn 0)

- 질문: 해지 신청했는데 다음 달 요금이 청구됐습니다
- 기대 동작: ANSWER
- 실제 답변: 해지 신청 후에도 다음 달 요금이 청구되는 것은 해지 처리 과정에서 일시적인 청구일 수 있습니다. 해지 신청이 접수되면 해당 월의 남은 기간에 대한 요금이 일할 계산되어 청구되며, 이후 달부터는 요금이 발생하지 않습니다. 청구 내역을 고객센터에서 자세히 확인해 보시는 것을 권장드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `TERMINATE-0085`: 해지는 신청 즉시 처리되고 해지월 요금만 일할 계산됩니다. 청구 내역을 고객센터에서 확인해 보세요.
  - `SERVICE-0051`: 해지한 달의 월 이용료는 일할 계산되지 않아 그대로 청구됩니다. 다음 달부터는 나오지 않습니다.
  - `TERMINATE-0089`: 해지 신청 즉시 처리되고, 그 달 요금은 사용한 날수만큼 일할 계산되어 청구됩니다.

## PIPE-TERMINATE-0090 (turn 0)

- 질문: 요금 부담돼서 해지했는데 위약금이 더 나와요
- 기대 동작: ANSWER
- 실제 답변: 약정 기간 내에 해지를 진행하셨다면 반환금이 발생할 수 있습니다. 만약 해지 시점이 약정 기간의 절반을 초과했다면, 반환금은 절반만 적용될 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `TERMINATE-0090`: 약정 기간 안에 해지하면 반환금이 있습니다. 12개월 전이면 전부, 넘었으면 절반입니다.

## PIPE-TERMINATE-0092 (turn 0)

- 질문: 정지 중인데 청구서가 왔어요
- 기대 동작: ANSWER
- 실제 답변: 정지 중에 청구서가 도착하셨다니 다행입니다. 청구서를 확인하시고 요금을 정산하시면 서비스 정지 해제에 필요한 시간 내에 정상화될 수 있습니다. 추가 도움이 필요하시면 언제든지 연락 주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["MISSED"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `BILLING-0059`: 청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.

## PIPE-TERMINATE-0095 (turn 0)

- 질문: 한 번에 내는 거랑 나눠 내는 거랑 뭐가 더 나아요?
- 기대 동작: ANSWER
- 실제 답변: 반면에 할부 납부는 금액 부담을 줄여주며, 일정 기간 동안 나눠서 납부할 수 있어 경제적 부담을 덜 수 있습니다. 고객님의 재정 상황과 편의성에 따라 선택하시면 됩니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0095`: 일시 납부는 그 자리에서 정리가 끝나고, 할부 유지는 기존 일정대로 계속 나눠 내실 수 있습니다. 편하신 쪽으로 선택하시면 됩니다.

## PIPE-TERMINATE-0096 (turn 0)

- 질문: 정지하는 거랑 아예 해지하는 거랑 뭐가 더 나아요?
- 기대 동작: ANSWER
- 실제 답변: 일시 정지는 기본료의 절반만 내면 되고 번호를 유지할 수 있어 추후 재가입이 용이합니다. 반면에 해지는 요금 부담이 완전히 사라지지만, 사용하던 번호와 계약이 종료되므로 다시 개통할 때는 새로운 절차가 필요합니다. 번호 유지와 재사용 가능성을 고려하신다면 일시 정지를, 완전히 요금 부담을 없애고 싶으시다면 해지를 추천드립니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0096`: 일시 정지는 기본료의 50%만 내고 번호를 유지할 수 있습니다. 해지는 요금이 아예 안 나가지만 번호와 계약이 없어집니다.

## PIPE-TERMINATE-0104 (turn 0)

- 질문: 장기 미사용이면 일시 정지와 해지 중 어느 쪽이 유리한가요?
- 기대 동작: ANSWER
- 실제 답변: 장기 미사용 시 번호를 계속 유지하고 싶으시다면 일시 정지를 추천드립니다. 일시 정지 시 기본료가 50% 감면되며, 1회에 30일, 연 최대 90일까지 가능합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0104`: 번호를 유지하려면 일시 정지(기본료 50%, 1회 30일, 연 90일)가 낫고, 다시 쓸 계획이 없으면 해지가 낫습니다.

## PIPE-TERMINATE-0105 (turn 0)

- 질문: 요금 부담으로 해지할 때 홈페이지와 고객센터 중 어느 쪽이 빠른가요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["OVER_REFUSAL",true,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0105`: 둘 다 신청 즉시 해지되어 차이가 없습니다.

## PIPE-TERMINATE-0113 (turn 0)

- 질문: 폰 잃어버렸는데 매장 가는 거랑 전화하는 거랑 뭐가 빨라요
- 기대 동작: ANSWER
- 실제 답변: 둘 다 즉시 처리가 가능하니 편하신 방법으로 선택하시면 됩니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0113`: 똑같이 바로 됩니다. 전화가 편하시면 전화로 하세요.
  - `TERMINATE-0109`: 셋 다(매장·전화·홈페이지) 신청 즉시 해지되어 처리 속도는 같습니다.

## PIPE-TERMINATE-0115 (turn 0)

- 질문: 요금 부담돼서 해지하는데 할부 한 번에 내는 게 나아요
- 기대 동작: ANSWER
- 실제 답변: 해지 시 할부금을 한 번에 내는 것이 원칙입니다. 하지만 요금 부담이 크시다면, 기존 할부 일정을 유지하며 할부금을 계속 나눠서 납부하는 방법도 선택 가능합니다. 고객센터에 문의하시면 개인 상황에 맞는 최적의 방법을 안내받으실 수 있습니다.

- qwen: `{"grounding":"REVIEW","quality":["COMPLETE"],"abstention":["REVIEW",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `TERMINATE-0115`: 원칙은 한 번에 내는 겁니다. 부담되시면 계속 나눠 내는 것도 됩니다.
  - `TERMINATE-0099`: 한 번에 내면 정리가 끝나고, 계속 내면 기존 일정대로 부담이 나뉩니다. 원칙은 일시 납부이고 유지는 선택입니다.
  - `TERMINATE-0083`: 남은 할부금은 원칙적으로 일시 납부입니다. 부담스러우시면 해지 후에도 기존 할부 일정을 유지하는 방법을 선택하실 수 있으니 고객센터에 요청해 주세요.

## PIPE-USIM-0007 (turn 0)

- 질문: 듀얼심이 뭐예요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":null,"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## PIPE-USIM-0012 (turn 0)

- 질문: 유심 분실 신고는 어디로 하나요?
- 기대 동작: ANSWER
- 실제 답변: 신고 후 즉시 이용이 정지됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["MISSED"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0012`: 고객센터나 홈페이지에서 신고하시면 즉시 이용 정지됩니다.
  - `USIM-0036`: 고객센터나 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 이후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.

## PIPE-USIM-0027 (turn 0)

- 질문: 유심이 깨져서 eSIM으로 바꾸려면 어떻게 해요?
- 기대 동작: ANSWER
- 실제 답변: 유심이 깨져서 eSIM으로 전환을 원하신다면, 먼저 현재 사용 중인 단말이 eSIM을 지원하는지 확인해 주세요. 지원한다면 매장이나 온라인을 통해 전환 신청을 하실 수 있으며, 이 과정에서 발생하는 비용은 2,750원입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0027`: eSIM을 지원하는 단말인지 먼저 확인하시고, 매장이나 온라인에서 발급을 신청하시면 됩니다. 비용은 2,750원입니다.
  - `USIM-0047`: 폰이 eSIM을 지원하면 매장에서 바꿔 드립니다. 2,750원입니다.
  - `USIM-0035`: eSIM을 지원하는 단말이라면 매장이나 온라인에서 전환 신청을 하시면 됩니다. 발급 비용은 2,750원입니다.

## PIPE-USIM-0030 (turn 0)

- 질문: 해외에서 부모님이 대신 유심을 받아 보내려면 뭘 준비해야 해요?
- 기대 동작: ANSWER
- 실제 답변: 해외에서 부모님이 대신 유심을 받아 보내려면 위임장과 함께 대리인의 신분증, 그리고 가입자의 신분증 사본을 준비하셔야 합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0030`: 대리인 방문이라 위임장, 대리인 신분증, 가입자 신분증 사본을 준비하셔야 합니다.

## PIPE-USIM-0036 (turn 0)

- 질문: 유심 분실 시 처리 절차를 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 이후 새 유심을 재발급 받으시면 기존 유심은 자동으로 해지됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0036`: 고객센터나 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 이후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.
  - `USIM-0040`: 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.
  - `USIM-0108`: 잃어버린 것이 아니면 재발급으로 진행합니다. 새 유심을 받으면 기존 유심은 자동 해지됩니다.

## PIPE-USIM-0041 (turn 0)

- 질문: 재발급 신청 절차를 순서대로 알려주세요.
- 기대 동작: ANSWER
- 실제 답변: 재발급 신청은 주로 두 가지 방법으로 진행됩니다. 둘째, 온라인으로 신청하시면 택배로 수령하실 수 있으며, 이 경우 처리 기간은 약 2~3 영업일이 소요됩니다. 매장 방문이 어려우시다면 온라인 신청을 추천드립니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - `USIM-0041`: 매장을 방문하시면 즉시 발급받으실 수 있습니다. 매장 방문이 어려우시면 온라인으로 신청해 택배로 받으실 수 있으며 2~3 영업일이 걸립니다.

## PIPE-USIM-0049 (turn 0)

- 질문: 기기변경 때 유심을 택배로 받을 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 네, 기기변경 시 유심을 택배로 받으실 수 있습니다. 온라인으로 신청하시면 2~3 영업일 내에 배송받으실 수 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"UNCERTAIN",["UNCERTAIN"]]}`
- FAQ 근거:
  - `USIM-0049`: 네, 온라인으로 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다.
  - `USIM-0029`: 온라인으로 재발급을 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다. 비용은 7,700원입니다.
  - `USIM-0009`: 온라인 신청 시 택배로 2~3 영업일이 걸립니다. 매장 방문은 즉시 발급이며 비용은 7,700원입니다.

## PIPE-USIM-0054 (turn 0)

- 질문: 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요?
- 기대 동작: ANSWER
- 실제 답변: 새 폰으로 변경 시 유심을 받기 위해서는 본인의 신분증이 필수적으로 필요합니다. 신분증 없이는 유심 발급이 어렵습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- 사람 판정: `{"caseId":"PIPE-USIM-0054","turnIndex":0,"pipelineFailure":"NONE","grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":"NOT_APPLICABLE","answerability":"ENOUGH"}`
- FAQ 근거:
  - `USIM-0054`: 본인 신분증이 필요합니다. 신분증 없이는 발급이 어렵습니다.
  - `USIM-0094`: 신분증만 가져오시면 됩니다.

## PIPE-USIM-0060 (turn 0)

- 질문: 해외에서 홈페이지로 분실 신고가 가능한가요?
- 기대 동작: ANSWER
- 실제 답변: 네, 해외에서도 홈페이지를 통해 분실 신고가 가능합니다. 신고 후 즉시 이용 정지 처리가 이루어집니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0060`: 네, 홈페이지에서 신고하시면 즉시 이용 정지됩니다.

## PIPE-USIM-0067 (turn 0)

- 질문: 제 폰에서도 eSIM 될까요
- 기대 동작: ANSWER
- 실제 답변: 네, 가능합니다. 단, 귀하의 폰 모델이 eSIM을 지원해야 합니다. 지원 여부는 제조사에 문의하시거나, 제품 사양을 확인해 보시는 것이 좋습니다. 비용은 2,750원입니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0067`: eSIM을 지원하는 단말이면 가능합니다. 비용은 2,750원입니다.

## PIPE-USIM-0075 (turn 0)

- 질문: 외국에서 산 폰인데 eSIM이 안 잡혀요
- 기대 동작: ANSWER
- 실제 답변: eSIM 기능은 지원 가능한 단말 모델에서만 사용 가능합니다. 외국에서 구매하신 폰이 eSIM을 지원하는지 매장이나 제품 설명서를 통해 확인해 주시기 바랍니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0075`: eSIM은 지원 단말에서만 쓸 수 있습니다. 단말 지원 여부를 매장에서 확인해 보세요.

## PIPE-USIM-0077 (turn 0)

- 질문: 유심이 부러졌는데 오늘 급하게 써야 해요
- 기대 동작: ANSWER
- 실제 답변: 매장을 방문하시면 유심 재발급이 즉시 가능합니다. 재발급 비용은 7,700원입니다. 급하신 상황에 도움이 되시길 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":null}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0077`: 매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.

## PIPE-USIM-0083 (turn 0)

- 질문: 기존 유심이 자꾸 오류가 나서 eSIM으로 바꾸고 싶은데 제 폰이 지원하는지 모르겠어요
- 기대 동작: ANSWER
- 실제 답변: eSIM 지원 여부는 매장에서 확인해 드릴 수 있습니다. 폰이 eSIM을 지원한다면 전환이 가능하며, 발급 비용은 2,750원입니다. 매장 방문을 권장드립니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0083`: eSIM은 지원 단말에서만 전환하실 수 있습니다. 단말 지원 여부는 매장에서 확인해 드리며, 발급 비용은 2,750원입니다.
  - `USIM-0087`: eSIM은 지원 단말에서만 발급할 수 있습니다. 지원하지 않는 단말이면 물리 유심을 재발급받으셔야 합니다.
  - `USIM-0091`: eSIM을 지원하는 단말이라면 바꾸실 수 있습니다. 발급 비용은 2,750원이고, 듀얼심으로 최대 2개 회선을 동시에 쓰실 수 있습니다.

## PIPE-USIM-0086 (turn 0)

- 질문: 분실 재발급을 대리인에게 부탁했는데 가입자 신분증 원본이 없어 거절됐습니다
- 기대 동작: ANSWER
- 실제 답변: 대리인을 통한 분실 재발급 시, 가입자 신분증의 원본이 아닌 사본이 필요합니다. 위임장과 함께 대리인의 신분증도 함께 제출하셨는지 확인해 주세요. 필요한 서류가 모두 준비되었다면 다시 신청해 주시기 바랍니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0086`: 대리인 방문은 가입자 신분증 사본이면 됩니다. 위임장과 대리인 신분증을 함께 지참했는지 확인해 주세요.

## PIPE-USIM-0095 (turn 0)

- 질문: 물리 유심이랑 eSIM이랑 비용이 달라요?
- 기대 동작: ANSWER
- 실제 답변: 네, 물리 유심 재발급 비용은 7,700원이고, eSIM 발급 비용은 2,750원으로 eSIM이 더 저렴합니다.

- qwen: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":null}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0095`: 물리 유심 재발급은 7,700원, eSIM 발급은 2,750원으로 eSIM이 더 저렴합니다.
  - `USIM-0099`: eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.
  - `USIM-0103`: eSIM은 2,750원이고 지원 단말이어야 합니다. 단말이 지원하지 않으면 7,700원의 물리 유심 재발급을 선택하셔야 합니다.

## PIPE-USIM-0097 (turn 0)

- 질문: 유심 깨졌을 때 매장이랑 택배 중 뭐가 나아요?
- 기대 동작: ANSWER
- 실제 답변: 비용은 두 방법 모두 7,700원으로 동일합니다. 상황에 맞게 선택하시면 됩니다.

- qwen: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["COMPLETE"],"abstention":null}`
- sonnet-4-6: `{"grounding":"SUPPORTED","quality":["PARTIAL"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0097`: 급하면 매장이 즉시 발급이라 낫습니다. 택배는 2~3 영업일 걸리고 비용은 둘 다 7,700원입니다.

## PIPE-USIM-0099 (turn 0)

- 질문: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?
- 기대 동작: ANSWER
- 실제 답변: 물리 유심을 그대로 사용하는 경우 기존 유심을 새 기기에 삽입하여 사용할 수 있습니다. 반면에 eSIM을 새로 받는 경우, 2,750원의 비용이 발생하며, 지원하는 단말에서만 활성화 가능합니다. eSIM의 장점은 듀얼심 기능을 통해 최대 2개의 회선을 동시에 사용할 수 있다는 점입니다. 어떤 방식이 더 적합한지는 사용 패턴과 기기 호환성을 고려해 결정하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0099`: eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.

## PIPE-USIM-0104 (turn 0)

- 질문: 기기변경 시 분실 신고를 하는 것과 재발급만 하는 것은 어떻게 다른가요?
- 기대 동작: ANSWER
- 실제 답변: 분실 신고는 즉시 기기의 사용을 정지시키는 절차로, 유심의 분실이나 도난 상황에서 보안을 강화하기 위해 이루어집니다. 반면에 재발급은 새로운 유심을 발급받으면서 기존 유심이 자동으로 해지되는 과정으로, 유심만 교체하는 경우에 해당합니다. 따라서 분실이 의심되는 경우에는 분실 신고를, 단순히 유심만 교체하려는 경우에는 재발급 절차를 진행하시면 됩니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"SUPPORTED","quality":["COMPLETE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["COMPLETE"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- FAQ 근거:
  - `USIM-0104`: 분실 신고는 즉시 정지 조치이고, 재발급은 새 유심을 받으면서 기존 유심이 자동 해지되는 절차입니다. 잃어버린 게 아니라면 재발급만 하시면 됩니다.

## REG-FAQ_COMPOUND (turn 0)

- 질문: 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요?
- 기대 동작: ANSWER
- 실제 답변: 안내드릴 수 있는 정보가 없습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["COMPLETE","COMPLETE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN","UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT","INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"REG-FAQ_COMPOUND","turnIndex":0,"pipelineFailure":"NONE","grounding":"NOT_APPLICABLE","quality":["MISSED","MISSED"],"abstention":"APPROPRIATE","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## REG-MIXED (turn 0)

- 질문: 유심 재발급 비용은 얼마고 배송비도 내야 하나요?
- 기대 동작: PARTIAL_ANSWER
- 실제 답변: 유심 재발급 비용은 7,700원입니다. 온라인으로 신청하신 경우에는 택배로 배송되며, 배송비는 별도로 발생하지 않습니다. 매장 방문 시에는 즉시 받으실 수 있습니다.

- qwen: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":["SHOULD_ABSTAIN",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"REG-MIXED","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["PARTIAL"],"abstention":"SHOULD_ABSTAIN","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `USIM-0001`: 7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.
  - `USIM-0073`: 인식 오류가 계속되면 재발급을 받으시는 게 좋습니다. 매장에서는 즉시 발급되고, 비용은 7,700원입니다.
  - `USIM-0077`: 매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.

## REG-NO_EVIDENCE (turn 0)

- 질문: 유심 재발급하면 택배비는 따로 내나요?
- 기대 동작: ABSTAIN
- 실제 답변: 유심 재발급 비용은 7,700원으로 포함되어 있습니다.

- qwen: `{"grounding":"SUPPORTED","quality":["NOT_APPLICABLE"],"abstention":["NOT_APPLICABLE",false,"ENOUGH",["ENOUGH"]]}`
- gpt-oss-120b: `{"grounding":null,"quality":["MISSED"],"abstention":[null,false,"ENOUGH",["ENOUGH"]]}`
- sonnet-4-6: `{"grounding":"REVIEW","quality":["REVIEW"],"abstention":["REVIEW",false,"INSUFFICIENT",["INSUFFICIENT"]]}`
- 사람 판정: `{"caseId":"REG-NO_EVIDENCE","turnIndex":0,"pipelineFailure":"NONE","grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":"SHOULD_ABSTAIN","answerability":"INSUFFICIENT"}`
- FAQ 근거:
  - `USIM-0001`: 7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.
  - `USIM-0029`: 온라인으로 재발급을 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다. 비용은 7,700원입니다.
  - `USIM-0009`: 온라인 신청 시 택배로 2~3 영업일이 걸립니다. 매장 방문은 즉시 발급이며 비용은 7,700원입니다.

## REG-STORE (turn 0)

- 질문: 강남역 근처 유플러스 매장 알려줘
- 기대 동작: STORE_LOOKUP
- 실제 답변: 현재 가까운 매장 정보를 바로 확인하기 어려워요. 잠시 후 다시 시도하거나 고객센터를 이용해 주세요.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"UNSUPPORTED","quality":["MISSED"],"abstention":["SHOULD_ABSTAIN",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["MISSED"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음

## REG-UNRELATED (turn 0)

- 질문: 오늘 서울 날씨 어때?
- 기대 동작: OUT_OF_SCOPE
- 실제 답변: 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

- qwen: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- gpt-oss-120b: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["REVIEW",true,"UNCERTAIN",["UNCERTAIN"]]}`
- sonnet-4-6: `{"grounding":"NOT_APPLICABLE","quality":["NOT_APPLICABLE"],"abstention":["APPROPRIATE",true,"INSUFFICIENT",["INSUFFICIENT"]]}`
- FAQ 근거:
  - 저장된 FAQ 근거 없음
