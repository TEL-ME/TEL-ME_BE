# 공통 품질 평가

파이프라인 전체의 품질 평가 기준·절차와 여러 호출을 함께 비교한 기록을 모은다. 운영 요청 중 검색 근거를 판정하는 기능은 [파이프라인의 근거 판정](../01-pipeline/README.md#04-근거-판정)이며, 여기의 LLM Judge는 생성 결과를 평가하는 도구다.

## LLM Judge

- [평가 체계와 결과 색인](chat-judge/README.md)
- [평가 기준](chat-judge/chat-pipeline-quality-criteria.md)
- [측정 절차](chat-judge/service-evaluation.md)
- [라벨 검토 기록](chat-judge/label-review.md)
- [사람 판정표](chat-judge/judge-validation-v1-review-form.md)
- [실행별 결과](chat-judge/results/)
- [실행 모듈](../../scripts/chat_judge/)

## 여러 호출의 비교와 개선 기록

- [LLM Bedrock gpt-oss-120b 전환 기능별 비교](model-comparison/bedrock-switch-evaluation.md)
- [TELME-206 실시간 답변 품질 개선](model-comparison/chat-live-answer-fixes.md)
- [EXAONE 네이티브 Ollama 0.34.0 반복 평가 결과](model-comparison/exaone-repeat-evaluation.md)

## 결과 보존과 해석

- 실행 폴더명과 원시 자료는 측정 당시의 식별자다. 파일명·압축 내부 경로·manifest·라벨·수치를 일괄 정규화하지 않는다.
- 결과 README의 탐색 링크와 현재 위치에서 실행할 명령의 문서 경로만 이동에 맞춘다.
- 코드 커밋, 데이터셋·라벨 버전, 모델·환경·설정과 측정 범위를 함께 읽는다. 과거 비교 결과를 현재 운영 성능으로 환산하지 않는다.
- 검색 전용 평가셋 보강·다중 정답 실험은 [검색 단계](../01-pipeline/README.md#03-faq-검색)에 있다.
