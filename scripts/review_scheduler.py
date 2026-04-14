#!/usr/bin/env python3
"""
TIL 간격 반복 복습 스케줄러
- TIL 파일을 스캔하여 복습이 필요한 항목을 찾고
- Notion 데이터베이스에 오늘의 복습 항목을 추가합니다.

사용법:
  python scripts/review_scheduler.py          # 오늘 복습 대상 → Notion 전송
  python scripts/review_scheduler.py --dry-run # Notion 전송 없이 목록만 출력
"""

import os
import sys
import json
import re
import random
import subprocess
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import quote

import httpx

# 간격 반복 주기 (일): 1일 → 3일 → 7일 → 14일 → 30일 → 60일 → 90일 → 180일
INTERVALS = [1, 3, 7, 14, 30, 60, 90, 180]

TIL_ROOT = Path(__file__).resolve().parent.parent
REVIEW_STATE_FILE = TIL_ROOT / "reviews.json"
EXCLUDE_DIRS = {".git", "practice", "template", "img", "scripts", ".github", "node_modules"}
EXCLUDE_FILES = {"HELP.md", "README.md", "MEMORY.md"}
MAX_PER_DAY = 2  # 하루 최대 복습 항목 수


def get_github_repo_url():
    try:
        result = subprocess.run(
            ["git", "-C", str(TIL_ROOT), "remote", "get-url", "origin"],
            capture_output=True, text=True, timeout=5
        )
        url = result.stdout.strip()
        if url.startswith("git@"):
            url = url.replace(":", "/").replace("git@", "https://")
        url = url.removesuffix(".git")
        return f"{url}/blob/main"
    except Exception:
        return "https://github.com/biuea/TIL/blob/main"


def get_til_files():
    """TIL 마크다운 파일 목록 조회"""
    til_files = []
    for md_file in TIL_ROOT.rglob("*.md"):
        rel = md_file.relative_to(TIL_ROOT)
        parts = rel.parts
        if any(ex in parts for ex in EXCLUDE_DIRS):
            continue
        if md_file.name in EXCLUDE_FILES:
            continue
        til_files.append(str(rel))
    return sorted(til_files)


def load_state():
    if REVIEW_STATE_FILE.exists():
        with open(REVIEW_STATE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_state(state):
    with open(REVIEW_STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2, ensure_ascii=False)


def next_review_date(review_count, from_date):
    """간격 반복에 따른 다음 복습 날짜 계산"""
    idx = min(review_count, len(INTERVALS) - 1)
    d = datetime.strptime(from_date, "%Y-%m-%d")
    return (d + timedelta(days=INTERVALS[idx])).strftime("%Y-%m-%d")


def extract_title(filepath):
    full = TIL_ROOT / filepath
    try:
        with open(full, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("# "):
                    return line.strip()[2:].strip()
    except Exception:
        pass
    return Path(filepath).stem


def extract_review_questions(filepath):
    """복습 질문 또는 요약 섹션 추출"""
    full = TIL_ROOT / filepath
    try:
        with open(full, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception:
        return ""

    # 복습 질문 섹션 우선
    m = re.search(r"###\s*복습\s*질문\s*\n(.*?)(?=\n##|\Z)", content, re.DOTALL)
    if m and m.group(1).strip():
        return m.group(1).strip()[:2000]

    # 요약 섹션
    m = re.search(r"###\s*요약\s*\n(.*?)(?=\n##|\Z)", content, re.DOTALL)
    if m and m.group(1).strip():
        return m.group(1).strip()[:2000]

    return ""


def get_category(filepath):
    parts = Path(filepath).parts
    return parts[0] if len(parts) > 1 else "기타"


def notion_request(token, method, path, body=None):
    """Notion REST API 호출"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Notion-Version": "2022-06-28",
        "Content-Type": "application/json",
    }
    url = f"https://api.notion.com/v1/{path}"
    if method == "POST":
        r = httpx.post(url, headers=headers, json=body, timeout=30)
    elif method == "PATCH":
        r = httpx.patch(url, headers=headers, json=body, timeout=30)
    else:
        r = httpx.get(url, headers=headers, timeout=30)
    r.raise_for_status()
    return r.json()


def parse_inline(text):
    """마크다운 인라인 서식 → Notion rich_text 배열"""
    segments = []
    pattern = re.compile(r"(\*\*(.+?)\*\*|`(.+?)`)")
    last = 0
    for m in pattern.finditer(text):
        if m.start() > last:
            segments.append({"text": {"content": text[last:m.start()]}})
        if m.group(2):
            segments.append({"text": {"content": m.group(2)}, "annotations": {"bold": True}})
        elif m.group(3):
            segments.append({"text": {"content": m.group(3)}, "annotations": {"code": True}})
        last = m.end()
    if last < len(text):
        segments.append({"text": {"content": text[last:]}})
    return segments or [{"text": {"content": text}}]


def rich(text):
    """텍스트 → Notion rich_text (2000자 제한 처리)"""
    text = text[:2000]
    return parse_inline(text)


def markdown_to_blocks(content):
    """마크다운 본문 → Notion 블록 리스트"""
    blocks = []
    lines = content.split("\n")
    i = 0

    while i < len(lines):
        line = lines[i]

        # 코드 블록
        if line.startswith("```"):
            lang = line[3:].strip() or "plain text"
            notion_langs = {
                "kotlin", "java", "python", "sql", "javascript", "typescript",
                "bash", "shell", "json", "yaml", "xml", "go", "rust", "c",
                "c++", "html", "css", "dockerfile", "graphql", "markdown",
            }
            if lang not in notion_langs:
                lang = "plain text"
            code_lines = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                code_lines.append(lines[i])
                i += 1
            blocks.append({
                "type": "code",
                "code": {
                    "rich_text": [{"text": {"content": "\n".join(code_lines)[:2000]}}],
                    "language": lang,
                },
            })
            i += 1
            continue

        # 테이블
        if line.startswith("|"):
            table_lines = []
            while i < len(lines) and lines[i].startswith("|"):
                table_lines.append(lines[i])
                i += 1
            rows = []
            for tl in table_lines:
                if re.match(r"^\|[\s\-:]+\|$", tl.strip()):
                    continue
                cells = [c.strip() for c in tl.strip().strip("|").split("|")]
                rows.append(cells)
            if rows:
                width = max(len(r) for r in rows)
                children = []
                for row in rows:
                    while len(row) < width:
                        row.append("")
                    children.append({
                        "type": "table_row",
                        "table_row": {
                            "cells": [rich(cell) for cell in row],
                        },
                    })
                blocks.append({
                    "type": "table",
                    "table": {
                        "table_width": width,
                        "has_column_header": True,
                        "has_row_header": False,
                        "children": children,
                    },
                })
            continue

        # 헤딩
        if line.startswith("### "):
            blocks.append({"type": "heading_3", "heading_3": {"rich_text": rich(line[4:])}})
        elif line.startswith("## "):
            blocks.append({"type": "heading_2", "heading_2": {"rich_text": rich(line[3:])}})
        elif line.startswith("# "):
            blocks.append({"type": "heading_1", "heading_1": {"rich_text": rich(line[2:])}})
        # 구분선
        elif line.strip() == "---":
            blocks.append({"type": "divider", "divider": {}})
        # 인용
        elif line.startswith("> "):
            blocks.append({"type": "quote", "quote": {"rich_text": rich(line[2:])}})
        # 번호 목록
        elif re.match(r"^\d+\.\s", line):
            text = re.sub(r"^\d+\.\s", "", line)
            blocks.append({"type": "numbered_list_item", "numbered_list_item": {"rich_text": rich(text)}})
        # 불릿 목록 (서브불릿 포함)
        elif re.match(r"^(\s*)[*\-]\s", line):
            indent = len(re.match(r"^(\s*)", line).group(1))
            text = re.sub(r"^\s*[*\-]\s", "", line)
            prefix = "  " * (indent // 2) if indent >= 2 else ""
            blocks.append({"type": "bulleted_list_item", "bulleted_list_item": {"rich_text": rich(prefix + text)}})
        # 빈 줄
        elif not line.strip():
            pass
        # 일반 텍스트
        else:
            blocks.append({"type": "paragraph", "paragraph": {"rich_text": rich(line)}})

        i += 1

    return blocks


def read_file_content(filepath):
    """TIL 파일 전체 내용 읽기"""
    full = TIL_ROOT / filepath
    try:
        with open(full, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


def create_notion_page(token, db_id, info):
    """Notion 데이터베이스에 복습 항목 추가 (본문 포함)"""
    # 본문을 Notion 블록으로 변환
    blocks = markdown_to_blocks(info.get("content", ""))

    # Notion API는 한 번에 최대 100 블록 → 초과 시 append로 추가
    first_batch = blocks[:100]
    remaining = blocks[100:]

    body = {
        "parent": {"database_id": db_id},
        "properties": {
            "제목": {"title": [{"text": {"content": info["title"]}}]},
            "카테고리": {"select": {"name": info["category"]}},
            "복습 날짜": {"date": {"start": info["date"]}},
            "복습 횟수": {"number": info["review_count"]},
            "상태": {"checkbox": False},
        },
        "children": first_batch,
    }

    result = notion_request(token, "POST", "pages", body)

    # 100블록 초과분 추가
    page_id = result["id"]
    while remaining:
        batch = remaining[:100]
        remaining = remaining[100:]
        notion_request(token, "PATCH", f"blocks/{page_id}/children", {"children": batch})


def init_new_files(state, til_files, today_str):
    """새 TIL 파일을 단계적으로 복습 일정에 배치 (하루 MAX_PER_DAY개씩)"""
    new_files = [f for f in til_files if f not in state]
    if not new_files:
        return

    random.shuffle(new_files)
    today = datetime.strptime(today_str, "%Y-%m-%d")

    for i, filepath in enumerate(new_files):
        offset_days = i // MAX_PER_DAY
        scheduled = (today + timedelta(days=offset_days)).strftime("%Y-%m-%d")
        state[filepath] = {
            "review_count": 0,
            "created": today_str,
            "last_reviewed": None,
            "next_review": scheduled,
        }

    print(f"  새 TIL {len(new_files)}개 등록 (향후 {len(new_files) // MAX_PER_DAY + 1}일에 걸쳐 분배)")


def main():
    dry_run = "--dry-run" in sys.argv
    today = datetime.now().strftime("%Y-%m-%d")

    state = load_state()
    til_files = set(get_til_files())

    # 새 파일 등록
    init_new_files(state, til_files, today)

    # 삭제된 파일 정리
    removed = [k for k in state if k not in til_files]
    for k in removed:
        del state[k]
    if removed:
        print(f"  삭제된 TIL {len(removed)}개 정리")

    # 오늘 복습 대상
    due = [f for f in til_files if state.get(f, {}).get("next_review", "9999") <= today]
    due.sort(key=lambda f: state[f]["review_count"])  # 적게 복습한 것 우선

    if not due:
        print(f"[{today}] 오늘 복습할 TIL이 없습니다.")
        save_state(state)
        return

    print(f"[{today}] 오늘 복습할 TIL: {len(due)}개")

    if dry_run:
        for filepath in due:
            info = state[filepath]
            print(f"  - [{get_category(filepath)}] {extract_title(filepath)} (복습 {info['review_count']}회)")
        save_state(state)
        return

    token = os.environ.get("NOTION_API_KEY")
    db_id = os.environ.get("NOTION_DATABASE_ID")
    repo_url = get_github_repo_url()

    if not token or not db_id:
        print("NOTION_API_KEY 또는 NOTION_DATABASE_ID 환경변수가 필요합니다.")
        print("복습 대상 목록:")
        for filepath in due:
            print(f"  - [{get_category(filepath)}] {extract_title(filepath)}")
        save_state(state)
        sys.exit(1)

    for filepath in due:
        info = state[filepath]
        title = extract_title(filepath)
        summary = extract_review_questions(filepath)
        category = get_category(filepath)
        encoded_path = quote(str(filepath))

        content = read_file_content(filepath)

        create_notion_page(token, db_id, {
            "title": f"[{category}] {title}",
            "category": category,
            "date": today,
            "review_count": info["review_count"] + 1,
            "content": content,
        })
        print(f"  -> [{category}] {title}")

        state[filepath]["review_count"] += 1
        state[filepath]["last_reviewed"] = today
        state[filepath]["next_review"] = next_review_date(
            state[filepath]["review_count"], today
        )

    save_state(state)
    print("완료!")


if __name__ == "__main__":
    main()
