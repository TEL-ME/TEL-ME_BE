# 실제 채팅 파이프라인 답변 품질 측정

Spring 채팅 API가 생성하고 저장한 최종 답변을 vLLM Qwen3-14B-AWQ로 평가했다.
점수는 이 고정 평가 질문에서의 자동 판정 결과이며 실제 이용자 질문 분포의 전체 서비스 환각률로 일반화하지 않는다.
근거 없는 주장은 실제 RAG 입력에 포함된 근거를 기준으로 판단한다. 참인 외부 지식도 근거가 없으면 이 정책에서는 통과하지 않는다.

| 지표 | 결과 |
| --- | --- |
| 질문 수 | 509/509 |
| 실행 완료율 | 97.05% (494/509) |
| 답변 품질 통과율 | 56.17% (273/486) |
| 답변 가능 질문의 완전 답변율 | 75.23% (331/440) |
| 사실 답변의 근거 없는 답변 발생률 | 32.29% (113/350) |
| 사실 주장 단위 근거 부족률 | 18.68% (142/760) |
| 전체 질문 중 근거 없는 답변 발생 | 22.2% (113/509) |
| 근거 미확정 건수를 포함한 전체 질문 기준 발생률 범위 | [22.2, 28.29]% |
| 근거 판정 가능률 | 93.91% (478/509) |
| 정답 근거 포함: 검색 후보 / RAG 입력 / 저장 근거 | 75.74% (384/507) / 75.74% (384/507) / 72.78% (369/507) |
| 세 축 완전 채점 / 미채점 / 사람 검토 | 478 / 31 / 7 |
| 품질 미확정 23건을 고려한 통과율 범위 | [53.63, 58.15]% |
| 파이프라인 P50 / P95 | 1315 / 2328 ms |

품질은 필요한 내용을 정확히 답하거나 적절히 보류했으며 근거 없는 단정이 없는 경우에 통과한다.
미채점 또는 모호한 판정은 정상 답변으로 처리하지 않는다. 축별로 유효한 판정을 보존한다.

| 사례 | 질문 | 실행 | 근거성 | 품질 통과 | 발견 사항 |
| --- | --- | --- | --- | --- | --- |
| PIPE-BILLING-0014:1 | 청구서 수령 방법을 바꾸면 언제부터 적용되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0002:1 | 요금 납부일이 며칠이에요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0023:1 | 청구서는 언제 오고 요금은 언제까지 내요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0020:1 | 이중 납부 환불 대신 다음 달 요금 차감이 가능한가요? | COMPLETED | UNSCORED | None |  |
| PIPE-BILLING-0003:1 | 요금 안 내면 며칠 뒤에 전화가 끊겨요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0032:1 | 휴대폰으로 결제하는 거 한 달에 얼마까지 돼요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0021:1 | 청구서 수령 방법 종류를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0007:1 | 우편 청구서는 돈 내야 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0065:1 | 자동이체 두 번 나갔는데 어떻게 해요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0052:1 | 발신 정지 이후 재개까지의 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0037:1 | 청구서 받고 나서 납부까지 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0062:1 | 요금 안 냈는데 두 번 낸 거 있으면 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0054:1 | 미납 요금을 가상계좌로 납부하는 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0042:1 | 우편 청구서를 이메일로 바꾸려면 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0060:1 | 결제 한도 올리려면 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0053:1 | 소액결제 한도를 다시 낮추는 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0097:1 | 두 번 낸 거 자식 계좌로 받을 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0074:1 | 미납이 있어도 소액결제 한도를 올릴 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0087:1 | 청구서를 못 받았다는 이유로 정지가 유예되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0092:1 | 요금제 이번 달에 바꿀 수 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0069:1 | 이중 납부 환불을 배우자 계좌로 받을 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-BILLING-0084:1 | 모바일 알림 청구서로 바꾸면 요금이 붙나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0090:1 | 요금 밀렸는데 두 번 낸 것도 환불되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0072:1 | 25일 전에 미리 낼 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-BILLING-0100:1 | 요금이 너무 많이 나왔는데 25일까지 다 못 낼 것 같아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0113:1 | 요금제를 바꿨는데 청구서에 두 요금제가 다 찍혀 있습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0123:1 | 청구서에 결제한 적 없는 소액결제가 있어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0102:1 | 휴대폰 결제가 갑자기 안 돼요 | COMPLETED | UNSCORED | None |  |
| PIPE-BILLING-0115:1 | 청구서를 못 받았는데 발신이 정지됐습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0129:1 | 요금 두 번 냈는데 전화가 끊겼어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0107:1 | 청구서가 안 와서 언제 내야 할지 모르겠어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0117:1 | 자동이체와 카드 결제가 같은 달에 둘 다 나갔습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0154:1 | 종이랑 이메일이랑 뭐가 달라요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-BILLING-0135:1 | 청구서 나오는 날이랑 내는 날이랑 왜 달라요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-BILLING-0146:1 | 미납 상태에서 환불과 차감 중 어느 쪽이 유리한가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0155:1 | 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-BILLING-0138:1 | 자동이체랑 가상계좌랑 뭐가 나아요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-BILLING-0148:1 | 요금제 변경 적용일과 청구 기준일은 어떻게 다른가요? | COMPLETED | REVIEW | None | HUMAN_REVIEW |
| PIPE-BILLING-0158:1 | 밀렸을 때 한도가 줄어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0137:1 | 한도 올리려면 뭐가 필요해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0015:1 | 5G 스탠다드 월정액과 기본 데이터를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0023:1 | 데이터 선물 얼마나 되나요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0003:1 | 가족한테 데이터 한 번에 얼마까지 보낼 수 있어요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0017:1 | 소진 후 속도 제한 값이 요금제마다 다른가요? | COMPLETED | UNSCORED | None |  |
| PIPE-PLAN-0022:1 | 데이터 다 쓰면 아예 안 되나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-PLAN-0001:1 | 데이터 많이 주는 5G 요금제는 몇 개나 있어요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0011:1 | 요금제 몇 종류나 있어요? 이름 말고 그냥 개수만요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| PIPE-PLAN-0026:1 | 알뜰 요금제라는 게 뭐예요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0038:1 | 데이터 선물은 어떻게 보내요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0044:1 | 만 65세가 된 뒤 시니어 요금제로 전환하는 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PLAN-0058:1 | 남은 데이터 손주한테 어떻게 줘요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-PLAN-0037:1 | 데이터 소진 알림 받은 뒤엔 어떻게 해야 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0050:1 | 예산 5만 원 안에서 요금제 고르는 절차를 알려주세요. | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-PLAN-0060:1 | 요금제 뭐가 있는지 어떻게 알아요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0036:1 | 나이 바뀌어서 요금제 바꾸려는데 어떤 게 있는지 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-PLAN-0047:1 | 가족 회선의 속도 제한 상태를 확인하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0069:1 | 데이터 많이 쓰는 청소년도 청소년 요금제 되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PLAN-0083:1 | 손주한테 데이터 줄 수 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0072:1 | 언리미티드로 바꾸면 속도 제한이 아예 없어지나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PLAN-0061:1 | 5G 요금제 안 쓰고 LTE만 계속 쓸 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0090:1 | 5만 원 안쪽 요금제 있나요 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-PLAN-0080:1 | 나이와 상관없이 5G 라이트에 가입할 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0066:1 | 알뜰 요금제로 5G를 쓸 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PLAN-0081:1 | 데이터 조금 쓰는데 싼 거 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0103:1 | 가족한테 데이터를 나눠주고 싶은데 한도를 넘겼는지 확인이 안 됩니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0096:1 | 프리미엄이 5G예요 LTE예요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0116:1 | 5G 요금제 이름 좀 알려줘요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0106:1 | 요금 절감을 위해 요금제 라인업을 봤는데 구분이 헷갈립니다 | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| PIPE-PLAN-0091:1 | 요금제가 너무 많아서 뭐가 뭔지 모르겠어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0115:1 | 요금이 너무 많이 나와서 낮은 요금제로 바꾸고 싶어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0108:1 | 성인이 되면서 요금제를 바꿨더니 이월 데이터가 줄었습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PLAN-0092:1 | 데이터가 갑자기 느려졌어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0150:1 | 싼 거랑 비싼 거랑 얼마 차이예요 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-PLAN-0135:1 | 가족 4명이 5G 프리미엄과 LTE 맥스로 나눌 때 요금 차이는 어떻게 되나요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-PLAN-0121:1 | 5G랑 LTE랑 뭐가 달라요? | COMPLETED | UNSCORED | None |  |
| PIPE-PLAN-0145:1 | 제일 싼 거랑 제일 비싼 요금제 차이가 얼마나 나요 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-PLAN-0136:1 | 연령 제한 요금제와 일반 라인업의 종류 수 차이를 알려주세요. | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-PLAN-0127:1 | 가족 중 언리미티드 쓰는 사람이랑 아닌 사람이랑 뭐가 달라요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0143:1 | 손주한테 주는 거랑 넘기는 거랑 뭐가 나아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0137:1 | 400kbps 제한과 언리미티드의 실사용 차이가 큰가요? | COMPLETED | UNSCORED | None |  |
| PIPE-DEVICE-0025:1 | 할부하면 이자 붙어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0016:1 | 분실 단말은 반품 규정이 적용되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0004:1 | 배터리가 이상해서 새 폰 반품하려는데 기한이 얼마예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0026:1 | 잃어버린 폰 할부 남았는데 새 폰 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0015:1 | 기기변경 전 파손 수리 시 자기부담금 비율을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0008:1 | 침수된 폰은 교환 대상이 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0024:1 | 배터리 이상한 새 폰 바꿔줘요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0012:1 | 액정 불량 단말의 교환 가능 조건을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0028:1 | 단말 반품이나 교환은 어떻게 신청해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0042:1 | 액정 파손 시 기기변경 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0048:1 | 물에 빠뜨린 새 폰 교환하려면 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0033:1 | 침수돼서 새 폰 할부로 사려는데 어떻게 진행돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0040:1 | 기기변경 직후 새 단말 교환 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0054:1 | 배터리가 금방 닳아서 폰 바꾸고 싶은데 조건이 뭐예요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0034:1 | 배터리가 오래돼서 기기변경하려면 뭘 준비해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0037:1 | 단말 할부 기간과 수수료율을 어떻게 확인하고 선택하나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0073:1 | 할부를 짧게 할 수도 있나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-DEVICE-0062:1 | 액정만 깨졌는데 기기변경 대신 수리로 갈 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0070:1 | 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0076:1 | 잃어버렸다가 찾은 새 폰 반품되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0063:1 | 침수 폰도 보험 처리가 될까요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-DEVICE-0072:1 | 액정 보호필름을 붙였는데 교환이 가능한가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0080:1 | 바꾼 지 며칠 안 된 폰 다시 바꿀 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0061:1 | 잃어버린 폰 할부가 남았는데 새 폰을 30개월로 할 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0095:1 | 기기변경 전 수리를 받았는데 자기부담금이 30%보다 많이 나왔습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0099:1 | 물에 빠뜨렸습니다. 어디로 가야 합니까. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-DEVICE-0084:1 | 배터리가 하루도 안 가는 새 폰인데 교환 거절당했어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0098:1 | 침수로 기기변경하려는데 할부 승계가 거절됐습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0102:1 | 액정 깨진 폰 바꾸러 갔는데 안 된대요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0089:1 | 배터리 때문에 새로 샀는데 할부 기간을 잘못 골랐어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0096:1 | 분실 신고 후 찾은 새 폰을 반품하려는데 15일째입니다 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-DEVICE-0105:1 | 기기 바꿨는데 이자가 붙어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0109:1 | 24개월이랑 30개월이랑 이자율이 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0119:1 | 배터리 수리 시 보험 가입자와 미가입자의 부담 차이는 얼마나 되나요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-DEVICE-0130:1 | 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0111:1 | 분실이랑 파손이랑 처리가 어떻게 달라요? | COMPLETED | UNSCORED | None |  |
| PIPE-DEVICE-0116:1 | 반품과 교환은 조건이 서로 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0129:1 | 배터리 때문에 새로 사는데 할부 길게 하는 게 나아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0110:1 | 기기변경이랑 새 회선 개통이랑 뭐가 달라요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-DEVICE-0117:1 | 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0004:1 | 회사 이름으로 폰 몇 개까지 만들 수 있어요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0012:1 | 자녀 명의 회선을 포함해 성인 1인이 개통 가능한 회선 수를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0019:1 | 가입하면 언제부터 되나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0007:1 | 애 폰 온라인으로 신청하면 언제 개통돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0013:1 | 외국인 가입 시 여권 외에 필요한 서류를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0022:1 | 손주가 열세 살인데 폰 만들어줄 수 있어요 | COMPLETED | SUPPORTED | False | OVER_REFUSAL |
| PIPE-SUBSCRIBE-0005:1 | 두 번째 폰 만들 때도 신분증 가져가야 해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0015:1 | 세컨드폰 매장 개통 소요 시간을 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SUBSCRIBE-0049:1 | 회사 폰 만들 때 뭐 가져가요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0036:1 | 첫 개통인데 회선 한도는 어떻게 확인해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0041:1 | 첫 개통 시 신분증 확인 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0052:1 | 자식들 폰 다 만들려면 몇 개까지 돼요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0029:1 | 휴대폰 새로 만들려는데 신분증 말고 또 뭐 챙겨야 하나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0037:1 | 개통 시 지참해야 하는 신분증 종류를 정리해서 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0051:1 | 처음 만드는 건데 매장 가면 바로 돼요 | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SUBSCRIBE-0033:1 | 외국인 친구가 개통하려면 서류를 어떻게 준비해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0077:1 | 아들 대신 제가 가서 아들 폰 만들 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0067:1 | 자녀 회선 온라인 신청 후 당일 개통이 가능한가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0061:1 | 첫 개통인데 주민등록증이 없으면 안 될까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0079:1 | 회사 폰 오늘 바로 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0068:1 | 외국인 법인 대표도 별도 심사 대상인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0056:1 | 회선을 하나 더 만들 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0081:1 | 운전면허증만 있는데 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0066:1 | 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0094:1 | 법인 명의로 미성년 인턴 회선을 만들려다 거절됐습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0105:1 | 폰 하나 더 하려는데 신분증 때문에 안 된대요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0088:1 | 외국인인데 회선을 추가하려니 한도라고 해요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0095:1 | 세컨드폰을 20:00 넘어서 신청했는데 처리가 안 됩니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0099:1 | 신청했는데 왜 오늘 안 되나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-SUBSCRIBE-0084:1 | 회사 이름으로 폰 만들려는데 심사 때문에 안 된대요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0096:1 | 첫 개통인데 회선 한도 초과라고 나옵니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0101:1 | 처음 만드는데 신분증이 안 된대요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0109:1 | 처음 폰을 만드는 건데 신분증은 뭘 가져가야 하나요? 운전면허증도 되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0117:1 | 자녀 동행 개통과 본인 단독 개통은 신분증 요건이 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0127:1 | 아들 폰 매장이 빨라요 인터넷이 빨라요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SUBSCRIBE-0112:1 | 애 명의 회선이랑 제 명의 회선이랑 한도가 달라요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0119:1 | 법인 회선은 매장과 온라인 중 어느 쪽이 빠른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0123:1 | 매장이랑 인터넷 중 어디가 빨리 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0110:1 | 세컨드폰을 제 명의로 하는 거랑 애 명의로 하는 거랑 서류가 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0122:1 | 자녀 명의와 부모 명의 중 어느 쪽으로 개통하는 게 서류가 간단한가요? | COMPLETED | UNSCORED | None |  |
| PIPE-PORTING-0022:1 | 밀린 요금 있으면 못 옮겨요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0014:1 | 번호이동 신청 자격 조건에 미납 여부가 포함되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0001:1 | 번호이동 신청하면 얼마나 걸려요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0024:1 | 옮기고 나서 무를 수 있는 기간이 얼마예요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0010:1 | 번호이동 신청 시 필요한 서류를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0002:1 | 번호이동할 때 신분증 말고 또 뭐 있어야 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0023:1 | 명의 바꾸고 바로 옮길 수 있어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0015:1 | 번호이동 제한 기간 기준일이 개통일인가요, 명의변경일인가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0032:1 | 옮긴 걸 없던 일로 하려면 누구한테 말해요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PORTING-0048:1 | 옮겼는데 다시 돌아가려면 어떻게 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PORTING-0033:1 | 번호이동 처리 절차와 소요 시간을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0028:1 | 통화가 자꾸 끊겨서 원래 통신사로 돌아가고 싶은데 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0043:1 | 번호이동 자격이 언제 생기는지 궁금합니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0034:1 | 미납 요금이 있을 때 번호이동 진행 순서를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0029:1 | 번호이동 신청하면 그다음에 뭘 해야 돼요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-PORTING-0044:1 | 옮긴 거 무르려면 어떻게 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PORTING-0052:1 | 이동한 지 일주일 됐는데 취소할 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0060:1 | 철회 후 원래 통신사 요금제도 그대로 복원되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0065:1 | 토요일에도 번호이동 되나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0053:1 | 밤에 신청해도 다음 날 아침에 바로 될까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0058:1 | 기존 통신사에 미납금이 있어도 신청할 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0071:1 | 개통 두 달인데 되나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-PORTING-0049:1 | 다른 데서 번호 그대로 옮겨오려는데 저녁 늦게 신청해도 그날 바로 되나요? | COMPLETED | UNSCORED | None |  |
| PIPE-PORTING-0061:1 | 20:00 직전에 신청하면 당일 처리가 가능한가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0085:1 | 일요일 오후에 신청했는데 진행이 안 됩니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0073:1 | 번호이동 신청한 지 3시간 넘었는데 아직 안 됩니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0091:1 | 새 폰 샀는데 번호를 못 옮긴대요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0081:1 | 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0078:1 | 결합 해지했는데 번호이동이 막혔어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0092:1 | 옮겼는데 전화가 잘 안 돼요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0087:1 | 기기변경한 지 얼마 안 됐다고 번호이동이 안 된답니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0077:1 | 신청한 지 하루가 넘었는데 아직도 안 됐어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL, RETRIEVAL_MISS |
| PIPE-PORTING-0111:1 | 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요. | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0107:1 | 개통 후 3개월과 명의변경 후 1개월 중 어느 조건이 더 자주 걸리나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0098:1 | 미납 요금 먼저 내고 오는 거랑 그냥 오는 거랑 뭐가 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0113:1 | 빨리 되는 날이랑 늦는 날이랑 있어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-PORTING-0105:1 | 평일과 주말의 번호이동 처리 시간이 다른가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0097:1 | 아침에 신청하는 거랑 저녁에 신청하는 거랑 뭐가 빨라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0110:1 | 밀린 요금 있는 거랑 없는 거랑 뭐가 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PORTING-0102:1 | 미납 요금이 있을 때랑 없을 때 신청 절차가 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0003:1 | 폰을 잃어버려서 해지하면 할부금은 어떻게 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0013:1 | 분실 후 해지 시 요금 정산 기준을 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0020:1 | 안 쓰는 동안 요금 줄일 수 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0001:1 | 번호 해지는 어떻게 해요? | COMPLETED | UNSCORED | None |  |
| PIPE-TERMINATE-0009:1 | 장기 해외 체류로 해지할 때 신청 채널과 처리 시점을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0021:1 | 군대 가는 아들 폰 어디서 해지해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0004:1 | 한동안 안 쓸 건데 정지시킬 수 있어요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0010:1 | 약정 중간에 해지하면 위약금이 얼마나 나오나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-TERMINATE-0043:1 | 폰 잃어버려서 해지하는데 할부는 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0037:1 | 해외 체류 중 온라인 해지 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0031:1 | 입대 전에 할부 남은 폰을 해지하려면 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0041:1 | 해지하려면 어디로 가야 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0033:1 | 분실한 단말의 회선을 해지하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0029:1 | 해외에 오래 있을 건데 해지는 어떤 순서로 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0047:1 | 안 쓰는 폰 해지할 때 할부 남은 건 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0034:1 | 약정 반환금 계산 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0071:1 | 군대 가는 아들 폰 해지하면 할부 계속 나눠 낼 수 있나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-TERMINATE-0049:1 | 해외 나가 있는 동안 홈페이지로 해지할 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0058:1 | 11개월째에 해지하면 위약금을 다 물어야 하나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0069:1 | 외국에서 전화로 해지할 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0056:1 | 군 복무 기간 내내 일시 정지를 걸 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0061:1 | 입대 당일에 해지 신청해도 바로 처리되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0068:1 | 일 년에 몇 번이나 정지할 수 있어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0053:1 | 폰을 잃어버렸는데 매장 안 가고 해지할 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0075:1 | 해지하려는데 할부금 한 번에 내라고 해서 부담돼요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0093:1 | 폰 잃어버려서 해지하려는데 어디로 해요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-TERMINATE-0084:1 | 30일 정지가 끝났는데 자동으로 다시 정지되지 않습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0073:1 | 군대를 가는데 쓰던 번호를 어떻게 해야 할지 모르겠어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-TERMINATE-0090:1 | 요금 부담돼서 해지했는데 위약금이 더 나와요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0085:1 | 해지 신청했는데 다음 달 요금이 청구됐습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0080:1 | 정지시켰는데 요금이 반이나 나와요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0092:1 | 정지 중인데 청구서가 왔어요 | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| PIPE-TERMINATE-0107:1 | 장기 미사용 회선 해지 시 할부 유지와 일시 납부는 절차가 다른가요? | COMPLETED | UNSCORED | None |  |
| PIPE-TERMINATE-0113:1 | 폰 잃어버렸는데 매장 가는 거랑 전화하는 거랑 뭐가 빨라요 | COMPLETED | NOT_APPLICABLE | None | HUMAN_REVIEW |
| PIPE-TERMINATE-0095:1 | 한 번에 내는 거랑 나눠 내는 거랑 뭐가 더 나아요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0105:1 | 요금 부담으로 해지할 때 홈페이지와 고객센터 중 어느 쪽이 빠른가요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-TERMINATE-0115:1 | 요금 부담돼서 해지하는데 할부 한 번에 내는 게 나아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0096:1 | 정지하는 거랑 아예 해지하는 거랑 뭐가 더 나아요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-TERMINATE-0104:1 | 장기 미사용이면 일시 정지와 해지 중 어느 쪽이 유리한가요? | COMPLETED | UNSCORED | None |  |
| PIPE-TERMINATE-0112:1 | 오래 안 쓰면 정지가 나아요 해지가 나아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0024:1 | 새 폰으로 바꾸면 옛날 유심은 자동으로 없어져요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0012:1 | 유심 분실 신고는 어디로 하나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-USIM-0007:1 | 듀얼심이 뭐예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-USIM-0020:1 | 유심 없어지면 어떻게 되나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0011:1 | eSIM 발급 비용과 지원 조건을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0005:1 | 해외 나가기 전에 유심 새로 받으면 택배로 며칠 걸려요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0018:1 | 유심 받을 때 신분증 꼭 있어야 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0016:1 | 분실 신고 후 재발급하면 기존 유심은 자동 해지되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0027:1 | 유심이 깨져서 eSIM으로 바꾸려면 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0035:1 | 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0041:1 | 재발급 신청 절차를 순서대로 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| PIPE-USIM-0028:1 | 유심을 잃어버렸을 때 정지시키는 방법 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-USIM-0033:1 | 인식이 안 되는 유심을 재발급하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0046:1 | 유심 잃어버려서 아들이 대신 받으려면 뭐 줘야 해요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0030:1 | 해외에서 부모님이 대신 유심을 받아 보내려면 뭘 준비해야 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0036:1 | 유심 분실 시 처리 절차를 알려주세요. | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-USIM-0054:1 | 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0067:1 | 제 폰에서도 eSIM 될까요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0058:1 | 유심이 파손돼서 대리인이 재발급받으려면 어떤 서류가 필요한가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0049:1 | 기기변경 때 유심을 택배로 받을 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0072:1 | 유심 깨졌는데 신고해야 하나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0060:1 | 해외에서 홈페이지로 분실 신고가 가능한가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0052:1 | 유심 잃어버렸는데 바로 정지시킬 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0065:1 | 외국 가기 전에 유심 바로 받을 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0086:1 | 분실 재발급을 대리인에게 부탁했는데 가입자 신분증 원본이 없어 거절됐습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0089:1 | 새 폰인데 유심이 안 맞아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0077:1 | 유심이 부러졌는데 오늘 급하게 써야 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0083:1 | 기존 유심이 자꾸 오류가 나서 eSIM으로 바꾸고 싶은데 제 폰이 지원하는지 모르겠어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0092:1 | 유심이 깨져서 새로 받았는데 예전 게 아직 되나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0075:1 | 외국에서 산 폰인데 eSIM이 안 잡혀요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0085:1 | 출국 이틀 전인데 온라인으로 신청한 유심이 못 올 것 같습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0094:1 | 새 폰 유심 받으려는데 뭐 가져가야 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0097:1 | 유심 깨졌을 때 매장이랑 택배 중 뭐가 나아요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0104:1 | 기기변경 시 분실 신고를 하는 것과 재발급만 하는 것은 어떻게 다른가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-USIM-0114:1 | 내가 가는 거랑 자식 보내는 거랑 서류가 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0099:1 | 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요? | COMPLETED | UNSCORED | None |  |
| PIPE-USIM-0108:1 | 인식 불량 유심은 분실 처리와 재발급 중 어느 쪽으로 진행하나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0112:1 | 깨진 유심은 신고를 해요 새로 받아요 | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0095:1 | 물리 유심이랑 eSIM이랑 비용이 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0106:1 | 분실 재발급에서 본인 방문과 대리인 방문은 서류가 얼마나 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0008:1 | 미성년 자녀 회선을 성인이 된 시점에 본인 명의로 바꿀 때 소요 시간과 비용을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0014:1 | 명의 바꾸는 데 오래 걸려요? | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| PIPE-NAME_CHANGE-0005:1 | 엄마 폰 제 이름으로 바꾸는 데 얼마나 걸려요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0007:1 | 명의변경 시 필요한 서류를 정리해서 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0015:1 | 남편 폰 제 이름으로 하는데 할부 남았으면 어떻게 돼요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0002:1 | 명의변경 하면 돈 들어요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0009:1 | 할부금이 남아 있으면 신용 심사는 누가 받나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0017:1 | 명의 바꾸는 데 돈 들어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0024:1 | 미성년자한테 폰 명의 넘기는 절차가 어떻게 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0030:1 | 퇴사자에게 회사 회선을 넘길 때 할부 잔액이 있으면 어떻게 진행되나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-NAME_CHANGE-0036:1 | 미성년자 손주한테 넘길 수 있어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-NAME_CHANGE-0020:1 | 미성년 자녀가 성인이 됐는데 명의를 자녀 앞으로 바꾸는 순서를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0027:1 | 미성년자 간 명의변경 절차를 알고 싶습니다. | COMPLETED | SUPPORTED | False | OVER_REFUSAL |
| PIPE-NAME_CHANGE-0032:1 | 명의변경 신청하면 어떻게 처리돼요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0021:1 | 신용 심사에서 떨어지면 명의변경이 아예 안 되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0025:1 | 가족 간 명의변경 시 방문과 서류 절차를 정리해 주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0038:1 | 미납 요금 있어도 명의변경 될까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0054:1 | 미성년자끼리 명의변경 되나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0048:1 | 만 17세 동생에게 제 회선을 넘길 수 있나요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-NAME_CHANGE-0039:1 | 핸드폰 값 남았는데 이름 바꿀 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0049:1 | 아들이 대신 가서 명의 바꿔올 수 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0044:1 | 당일에 바로 처리가 가능한가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0042:1 | 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0051:1 | 할부금이 남아 있어도 명의 바꿀 수 있나요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0058:1 | 퇴사했는데 회사 담당자가 매장에 같이 못 온대요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-NAME_CHANGE-0063:1 | 명의변경 신청했는데 신용 심사에서 거절됐습니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0068:1 | 수수료를 내라고 하는데 원래 그런가요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-NAME_CHANGE-0057:1 | 형 폰을 제 명의로 바꾸려는데 심사에서 막혔대요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0065:1 | 배우자 명의로 바꿨는데 처리가 30분 넘게 걸리고 있습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0070:1 | 회사가 폐업해서 양도인이 없어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0059:1 | 명의변경 하러 갔는데 요금 밀린 게 있다고 안 해줘요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0061:1 | 아버지 신분증만 가지고 갔는데 처리가 안 됐습니다 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-NAME_CHANGE-0086:1 | 명의변경 빨리 되나요 오래 걸리나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0083:1 | 미납 요금을 먼저 내고 명의변경하는 것과 명의변경 후에 내는 것 중 어느 쪽인가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0076:1 | 엄마가 오는 거랑 위임장 받아오는 거랑 뭐가 더 확실해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-NAME_CHANGE-0088:1 | 내가 가는 거랑 위임장 주는 거랑 뭐가 빨라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0079:1 | 직접 방문과 대리 방문 중 어느 쪽이 서류가 더 간단한가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0075:1 | 할부 있는 폰이랑 없는 폰이랑 명의변경 시간이 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0085:1 | 딸이랑 같이 가는 거랑 딸 혼자 보내는 거랑 뭐가 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0081:1 | 할부금이 없을 때랑 있을 때 명의변경 절차가 어떻게 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0005:1 | 로밍 신청됐는지 어디서 확인해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0014:1 | 워치도 로밍 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0010:1 | 현지 망 자동 연결에 보통 얼마나 걸리나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0002:1 | 로밍 신청하고 얼마나 기다려야 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0018:1 | 로밍 차단 풀려면 뭐 해야 돼요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-ROAMING-0011:1 | 데이터 무제한 로밍 요금이 어떻게 되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0006:1 | 7일 로밍권은 얼마예요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0013:1 | 로밍 요금 한도가 얼마예요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0025:1 | 현지 도착 후 로밍 설정 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0032:1 | 로밍 신청은 어디서 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-ROAMING-0022:1 | 로밍은 어떻게 신청해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0027:1 | 고객센터 전화로 로밍을 신청하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0034:1 | 손목시계도 로밍 신청해요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-ROAMING-0021:1 | 로밍 요금제는 어떻게 골라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0030:1 | 로밍이 안 잡힐 때 확인 순서를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0031:1 | 오래 나가 있을 건데 로밍은 뭘로 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-ROAMING-0041:1 | 3일만 가는데 일 단위 요금제로도 충분할까요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-ROAMING-0037:1 | 출장 당일 아침에 신청해도 될까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0046:1 | 일주일 여행인데 무제한으로 할 수 있나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-ROAMING-0045:1 | 도착 직후 바로 데이터를 쓸 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0039:1 | 로밍 데이터를 다른 회선이랑 나눠 쓸 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0048:1 | 차단돼도 전화는 되나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0044:1 | 듀얼번호를 쓰면 요금이 추가되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0038:1 | 차단된 데이터를 다시 쓸 수 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-ROAMING-0054:1 | 현지 거래처가 현지 번호를 달라는데 유심을 따로 사야 하나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0060:1 | 로밍 켰는데 10분이 지나도 연결이 안 돼요 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-ROAMING-0064:1 | 스마트워치 샀는데 인터넷이 안 잡혀요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-ROAMING-0051:1 | 로밍 요금이 얼마나 나올지 걱정돼요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0058:1 | 로밍 데이터가 갑자기 안 됩니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0062:1 | 신청했는데 아직 안 돼요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-ROAMING-0053:1 | 현지에서 영상 보다가 갑자기 인터넷만 안 되고 전화는 돼요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-ROAMING-0056:1 | 보름 동안 로밍을 썼는데 요금이 생각보다 많이 나왔습니다 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-ROAMING-0078:1 | 막혔을 때 본인 인증 하는 거랑 그냥 두는 거랑 뭐가 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0073:1 | 차단 전이랑 후랑 이용할 수 있는 게 달라지나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0069:1 | 폰 로밍만 하면 워치도 같이 되는 거예요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0077:1 | 집에서 신청하는 거랑 공항에서 신청하는 거랑 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0071:1 | 한 달 정도 있을 예정인데 일 단위랑 무제한 중 뭐가 유리한가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-ROAMING-0070:1 | 앱에서 보는 거랑 홈페이지에서 보는 거랑 로밍 가입 정보가 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0076:1 | 하루짜리 며칠 쓰는 거랑 일주일권이랑 뭐가 싸요 | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-ROAMING-0074:1 | 워치와 태블릿 로밍은 각각 따로 신청해야 하나요, 하나로 묶이나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0014:1 | 신고하면 확인하는 데 며칠 걸려요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0009:1 | 명의도용 조사 기간이 얼마나 되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0003:1 | 매장 문 여는 시간이 몇 시예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0013:1 | 매장 몇 시까지 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0006:1 | 부가서비스는 가입하면 언제부터 쓸 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0005:1 | 가입한 거 증명하는 서류 뗄 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0015:1 | 증명서는 몇 년 치까지 나와요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0008:1 | 직영 매장 토요일 영업시간을 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0016:1 | 부가서비스 가입이랑 해지는 어떻게 해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0026:1 | 부가서비스 없애려면 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0021:1 | 부가서비스 해지 시 요금 처리 흐름을 설명해 주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0019:1 | 내 명의로 모르는 회선이 있는 것 같은데 신고 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0029:1 | 명의도용 신고는 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0025:1 | 해지확인서를 발급받는 방법을 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0020:1 | 증명서는 어떻게 발급받아요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-SERVICE-0027:1 | 멤버십 등급 확인은 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0033:1 | 평일 저녁 7시 넘어서 매장에 갈 수 있을까요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0036:1 | 부가서비스를 가입한 당일에 해지해도 그 달 요금이 나오나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0041:1 | 부가서비스 해지하고 다시 가입할 수 있나요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0032:1 | 올해 요금을 많이 내면 바로 등급이 올라갈 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0035:1 | 해지한 지 2년 된 회선도 증명서 발급되나요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-SERVICE-0038:1 | 공휴일에도 매장 열어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0031:1 | 부가서비스 해지하면 그 달 요금은 안 나가나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0034:1 | 신고하면 요금을 다 돌려받을 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0042:1 | 멤버십 등급이 왜 갑자기 내려갔는지 모르겠어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0053:1 | 토요일 오후 늦게 갔더니 닫혀 있었어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0046:1 | 해지 신청했는데 아직도 서비스가 이용되고 있어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0044:1 | 신고했는데 며칠째 연락이 없어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0051:1 | 부가서비스 해지했는데 요금이 또 나왔어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| PIPE-SERVICE-0049:1 | 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0043:1 | 일요일에 갔는데 매장이 닫혀 있었어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-SERVICE-0052:1 | 멤버십 등급이 왜 바뀌었어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SERVICE-0058:1 | 평일이랑 토요일 운영시간이 어떻게 다른가요? | FAILED | UNSCORED | False | PROCESSING_FAILURE |
| PIPE-SERVICE-0062:1 | 등급이 몇 단계나 있고 뭘로 정해져요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0056:1 | 부가서비스 가입할 때랑 해지할 때랑 요금 계산이 달라요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0061:1 | 가입이랑 해지 중에 어느 쪽이 더 빨리 적용되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0065:1 | 홈페이지로 받는 거랑 매장에서 받는 거랑 증명서가 달라요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0055:1 | 증명서 종류마다 발급 방법이 달라요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-SERVICE-0060:1 | 가입사실확인서와 요금납부확인서는 발급 조건이 다른가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0063:1 | 토요일에 가도 됩니까. 평일이랑 시간이 다른가요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-COMPOUND-001:1 | 한도를 월 100만 원 이상으로 올릴 수 있나요 그리고 분실로 해지할 때 위약금 확인 절차를 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-002:1 | 요금이 부담돼서 일부만 내도 정지가 풀리나요 그리고 일시 정지 중에도 요금이 부과되나요? | COMPLETED | UNSCORED | None | RETRIEVAL_MISS |
| PIPE-COMPOUND-003:1 | 본인 인증 안 하면 휴대폰 결제 얼마까지 돼요 그리고 1회 정지 기간을 30일보다 길게 설정할 수 있나요? | FAILED | UNSCORED | False | PROCESSING_FAILURE, RETRIEVAL_MISS |
| PIPE-COMPOUND-004:1 | 요금 밀렸는데 청구서 방법 바꿀 수 있나요 그리고 위약금 얼마 나오는지 미리 알려면 어떻게 해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-005:1 | 요금제 바꾸면 그 달 요금은 어떻게 계산돼요 그리고 폰 잃어버려서 해지하면 위약금 있어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-006:1 | 데이터 다 썼다고 문자 오면 어떻게 해요 그리고 해외 단말도 eSIM 발급이 되나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-007:1 | 가족 모두 5G 언리미티드로 바꿀 수 있나요 그리고 유심 재발급을 대리인에게 맡길 때 절차를 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-008:1 | 안 쓴 데이터가 다음 달로 얼마나 넘어가요 그리고 eSIM으로 회선 두 개를 동시에 쓸 수 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-009:1 | 연령 제한 없는 요금제만 따로 있나요 그리고 폰을 잃어버려서 eSIM으로 새로 받고 싶은데 어떻게 해요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-010:1 | 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-011:1 | 이미 한 번 교환했는데 다시 교환할 수 있을까요 그리고 대학생인 제가 부모님 명의 폰을 제 명의로 바꿀 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-012:1 | 기기변경 전에 파손 수리를 받으려면 어떻게 해요 그리고 명의변경 신청하고 나서 얼마나 기다리면 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-013:1 | 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-014:1 | 기기변경 시 새 할부 계약 절차를 알려주세요. 그리고 미성년자 명의 회선을 다른 미성년자에게 넘길 수 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-015:1 | 액정 깨졌을 때 수리는 어떻게 받나요 그리고 성인 된 자녀 명의로 폰을 넘기려면 뭐가 필요해요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-016:1 | 자녀 명의로 가입하려는데 나이 제한이 어떻게 되나요 그리고 가족이랑 로밍 데이터 나눠 쓰려면 어떻게 신청해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-017:1 | 자녀 명의 개통 절차를 단계별로 알려주세요. 그리고 요금 부담돼서 그런데 하루만 로밍 쓸 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-018:1 | 첫 개통을 온라인으로 하면 처리 시간이 늦어지나요 그리고 듀얼번호 신청 절차와 비용을 알려주세요. | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, OVER_REFUSAL, RETRIEVAL_MISS |
| PIPE-COMPOUND-019:1 | 법정대리인 동의가 필요한 연령 구간을 알려주세요. 그리고 로밍 데이터 나눠쓰기는 몇 회선까지 가능한가요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-020:1 | 법인 명의 개통 시 담당자 여권만으로 가능한가요 그리고 공항 도착 후에 신청해도 늦지 않나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-021:1 | 번호이동 최대 소요 시간을 알려주세요. 그리고 부가서비스 해지하면 언제까지 쓸 수 있어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-022:1 | 개통한 지 두 달 됐는데 옮길 수 있나요 그리고 명의도용 신고 후 요금 취소까지 어떤 단계를 거치나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-023:1 | 철회 가능 기간의 기산일은 언제인가요 그리고 부가서비스 신청하면 오늘부터 바로 되는 거예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-024:1 | 번호이동 철회 시 회선은 어떻게 처리되나요 그리고 멤버십 등급 산정 기간을 제가 지정할 수 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-025:1 | 번호이동 하고 싶은데 지금 조건이 되는지 어떻게 확인해요 그리고 4년 전 요금 낸 기록이 필요한데 안 나와요 | COMPLETED | SUPPORTED | None | RETRIEVAL_MISS |
| PIPE-COMPOUND-026:1 | 일시 정지 연간 한도가 어떻게 되나요 그리고 청구서를 이메일이랑 우편 둘 다 받을 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-027:1 | 오래 안 쓸 건데 정지 석 달 되나요 그리고 청구서 안 왔는데 요금 안 내면 어떻게 돼요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-028:1 | 장기 미사용 회선 해지하면서 할부금은 어떻게 처리해요 그리고 소액결제 한도 변경이 즉시 반영되나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-029:1 | 전화나 홈페이지로도 해지 신청이 가능한가요 그리고 요금 내는 방법이 네 가지라는데 뭐뭐예요? | COMPLETED | UNSCORED | None | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-030:1 | 오래 안 쓸 건데 정지하면 요금 얼마 나와요 그리고 청구서에서 이중 납부를 발견했을 때 다음 달 차감으로 처리하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-COMPOUND-031:1 | 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-032:1 | 새 폰 사서 유심 바꾸면 옛날 건 어떻게 해요 그리고 손주 데이터가 느려졌다는데 왜 그래요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-033:1 | 외국 가기 전에 유심 새로 받으려면 어떻게 해요 그리고 부모님 시니어 요금제 가입 시 필요한 서류를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-034:1 | 유심 잃어버렸는데 폰 없이도 정지시킬 수 있나요 그리고 가족이 다 같이 쓸 요금제를 고르려면 어떻게 봐야 해요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-035:1 | 새 단말로 바꾸면서 eSIM으로 전환하는 절차를 알려주세요. 그리고 남은 데이터 다음 달에 쓸 수 있어요 | COMPLETED | UNSCORED | None | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-036:1 | 법인 회선을 개인 명의로 전환할 때 구비 서류를 알려주세요. 그리고 사용하다가 마음에 안 들면 교환할 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-037:1 | 퇴사하면서 회사폰 명의를 제 앞으로 가져오려는데 할부가 남아 있어요. 조건이 뭐예요 그리고 액정 깨져서 새로 사면 30개월 되나요 | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| PIPE-COMPOUND-038:1 | 자식한테 폰 넘기는데 할부가 남았으면 어떻게 해요 그리고 분실 신고 후 다시 찾으면 정지를 풀 수 있나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-039:1 | 회사 폰 내 걸로 만들려면 어떻게 해요 그리고 액정 파손 후 새 단말 할부 수수료율을 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-040:1 | 회사 담당자가 대리로 와서 양도 처리를 해도 되나요 그리고 배터리 교체 비용도 할부에 포함할 수 있나요? | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| PIPE-COMPOUND-041:1 | 해외에서 번호 두 개 쓸 수 있어요 그리고 외국인인데 개통하면 얼마 만에 쓸 수 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-042:1 | 해외 나갈 때 데이터 하루에 얼마예요 그리고 법인 명의로 미성년 직원 회선을 만들 수 있는 절차가 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-043:1 | 출국하기 전에 로밍 가입됐는지 확인하려면 어떻게 해요 그리고 자녀 대신 제 신분증으로 자녀 폰을 만들 수 있을까요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-044:1 | 로밍 요금 자동 차단 해제 절차를 알려주세요. 그리고 부모님 동의 없이 고등학생이 개통할 수 있을까요? | FAILED | UNSCORED | False | PROCESSING_FAILURE, RETRIEVAL_MISS |
| PIPE-COMPOUND-045:1 | 앱 없이도 로밍 됐는지 알 수 있나요 그리고 법인 회선 개통 소요 시간과 처리 시간대를 알려주세요. | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-COMPOUND-046:1 | 멤버십 등급 산정과 갱신 절차를 알려주세요. 그리고 번호이동 서류 준비 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-COMPOUND-047:1 | 매장에 갈 건데 시간을 어떻게 맞추면 돼요 그리고 결합 해지하고 번호이동하려면 순서가 어떻게 돼요? | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-COMPOUND-048:1 | 제 명의로 누가 개통한 것 같아요. 신고하면 어떻게 되나요 그리고 다른 회사로 번호 가져가는 거 어떻게 하는 거예요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-049:1 | 증명서 인터넷으로 바로 뗄 수 있어요 그리고 개통하고 보름 지났는데 철회할 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-050:1 | 내 멤버십 등급이 어떻게 정해지는지 순서대로 설명해 주세요. 그리고 번호이동 신청부터 완료까지 흐름을 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-OUT-001:1 | 이번 주 나스닥 지수는 얼마나 올랐나요? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-002:1 | 김치찌개를 맛있게 끓이는 방법 알려줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-003:1 | 고려 시대의 수도는 어디였나요? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-004:1 | x의 제곱을 미분하면 무엇인가요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-005:1 | 어제 프로야구 경기 결과가 뭐야? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-006:1 | 주말에 볼 만한 영화 추천해줘 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-007:1 | 피아노를 혼자 조율하는 법을 알려줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-008:1 | 베란다 화분에 물을 얼마나 자주 줘야 해? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-009:1 | 고양이 사료를 하루에 얼마나 줘야 할까? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-010:1 | 오늘 K리그 득점 선두는 누구야? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-011:1 | 이 문장을 프랑스어로 번역해줘: 안녕하세요 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-012:1 | 야간 사진을 찍을 때 셔터 속도는 어떻게 잡아? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-013:1 | 마라톤 준비를 위한 러닝 계획을 짜줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-014:1 | 최근 지진이 발생한 지역을 알려줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-015:1 | 게이밍 노트북 GPU는 어떻게 골라? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-016:1 | 장거리 비행기 좌석은 어디가 편해? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-017:1 | 아파트 전세 계약할 때 주의할 점은? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-018:1 | 주식 매도 시점은 어떻게 정하면 좋아? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-019:1 | 한국사 시험 공부 순서를 추천해줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-020:1 | 체스에서 퀸을 잘 활용하는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-021:1 | 북극성과 다른 별을 구분하는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-022:1 | 파스타 면은 보통 몇 분 삶아야 해? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-023:1 | 조선 세종대왕의 즉위 연도는 언제야? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-024:1 | 삼각형의 넓이는 어떻게 계산하나요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-025:1 | 올해 프로농구 우승팀은 누구야? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-026:1 | 수채화 물감의 번짐을 줄이는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-027:1 | 기타 줄을 새로 교체하는 순서를 알려줘 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-028:1 | 실내 바질 잎이 노랗게 변하는 이유는? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-029:1 | 강아지 산책은 하루에 몇 번 하는 게 좋아? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-030:1 | 세계에서 가장 높은 산의 높이는 얼마야? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-031:1 | 독일어로 감사합니다를 어떻게 말해? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-032:1 | 카메라 조리개 값은 뭘 뜻하나요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-033:1 | 등산화는 발에 어떻게 맞춰 골라야 해? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-034:1 | 목성의 위성은 몇 개인가요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-035:1 | 가정용 커피 원두는 어떻게 보관해? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-036:1 | 해외여행 여권 유효기간은 얼마나 남아야 해? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-037:1 | 집에서 벽지 얼룩을 지우는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-038:1 | 채권 가격과 금리의 관계를 설명해줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-039:1 | 영어 듣기 공부를 매일 어떻게 하면 좋아? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-040:1 | 바둑에서 포석을 연습하는 방법을 알려줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-041:1 | 달 표면의 중력은 지구와 얼마나 다른가요? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-042:1 | 떡볶이 양념을 덜 맵게 만드는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-043:1 | 로마 제국의 수도는 어디였나요? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-044:1 | 원기둥의 부피는 어떻게 구하나요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-045:1 | 배구 경기의 세트 승리 조건은 뭐예요? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-046:1 | 붓글씨를 처음 배울 때 필요한 도구는? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-047:1 | 드럼 스틱 잡는 방법을 알려줘 | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-048:1 | 장미 화분의 가지치기는 언제 하나요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-049:1 | 반려견 목욕 주기는 어느 정도가 좋아요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-050:1 | 아마존강의 길이는 얼마나 되나요? | COMPLETED | NOT_APPLICABLE | True |  |
| REG-FAQ_FACT:1 | 가상계좌가 무엇인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| REG-FAQ_CONDITION:1 | 소액결제 한도 얼마까지 돼요? | COMPLETED | SUPPORTED | True |  |
| REG-NO_EVIDENCE:1 | 유심 재발급하면 택배비는 따로 내나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| REG-MIXED:1 | 유심 재발급 비용은 얼마고 배송비도 내야 하나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| REG-UNRELATED:1 | 오늘 서울 날씨 어때? | COMPLETED | NOT_APPLICABLE | True |  |
| REG-MULTI_TURN:1 | 요금제는 한 달에 몇 번 바꿀 수 있어요? | COMPLETED | SUPPORTED | True |  |
| REG-MULTI_TURN:2 | 가입한 달에도 돼요? | COMPLETED | SUPPORTED | True |  |
| REG-FAQ_COMPOUND:1 | 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| REG-STORE:1 | 강남역 근처 유플러스 매장 알려줘 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, STORE_NOT_CONNECTED |
