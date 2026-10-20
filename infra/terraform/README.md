# TEL-ME 배포 인프라 (Terraform)

서울 리전 9~10일 시연 배포용. 서비스 주소는 `https://www.telme.site`(가비아 도메인 + us-east-1 ACM 인증서를 CloudFront에 연결)다. 도메인 변수를 비우면 CloudFront 기본 주소(`*.cloudfront.net`)로도 열 수 있다.

```text
브라우저 ─HTTPS→ CloudFront ─┬ /*                          → S3 (OAC)
                             └ /api/*, /oauth2/*, /login/oauth2/* ─HTTP 80 + 비밀 헤더→ ALB
                                                                     └ ASG: 앱 EC2 t3.large (Spring Boot + TEI bge-m3)
앱 EC2 ─┬ RDS PostgreSQL 16 (프라이빗 서브넷)
        ├ Bedrock gpt-oss-120b (도쿄 ap-northeast-1)  ← 평소 모든 LLM 호출
        └ GPU EC2 g6.xlarge vLLM EXAONE (평소 중지)   ← Bedrock 장애 때만
```

NAT Gateway는 없다. 앱·GPU EC2는 퍼블릭 서브넷에 두고 보안 그룹으로 막는다. SSH는 열지 않고 SSM Session Manager로 접속한다.

## 파일

| 파일 | 내용 |
|---|---|
| `versions.tf` | provider, S3 backend(`telme-tfstate-989624287827`, 잠금은 lock 파일) |
| `network.tf` | VPC, 퍼블릭·프라이빗 서브넷 각 2개, IGW, S3 게이트웨이 엔드포인트, 보안 그룹 4개 |
| `iam.tf` | 앱·GPU 인스턴스 역할, GitHub Actions OIDC 역할 |
| `data.tf` | ECR, RDS |
| `app.tf` | ALB(80, 비밀 헤더 규칙), 대상 그룹(sticky), 시작 템플릿, ASG |
| `gpu.tf` | GPU EC2(만든 뒤 중지) |
| `frontend.tf` | S3, CloudFront(오리진 2개, SPA 함수) |
| `config.tf` | Parameter Store `/telme/prod/env/*`, 이미지 태그, 앱 시크릿 껍데기 |
| `monitoring.tf` | 로그 그룹 7일, SNS 이메일, 알람 4개(5xx, 정상 서버 0대, GPU 2시간, Bedrock 대체) |
| `user_data/app.sh` | 앱 EC2 첫 부팅: env 파일 → TEI → 백엔드 컨테이너. `/opt/telme/start.sh`로 다시 실행 가능 |
| `user_data/gpu.sh` | GPU 부팅마다: `/opt/telme/vllm.sh`, 30분 유휴 시 자동 중지 cron |

## 준비

- AWS CLI 프로필 `telme`(계정 989624287827). `aws sts get-caller-identity --profile telme`로 확인
- `cp terraform.tfvars.example terraform.tfvars` 후 `alarm_email` 입력
- 백엔드에 TELME-187(vLLM·TEI 클라이언트)이 머지되어 있어야 한다. 없으면 `LLM_PROVIDER=vllm`, `EMBEDDING_PROVIDER=tei`가 동작하지 않는다

## 도메인 (가비아 `telme.site`)

DNS는 가비아에 두고, 인증서만 ACM에서 받는다. 루트(`telme.site`)는 CNAME을 걸 수 없어 `www.telme.site`를 서비스 주소로 쓴다.

1. 콘솔 리전을 **us-east-1**로 바꾸고 ACM → 퍼블릭 인증서 요청: `www.telme.site`, `telme.site`, DNS 검증
2. 가비아 DNS 관리에 검증용 CNAME 추가. 호스트 칸에는 `_xxxx`처럼 `.telme.site` 앞부분만 넣는다. 레코드는 지우지 않는다(갱신 때 다시 확인)
3. 발급되면 `terraform.tfvars`에 넣는다
   ```hcl
   domain_names        = ["www.telme.site"]
   acm_certificate_arn = "arn:aws:acm:us-east-1:989624287827:certificate/..."
   ```
4. apply 뒤 가비아에 CNAME `www` → `terraform output -raw cloudfront_domain_name`
5. 루트 주소는 가비아 도메인 포워딩으로 `www`에 넘긴다(선택)

인증서는 Terraform 밖에 있어 destroy해도 남는다. 다시 올리면 `www` CNAME 값만 새 CloudFront 주소로 고친다. 두 변수를 비우면 `*.cloudfront.net`으로 동작한다.

## 초기 데이터

운영 DB에는 Flyway의 `db/migration`만 적용된다(`application-prod.yml`). 로컬 샘플인 `db/dev-migration`은 들어가지 않으므로 아래 세 가지를 2차 apply 뒤(앱이 떠서 테이블이 생긴 뒤) 한 번 넣는다.

| 데이터 | 로컬에서 넣던 곳 | 운영에서 넣는 방법 |
|---|---|---|
| FAQ 1,150건 + 벡터 2종 | (로컬은 별도 적재) | `scripts/data/faq_full_1150.json`을 앱 이미지의 FAQ 적재 기능(`FAQ_BATCH_LOAD_ENABLED`)으로 넣는다. 벡터는 TEI가 만든다 |
| 매장 150곳 | `V7__seed_stores.sql` | 같은 SQL을 psql로 넣는다. 업무 종류(`store_service_types`)는 운영 마이그레이션 V20에 이미 있다 |
| 관리자 계정 | `V2`(계정) + `V8`(비밀번호 `admin1234`) | 새 이메일·비밀번호로 직접 만든다. 로컬 비밀번호를 운영에 쓰지 않는다 |
| 요금제 15종 | — | 운영 마이그레이션 V29에 있어 자동으로 들어간다 |

넣지 않는 것: `V2`의 샘플 사용자·매장 2곳·FAQ 2건, `V12`의 더미 벡터. 더미 벡터가 섞이면 검색 결과가 틀어진다.

모두 앱 EC2에서 한다(`aws ssm start-session --profile telme --target <앱 인스턴스 ID>`). DB 접속 정보는 `/opt/telme/app.env`에 있다.

```bash
sudo -i
mkdir -p /opt/telme/seed && cd /opt/telme/seed
# 레포가 공개라 develop의 파일을 그대로 받는다
curl -fsSLO https://raw.githubusercontent.com/TEL-ME/TEL-ME_BE/develop/scripts/data/faq_full_1150.json
curl -fsSLO https://raw.githubusercontent.com/TEL-ME/TEL-ME_BE/develop/src/main/resources/db/dev-migration/V7__seed_stores.sql

# psql은 컨테이너로 쓴다. 작은따옴표 안의 변수는 컨테이너 안에서 app.env 값으로 풀리고, 인자는 "$@"로 그대로 넘긴다
psql_run() {
  docker run --rm -i --network host --env-file /opt/telme/app.env -v /opt/telme/seed:/seed:ro postgres:16 \
    sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql "host=$POSTGRES_HOST port=$POSTGRES_PORT dbname=$POSTGRES_DB user=$POSTGRES_USER sslmode=require" -v ON_ERROR_STOP=1 "$@"' sh "$@"
}
```

1. **매장**
   ```bash
   psql_run -f /seed/V7__seed_stores.sql
   ```
2. **FAQ와 벡터**: 같은 이미지를 한 번 더 띄워 적재만 하고 끝낸다(`exit-after`). 떠 있는 앱과 포트가 겹치지 않게 18080을 쓴다. 이미 있는 FAQ(`slot_id`)는 건너뛰므로 다시 돌려도 안전하다
   ```bash
   docker run --rm --network host --env-file /opt/telme/app.env \
     -e SERVER_PORT=18080 -e JAVA_TOOL_OPTIONS=-Xmx1g \
     -e FAQ_BATCH_LOAD_ENABLED=true -e FAQ_BATCH_LOAD_PATH=/seed/faq_full_1150.json \
     -v /opt/telme/seed:/seed:ro \
     "$(docker inspect telme-app --format '{{.Config.Image}}')"
   # 마지막에 "[FaqBatchLoadRunner] 완료 - 파일 1150건 ... 신규 적재 1150건"이 나오면 된다
   ```
3. **관리자 계정**: 비밀번호 해시는 내 Mac에서 만든다(비밀번호가 서버 기록에 남지 않게)
   ```bash
   # Mac
   htpasswd -bnBC 10 "" '<관리자 비밀번호>' | tr -d ':\n'; echo   # $2y$10$... 출력
   ```
   ```bash
   # 앱 EC2. 해시의 $가 풀리지 않게 따옴표 친 heredoc으로 파일을 만든다
   cat > /opt/telme/seed/admin.sql <<'SQL'
   INSERT INTO users (email, password_hash, name, role, status)
   VALUES ('<관리자 이메일>', '<위 해시>', '운영자', 'ADMIN', 'ACTIVE');
   SQL
   psql_run -f /seed/admin.sql && rm /opt/telme/seed/admin.sql
   ```
4. **확인**
   ```bash
   psql_run -c "select (select count(*) from faqs) faqs, (select count(embedding_question) from faq_embeddings) question_vectors, (select count(*) from stores) stores, (select count(*) from plans) plans, (select count(*) from users where role='ADMIN') admins"
   # faqs 1150, question_vectors 1150, stores 150, plans 15, admins 1
   ```
   화면에서 FAQ 질문 하나, 매장 찾기, 관리자 로그인까지 확인한다.

## 처음 띄우기

1. **1차 apply** (`app_instance_count = 0`)
   ```bash
   terraform init
   terraform plan -out=tfplan
   terraform apply tfplan
   ```
   이미지가 없을 때 서버를 띄우면 헬스 체크 실패로 계속 교체되므로 앱 서버는 0대로 만든다.
2. **메일함에서 SNS 구독 확인**을 누른다.
3. **시크릿 값 넣기** (`terraform output app_secret_arn`)
   ```bash
   aws secretsmanager put-secret-value --profile telme --secret-id telme/prod/app \
     --secret-string file://app-secret.json   # 파일은 커밋하지 않고 넣은 뒤 지운다
   ```
   키: `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REST_API_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
4. **외부 콘솔 등록** (`terraform output oauth_redirect_uris`, `site_url`)
   - Google·Kakao 로그인 리다이렉트 URI
   - 카카오맵 JavaScript SDK 도메인에 `site_url`
5. **백엔드 이미지 올리기** (태그는 바꿀 수 없으므로 매번 새 태그)
   ```bash
   REPO=$(terraform output -raw ecr_repository_url)
   aws ecr get-login-password --profile telme | docker login --username AWS --password-stdin ${REPO%%/*}
   docker build --platform linux/amd64 -t $REPO:<git-sha> ../..
   docker push $REPO:<git-sha>
   ```
6. **2차 apply**: `terraform.tfvars`에서 `app_instance_count = 1`, `app_image_tag = "<git-sha>"`로 바꾸고 apply. 첫 기동에 Flyway가 RDS에 확장·테이블을 만든다.
7. **프론트 올리기** (FE 레포, `VITE_API_BASE_URL`은 비우고 `VITE_ENABLE_MSW=false`)
   ```bash
   npm run build
   aws s3 sync dist s3://$(terraform output -raw frontend_bucket) --delete --profile telme
   aws cloudfront create-invalidation --distribution-id $(terraform output -raw cloudfront_distribution_id) --paths '/*' --profile telme
   ```
8. **초기 데이터 넣기**: 위 "초기 데이터" 절을 따른다. 운영은 `db/dev-migration`(샘플 데이터)을 적용하지 않아 FAQ·매장·관리자 계정이 비어 있다.
9. **GPU 처음 준비**
   ```bash
   aws ec2 start-instances --profile telme --instance-ids $(terraform output -raw gpu_instance_id)
   aws ssm start-session --profile telme --target <gpu-instance-id>
   sudo touch /etc/telme/keep-running   # 모델 받는 동안 자동 중지 끄기
   sudo /opt/telme/vllm.sh
   curl -s localhost:8000/health && curl -s localhost:8000/v1/models
   sudo rm /etc/telme/keep-running && sudo shutdown -h now   # 중지 상태로 돌아간다
   ```

## 새 버전 배포

이미지를 새 태그로 push → SSM 파라미터 갱신 → Instance Refresh(새 서버를 먼저 띄우고 옛 서버를 내린다).

GitHub Actions가 `telme-github-deploy` 역할(OIDC)로 같은 일을 한다. AWS 키는 GitHub에 넣지 않는다.

| 레포 | 워크플로 | 언제 | 저장소 변수(Settings → Secrets and variables → Actions → Variables) |
|---|---|---|---|
| TEL-ME_BE | `.github/workflows/deploy.yml` | develop의 CI가 통과한 뒤, 또는 수동 실행 | `DEPLOY_ENABLED=true` |
| TEL-ME_FE | `.github/workflows/deploy.yml` | develop push, 또는 수동 실행 | `DEPLOY_ENABLED=true`, `CLOUDFRONT_DISTRIBUTION_ID`, `VITE_KAKAO_MAP_KEY` |

`DEPLOY_ENABLED`가 없으면 워크플로는 건너뛴다. 2차 apply가 끝난 뒤에 켠다.

교체가 실패·취소되거나 35분 안에 끝나지 않으면 워크플로가 SSM 이미지 태그를 이전 값으로 되돌린다(시간 초과면 교체도 취소). 최소 정상 100%라 옛 서버는 그대로 남는다.

손으로 할 때:

```bash
aws ssm put-parameter --profile telme --name /telme/prod/deploy/image_tag --value <git-sha> --overwrite
# 설정을 꼭 준다. 없으면 최소 정상 90%라 한 대 구성에서 옛 서버를 먼저 내릴 수 있다
aws autoscaling start-instance-refresh --profile telme --auto-scaling-group-name telme-app --strategy Rolling \
  --preferences '{"MinHealthyPercentage":100,"MaxHealthyPercentage":200,"InstanceWarmup":600}'
```

실패하면 태그를 이전 값으로 되돌린다(`put-parameter`를 이전 태그로 다시 실행).

설정값이나 시크릿만 바꿨으면 앱 EC2에서 `sudo /opt/telme/start.sh`를 다시 실행한다. DB 비밀번호도 부팅할 때 한 번 읽으므로, RDS 비밀번호를 손으로 회전했다면 같은 방법으로 다시 읽힌다(자동 회전은 365일로 늘려 시연 중에는 바뀌지 않는다).

## 끝낼 때

RDS는 삭제 방지가 켜져 있어 바로 지워지지 않는다(실수 방지).

1. `data.tf`의 `deletion_protection = false`로 바꾸고 `terraform apply`
2. `terraform destroy`
3. 남는 것: RDS 최종 스냅샷 `telme-db-final`, state 버킷, ACM 인증서(us-east-1). 필요 없으면 콘솔에서 지운다
4. 가비아에서 `www` CNAME을 지운다(없는 CloudFront 주소를 가리키게 두지 않는다)
5. Billing에서 남은 요금이 없는지 확인

## 주의

- CloudFront → ALB 구간은 HTTP다. 사용자와 만나는 구간(브라우저 → CloudFront)만 HTTPS로 두고, ALB에는 인증서를 따로 붙이지 않았다. 대신 ALB는 CloudFront IP 대역(보안 그룹) + CloudFront가 붙이는 비밀 헤더가 맞는 요청만 받는다. 이 구간까지 암호화하려면 ALB용 인증서(서울 리전 ACM, 예: `origin.telme.site`)와 HTTPS 리스너를 추가하면 된다.
- 비밀 헤더 값과 DB 주소는 state에 남는다. state 버킷은 암호화·퍼블릭 차단·버전 관리가 켜져 있다.
- 도메인 없이 CloudFront를 지웠다 다시 만들면 주소가 바뀌어 OAuth·카카오맵 등록을 다시 해야 한다. 도메인을 쓰면 가비아 `www` CNAME만 고친다.
- GPU는 30분 유휴 시 스스로 꺼진다. 2시간 넘게 켜져 있으면 알람이 온다.
- Bedrock 호출이 실패해 엑사원으로 대체된 호출이 5분 안에 2번 이상이면 앱이 꺼진 GPU를 켠다(TELME-204, `VLLM_GPU_INSTANCE_ID`). 조건 추출의 파싱 재시도는 세지 않고, 일시 오류(8초 시간 초과·429) 한 번으로는 켜지 않는다. 세션 첫 질문 장애면 첫 턴에, 이어 묻는 질문처럼 턴당 대체가 1번이면 두 번째 실패 턴에 켜진다. 켜서 vLLM이 첫 답을 내기까지 약 1분 40초~2분 40초(2026-10-19 실측)이고, 그동안 대체 호출은 실패한다. 대체가 한 번이라도 생기면 `telme-bedrock-fallback` 알람이 온다.
- RDS 백업은 KST 02:00~02:30, 유지 관리는 KST 월 03:00~04:00. 자동 마이너 버전 업그레이드는 꺼 두었다.
- TEI는 t3.large 메모리에 맞게 `--max-batch-tokens 2048 --auto-truncate`로 띄운다(기본값이면 OOM으로 재시작 반복). 새 앱 서버는 부팅할 때 Hugging Face에서 bge-m3(약 2.3GB)를 받는다.
- 컨테이너에 `AWS_ACCESS_KEY_ID`·`AWS_BEARER_TOKEN_BEDROCK`을 넣지 않는다. 넣으면 역할보다 먼저 쓰여 Bedrock 호출이 깨진다.
