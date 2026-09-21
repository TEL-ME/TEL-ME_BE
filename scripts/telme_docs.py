# 문서 파서

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

DOCS_DIR = Path(__file__).resolve().parent.parent / "docs"
POLICY_PATH = DOCS_DIR / "POLICY.md"
TAXONOMY_PATH = DOCS_DIR / "FAQ_TAXONOMY.md"

EXPECTED_CATEGORIES = 10
EXPECTED_QUESTION_TYPES = 5
EXPECTED_PERSONAS = 3
EXPECTED_POLICY_ITEMS = 45
EXPECTED_TOTAL_QUOTA = 1150

# 문서 구조가 파서 기대와 불일치
class DocumentError(RuntimeError):
    pass


# 마크다운 표 파싱
_HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
_SEPARATOR = re.compile(r"^\|[\s:|-]+\|$")


@dataclass(frozen=True)
class Table:
    section: tuple[str, ...]  # 헤딩 경로
    header: tuple[str, ...]
    rows: tuple[tuple[str, ...], ...]
    line_no: int

    def column(self, name: str) -> int:
        try:
            return self.header.index(name)
        except ValueError:
            raise DocumentError(
                f"{self.line_no}번째 줄 표에 '{name}' 열 없음. 열: {self.header}"
            ) from None


def _clean(cell: str) -> str:
    return cell.strip().strip("`").replace("**", "").strip()


def _split_row(line: str) -> tuple[str, ...]:
    return tuple(_clean(c) for c in line.strip().strip("|").split("|"))


def parse_tables(path: Path) -> list[Table]:
    lines = path.read_text(encoding="utf-8").splitlines()
    stack: list[str] = []
    tables: list[Table] = []
    i = 0
    in_fence = False

    while i < len(lines):
        line = lines[i]

        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            i += 1
            continue
        if in_fence:
            i += 1
            continue

        heading = _HEADING.match(line)
        if heading:
            level = len(heading.group(1))
            del stack[level - 1:]
            stack.append(heading.group(2).strip())
            i += 1
            continue

        # 표 = 헤더 줄 + 구분 줄 + 본문 줄
        if line.startswith("|") and i + 1 < len(lines) and _SEPARATOR.match(lines[i + 1]):
            header = _split_row(line)
            header_line = i + 1
            i += 2
            rows: list[tuple[str, ...]] = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(_split_row(lines[i]))
                i += 1
            tables.append(Table(tuple(stack), header, tuple(rows), header_line))
            continue

        i += 1

    return tables

# 섹션명 + 헤더 앞부분으로 표 1개 특정(0개/2개 이상이면 실패)
def find_table(
    tables: list[Table], *, section: str, header_startswith: tuple[str, ...]
) -> Table:
    matched = [
        t
        for t in tables
        if any(section in s for s in t.section)
        and t.header[: len(header_startswith)] == header_startswith
    ]
    if len(matched) != 1:
        found = [(t.section, t.header, t.line_no) for t in matched]
        raise DocumentError(
            f"섹션 '{section}' + 헤더 {header_startswith} 로 표 특정 실패 "
            f"({len(matched)}개), 후보: {found}"
        )
    return matched[0]


def _require_count(label: str, actual: int, expected: int) -> None:
    if actual != expected:
        raise DocumentError(f"{label}: {expected}개 기대, {actual}개 읽음")


# 수치 추출

# 단위 붙은 수치만 대조 대상
_UNITS = (
    "만\\s*원|억\\s*원|원"
    # 교대(|)는 앞에서부터 매칭되므로 긴 단위를 먼저
    # 개월이 월보다, 영업일이 일보다 앞
    "|년|개월|월|영업일|일|주"
    "|시간|분"
    "|회선|회|종|개|세|명|건|장|통"
    "|%|퍼센트"
    "|GB|MB|TB|KB|kbps|Mbps|Gbps"
)
# 쉼표는 천단위일 때만 숫자의 일부
_NUM = r"\d+(?:,\d{3})*(?:\.\d+)?"

_RANGE_RE = re.compile(rf"({_NUM})\s*[~∼-]\s*({_NUM})\s*({_UNITS})")
_SINGLE_RE = re.compile(rf"({_NUM})\s*({_UNITS})")
# \b 사용 불가
_TIME_RE = re.compile(r"(?<!\d)(\d{1,2}):(\d{2})(?!\d)")

_UNIT_ALIAS = {"퍼센트": "%"}


def _norm_unit(unit: str) -> str:
    u = re.sub(r"\s+", "", unit)
    return _UNIT_ALIAS.get(u, u)


def _emit(out: set[str], raw_num: str, unit: str) -> None:
    num = raw_num.replace(",", "")
    u = _norm_unit(unit)
    out.add(f"{num}{u}")
    # '30만원' = '300,000원' -> 양쪽 표기 모두 수용
    if u in ("만원", "억원"):
        scale = 10_000 if u == "만원" else 100_000_000
        try:
            out.add(f"{int(float(num) * scale)}원")
        except ValueError:
            pass

# 단위 붙은 수치를 정규화해 추출
def extract_numbers(text: str) -> set[str]:
    found: set[str] = set()

    def take_range(m: re.Match[str]) -> str:
        _emit(found, m.group(1), m.group(3))
        _emit(found, m.group(2), m.group(3))
        return " "

    rest = _RANGE_RE.sub(take_range, text)
    for m in _SINGLE_RE.finditer(rest):
        _emit(found, m.group(1), m.group(2))
    for m in _TIME_RE.finditer(text):
        found.add(f"{int(m.group(1)):02d}:{m.group(2)}")
    return found


# FAQ_TAXONOMY.md
@dataclass(frozen=True)
class Taxonomy:
    categories: dict[str, str]  # 코드 -> 한글명
    question_types: dict[str, str]
    personas: dict[str, str]
    quotas: dict[str, int]  # 코드 -> 목표 건수
    triggers: dict[str, tuple[str, ...]]  # 코드 -> 사유 목록

    @property
    def total_quota(self) -> int:
        return sum(self.quotas.values())


def load_taxonomy(path: Path = TAXONOMY_PATH) -> Taxonomy:
    tables = parse_tables(path)

    def code_name(section: str, name_col: str, expected: int, label: str) -> dict[str, str]:
        t = find_table(tables, section=section, header_startswith=("코드", name_col))
        out = {r[0]: r[1] for r in t.rows if r[0]}
        _require_count(label, len(out), expected)
        return out

    categories = code_name("카테고리 10종", "한글명", EXPECTED_CATEGORIES, "카테고리")
    question_types = code_name("질문유형 5종", "이름", EXPECTED_QUESTION_TYPES, "질문유형")
    personas = code_name("페르소나 3종", "이름", EXPECTED_PERSONAS, "페르소나")

    quota_table = find_table(
        tables, section="배분", header_startswith=("카테고리", "목표 건수")
    )
    col = quota_table.column("목표 건수")
    quotas = {
        r[0]: int(r[col])
        for r in quota_table.rows
        if r[0] in categories and r[col].isdigit()
    }
    _require_count("목표 건수", len(quotas), EXPECTED_CATEGORIES)
    if sum(quotas.values()) != EXPECTED_TOTAL_QUOTA:
        raise DocumentError(
            f"목표 건수 합계 {sum(quotas.values())} "
            f"({EXPECTED_TOTAL_QUOTA} 기대) 배분 표 확인 필요"
        )

    trigger_table = find_table(
        tables, section="질문 형태", header_startswith=("카테고리", "사유 예시")
    )
    triggers = {
        r[0]: tuple(s.strip() for s in r[1].split(",") if s.strip())
        for r in trigger_table.rows
        if r[0] in categories
    }
    _require_count("사유", len(triggers), EXPECTED_CATEGORIES)

    missing = set(categories) - set(triggers)
    if missing:
        raise DocumentError(f"사유 미정의 카테고리: {sorted(missing)}")

    return Taxonomy(categories, question_types, personas, quotas, triggers)


# POLICY.md

_POLICY_ITEM = re.compile(r"^####\s+([A-Z_]+-\d+)\s*·\s*(.+)$")
_POLICY_SECTION = re.compile(r"^##\s+([A-Z_]+)\s*·")
_KEY_VALUES = re.compile(r"^-\s+\*\*핵심 수치\*\*\s*:\s*(.+)$")


@dataclass(frozen=True)
class PolicyItem:
    ref: str
    category: str
    title: str
    key_values: str  # 핵심 수치 줄 원문
    body: str  # 항목 블록 전체
    index_values: str  # 정책 값 색인 행
    numbers: frozenset[str]  # 허용 수치

    @property
    def sources(self) -> str:
        return f"{self.ref} ({self.title})"


@dataclass(frozen=True)
class Policy:
    items: dict[str, PolicyItem]
    common_numbers: frozenset[str]  # 전체 공통 전제의 수치

    def by_category(self, category: str) -> list[PolicyItem]:
        return [i for i in self.items.values() if i.category == category]

    # COMPARE 답변은 정책 항목을 여러 개 인용한다 (FAQ_TAXONOMY.md 2절)
    def allowed(self, *refs: str) -> frozenset[str]:
        out = self.common_numbers
        for ref in refs:
            out = out | self.items[ref].numbers
        return out


def load_policy(path: Path = POLICY_PATH) -> Policy:
    lines = path.read_text(encoding="utf-8").splitlines()

    # 항목 블록 수집
    blocks: list[tuple[str, str, str, list[str]]] = []
    category = ""
    current: tuple[str, str, str] | None = None
    body: list[str] = []
    common: list[str] = []
    in_common = False

    def flush() -> None:
        if current:
            blocks.append((*current, body[:]))

    for line in lines:
        sec = _POLICY_SECTION.match(line)
        if sec:
            flush()
            current, body[:] = None, []
            category = sec.group(1)
            in_common = False
            continue
        if line.startswith("## "):
            flush()
            current, body[:] = None, []
            in_common = "공통 전제" in line
            continue

        item = _POLICY_ITEM.match(line)
        if item:
            flush()
            current = (item.group(1), category, item.group(2).strip())
            body = []
            continue

        if in_common:
            common.append(line)
        elif current:
            body.append(line)
    flush()

    _require_count("정책 항목", len(blocks), EXPECTED_POLICY_ITEMS)

    # 정책 값 색인 수집
    index_table = find_table(
        parse_tables(path),
        section="정책 값 색인",
        header_startswith=("policy_ref", "카테고리", "핵심 수치"),
    )
    index = {r[0]: r[2] for r in index_table.rows if r[0]}
    _require_count("정책 값 색인", len(index), EXPECTED_POLICY_ITEMS)

    # 허용 수치 = 항목 블록 ∪ 색인 행
    items: dict[str, PolicyItem] = {}
    for ref, cat, title, block in blocks:
        if ref not in index:
            raise DocumentError(f"{ref} 정책 값 색인에 없음")
        text = "\n".join(block)
        key = next((m.group(1) for m in map(_KEY_VALUES.match, block) if m), "")
        if not key:
            raise DocumentError(f"{ref} '핵심 수치' 줄 없음")
        items[ref] = PolicyItem(
            ref=ref,
            category=cat,
            title=title,
            key_values=key,
            body=text,
            index_values=index[ref],
            numbers=frozenset(extract_numbers(text) | extract_numbers(index[ref])),
        )

    return Policy(items, frozenset(extract_numbers("\n".join(common))))


if __name__ == "__main__":
    tax = load_taxonomy()
    pol = load_policy()
    print(f"카테고리 {len(tax.categories)}종 / 질문유형 {len(tax.question_types)}종 "
          f"/ 페르소나 {len(tax.personas)}종")
    print(f"목표 건수 합계 {tax.total_quota}건")
    print(f"정책 항목 {len(pol.items)}개 / 공통 수치 {len(pol.common_numbers)}개")
    for code in tax.categories:
        n = len(pol.by_category(code))
        print(f"  {code:<12} 정책 {n}개 / 목표 {tax.quotas[code]:>4}건 "
              f"/ 사유 {len(tax.triggers[code])}종")
