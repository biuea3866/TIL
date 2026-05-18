# 클로드 코드 권한 운영

## 권한 시스템이란
---
클로드 코드의 모든 도구 호출은 권한 시스템을 거친다. 패턴 기반 규칙으로 자동 허용·자동 거부·사용자 확인을 결정하는 게이트다.

이 문서는 **운영 관점**에서 권한을 어떻게 설계할지 다룬다. 평가 순서·`deny > allow > ask` 같은 동작 원리는 [claude_execution.md](claude_execution.md#권한-시스템)에서 다룬다.

## 왜 deny가 이기나
---
같은 명령이 deny와 allow에 모두 있으면 deny가 이긴다. 이건 "fail-safe" 원칙이다. AWS IAM, Linux capabilities, 방화벽 규칙 등 거의 모든 권한 시스템이 같은 정책을 따른다.

### 세 가지 이유

| 이유 | 설명 |
|---|---|
| **실수 비용의 비대칭성** | deny를 빠뜨려서 위험 명령이 실행되는 피해가, allow를 빠뜨려 사용자에게 한 번 더 묻는 불편보다 훨씬 크다 |
| **명시적 거부의 의미 보존** | "이건 절대 안 됨"이라고 적어둔 의도가 다른 곳의 broad allow에 의해 silently 덮이지 않아야 한다 |
| **broad allow + narrow deny 패턴 활성화** | "git 관련은 다 허용하되 force push만 막기" 같은 정책을 깔끔하게 표현할 수 있다 |

> 권한 정책 설계 시 "의심스러우면 deny" 원칙이 항상 옳다. 잘못 차단된 명령은 사용자가 한 번 더 풀어주면 되지만, 잘못 실행된 명령은 되돌릴 수 없는 경우가 많다.

## 핵심 운영 패턴: broad allow + narrow deny
---
가장 자주 쓰는 패턴이다. **광범위하게 허용하고, 위험한 것만 콕 집어 막는다.**

```json
{
  "permissions": {
    "allow": [
      "Bash(git:*)",          // git 전체 허용
      "Bash(npm:*)",          // npm 전체 허용
      "Read(**)"              // 모든 파일 읽기 허용
    ],
    "deny": [
      "Bash(git push --force:*)",   // force push만 콕 집어 차단
      "Bash(git push -f:*)",
      "Bash(rm -rf:*)",
      "Read(**/.env*)"              // .env 파일만 읽기 금지
    ]
  }
}
```

평소엔 묻지 않고 동작하지만, 정말 위험한 동작만 골라서 차단된다.

| 명령 | 평가 | 결과 |
|---|---|---|
| `git status` | deny 매칭 X → allow 매칭 O | 실행 |
| `git diff` | deny 매칭 X → allow 매칭 O | 실행 |
| `git push origin main` | deny 매칭 X (force가 아님) → allow 매칭 O | 실행 |
| `git push --force origin main` | **deny 매칭 O** | 거부 |
| `git pull` | deny 매칭 X → allow 매칭 O | 실행 |
| `rm -rf node_modules` | **deny 매칭 O** | 거부 |
| `Read(.env.local)` | **deny 매칭 O** | 거부 |
| `Read(src/main.kt)` | deny 매칭 X → allow 매칭 O | 실행 |

### 패턴 우선순위

```
1. deny 패턴 평가 (구체적인 차단)
   ↓ 매칭 안 됨
2. allow 패턴 평가 (광범위한 허용)
   ↓ 매칭 안 됨
3. ask (사용자에게 확인)
```

deny는 "예외 규칙"으로 동작하고, allow는 "기본 허용 범위"를 결정한다.

## 스코프 우선순위
---
권한 설정은 여러 위치에 동시 존재할 수 있다. 같은 명령이 다른 설정 파일에 있을 때도 deny가 이기고, 추가로 스코프 우선순위도 함께 작동한다.

```
관리되는 설정(managed)   ← 조직 관리자가 배포, 최고 우선순위
  └─ user 설정 (~/.claude/settings.json)
      └─ project 설정 (.claude/settings.json)
          └─ local 설정 (.claude/settings.local.json)
```

| 위치 | 범위 | 누가 관리 | git 트래킹 |
|---|---|---|---|
| **managed** | 조직 전체 | 관리자가 배포 | 별도 배포 채널 |
| **user** | 사용자 개인 (모든 프로젝트) | 사용자 본인 | X |
| **project** | 현재 프로젝트 (팀 공유) | 팀 | ✅ |
| **local** | 현재 프로젝트 개인용 | 사용자 본인 | X (`.gitignore`) |

### 시나리오별 결과

| 시나리오 | 결과 |
|---|---|
| managed에 deny, project에 allow | **deny** (deny 우선 + managed 우선) |
| user에 allow, project에 deny | **deny** (deny 우선) |
| project에 deny, local에 allow | **deny** (deny 우선) |
| managed에 allow, user에 deny | **deny** (deny 우선) |
| project에 allow, local에 deny | **deny** (deny 우선) |

핵심: **deny는 어디서 선언되든 이긴다.** 스코프 우선순위는 동일한 종류의 규칙들끼리 충돌할 때만 의미가 있다.

### 운영 예시

```
[관리자가 사내 정책으로 배포 (managed)]
{
  "deny": ["Bash(git push --force:*)", "Bash(curl:*)"]
}

[사용자가 자기 환경 편의 (user)]
{
  "allow": ["Bash(git:*)", "Bash(npm:*)", "Read(**)"]
}

[프로젝트 팀이 공통 규약 (project, .claude/settings.json)]
{
  "allow": ["Bash(./gradlew:*)", "Bash(docker:*)"],
  "deny": ["Read(**/.env*)"]
}

[개인적인 편의 (local, .claude/settings.local.json)]
{
  "allow": ["Bash(my-custom-script:*)"]
}
```

→ 사용자가 `git push --force` 시도: **managed의 deny로 차단**. 사용자가 local에서 allow를 추가해도 풀 수 없다.

## Permission Mode 운영
---
권한 규칙 위에 한 층 더 올라가는 **전역 정책**이다. 환경(개발/자동화/실험)에 따라 모드를 바꿔 운영한다.

| 모드 | 동작 | 권장 환경 |
|---|---|---|
| `default` | 규칙 기반 평가, 매칭 실패 시 사용자에게 물음 | 일반 대화형 개발 |
| `acceptEdits` | 파일 수정류는 자동 허용, 위험 명령은 여전히 확인 | 신뢰하는 작업에서 마찰 줄이기 |
| `bypassPermissions` | 모든 도구 자동 허용 | CI/자동화 (반드시 격리 환경에서) |
| `plan` | 변경 도구 전면 차단, 읽기·계획만 수행 | 코드 변경 전 검토 |

### 운영 시 주의사항

```
default              → 안전, 마찰 있음        → 처음 사용·신뢰 구축 단계
acceptEdits          → 마찰 적음, 파일 안전   → 익숙해진 일상 개발
bypassPermissions    → 무방비                 → CI 또는 격리된 worktree
plan                 → 변경 없음, 검토만      → 위험한 작업 시작 전
```

`bypassPermissions`는 가장 위험하다. 사용 시 다음 중 하나의 격리 장치가 필수다.

- 격리된 git worktree
- Docker 컨테이너
- 일회용 VM
- 명확히 정의된 deny 패턴 + 훅 가드

## 패턴 문법
---
권한 규칙은 `<도구>(<인자 패턴>)` 형식이다.

### 도구별 패턴 예시

```json
{
  "allow": [
    "Bash(git:*)",                    // git으로 시작하는 모든 명령
    "Bash(git status:*)",             // git status 명령만 (인자 자유)
    "Bash(npm run test:*)",           // npm run test 명령
    "Read(**)",                       // 모든 파일 읽기
    "Read(src/**)",                   // src 하위 파일만 읽기
    "Edit(src/**/*.kt)",              // src 하위 .kt 파일만 수정
    "WebFetch(https://docs.anthropic.com/**)",  // 특정 도메인만 허용
    "mcp__github__*",                 // GitHub MCP 서버의 모든 도구
    "Agent(Explore)",                 // Explore 서브에이전트만 호출 허용
    "Agent(*)"                        // 모든 서브에이전트 허용
  ]
}
```

| 와일드카드 | 의미 |
|---|---|
| `*` | 임의 문자열 (단일 세그먼트) |
| `**` | 임의 문자열 (다중 세그먼트, 디렉토리 횡단) |
| `:*` | Bash 명령에서 "이후 인자 자유" |

### 좋은 패턴 vs 나쁜 패턴

```json
// ❌ 너무 광범위
{ "allow": ["Bash(*)"] }   // 모든 셸 명령 허용 — 사실상 무방비

// ❌ 너무 좁음
{ "allow": ["Bash(git status)"] }   // git status에 인자 못 붙임

// ✅ 적절
{ "allow": ["Bash(git status:*)", "Bash(git diff:*)"] }

// ❌ deny에 광범위 패턴
{ "deny": ["Bash(*)"] }   // 모든 명령 차단 — 사용 불가

// ✅ deny는 구체적으로
{ "deny": ["Bash(rm -rf:*)", "Bash(git push --force:*)"] }
```

## 권장 정책 템플릿
---
시작점으로 쓸 만한 기본 정책이다. 프로젝트 성격에 따라 조정한다.

### 일반 개발 프로젝트

```json
{
  "permissions": {
    "allow": [
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)",
      "Bash(git pull:*)",
      "Bash(git checkout:*)",
      "Bash(git branch:*)",
      "Bash(./gradlew:*)",
      "Bash(npm run:*)",
      "Bash(yarn:*)",
      "Read(**)",
      "Edit(src/**)",
      "Edit(test/**)",
      "Glob(**)",
      "Grep(**)"
    ],
    "deny": [
      "Bash(git push --force:*)",
      "Bash(git push -f:*)",
      "Bash(git reset --hard:*)",
      "Bash(rm -rf:*)",
      "Bash(sudo:*)",
      "Read(**/.env*)",
      "Read(**/secrets/**)",
      "Read(**/credentials*)",
      "Edit(.github/**)",
      "Edit(infra/**)"
    ]
  }
}
```

### 자동화/CI 환경

```json
{
  "permissionMode": "bypassPermissions",
  "permissions": {
    "deny": [
      "Bash(git push --force:*)",
      "Bash(rm -rf /:*)",
      "Bash(sudo:*)",
      "WebFetch(http://*)"
    ]
  }
}
```

`bypassPermissions` 모드에서도 deny는 여전히 작동한다. 자동화에서는 "절대 안 되는 것"만 명시한다.

### 코드 검토 전용

```json
{
  "permissionMode": "plan",
  "permissions": {
    "allow": [
      "Read(**)",
      "Grep(**)",
      "Glob(**)",
      "Bash(git log:*)",
      "Bash(git diff:*)"
    ]
  }
}
```

`plan` 모드는 모든 변경 도구를 차단한다. 위험한 작업 시작 전 분석·계획 단계에 적합하다.

## 훅과의 조합
---
권한 시스템(정적 규칙)으로 막을 수 없는 동적 조건은 `PreToolUse` 훅으로 보완한다.

```json
{
  "permissions": {
    "allow": ["Bash(git push:*)"]
  },
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "/path/to/check-branch-protected.sh"
          }
        ]
      }
    ]
  }
}
```

`check-branch-protected.sh`에서 "현재 브랜치가 main이면 차단" 같은 동적 검증을 한다. 자세한 훅 사용은 [claude_hook.md](claude_hook.md)에서 다룬다.

| 검증 종류 | 도구 |
|---|---|
| 정적 패턴 매칭 (`git push --force`) | **권한 시스템** |
| 동적 조건 (현재 브랜치, 시간대, 변경 파일 수) | **PreToolUse 훅** |
| 결과 후처리 (린트, 포맷) | **PostToolUse 훅** |
| 전역 정책 전환 | **Permission Mode** |

## 흔히 하는 실수
---

| 실수 | 결과 | 회피법 |
|---|---|---|
| `Bash(*)` allow | 사실상 무방비 | 도구별·명령별로 좁히기 |
| deny 없이 allow만 정의 | 명시적 안전망 없음 | 위험 패턴은 deny에 항상 포함 |
| `.env` 보호 누락 | 시크릿 노출 위험 | `deny: Read(**/.env*)` 필수 |
| `bypassPermissions` + 격리 없음 | 시스템 사고 위험 | worktree·컨테이너에서만 사용 |
| project allow를 user에서 풀려 함 | 작동 안 함 (deny 우선) | 정말 필요하면 project에서 풀기 |
| 패턴 와일드카드 오용 | 의도와 다른 매칭 | `*` vs `**` 차이 숙지 |
| 훅으로 검증해야 할 동적 조건을 패턴으로 시도 | 표현 한계 | 동적 조건은 PreToolUse 훅으로 |

## 한 줄 요약
---
> **broad allow + narrow deny로 시작하고, deny는 어디서든 이기며, 스코프는 managed > user > project > local 순으로 우선한다. 정적 패턴은 권한 규칙으로, 동적 조건은 PreToolUse 훅으로 보완한다.**
