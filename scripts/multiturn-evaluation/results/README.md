# 멀티턴 요약 평가 결과

실행 입력과 모델 원시 응답, 집계 및 파일 해시를 보관한다.

| 결과 | 파일 | 내용 |
| --- | --- | --- |
| V1 요약 비교 | [metrics.json](V1-summary-comparison/metrics.json) | 요약 방식별 60개 사례의 집계 |
| V1 원시 결과 | [final-summary.jsonl.gz](V1-summary-comparison/raw/final-summary.jsonl.gz) | 세 방식의 사례별 입력과 모델 요청 및 응답, 총 180행 |
| V2 경계 사례 | [final-boundaries.jsonl.gz](V2-live-api/raw/final-boundaries.jsonl.gz) | 경계 조건 3행 |
| V2 실제 API 사례 | [final-cases.jsonl.gz](V2-live-api/raw/final-cases.jsonl.gz) | 채팅 API를 거친 사례 8행 |
| V2 모델 호출 | [final-model.jsonl.gz](V2-live-api/raw/final-model.jsonl.gz) | 해당 실행의 모델 요청과 응답 64행 |
| V3 생략형과 주제 경계 | [metrics.json](V3-review-regressions/metrics.json) | 7개 유형의 실제 API 실행 10회, 검증 결과와 전체 빌드 집계 |
| V3 실제 API 사례 | [final-cases.jsonl.gz](V3-review-regressions/raw/final-cases.jsonl.gz) | 문맥 복원, 독립 질문, 되묻기 대기와 기존 요약의 회귀 사례 10행 |
| V3 모델 호출 | [final-model.jsonl.gz](V3-review-regressions/raw/final-model.jsonl.gz) | 실제 모델 요청과 원시 응답 51행 |
| 실행 정보 | [manifest.json](manifest.json) | 원시 파일의 압축 해제 후 SHA256, 코드와 모델 정보, 선택한 입력 |

압축을 풀어도 원시 파일의 내용은 바뀌지 않는다. 각 원시 파일의 압축 해제 후 SHA256은 `manifest.json`의 `archives[].uncompressedSha256`으로 확인할 수 있다. `summary-cases.json`은 평가 입력이므로 상위 폴더에 그대로 둔다.

V3의 파일 해시와 코드 및 모델 정보는 V3의 `metrics.json`에 기록한다. `VERIFIED`는 문맥 연결과 검색 경로 검증이며 답변 정확도 점수가 아니다.

V1의 60개 사례는 독립된 고객 대화 60개가 아니다. 6개 변화 유형을 반복해 만든 입력이며, `metrics.json`의 `distinctInputDialogues`는 24다. 이 결과를 실제 서비스의 전체 답변 정확도로 해석하지 않는다. V2의 8개 API 사례도 같은 한계를 가진다.

## 실행

Ollama에 EXAONE과 bge-m3, 평가 DB에 FAQ와 벡터를 준비한다. 프로젝트 루트에서 실행한다.

```powershell
$env:POSTGRES_PORT = '5440'
$env:POSTGRES_DB = 'telme_121_verification'
$env:RUN_TELME121_LIVE = 'true'
./gradlew.bat test --tests '*MultiturnLiveApiEvaluationTest.recordsReviewRegressionsThroughActualApi'
Remove-Item Env:RUN_TELME121_LIVE
$env:POSTGRES_DB = 'telme_121_fullchecks'
./gradlew.bat clean test build --build-cache --console=plain *> .measure/telme121/review-fullchecks.log
$cases = Get-ChildItem .measure/telme121/live-api/review-regressions-*.jsonl |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
python -X utf8 scripts/multiturn-evaluation/export_results.py --review $cases.FullName --checks .measure/telme121/review-fullchecks.log
```

V1과 V2 내보내기는 `export_results.py --summary <요약> --api <API 사례> --boundaries <경계 사례> --checks <빌드 로그>`를 사용한다. 선택한 원시 자료 4개를 압축하고 `manifest.json`에 해시와 실행 정보를 기록한다.
