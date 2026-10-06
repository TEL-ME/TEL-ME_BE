# 실제 채팅 파이프라인 답변 품질 측정

Spring 채팅 API가 생성하고 저장한 최종 답변을 vLLM Qwen3-14B-AWQ로 평가했다.
점수는 이 고정 평가 질문에서의 자동 판정 결과이며 실제 이용자 질문 분포의 전체 서비스 환각률로 일반화하지 않는다.
근거 없는 주장은 실제 RAG 입력에 포함된 근거를 기준으로 판단한다. 참인 외부 지식도 근거가 없으면 이 정책에서는 통과하지 않는다.

| 지표 | 결과 |
| --- | --- |
| 질문 수 | 40/40 |
| 실행 완료율 | 100.0% (40/40) |
| 답변 품질 통과율 | 60.53% (23/38) |
| 답변 가능 질문의 완전 답변율 | 72.22% (26/36) |
| 사실 답변의 근거 없는 답변 발생률 | 26.67% (8/30) |
| 사실 주장 단위 근거 부족률 | 16.67% (11/66) |
| 전체 질문 중 근거 없는 답변 발생 | 20.0% (8/40) |
| 근거 미확정 건수를 포함한 전체 질문 기준 발생률 범위 | [20.0, 25.0]% |
| 근거 판정 가능률 | 95.0% (38/40) |
| 정답 근거 포함: 검색 후보 / RAG 입력 / 저장 근거 | 77.5% (31/40) / 77.5% (31/40) / 77.5% (31/40) |
| 세 축 완전 채점 / 미채점 / 사람 검토 | 39 / 1 / 1 |
| 품질 미확정 2건을 고려한 통과율 범위 | [57.5, 62.5]% |
| 파이프라인 P50 / P95 | 1320 / 2358 ms |

품질은 필요한 내용을 정확히 답하거나 적절히 보류했으며 근거 없는 단정이 없는 경우에 통과한다.
미채점 또는 모호한 판정은 정상 답변으로 처리하지 않는다. 축별로 유효한 판정을 보존한다.

| 사례 | 질문 | 실행 | 근거성 | 품질 통과 | 발견 사항 |
| --- | --- | --- | --- | --- | --- |
| PIPE-BILLING-0087:1 | 청구서를 못 받았다는 이유로 정지가 유예되나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0084:1 | 모바일 알림 청구서로 바꾸면 요금이 붙나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-BILLING-0155:1 | 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| PIPE-PLAN-0060:1 | 요금제 뭐가 있는지 어떻게 알아요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PLAN-0081:1 | 데이터 조금 쓰는데 싼 거 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-PLAN-0116:1 | 5G 요금제 이름 좀 알려줘요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0008:1 | 침수된 폰은 교환 대상이 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0130:1 | 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요 | COMPLETED | SUPPORTED | True |  |
| PIPE-DEVICE-0117:1 | 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-SUBSCRIBE-0067:1 | 자녀 회선 온라인 신청 후 당일 개통이 가능한가요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0066:1 | 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SUBSCRIBE-0088:1 | 외국인인데 회선을 추가하려니 한도라고 해요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0081:1 | 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-PORTING-0077:1 | 신청한 지 하루가 넘었는데 아직도 안 됐어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-PORTING-0111:1 | 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요. | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0010:1 | 약정 중간에 해지하면 위약금이 얼마나 나오나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| PIPE-TERMINATE-0043:1 | 폰 잃어버려서 해지하는데 할부는 어떻게 해요 | COMPLETED | SUPPORTED | True |  |
| PIPE-TERMINATE-0033:1 | 분실한 단말의 회선을 해지하는 절차를 알려주세요. | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0035:1 | 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0054:1 | 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요? | COMPLETED | SUPPORTED | True |  |
| PIPE-USIM-0065:1 | 외국 가기 전에 유심 바로 받을 수 있나요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0014:1 | 명의 바꾸는 데 오래 걸려요? | COMPLETED | SUPPORTED | None | HUMAN_REVIEW, RETRIEVAL_MISS |
| PIPE-NAME_CHANGE-0017:1 | 명의 바꾸는 데 돈 들어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-NAME_CHANGE-0042:1 | 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| PIPE-ROAMING-0002:1 | 로밍 신청하고 얼마나 기다려야 돼요? | COMPLETED | SUPPORTED | True |  |
| PIPE-ROAMING-0032:1 | 로밍 신청은 어디서 해요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| PIPE-ROAMING-0022:1 | 로밍은 어떻게 신청해요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0034:1 | 신고하면 요금을 다 돌려받을 수 있나요? | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0044:1 | 신고했는데 며칠째 연락이 없어요 | COMPLETED | SUPPORTED | True |  |
| PIPE-SERVICE-0049:1 | 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다 | COMPLETED | SUPPORTED | True |  |
| PIPE-COMPOUND-010:1 | 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요. | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-013:1 | 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| PIPE-COMPOUND-031:1 | 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요. | COMPLETED | UNSCORED | None | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| PIPE-OUT-020:1 | 체스에서 퀸을 잘 활용하는 방법은? | COMPLETED | NOT_APPLICABLE | True |  |
| PIPE-OUT-036:1 | 해외여행 여권 유효기간은 얼마나 남아야 해? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| PIPE-OUT-049:1 | 반려견 목욕 주기는 어느 정도가 좋아요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| REG-FAQ_FACT:1 | 가상계좌가 무엇인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| REG-NO_EVIDENCE:1 | 유심 재발급하면 택배비는 따로 내나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| REG-MIXED:1 | 유심 재발급 비용은 얼마고 배송비도 내야 하나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| REG-FAQ_COMPOUND:1 | 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
