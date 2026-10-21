#!/bin/bash
# 앱 EC2 첫 부팅: Docker 설치 → 설정, 비밀값을 env 파일로 → TEI(bge-m3, CPU) → 백엔드 컨테이너
# 설정이나 비밀값을 바꾼 뒤에는 SSM으로 접속해 /opt/telme/start.sh를 다시 실행하면 된다
set -euo pipefail

dnf install -y docker jq
systemctl enable --now docker

mkdir -p /opt/telme/tei-data
cat > /opt/telme/start.sh <<'SCRIPT'
#!/bin/bash
set -euo pipefail

REGION="${region}"
REGISTRY="${registry}"
REPOSITORY_URL="${repository_url}"
ENV_PREFIX="${env_prefix}"
IMAGE_TAG_PARAM="${image_tag_param}"
APP_SECRET_ARN="${app_secret_arn}"
DB_SECRET_ARN="${db_secret_arn}"
TEI_IMAGE="${tei_image}"
LOG_GROUP="${log_group}"

TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
INSTANCE_ID=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)

# 1) 환경 변수 파일. 일반 설정은 Parameter Store, 비밀값은 Secrets Manager
ENV_FILE=/opt/telme/app.env
TMP=$(mktemp)
chmod 600 "$TMP"
aws ssm get-parameters-by-path --region "$REGION" --path "$ENV_PREFIX/" --recursive \
  --query 'Parameters[].[Name,Value]' --output text \
  | while IFS=$'\t' read -r name value; do echo "$${name##*/}=$value"; done >> "$TMP"

aws secretsmanager get-secret-value --region "$REGION" --secret-id "$DB_SECRET_ARN" \
  --query SecretString --output text \
  | jq -r '"POSTGRES_USER=\(.username)\nPOSTGRES_PASSWORD=\(.password)"' >> "$TMP"

# 앱 비밀값은 사람이 콘솔에서 넣는다. 아직 없으면 더미값으로 뜨고 로그인만 안 된다
if APP_SECRET=$(aws secretsmanager get-secret-value --region "$REGION" --secret-id "$APP_SECRET_ARN" \
    --query SecretString --output text 2>/dev/null); then
  echo "$APP_SECRET" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$TMP"
else
  echo "[telme] $APP_SECRET_ARN 값이 없어 OAuth·Kakao 키 없이 띄운다" >&2
fi
mv "$TMP" "$ENV_FILE"

# 2) TEI. 127.0.0.1에만 열어 서버 밖에서는 못 부르게 한다
# --max-batch-tokens 기본값(16384)은 준비 단계에서 약 7.6GB를 써 t3.large(8GB)에서 OOM으로 재시작을 반복한다.
# 2048이면 약 3.1GB. FAQ 최장 글이 약 100토큰이라 --auto-truncate로 잘리는 입력은 없다(벡터가 DB와 같음 확인)
docker rm -f telme-tei >/dev/null 2>&1 || true
docker run -d --name telme-tei --restart unless-stopped \
  -p 127.0.0.1:8081:80 \
  -v /opt/telme/tei-data:/data \
  --log-driver awslogs --log-opt awslogs-region="$REGION" \
  --log-opt awslogs-group="$LOG_GROUP" --log-opt awslogs-stream="tei-$INSTANCE_ID" \
  "$TEI_IMAGE" --model-id BAAI/bge-m3 --max-client-batch-size 64 \
  --max-batch-tokens 2048 --auto-truncate

# 첫 부팅에는 bge-m3(약 2.3GB)를 받느라 시간이 걸린다. 앱의 임베딩 워밍업이 실패하지 않게 기다린다
for _ in $(seq 1 120); do
  if curl -sf http://127.0.0.1:8081/health >/dev/null; then break; fi
  sleep 5
done

# 3) 백엔드. host 네트워크라 127.0.0.1:8081(TEI)을 바로 부르고, 8080은 보안 그룹이 ALB에서만 받는다
IMAGE_TAG=$(aws ssm get-parameter --region "$REGION" --name "$IMAGE_TAG_PARAM" --query Parameter.Value --output text)
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$REPOSITORY_URL:$IMAGE_TAG"

docker rm -f telme-app >/dev/null 2>&1 || true
docker run -d --name telme-app --restart unless-stopped \
  --network host \
  --env-file "$ENV_FILE" \
  --log-driver awslogs --log-opt awslogs-region="$REGION" \
  --log-opt awslogs-group="$LOG_GROUP" --log-opt awslogs-stream="app-$INSTANCE_ID" \
  "$REPOSITORY_URL:$IMAGE_TAG"
SCRIPT

chmod 700 /opt/telme/start.sh
/opt/telme/start.sh
