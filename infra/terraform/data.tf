resource "aws_ecr_repository" "backend" {
  name                 = "${var.name}-backend"
  image_tag_mutability = "IMMUTABLE"
  # 배포가 끝나면 destroy로 지운다. 이미지가 남아 있어도 지워지게 한다
  force_delete = true

  # push할 때 알려진 취약점(CVE)을 기본 스캔한다. 무료이고 결과로 배포를 막지는 않는다(콘솔 ECR → 이미지 → 취약성)
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개만 보관"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ---------- RDS ----------

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = aws_subnet.private[*].id
}

# pgvector, PostGIS 확장은 Flyway 마이그레이션이 CREATE EXTENSION으로 만든다
resource "aws_db_instance" "main" {
  identifier     = "${var.name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  # 비밀번호는 Secrets Manager가 만들고 관리한다(state에 평문이 남지 않음)
  manage_master_user_password = true

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 7
  # 백업, 유지 관리 시간을 한국 새벽으로 고정(UTC 기준). 자동 마이너 업그레이드는 단일 AZ에서 수 분 재시작하므로 시연 기간에는 끈다
  backup_window              = "17:00-17:30"         # KST 02:00~02:30
  maintenance_window         = "sun:18:00-sun:19:00" # KST 월 03:00~04:00
  auto_minor_version_upgrade = false
  apply_immediately          = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-db-final"
}

# RDS가 관리하는 비밀번호 회전 주기를 시연 기간보다 길게
resource "aws_secretsmanager_secret_rotation" "db" {
  secret_id          = aws_db_instance.main.master_user_secret[0].secret_arn
  rotate_immediately = false

  rotation_rules {
    automatically_after_days = 365
  }
}
