# Answer Guard 공유 회귀 검증 (TELME-190)

Java 21 환경에서 저장소 루트에서 실행한다. DB나 외부 API 키는 필요하지 않으며, 클라이언트는 mock/fake다. `.env`를 읽거나 로드하지 않는다.

```sh
./gradlew --max-workers=2 \
  -I scripts/answer-guard-regression/regression.init.gradle test assemble
```

Gradle 의존성이 캐시에 준비된 환경에서는 `--offline`을 추가할 수 있다. 개인 디렉터리나 심볼릭 링크가 필요하지 않다. init script를 명시할 때만 이 디렉터리의 테스트·자료가 추가된다. 기본 `./gradlew test`의 테스트 범위는 바꾸지 않는다.

## 공유 자료

| 파일 (`resources/answer-guard-recovery/`) | 내용 |
| --- | --- |
| `cases.json` | 인수인계 캡처와 합성 경계 사례 48개 |
| `recovery-safety.json` | 구현·리뷰 과정에서 추가한 경계 사례 113개 |
| `history.json` | 과거 평가에서 저장한 모델 답변 651개와 질문·근거 |
| `history-baseline.json` | 위 답변에 대한 기존 가드의 출력·예외 651개 |

압축된 로컬 원본을 검토 가능한 JSON으로 풀어 공유했다. 키·값은 원본과 동일하다. `manifest.json`은 공유 테스트·자료·실행 설정의 SHA-256이다.

## 결과 해석

- `ComparisonLocalContractTest`: 161개 경계 사례에 명시적 mock 판정 결과를 주입한다. 기존 `category` 필드는 과거 문법 기반 평가의 분류이며, 실제 의미 판정의 정답표로 간주하지 않는다. 이 테스트는 허용·차단 연결 계약을 확인한다. 긴 입력 한도 테스트 1개도 포함한다.
- `ComparisonHistoryReplayTest`: 추가 의미 판정을 사용하지 않는 기존 경로로 저장 답변 651개를 재생한다. 기준 출력·예외와 같은지 한 테스트에서 모두 확인한다. 기록된 답변을 재생할 뿐 모델을 새로 호출하지 않는다.
- init script는 위 테스트와 관련 기존 단위 테스트를 함께 실행한다. 공유 전 같은 범위에서 1,663건 통과했다. 테스트 수는 저장소의 관련 단위 테스트가 변경되면 달라질 수 있다.
- 재생 결과: `build/reports/answer-guard-recovery/replay-history.json`, JUnit 결과: `build/test-results/test/`, HTML: `build/reports/tests/test/index.html`.

실제 판정 모델의 복구율·오통과율을 이 mock 결과에서 추정하면 안 된다. 리뷰어가 별도로 평가한 블라인드 65문장과 품질셋 307턴은 이 자료에 포함되지 않는다. 해당 원시 자료는 아직 전달받지 않았다.

과거 가드의 별도 결함은 기준 출력에도 남을 수 있다. 기준선 일치가 답변의 의미적 정확성을 증명하지는 않는다. 개인 로컬 로그·개발 환경 상태·폐기한 문법 기반 테스트는 공유 대상에서 제외했다.
