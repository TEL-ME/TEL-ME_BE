# 멀티턴 요약 평가 결과

이 폴더에는 발표와 코드 리뷰에 사용한 **최종 실행 자료**만 보관한다. 중간 실행 15개의 원시 로그는 제외했다. 수치를 다시 계산하거나 개별 사례를 살펴볼 때는 아래 파일을 사용한다.

| 결과 | 파일 | 내용 |
| --- | --- | --- |
| V1 요약 비교 | [metrics.json](V1-summary-comparison/metrics.json) | 요약 방식별 60개 사례의 집계 |
| V1 원시 결과 | [final-summary.jsonl.gz](V1-summary-comparison/raw/final-summary.jsonl.gz) | 세 방식의 사례별 입력과 모델 요청 및 응답, 총 180행 |
| V2 경계 사례 | [final-boundaries.jsonl.gz](V2-live-api/raw/final-boundaries.jsonl.gz) | 경계 조건 3행 |
| V2 실제 API 사례 | [final-cases.jsonl.gz](V2-live-api/raw/final-cases.jsonl.gz) | 채팅 API를 거친 사례 8행 |
| V2 모델 호출 | [final-model.jsonl.gz](V2-live-api/raw/final-model.jsonl.gz) | 해당 실행의 모델 요청과 응답 64행 |
| 실행 정보 | [manifest.json](manifest.json) | 원시 파일의 압축 해제 후 SHA256, 코드와 모델 정보, 선택한 입력 |

압축을 풀어도 원시 파일의 내용은 바뀌지 않는다. 각 원시 파일의 압축 해제 후 SHA256은 `manifest.json`의 `archives[].uncompressedSha256`으로 확인할 수 있다. `summary-cases.json`은 평가 입력이므로 상위 폴더에 그대로 둔다.

V1의 60개 사례는 독립된 고객 대화 60개가 아니다. 6개 변화 유형을 반복해 만든 입력이며, `metrics.json`의 `distinctInputDialogues`는 24다. 이 결과를 실제 서비스의 전체 답변 정확도로 해석하지 않는다. V2의 8개 API 사례도 같은 한계를 가진다.

새로 결과를 내보낼 때는 `export_results.py`에 최종 요약, API, 경계 사례와 빌드 로그를 지정한다. 스크립트는 선택한 원시 자료 4개만 압축해 내보내고 `manifest.json`을 갱신한다. 기존에 기록된 로컬 절대 경로와 모델 버전은 **당시 실행의 정보**이므로 다른 환경에서 재실행할 때는 새 경로와 모델 정보를 기록해야 한다.
