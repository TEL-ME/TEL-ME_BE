# TELME-183 요청 개수 판정 원시 결과

[비교 보고서](../../../../docs/EXAONE_REPEAT_EVALUATION.md)의 2026-10-17 네이티브 Ollama 0.34.0 반복 평가 자료입니다.
과거 PR 스냅샷의 평가이며 최신 develop의 회귀 테스트 결과가 아닙니다.

- `metrics.json`: 이 단계의 회차별 채점 집계와 반복 비교. Git에서 확인할 수 있는 요약입니다.
- `results.zip`: 이 단계의 1·2회차 원시 응답, 실행 명령·설정, 평가셋, 채점·검증 자료, 실행 로그.
- 공통 환경 자료와 당시 실행·채점 스크립트는 각 단계 ZIP에 함께 보존합니다.
- `results/manifest.json`과 공통 실행·채점 스크립트는 전체 실험을 설명하므로 다른 단계의 설정도 포함합니다. 단계별 신규 원시 응답과 `results/scores.json`은 분리했습니다.

`route-full-1/2`는 요청 개수 판정 단독 호출과 구분한 전체 FAQ 라우팅의 분해 결과입니다. 문맥 출력과 연결한 통합 실험은 아닙니다.

## 무결성 확인

저장소 루트에서 실행합니다. 압축은 작업용 임시 디렉터리에 해제합니다.

```bash
shasum -a 256 scripts/request-inventory-evaluation/results/TELME-183-20261017/results.zip
unzip scripts/request-inventory-evaluation/results/TELME-183-20261017/results.zip -d /tmp/TELME-183-request-inventory-evaluation
(cd /tmp/TELME-183-request-inventory-evaluation && shasum -a 256 -c checksums.sha256)
```

ZIP SHA-256: `bab1f10e620d18c2196f2b86c21276c5a61ab6e88e2de32b56caace7edf29dc8`

ZIP 내부 경로는 압축 해제 디렉터리 기준입니다. 실행별 설정은 `results/*-execution.json`,
평가용 코드 변경은 `results/adaptations/`, 원시 모델 응답은 `results/`에 있습니다.
로그에 포함된 `/private/tmp/` 등 절대 경로는 측정 당시 기록입니다.

## 사용 범위

기록한 스크립트는 당시 환경과 로컬 경로에 의존하며, 새 장비에서 즉시 실행하는 범용 도구가 아닙니다.
특히 `score_results.py`는 검색 서버를 호출하는 로직을 포함하므로 무결성 확인을 위해 실행하지 않습니다.
원본 인계 DB 덤프와 모델 바이너리는 Git에 포함하지 않습니다. 실행 조건과 재현 한계는 보고서를 참고합니다.
