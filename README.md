# TEL-ME_BE

RAG 기반 AI 통신 상담 및 위치 기반 매장 안내 서비스 **TEL-ME**의 백엔드 저장소입니다.

## 기술 스택

| 구분 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.5 (Wrapper) |
| PostgreSQL | 16 + pgvector 0.8.2 |
| Ollama | 0.34.0 |

주요 라이브러리: Spring Web · Validation · Data JPA(JdbcTemplate) · Security · Resilience4j · springdoc-openapi · Actuator · Lombok

## 사전 준비

- JDK 21
- Docker Desktop

## 실행 방법

### 1. 환경변수 파일 만들기

```bash
cp .env.example .env
```

기본값 그대로 실행되므로, 포트가 겹칠 때만 값을 바꾸면 됩니다. `.env`는 커밋하지 않습니다.
`bootRun`이나 STS는 `.env`를 자동으로 읽지 않습니다. DB 접속값을 바꿨다면 `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`를 애플리케이션 실행 환경에도 같은 값으로 설정합니다.

### 2. DB 실행

```bash
docker compose up -d
```

PostgreSQL 16 + pgvector가 실행되고, 최초 생성 시 `vector` 확장이 자동으로 켜집니다.
현재 Compose는 PostgreSQL과 선택 실행하는 Ollama만 띄웁니다. 아래 `bootRun`은 호스트에서 백엔드를 실행합니다.

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

STS에서는 `File → Import → Gradle → Existing Gradle Project`로 불러온 뒤 `TelmeApplication`을 실행합니다.

| 주소 | 설명 |
| --- | --- |
| http://localhost:8080/actuator/health | 서버 상태 확인 |
| http://localhost:8080/swagger-ui/index.html | API 문서 |

채팅 API는 회원 세션이나 자동 발급된 게스트 세션으로 이용할 수 있습니다. 피드백 기능의 활성화 방법과 요청 형식은 [피드백 API 문서](docs/feedback-api.md)를 참고하세요.

### 4. 종료

```bash
docker compose down      # 컨테이너 종료 (데이터 유지)
docker compose down -v   # 데이터까지 삭제
```

## 백엔드 Docker 이미지

`Dockerfile`은 Spring Boot API 이미지만 빌드합니다. 로컬에서 컨테이너로 확인하려면 위 방법으로 `.env` 파일을 만들고 DB를 실행한 뒤 다음 명령을 사용합니다.

```bash
docker build -t telme-be:local .
docker run --rm --env-file .env -e POSTGRES_HOST=host.docker.internal -p 127.0.0.1:8080:8080 telme-be:local
```

컨테이너의 `localhost`는 호스트 PC가 아니므로, Docker Desktop에서는 `host.docker.internal`로 Compose가 호스트에 공개한 DB 포트에 연결합니다. 포트나 DB 계정을 바꿨다면 `.env`의 값을 사용합니다. 종료할 때는 실행 중인 `docker run` 명령에서 `Ctrl+C`를 누릅니다.

EC2 배포에서는 로컬 `.env` 대신 RDS 접속값을 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 환경변수로 전달합니다. Docker 이미지 빌드는 테스트를 제외하며, 테스트는 아래 Gradle 빌드와 CI에서 실행합니다.

## Ollama (선택)

LLM 기능을 개발하거나 확인할 때만 필요합니다. 두 방식 중 하나를 선택합니다.

**Docker로 실행**

```bash
docker compose --profile ollama up -d
docker exec telme-ollama ollama pull exaone3.5:7.8b
docker exec telme-ollama ollama pull bge-m3
```

**앱으로 설치 (Mac 권장)**

Mac은 Docker 안에서 GPU를 쓰지 못해 응답이 느릴 수 있습니다. 속도가 필요하면 [Ollama 앱](https://ollama.com/download)을 설치하고 모델을 받습니다. 이 경우 Docker의 ollama 서비스는 띄우지 않습니다(포트 11434 충돌).

```bash
ollama pull exaone3.5:7.8b
ollama pull bge-m3
```

두 방식 모두 주소는 `http://localhost:11434`입니다.

## 빌드 · 테스트

```bash
./gradlew build
```

테스트가 DB에 접속하므로 `docker compose up -d`로 PostgreSQL을 먼저 실행해야 합니다.
develop · main 대상 PR과 push에서는 GitHub Actions가 같은 빌드를 자동으로 실행합니다.
