# 실제 채팅 파이프라인 답변 품질 측정

Spring 채팅 API가 생성하고 저장한 최종 답변을 vLLM Qwen3-14B-AWQ로 평가했다.
점수는 이 고정 평가 질문에서의 자동 판정 결과이며 실제 이용자 질문 분포의 전체 서비스 환각률로 일반화하지 않는다.
근거 없는 주장은 실제 RAG 입력에 포함된 근거를 기준으로 판단한다. 참인 외부 지식도 근거가 없으면 이 정책에서는 통과하지 않는다.

| 지표 | 결과 |
| --- | --- |
| 질문 수 | 259/259 |
| 실행 완료율 | 100.0% (259/259) |
| 답변 품질 통과율 | 45.82% (115/251) |
| 답변 가능 질문의 완전 답변율 | 46.2% (85/184) |
| 사실 답변의 근거 없는 답변 발생률 | 34.65% (44/127) |
| 사실 주장 단위 근거 부족률 | 39.89% (146/366) |
| 전체 질문 중 근거 없는 답변 발생 | 16.99% (44/259) |
| 근거 미확정 건수를 포함한 전체 질문 기준 발생률 범위 | [16.99, 18.15]% |
| 근거 판정 가능률 | 98.84% (256/259) |
| 정답 근거 포함: 검색 후보 / RAG 입력 / 저장 근거 | 50.24% (104/207) / 50.24% (104/207) / 50.24% (104/207) |
| 세 축 완전 채점 / 미채점 / 사람 검토 | 257 / 2 / 6 |
| 품질 미확정 8건을 고려한 통과율 범위 | [44.4, 47.49]% |
| 파이프라인 P50 / P95 | 2085 / 3419 ms |

품질은 필요한 내용을 정확히 답하거나 적절히 보류했으며 근거 없는 단정이 없는 경우에 통과한다.
미채점 또는 모호한 판정은 정상 답변으로 처리하지 않는다. 축별로 유효한 판정을 보존한다.

| 사례 | 질문 | 실행 | 근거성 | 품질 통과 | 발견 사항 |
| --- | --- | --- | --- | --- | --- |
| V2-SINGLE-A001:1 | 요금 밀려서 발신 막혔는데 지금 다 내면 언제 다시 전화 걸 수 있어요? 계속 못 내면 어떻게 되는지도 알려주세요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A002:1 | 폰 소액결제 한달 한도 얼마임? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A003:1 | 청구서 이메일로 받게 방금 바꿨는데 이번 달 거부터 바로 메일로 오나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-SINGLE-A004:1 | 실수로 요금을 두 번 냈는데 환불을 대학생 딸 계좌로 받아도 되나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A005:1 | 종이 청구서 계속 받으면 따로 돈 나가요? | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| V2-SINGLE-A006:1 | 부모님 청구서가 아직 우편으로 오는데 이메일로 돌리면 돈 좀 아껴지나요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A007:1 | 청구서 받은 적도 없는데 갑자기 전화가 안 걸려요ㅠ 이거 어떻게 풀어요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A008:1 | 소액결제 한도 올려놨던 거 다시 내리고 싶은데 신청하면 바로 적용돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A009:1 | 청구서는 매달 언제 날아와요? 언제까지 내야 되는지도요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A010:1 | 이번 달 요금 깜빡하고 못 냈는데 얼마나 지나면 폰 끊겨요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A011:1 | 요금이 이중으로 빠져나갔던데 환불 말고 담달 요금에서 빼주는 것도 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A012:1 | 이번 달 중간에 요금제 바꾸면 요금은 어떻게 나와요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A013:1 | 산 지 일주일 된 새 폰을 잃어버렸는데 이것도 반품 처리 되나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A014:1 | 폰 개통했는데 맘에 안 들어서 반품하려구요. 어디서 신청하고 조건은 뭐예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A015:1 | 단말 교환 신청 꼭 매장 가야 됨? 전화로도 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A016:1 | 받은 폰 액정에 줄이 가 있는데 교환 되나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A017:1 | 폰이 물에 빠져서 새로 사야 되는데 할부 몇 개월로 할 수 있어요? 수수료도 궁금해요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-SINGLE-A018:1 | 어제 산 폰 색상 다른 걸로 바꾸고 싶은데 가능? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A019:1 | 배터리 갈려고 하는데 폰 보험 들어 있으면 얼마나 싸져요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A020:1 | 폰 잃어버린 줄 알고 새로 샀는데 찾았어요ㅋㅋ 새 폰 반품하려는데 오늘이 개통 15일째예요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A021:1 | 교환은 반품보다 기간을 좀 더 길게 주나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A022:1 | 폰 분실해서 기변하려는데 조건 있어요? 지금 폰 할부 남은 건 어떻게 되고요 | COMPLETED | SUPPORTED | False | OVER_REFUSAL |
| V2-SINGLE-A023:1 | 산 지 3일 된 폰을 물에 빠뜨렸는데 새 걸로 교환 되나요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-SINGLE-A024:1 | 폰 할부로 사면 할부이자 몇 프로예요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A025:1 | 명변 하려다 신용심사에서 탈락했는데 다른 방법 없나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A026:1 | 남편 폰을 제 명의로 돌리려는데 뭐 챙겨 가야 돼요? 남편도 같이 가야 하나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A027:1 | 명의변경 수수료 있음? 시간은 얼마나 걸리고 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A028:1 | 명의 넘겨받으려는데 신용심사 거절 떴어요. 명의변경에 왜 심사를 하는 거예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-SINGLE-A029:1 | 요금 두 달 밀려 있는데 그 상태로 명의 넘길 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A030:1 | 점심시간에 잠깐 매장 들러서 명의변경 끝낼 수 있을까요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A031:1 | 회사폰 쓰다가 퇴사해서 제 명의로 가져오려는데 회사 담당자가 매장에 같이 못 간대요 어떡하죠 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-SINGLE-A032:1 | 동생 폰 할부 남은 거 제 명의로 가져오려는데 심사에서 막혔대요. 이제 어떻게 해야 돼요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A033:1 | 기계값 아직 다 안 갚았는데 명의 넘기는 거 가능함? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A034:1 | 어제 아빠 폰 제 명의로 바꾸러 아빠 신분증만 들고 갔는데 안 해주더라구요. 뭐가 문제였어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A035:1 | 할머니 폰을 제 이름으로 가져오려는데 시간 얼마나 걸리고 돈 들어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A036:1 | 애가 이번에 성인 돼서 애 이름으로 회선 넘기려는데 비용이랑 시간 어느 정도 들어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A037:1 | 데이터 거의 안 쓰는 편인데 저렴한 요금제 추천 좀 | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| V2-SINGLE-A038:1 | 생일 지나서 요금제 다시 고르려는데 5G LTE 다 포함해서 어떤 거 있는지 전부 알려줘요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A039:1 | 가족결합한 애 폰이 갑자기 느려졌다는데 속도 제한 걸린 건지 어떻게 봐요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A040:1 | 알뜰 요금제 중에 5G 되는 것도 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-SINGLE-A041:1 | 엄마한테 데이터 선물하려는데 한 번에 몇 기가까지 돼? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A042:1 | LTE 요금제는 몇 개 있어요? 헷갈려서 그런데 5G랑 알뜰도 같이 정리해 주세요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A043:1 | 언리미티드랑 다른 요금제 차이가 데이터 다 쓰고 나서 속도 차이인 거예요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A044:1 | 데이터 남는 거 손녀한테 주고 싶은데 어떻게 하는 거예요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A045:1 | 나이 상관없이 가입되는 요금제 중에 젤 싼 거랑 젤 비싼 거 가격이랑 데이터 비교해줘 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A046:1 | 알뜰요금제가 정확히 뭐예요? 종류는 몇 개? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A047:1 | 청소년이나 시니어 요금제 말고 일반 요금제는 몇 종류예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A048:1 | 제일 저렴한 요금제 얼마예요? 데이터는 얼마나 줘요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A049:1 | 번이 신청한 지 반나절 됐는데 아직도 안 넘어왔어요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A050:1 | 새 폰 개통한 지 한 달 됐는데 통신사 옮기려니까 안 된대요 왜죠 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-SINGLE-A051:1 | 번호이동하면 보통 얼마나 기다려야 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A052:1 | 지금 쓰는 통신사에 밀린 요금이 좀 있는데 번호이동 하려면 순서가 어떻게 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A053:1 | 번호이동 밤에도 처리돼요? | COMPLETED | UNSUPPORTED | None | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, HUMAN_REVIEW |
| V2-SINGLE-A054:1 | 오늘 밤 11시에 번이 신청하면 내일 아침 출근 전에는 끝나 있을까요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A055:1 | 번호이동 오전에 신청하는 게 나아요? 퇴근하고 저녁에 해도 상관없나 | COMPLETED | SUPPORTED | None | HUMAN_REVIEW, RETRIEVAL_MISS |
| V2-SINGLE-A056:1 | 예전 통신사에 요금 좀 남아 있는데 그래도 번호이동 신청 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A057:1 | 지금 폰 개통한 지 60일쯤 됐는데 다른 통신사로 번호 옮길 수 있나요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A058:1 | 번호이동 했는데 후회돼요ㅠ 원래 통신사로 돌아갈 수 있나요 | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| V2-SINGLE-A059:1 | 번이 철회하면 제 회선은 어떻게 돼요? 기한도 있어요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A060:1 | 번호이동 하려다가 미납 때문에 거절당함 ㅠ 어떻게 해야 함 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-SINGLE-A061:1 | 다음 주에 일본 가는데 폰 로밍하면 갤럭시워치도 같이 되는 거죠? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A062:1 | 로밍 그냥 공항 가서 신청해도 돼요? 아님 집에서 미리 해 놔야 하나 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A063:1 | 로밍 신청한 지 20분 됐는데 아직 안 터져요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-SINGLE-A064:1 | 다음 주에 7일 동안 베트남 가는데 데이터 무제한 로밍 되나요? 얼마예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A065:1 | 비행기 내리자마자 데이터 쓸 수 있나요? 공항에서 바로 택시 앱 불러야 해서요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A066:1 | 내일 아침 출장 비행기인데 로밍 공항 가는 길에 신청해도 안 늦어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A067:1 | 로밍 차단 걸려 있던데 해제하려면 뭐 해야 됨? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A068:1 | 일본 5박6일 가는데 7일짜리 로밍권 가격이 얼마죠 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-SINGLE-A069:1 | 해외 처음 나가는데 로밍 요금 폭탄 맞을까 봐 무서워요. 종류별로 가격 좀 알려주세요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A070:1 | 한 달 정도 유럽 출장인데 로밍 뭘로 하는 게 나을까요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A071:1 | 무제한 로밍 하루에 얼마? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A072:1 | 듀얼넘버 신청하면 매달 돈 더 나가요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A073:1 | 부가서비스 잘못 눌러서 오늘 가입됐는데 바로 해지하면 요금 안 나오죠? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A074:1 | 부가서비스 하나 해지하려는데 이번 달 요금은 빠지나요? 해지하면 바로 못 쓰게 되는 거예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A075:1 | 올해 요금 많이 냈는데 멤버십 등급 바로 올라가요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A076:1 | 새해 되자마자 멤버십 등급이 내려갔는데 이유가 뭐예요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A077:1 | 제가 모르는 폰이 제 명의로 개통돼 있어요. 신고하면 어떻게 처리돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A078:1 | 부가서비스 지금 신청하면 오늘부터 바로 적용돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A079:1 | 토요일에 매장 가려는데 몇 시까지 해요? 일요일도 열어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A080:1 | 부가서비스 해지했는데 청구서에 또 찍혀 있네요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-SINGLE-A081:1 | 명의도용 신고하면 그동안 나간 요금 전부 안 내도 되는 거예요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A082:1 | 가입사실확인서 필요한데 매장 꼭 가야 돼요? 홈페이지로 떼면 돈 들어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A083:1 | 요금납부확인서랑 해지확인서 발급 방법 다른가요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A084:1 | 재작년에 해지한 번호인데 해지확인서 아직 뽑을 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A085:1 | 세컨폰 하나 개통하려는데 제 이름으로 하는 거랑 중학생 아들 이름으로 하는 거랑 서류 차이 있나요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A086:1 | 법인폰 오늘 개통 가능? 오후에 매장 갈 거임 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A087:1 | I'm foreigner. 여권만 있으면 가입 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A088:1 | 외국인인데 회선 하나 더 만들려니까 한도 초과래요. 몇 개까지 되는 거예요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A089:1 | 개통 매장에서 하는 거랑 온라인으로 하는 거 중에 뭐가 더 빨라요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A090:1 | 딸 폰 오늘 안에 개통해 주고 싶은데 온라인으로 신청하면 늦나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A091:1 | 폰 처음 개통하러 가는데 신분증 어떤 거 가져가야 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A092:1 | 생애 첫 폰 만들러 매장 가면 그날 바로 받을 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A093:1 | 제 명의로 회선 몇 개까지 만들 수 있어요? 법인 명의는요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-SINGLE-A094:1 | 처음 개통하는 건데 회선한도초과라고 뜨네요 이게 뭐죠 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A095:1 | 초등학생 애 폰 만들려는데 애 명의로 하면 서류 복잡해요? 그냥 제 명의로 하는 게 나을까요 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A096:1 | 한국 온 지 얼마 안 된 외국인 친구 폰 개통 도와주려는데 뭐 챙겨서 가면 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A097:1 | 한 달 일시정지 끝났는데 자동으로 연장 안 되나요? 아직 해외에 더 있어야 하는데 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A098:1 | 약정 11개월차인데 지금 해지하면 위약금 전부 내야 돼요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A099:1 | 해지할 때 남은 기계값 한방에 내는 게 나아요 그냥 나눠 내는 게 나아요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-SINGLE-A100:1 | 반년 정도 해외 나가 있을 건데 번호는 살리고 싶어요. 정지랑 해지 중 뭐가 나음? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A101:1 | 이민 가게 돼서 폰 해지하려는데 어디서 신청하고 언제 끊겨요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-SINGLE-A102:1 | 해지 신청 어떻게 함? 이번 달 요금은 한 달치 다 나가? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A103:1 | 몇 달 폰 안 쓸 것 같은데 일시정지 가능해요? 정지 중에도 요금 나가요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A104:1 | 지금 미국에 있는데 한국 번호 홈페이지에서 해지 가능? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A105:1 | 안 쓰는 세컨폰 해지하려는데 할부금 남은 건 어떻게 처리돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A106:1 | 아들 다음 달에 입대해서 폰 해지하려는데 할부 남은 거 계속 매달 나눠서 내도 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A107:1 | 폰 번호 없애고 싶은데 해지 방법? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A108:1 | 한동안 폰 안 쓸 건데 그냥 해지할까 정지할까 고민돼요. 차이가 뭐예요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-SINGLE-A109:1 | 유심 새로 받으러 가는데 신분증 놓고 왔어요ㅠ 없으면 안 돼요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-SINGLE-A110:1 | 여행 중에 유심을 잃어버렸어요. 여기서 홈페이지로 분실신고 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A111:1 | 유심 잃어버림 신고 어디서 해 | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A112:1 | 유심 인식이 계속 안 돼서 새로 받아야 될 거 같은데 매장 가야 돼요 온라인도 돼요? 비용은요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A113:1 | 유심 부러졌는데 오늘 안에 새 걸로 바꿀 수 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A114:1 | 유심 깨진 김에 이심으로 갈아타고 싶은데 어떻게 해요? 얼마예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A115:1 | 저 지금 해외에 있는데 부모님이 한국에서 대신 유심 받아서 보내 주시려고 해요. 엄마가 뭐 들고 가야 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-SINGLE-A116:1 | 유심이 반으로 쪼개졌는데 분실처럼 따로 신고해야 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A117:1 | 유심 잃어버려서 재발급 받아야 하는데 남편 시켜도 되나요? 제가 갈 때랑 서류 뭐가 달라요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-SINGLE-A118:1 | 유심 깨졌어요. 분실신고 같은 걸 먼저 해야 되나 그냥 재발급받으면 되나 헷갈림 | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| V2-SINGLE-A119:1 | 잃어버린 유심 누가 주워서 쓸까 봐 걱정인데 재발급 받으면 옛날 유심은 자동으로 해지돼요? | COMPLETED | SUPPORTED | True |  |
| V2-SINGLE-A120:1 | 유심 바꾸러 제가 직접 가는 거랑 딸 보내는 거랑 챙길 서류 다른가요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-COMPOUND-C001:1 | 해외 나가면 무제한 로밍은 하루 얼마예요? 그리고 거기서 유심 잃어버리면 홈페이지로 분실신고 되나요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-COMPOUND-C002:1 | 개통한 지 두 달 됐는데 다른 통신사로 번호이동 되나요? 안 되면 기기변경은 언제부터 가능한지도 알려줘요 | COMPLETED | SUPPORTED | True |  |
| V2-COMPOUND-C003:1 | 요금 밀린 게 좀 있는데 이 상태로 명의변경이랑 번호이동 각각 되나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C004:1 | 종이 청구서 받으면 돈 나가요? 그리고 요금납부확인서는 어디서 떼는지도 알려주세요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-COMPOUND-C005:1 | 지금 약정 11개월째인데 해지하면 위약금 얼마나 물어야 돼요? 새 폰 할부로 사면 이자 몇 프로인지도요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C006:1 | 두 달 해외 가는데 폰 일시정지하면 요금 얼마나 나와요? 로밍 켜고 쓰면 하루에 얼마인지도 같이 알려주세요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-COMPOUND-C007:1 | 엄마 폰 명의를 제 걸로 옮기고 제 폰도 하나 새로 개통하려는데 매장에서 각각 얼마나 걸려요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-COMPOUND-C008:1 | 폰 떨어뜨려서 배터리도 부었고 유심도 깨졌어요. 보험 있으면 배터리 수리비 얼마나 내요? 유심은 오늘 바로 바꿀 수 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C009:1 | 가족한테 데이터 한 번에 얼마나 보낼 수 있어요? 그리고 소액결제 한 달 한도도 알려줘 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-COMPOUND-C010:1 | 외국인 친구가 가입하려는데 여권 말고 뭐 필요해요? 제일 싼 요금제 얼마인지도 알려주세요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-COMPOUND-C011:1 | 요금제 비싼 걸로 올리면 멤버십 등급도 바로 올라가나요? 이번 달 중간에 바꾸면 요금 계산은 어떻게 돼요? | COMPLETED | NOT_APPLICABLE | None | INCOMPLETE_ANSWER, HUMAN_REVIEW, RETRIEVAL_MISS |
| V2-COMPOUND-C012:1 | 새 폰 할부로 사려는데 몇 개월 할부 돼요? 그 김에 이심으로 바꾸면 비용은 얼마예요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-COMPOUND-C013:1 | 유심을 잃어버렸는데 누가 제 명의로 폰 개통까지 한 것 같아요. 분실신고는 어디서 하고, 명의도용 신고하면 어떻게 처리돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C014:1 | 번호 해지는 어디서 해요? 해지하고 나서 해지확인서는 언제까지 뗄 수 있는지도요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C015:1 | 번호이동 저녁에도 처리돼요? 매장은 평일 몇 시까지 여는지도 알려줘요 | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C016:1 | 아빠 폰을 제 명의로 바꾸려면 서류 뭐 필요해요? 그리고 아빠가 잃어버린 유심 재발급을 제가 대신 받으러 가면 뭐 챙겨야 하는지도요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C017:1 | 데이터 별로 안 쓰는데 싼 요금제 뭐 있어요? 개통은 매장이랑 인터넷 중 어디가 빨라요? | COMPLETED | UNSUPPORTED | None | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-COMPOUND-C018:1 | 번호이동하면서 새 폰도 샀는데 다 무르고 싶어요. 번호이동 철회랑 폰 반품 각각 기한이랑 조건이 어떻게 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-COMPOUND-C019:1 | 요금이 두 번 빠져나갔는데 환불 대신 다음 달 요금에서 빼줄 수 있어요? 그리고 오늘 가입한 부가서비스 바로 해지하면 이번 달 요금 안 나오죠? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-COMPOUND-C020:1 | 회선 한도 초과라서 새로 개통이 안 된대요. 몇 개까지 되는 거예요? 안 쓰는 회선 하나 해지하려면 어떻게 하는지도 알려주세요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M001:1 | 이번 달 요금이 계좌 자동이체로도 빠지고 카드로도 결제됐어요. 왜 두 번이나 나간 거예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M001:2 | 그런데 혹시 요금을 납부 기한까지 못 내면 회선 정지는 언제부터 걸리나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M002:1 | 청구서를 보니까 제가 쓴 적 없는 소액결제가 찍혀 있어요. 이거 어떻게 해야 하나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M002:2 | 그 한도는 올릴 수도 있어요? 최대 얼마까지 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M003:1 | 폰을 바꾸고 싶은데 지금 쓰는 폰 할부가 아직 남아 있어요. 그래도 바꿀 수 있나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M003:2 | 그렇게 바꾼 폰이 배터리가 이상하면요? 며칠 안에 말해야 무를 수 있어요? | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| V2-MULTI-M004:1 | 기기변경 하러 갔더니 남은 할부금 때문에 진행이 안 된다고 하네요. 어떻게 해야 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M004:2 | 개통한 지 며칠 안 된 새 폰이 배터리가 이상한데, 다른 폰으로 교환받을 수 있나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-MULTI-M005:1 | 중학생인 우리 애가 친구한테 자기 폰 명의를 넘겨주겠다는데 그게 가능한가요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M005:2 | 그럼 성인인 제가 만 17세 동생한테 넘기는 건요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M006:1 | 명의변경 하려는데 제가 직접 매장에 가는 거랑 위임장 써서 다른 사람 보내는 거랑 처리 속도가 달라요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-MULTI-M006:2 | 딸이 가는 경우엔요? 제가 같이 가는 거랑 딸 혼자 보내는 거랑 챙길 게 달라지나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M007:1 | 지금 가입할 수 있는 요금제가 몇 개나 되고 가격대는 어느 정도예요? | COMPLETED | UNSUPPORTED | None | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN, RETRIEVAL_MISS |
| V2-MULTI-M007:2 | 제 건 중간 정도라 매달 말쯤 다 썼다고 알림이 오던데, 그 뒤엔 어떻게 해야 해요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M008:1 | 데이터 다 쓰고 400kbps로 느려진 상태랑 언리미티드 요금제랑 실제로 써 보면 차이가 많이 나나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M008:2 | 가족 네 명이 5G 프리미엄이랑 LTE 맥스로 나눠서 가입하려는데, 두 요금제 가격이랑 데이터가 각각 어떻게 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, OVER_REFUSAL |
| V2-MULTI-M009:1 | 다른 통신사에서 번호 그대로 옮겨 오려는데, 예전 통신사 요금이 좀 밀려 있어요. 그래도 신청돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M009:2 | 그 회선 명의를 얼마 전에 제 앞으로 바꿨는데, 그래도 바로 할 수 있나요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M010:1 | 번호이동 하려는데 이전 통신사에 밀린 요금이 있는지 없는지에 따라 절차가 달라지나요? | COMPLETED | SUPPORTED | True | RETRIEVAL_MISS |
| V2-MULTI-M010:2 | 그거 토요일에도 처리돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M011:1 | 해외 도착해서 로밍을 켰는데 10분이 넘도록 신호가 안 잡혀요. 어떻게 해야 하죠? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-MULTI-M011:2 | 연결되고 나면 같이 온 가족이랑도 나눠 쓸 수 있어요? 몇 명까지 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M012:1 | 해외에 보름 있으면서 로밍을 썼는데 요금이 예상보다 훨씬 많이 나왔어요. 왜 이렇게 나온 거예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-MULTI-M012:2 | 다음 출장 땐 도착해서 뭘 하면 바로 써지는 거예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-MULTI-M013:1 | 제 명의가 도용된 것 같아서 신고했는데, 확인 결과는 언제쯤 나와요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M013:2 | 기다리는 동안 직접 가서 얘기해 보고 싶은데, 평일 퇴근하고 저녁 7시 넘어서 가도 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-MULTI-M014:1 | 토요일에 직영점에 가 보려는데 몇 시부터 몇 시까지 열어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, RETRIEVAL_MISS, ROUTING_MISMATCH |
| V2-MULTI-M014:2 | 그리고 올해 들어서 멤버십 등급이 떨어졌던데 이유가 뭔가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-MULTI-M015:1 | 새로 폰 개통하려는데 매장에서 하는 거랑 온라인으로 신청하는 거 중에 뭐가 더 빨라요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-MULTI-M015:2 | 저는 아까 온라인으로 넣었는데, 그럼 오늘 안에는 안 되는 거예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-MULTI-M016:1 | 지금 쓰는 번호 말고 회선을 하나 더 개통하고 싶은데, 한 사람이 몇 개까지 만들 수 있어요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M016:2 | 회사 이름으로 하면요? 그것도 바로 되나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-MULTI-M017:1 | 아들이 곧 입대해서 쓰던 폰을 해지하려는데 어디서 하면 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M017:2 | 그 폰 할부가 아직 꽤 남았는데 그건 어떻게 되는 거예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-MULTI-M018:1 | 지금 해외에 나와 있는데 여기서 전화로 회선 해지 신청할 수 있나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| V2-MULTI-M018:2 | 그럼 남은 할부금은 그때 한 번에 다 내야 돼요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M019:1 | 유심 카드가 깨져 버렸는데 이참에 eSIM으로 바꾸고 싶어요. 어떻게 하면 돼요? | COMPLETED | SUPPORTED | None | HUMAN_REVIEW |
| V2-MULTI-M019:2 | 듀얼심이라는 게 정확히 어떤 기능인가요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M020:1 | 유심이 깨졌는데 매장 가서 받는 거랑 택배로 받는 거 중에 뭐가 나을까요? | COMPLETED | SUPPORTED | True |  |
| V2-MULTI-M020:2 | 새 거 받으면 지금 깨진 건 혹시 아직 쓸 수 있는 거예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, RETRIEVAL_MISS |
| V2-NOANSWER-U001:1 | 유심 재발급 7,700원이라던데 멤버십 VIP면 할인되거나 무료로 해주나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U002:1 | 명의도용 신고하면 조사하는 3영업일 동안 그 회선은 바로 정지되나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U003:1 | 일시 정지 해놓으면 문자는 받을 수 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U004:1 | 로밍 7일 기간권 39,000원짜리는 데이터 몇 GB까지 주고, 다 쓰면 속도 제한 걸려요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U005:1 | 동생한테 선물받은 데이터 이번 달에 다 못 쓰면 그것도 다음 달로 이월돼요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U006:1 | 단말 보험 월 보험료는 얼마고, 개통하고 며칠 안에 가입해야 하나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U007:1 | 우편 청구서 월 500원이요, 저희 어머니가 만 70세시고 이메일을 못 쓰시는데 고령자는 면제 안 되나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U008:1 | 요금 두 번 빠져나간 거 환불받을 때 그 기간 이자도 붙여서 주나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U009:1 | eSIM 발급비 2,750원은 그 자리에서 결제해야 해요, 아니면 다음 달 요금에 같이 청구돼요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U010:1 | 할부 30개월로 샀는데 1년 쓰고 남은 할부금을 한 번에 갚으면 남은 기간 할부 수수료는 안 내도 되는 거죠? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U011:1 | 새 폰 14일 안에 반품하면 개통할 때 낸 유심비도 같이 돌려받나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U012:1 | 요금 못 내서 직권 해지됐으면 예전 번호 다시 살릴 수 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U013:1 | 멤버십 VIP 되면 영화나 카페 할인 같은 혜택은 뭐가 있어요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U014:1 | 듀얼번호로 받는 현지 번호는 어느 나라 번호까지 돼요? 베트남 출장인데 베트남 번호도 나오나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U015:1 | 해지하면 이월돼서 남아 있던 데이터는 돈으로 환산해서 돌려주나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U016:1 | 아버지 명의 폰을 제 명의로 바꾸면 아버지가 걸어둔 24개월 약정도 저한테 그대로 넘어오나요, 아니면 위약금 정산하고 새로 시작해요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U017:1 | 번호이동하면서 새 폰을 할부로 샀는데 14일 안에 철회하면 그 폰 할부는 어떻게 처리돼요? | COMPLETED | NOT_APPLICABLE | False | OVER_REFUSAL |
| V2-NOANSWER-U018:1 | 시니어 요금제는 한 달에 얼마고 데이터는 몇 기가 줘요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U019:1 | 가입사실확인서 영문으로도 발급돼요? 해외 비자 신청에 내야 해서요 | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U020:1 | 매장 점심시간 있어요? 12시 반쯤 가려고요 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, ROUTING_MISMATCH |
| V2-NOANSWER-U021:1 | 일시 정지 30일 끝나면 자동으로 풀려요? 제가 따로 해제 신청해야 돼요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U022:1 | 5G 언리미티드 쓰는데 노트북에 핫스팟(테더링)으로 연결해서 쓰는 것도 무제한이에요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U023:1 | 5G 프리미엄 플러스 요금제는 월 얼마예요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U024:1 | LTE 슬림 요금제로 바꾸고 싶은데 데이터 얼마나 줘요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U025:1 | 군인 전용 요금제 있나요? 아들이 다음 달 입대해요 | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U026:1 | 넷플릭스나 유튜브 프리미엄 포함된 요금제 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U027:1 | 스팸 전화 차단해주는 부가서비스 이름이 뭐고 한 달에 얼마예요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U028:1 | 통화연결음 바꾸는 컬러링 서비스 월 이용료 얼마예요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U029:1 | 집 인터넷이랑 IPTV를 휴대폰이랑 같이 묶으면 결합 할인 몇 퍼센트 돼요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U030:1 | 초등학생 키즈워치 전용 요금제 있어요? 월 얼마예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U031:1 | 크루즈 여행 가는데 배 위에서 쓰는 해상 로밍 상품이나 비행기 안에서 쓰는 기내 로밍 있나요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U032:1 | 선택약정 25% 할인 받으려면 어떻게 신청해요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U033:1 | '폰케어 플러스'라는 분실·파손 보험 들고 싶은데 보장 범위가 어떻게 돼요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U034:1 | 기기변경할 때 쓰던 폰 반납하면 중고 보상금 주는 프로그램 있죠? 얼마 쳐줘요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U035:1 | 전화번호만 다른 번호로 바꾸고 싶은데 번호 변경은 어떻게 하고 수수료 있어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U036:1 | 해외 나가서 해지하려는데, 돌아와서 같은 번호 다시 쓰고 싶거든요. 해지한 번호를 몇 달 보관해 두는 제도 있어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U037:1 | 어제 저녁에 통신 장애로 세 시간 넘게 전화가 안 됐는데 이런 건 요금 보상 받을 수 있나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U038:1 | 장애인 복지 할인 받으려면 어떤 서류 내야 해요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U039:1 | 기초생활수급자인데 통신비 감면 되나요 | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U040:1 | 아버지가 돌아가셨는데 아버지 명의 폰을 해지만 하려면 사망진단서나 가족관계증명서 같은 거 필요한가요? 명의변경은 안 할 거예요. | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U041:1 | 이번 달 요금이 너무 많이 나와서 그러는데 두세 번에 나눠서 내도 돼요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U042:1 | 납부 기한 넘기면 연체 가산금이 몇 퍼센트 붙어요? | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U043:1 | 쿠팡에서 자급제폰 샀는데 유심만 꽂으면 바로 써져요? 매장에서 따로 등록해야 하나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U044:1 | 휴대폰 요금도 현금영수증 발급되나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U045:1 | 법인 명의로 회선 10개 쓰고 있는데 통신요금 세금계산서 매달 받을 수 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U046:1 | 폰 잃어버렸는데 통신사에서 위치 추적해서 찾아주는 서비스 해주나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U047:1 | 외국인인데 비자가 다음 달에 만료돼요. 연장 심사 중이면 그동안 회선이 정지되나요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U048:1 | 매장에서 개통할 때 설명도 없이 부가서비스 세 개를 넣어놨더라고요. 석 달 동안 낸 요금 다 환불받을 수 있나요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U049:1 | 제 이번 달 요금 얼마 나왔어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U050:1 | 저 지금 데이터 몇 기가 남았어요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U051:1 | 제 약정 몇 개월 남았는지랑 지금 해지하면 위약금 정확히 얼마인지 계산해 주세요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U052:1 | 내 멤버십 등급 지금 뭐예요 | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U053:1 | 제 폰 할부금 아직 얼마 남았어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U054:1 | 어제 오후에 번호이동 신청했는데 지금 어디까지 진행됐는지 조회해 주실 수 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U055:1 | 지난달에 제가 소액결제로 뭐 결제했는지 내역 좀 보여주세요 | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U056:1 | SK텔레콤은 유심 재발급 비용이 얼마예요? 여기랑 비교해 보려고요 | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| V2-NOANSWER-U057:1 | KT로 번호이동하면 KT에서 공시지원금 얼마 줘요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U058:1 | LG유플러스 로밍이 더 싸다던데 거기는 하루 요금이 얼마예요? | COMPLETED | SUPPORTED | False | INCOMPLETE_ANSWER |
| V2-NOANSWER-U059:1 | 갤럭시 S24 울트라 액정 교체하면 삼성서비스센터에서 정가로 얼마 나와요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U060:1 | 아이폰에 애플케어플러스도 들어 있는데 여기 단말 보험이랑 중복으로 보상받을 수 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U061:1 | 폰 바꾸기 전에 카카오톡 대화 내용 백업은 어떻게 해요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U062:1 | 갤럭시에서 아이폰으로 바꾸는데 사진이랑 연락처 옮기는 방법 알려주세요 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U063:1 | 폰을 변기에 빠뜨렸는데 쌀통에 넣어두면 살아나요? 서비스센터 가기 전에 집에서 할 수 있는 거 있어요? | COMPLETED | NOT_APPLICABLE | True |  |
| V2-NOANSWER-U064:1 | 아이폰 화면이 갑자기 까맣게 안 켜져요 강제 재부팅 어떻게 해요 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U065:1 | 안드로이드 업데이트하고 나서 배터리가 엄청 빨리 닳는데 설정에서 뭘 꺼야 돼요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U066:1 | 집 와이파이는 잡히는데 인터넷이 안 돼요. 공유기 설정 어떻게 바꿔요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U067:1 | 보이스피싱 전화 받고 계좌이체를 해버렸어요. 은행 지급정지는 어떻게 신청해요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U068:1 | 아이 폰에 유해 앱 못 깔게 하려면 구글 패밀리 링크 어떻게 설정해요? | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U069:1 | 폰 저장공간 꽉 찼다고 뜨는데 뭐부터 지우면 돼요 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| V2-NOANSWER-U070:1 | 갤럭시 워치랑 폰 블루투스 연결이 자꾸 끊기는데 다시 페어링하는 방법 알려주세요 | COMPLETED | NOT_APPLICABLE | True | ROUTING_MISMATCH |
| REG-FAQ_FACT:1 | 가상계좌가 무엇인가요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| REG-FAQ_CONDITION:1 | 소액결제 한도 얼마까지 돼요? | COMPLETED | SUPPORTED | True |  |
| REG-NO_EVIDENCE:1 | 유심 재발급하면 택배비는 따로 내나요? | COMPLETED | NOT_APPLICABLE | True |  |
| REG-MIXED:1 | 유심 재발급 비용은 얼마고 배송비도 내야 하나요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, INCOMPLETE_ANSWER, SHOULD_ABSTAIN |
| REG-UNRELATED:1 | 오늘 서울 날씨 어때? | COMPLETED | NOT_APPLICABLE | True |  |
| REG-MULTI_TURN:1 | 요금제는 한 달에 몇 번 바꿀 수 있어요? | COMPLETED | SUPPORTED | True |  |
| REG-MULTI_TURN:2 | 가입한 달에도 돼요? | COMPLETED | SUPPORTED | True |  |
| REG-FAQ_COMPOUND:1 | 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요? | COMPLETED | UNSUPPORTED | False | UNSUPPORTED_CLAIM, SHOULD_ABSTAIN |
| REG-STORE:1 | 강남역 근처 유플러스 매장 알려줘 | COMPLETED | NOT_APPLICABLE | False | INCOMPLETE_ANSWER, UNNECESSARY_CLARIFICATION, STORE_NOT_CONNECTED |
