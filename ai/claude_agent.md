# 클로드 코드 에이전트
## 클로드 코드 에이전트란
---
에이전트는 페르소나와 기능을 정의하고, 재사용 가능한 구성이다. 모델, 시스템 프롬프트, 도구, MCP 서버 등을 정의하고, 에이전트가 이를 사용하도록 한다.

특정 유형의 작업을 처리하는 특화된 AI 어시스턴트 단위이며, 에이전트는 자신의 컨텍스트 윈도우에서 작업을 수행하고 요약만 호출자에게 반환한다.

핵심 동작 원리는 다음과 같다.

```
1. Gather Context  (상황 파악, 파일/코드 탐색)
2. Take Action     (도구 호출 - Edit, Bash 등)
3. Verify Work     (결과 확인, 테스트)
4. Repeat          (목표 달성까지 반복)
```

## 구조
---
### 기본 구조
에이전트는 YAML frontmatter를 가진 Markdown 파일로 정의된다. Frontmatter가 메타데이터·구성을 담고, 본문이 시스템 프롬프트가 된다.

```markdown
---
name: code-reviewer
description: 코드 품질과 보안을 검토하는 전문가. 코드 작성·수정 직후 즉시 사용
model: sonnet
tools: Read, Grep, Glob, Bash
mcpServers:
  - github
skills:
  - api-conventions
---

당신은 시니어 코드 리뷰어입니다.
git diff로 변경 사항을 확인한 뒤, 품질·보안·성능 관점에서 피드백을 제공합니다.
```

저장 위치에 따라 범위가 달라지며, 같은 이름이 겹치면 상위 우선순위가 이긴다.

|위치|범위|우선순위|
|-|-|-|
|관리되는 설정|조직 전체|1 (최고)|
|`--agents` CLI 플래그|현재 세션|2|
|`.claude/agents/`|현재 프로젝트|3|
|`~/.claude/agents/`|모든 프로젝트|4|
|플러그인 `agents/`|플러그인 활성 위치|5 (최저)|

### 1. name
에이전트의 고유 식별자다. 소문자와 하이픈만 사용한다. `@`-mention(`@code-reviewer (agent)`), CLI 플래그(`claude --agent code-reviewer`), `Agent` 도구 호출 시 이 이름이 사용된다.

```yaml
---
name: code-reviewer
---
```

### 2. model
에이전트가 사용할 모델을 지정한다.

* 모델 별칭: `sonnet`, `opus`, `haiku`
* 전체 ID: `claude-opus-4-7`, `claude-sonnet-4-6` 등
* `inherit`: 메인 대화와 동일한 모델 사용 (기본값)

```yaml
---
name: code-reviewer
model: sonnet
---
```

가벼운 검색·요약은 `haiku`, 복잡한 추론은 `opus`로 사용하여 효율적인 비용 사용이 가능하다.

### 3. system
시스템 프롬프트는 Frontmatter 아래 본문(body) 에 작성한다. 에이전트가 어떻게 동작해야 하는지를 정의한다.

```markdown
---
name: debugger
description: 에러·테스트 실패·예기치 못한 동작 디버깅 전문가
---

당신은 근본 원인 분석에 특화된 디버거입니다.

호출 시:
1. 에러 메시지와 스택 트레이스 수집
2. 재현 단계 식별
3. 실패 지점 격리
4. 최소한의 수정 구현
5. 솔루션 검증

각 이슈마다 다음을 제공합니다:
- 근본 원인 설명
- 진단을 뒷받침하는 증거
- 구체적인 코드 수정
- 테스트 접근법
- 재발 방지 권고
```

> 에이전트는 이 시스템 프롬프트만 받으며, 메인 에이전트의 전체 시스템 프롬프트나 대화 기록은 상속하지 않는다. 작업에 필요한 파일 경로·에러 메시지·결정 사항은 호출 프롬프트에 직접 포함해야 한다.

### 4. tools
에이전트가 사용할 수 있는 도구를 제한한다.

```yaml
# 허용 목록: Read, Grep, Glob, Bash만 사용 가능
tools: Read, Grep, Glob, Bash
```

```yaml
# 거부 목록: 부모의 모든 도구 상속하되 Write/Edit 제외
disallowedTools: Write, Edit
```

* `tools` 생략 시 부모의 모든 도구 상속
* 둘 다 설정 시 `disallowedTools`가 먼저 적용된 뒤 `tools`가 남은 풀에서 해결
* 양쪽에 모두 들어 있는 도구는 제거됨

| 용도 | 도구 조합 |
|-|-|
| 읽기 전용 분석 | `Read`, `Grep`, `Glob` |
| 테스트 실행 | `Bash`, `Read`, `Grep` |
| 코드 수정 | `Read`, `Edit`, `Write`, `Grep`, `Glob` |
| 전체 액세스 | (생략하여 부모 도구 상속) |

### 5. mcp_servers
이 에이전트에서만 사용할 MCP 서버를 지정한다. 메인 대화에서 분리하여 도구 설명이 메인 컨텍스트를 소비하지 않도록 한다.

MCP 서버를 지정하는 방식은 두 가지가 있다.

#### 방식 1 - 인라인 정의
에이전트 파일 안에서 MCP 서버를 처음부터 직접 정의한다. 에이전트 시작 시 연결되고 종료 시 끊긴다.

```yaml
---
name: browser-tester
description: Playwright로 실제 브라우저에서 기능 테스트
mcpServers:
  - playwright:
      type: stdio
      command: npx
      args: ["-y", "@playwright/mcp@latest"]
---
```

#### 방식 2 - 이름 참조
이미 다른 곳(`.mcp.json`, settings의 `mcpServers`, 플러그인 등)에 정의된 MCP 서버를 이름만 적어 가져다 쓴다. 부모 세션의 연결을 공유한다.

먼저 프로젝트 루트의 `.mcp.json`에 서버가 등록되어 있다고 가정한다.

```json
// .mcp.json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"]
    }
  }
}
```

그러면 에이전트 frontmatter에는 이름만 적으면 된다.

```yaml
---
name: pr-reviewer
description: GitHub PR 리뷰 에이전트
mcpServers:
  - github   # ← .mcp.json에 정의된 "github" 서버를 그대로 재사용
---
```

#### 인라인 vs 이름 참조

|구분|인라인 정의|이름 참조|
|-|-|-|
|형태|`- name: { type: stdio, ... }` (객체)|`- name` (문자열)|
|정의 위치|에이전트 파일 안|`.mcp.json`, settings 등 외부|
|연결|에이전트 시작 시 새로 연결|부모 세션의 연결 공유|
|메인 대화에서 사용|불가 (이 에이전트 전용)|가능 (이미 등록된 서버)|

#### 함께 사용도 가능
한 에이전트에서 두 방식을 섞을 수 있다.

```yaml
---
name: browser-tester
mcpServers:
  # 인라인 정의: 이 에이전트에만 적용
  - playwright:
      type: stdio
      command: npx
      args: ["-y", "@playwright/mcp@latest"]
  # 이름 참조: 이미 구성된 서버 재사용
  - github
---
```

### 6. skills
시작 시 에이전트의 컨텍스트에 미리 로드할 [Skills](claude_skill.md) 목록이다. 실행 중 검색·로드 없이 도메인 지식을 즉시 제공할 수 있다.

```yaml
---
name: api-developer
description: 팀 컨벤션에 따라 API 엔드포인트 구현
skills:
  - api-conventions
  - error-handling-patterns
---

API 엔드포인트를 구현합니다. 미리 로드된 skills의 컨벤션과 패턴을 따릅니다.
```

* 나열된 skill의 전체 콘텐츠가 시스템 프롬프트에 주입됨
* 나열하지 않은 skill도 `Skill` 도구를 통해 호출 가능
* skill 호출을 완전히 막으려면 `tools`에서 `Skill`을 제거하거나 `disallowedTools`에 추가

### 7. callable_agents
이 에이전트가 호출할 수 있는 다른 에이전트의 종류를 제한한다. `tools` 필드의 `Agent(...)` 구문으로 지정하며, `claude --agent`로 메인 스레드 실행 시에만 의미가 있다 (서브 에이전트는 다른 서브 에이전트를 생성할 수 없다).

```yaml
---
name: coordinator
description: 전문 에이전트 간 작업 조율
tools: Agent(worker, researcher), Read, Bash
---
```

* `Agent(worker, researcher)` → worker와 researcher 서브 에이전트만 생성 가능
* `Agent` (괄호 없음) → 모든 서브 에이전트 생성 허용
* `Agent` 생략 → 어떤 서브 에이전트도 생성 불가

특정 에이전트만 차단하려면 settings.json에 `deny`를 추가한다.

```json
{
  "permissions": {
    "deny": ["Agent(Explore)", "Agent(my-custom-agent)"]
  }
}
```

### 8. description
클로드가 이 에이전트에 자동 위임할지 결정하는 기준이 되는 필드다. 무엇을 하는 에이전트인지 + 언제 호출해야 하는지를 명확히 적는다.

```yaml
---
name: code-reviewer
description: Expert code review specialist. Proactively reviews code for quality, security, and maintainability. Use immediately after writing or modifying code.
---
```

작성 팁:
* "use proactively" 또는 "use immediately after ..." 같은 문구를 넣으면 자동 위임이 적극적으로 일어남
* 무엇을 하는지(role) + 언제 호출할지(trigger)를 한 문장에 압축
* 모든 에이전트의 `description`은 시스템 프롬프트에 미리 로드되므로, 너무 길게 쓰면 컨텍스트를 소비

### 9. metadata
에이전트의 동작·표시·라이프사이클을 제어하는 부가 메타데이터 필드들이다.

|필드|설명|
|-|-|
|`permissionMode`|`default`/`acceptEdits`/`auto`/`dontAsk`/`bypassPermissions`/`plan`. 권한 프롬프트 처리 방식|
|`maxTurns`|에이전트가 중지되기 전 최대 턴 수|
|`memory`|`user`/`project`/`local`. 지속적 메모리 범위|
|`background`|`true` 설정 시 항상 백그라운드로 실행|
|`effort`|`low`/`medium`/`high`/`xhigh`/`max`. 노력(추론) 수준|
|`isolation`|`worktree` 설정 시 임시 git worktree에서 실행|
|`color`|`red`/`blue`/`green`/`yellow`/`purple`/`orange`/`pink`/`cyan`. UI 표시 색상|
|`initialPrompt`|`--agent`로 메인 세션 실행 시 자동 제출되는 첫 사용자 턴|
|`hooks`|에이전트 라이프사이클에 묶인 hooks 정의|

지속적 메모리(`memory`) 범위:

|값|위치|
|-|-|
|`user`|`~/.claude/agent-memory/<name>/` - 모든 프로젝트 공통|
|`project`|`.claude/agent-memory/<name>/` - 프로젝트별, 버전 관리 공유 가능 (권장)|
|`local`|`.claude/agent-memory-local/<name>/` - 프로젝트별, 버전 관리 제외|

## 에이전트를 어떻게 잘 정의할 것인가
---
에이전트의 frontmatter는 **사용자가 직접 작성**한다. 다만 빈 파일로 시작하는 것보다 `/agents` 명령으로 인터랙티브하게 만드는 것이 가장 정확하다.

### 정의 방법
|방법|추천도|설명|
|-|-|-|
|`/agents` 명령|⭐⭐⭐|Claude Code에서 대화형 인터페이스로 생성. Claude가 frontmatter를 대신 작성|
|Markdown 파일 직접 작성|⭐⭐|`.claude/agents/<name>.md`에 YAML + 본문을 직접 작성|
|`--agents` CLI 플래그|⭐|JSON으로 세션 한정 정의. 자동화·테스트용|

가장 빠른 경로는 `/agents` → Library 탭 → Create new agent → "Generate with Claude" → 에이전트가 할 일을 자연어로 설명하면 Claude가 frontmatter·시스템 프롬프트를 모두 생성한다.

### 1. 한 가지 일만 하게 만든다
"코드 리뷰 + 테스트 실행 + 배포" 같은 만능 에이전트는 description 매칭이 흐려져 자동 위임이 안 일어난다. 한 작업당 한 에이전트로 쪼갠다.

### 2. description이 가장 중요하다
Claude는 description만 보고 위임 여부를 결정한다. `{무엇을} + {언제} + use proactively` 공식이 가장 잘 동작한다.

```yaml
# ❌ 나쁨 - 무엇을 하는 에이전트인지 불분명
---
name: code-reviewer
description: 코드 리뷰어
---

# ✅ 좋음 - 역할 + 트리거 + 적극성
---
name: code-reviewer
description: 코드 품질·보안·유지보수성을 검토하는 전문 코드 리뷰어. 코드 작성·수정 직후 즉시 사용 (use proactively).
---
```

### 3. tools로 권한을 최소화한다
필요한 도구만 명시적으로 허용한다. "혹시 모르니 모든 권한을 줘"는 사고의 원인이다.

|역할|권장 도구|
|-|-|
|리뷰어·분석가|`Read`, `Grep`, `Glob`|
|테스트 실행자|`Bash`, `Read`, `Grep`|
|구현자|`Read`, `Edit`, `Write`, `Grep`, `Glob`, `Bash`|

### 4. model은 작업 무게에 맞춘다
모든 에이전트를 Opus로 돌리면 비용이 폭증한다. 가벼운 검색·요약은 Haiku로 충분하다.

|작업|모델|
|-|-|
|파일 탐색, 키워드 검색|`haiku`|
|코드 리뷰, 일반 구현|`sonnet`|
|복잡한 추론, 아키텍처 결정|`opus`|

### 5. 본문은 "When invoked / 절차 / 출력 형식"으로 구성한다
공식 예시들이 공유하는 일관된 패턴이다.

```markdown
---
name: code-reviewer
description: 전문 코드 리뷰어. 코드 작성·수정 직후 즉시 사용 (use proactively).
model: sonnet
tools: Read, Grep, Glob, Bash
---

당신은 코드 품질과 보안을 보장하는 시니어 코드 리뷰어입니다.

호출 시:
1. git diff로 최근 변경 사항 확인
2. 수정된 파일에 집중
3. 즉시 리뷰 시작

체크리스트:
- 코드가 명확하고 가독성이 좋은가
- 노출된 시크릿이나 API 키가 없는가
- 적절한 에러 핸들링이 있는가
- 충분한 테스트 커버리지가 있는가

다음 형식으로 피드백을 제공합니다:
- Critical: ...
- Warning: ...
- Suggestion: ...
```

이 구조는 에이전트가 매번 같은 품질·같은 형식으로 결과를 내게 한다.

### 6. 출력 형식을 본문에 명시한다
서브 에이전트는 결과 요약만 메인에 반환하므로, 본문에 "Report only X", "In the format Y" 같은 제약을 명시한다. 그렇지 않으면 장황한 출력이 메인 컨텍스트를 오염시킨다.

```markdown
---
name: test-runner
description: 테스트 스위트를 실행하고 실패를 보고하는 에이전트. 코드 변경 후 즉시 사용 (use proactively).
tools: Bash, Read, Grep
---

당신은 테스트를 실행하고 실패한 것만 보고하는 에이전트입니다.

실패한 테스트의 파일 경로와 에러 메시지만 보고합니다.
통과한 테스트나 전체 스택 트레이스는 포함하지 않습니다.
```

### 7. 범위(저장 위치)를 의도적으로 선택한다
|어디에 저장|언제|
|-|-|
|`.claude/agents/`|이 프로젝트 전용. 팀과 공유 (버전 관리 체크인)|
|`~/.claude/agents/`|내 모든 프로젝트에서 쓸 개인용|
|플러그인|여러 사람·팀에 배포|

### 표준 템플릿
복사해서 채우기 좋은 표준 형태다.

```markdown
---
name: <소문자-하이픈-이름>
description: <역할 한 문장>. <트리거 조건>에서 즉시 사용 (use proactively).
model: sonnet
tools: Read, Grep, Glob
---

당신은 <전문 영역>에 특화된 <역할>입니다.

호출 시:
1. <첫 단계>
2. <둘째 단계>
3. <셋째 단계>

체크리스트:
- <확인할 항목 1>
- <확인할 항목 2>

다음 형식으로 결과를 보고합니다:
- <출력 형식 1>
- <출력 형식 2>

<강조점>에 집중하세요. <금지 사항>은 하지 마세요.
```

### 흔히 하는 실수

|실수|결과|
|-|-|
|description이 짧고 모호함|자동 위임 안 일어남|
|모든 도구 허용|사고 위험, 토큰 낭비|
|본문에 절차 없이 "잘 해줘"|매번 다른 품질의 결과|
|출력 형식 미지정|메인 컨텍스트 오염|
|Opus 남발|비용 폭증|
|YAML 들여쓰기 오류|파싱 실패로 로드 안 됨|
|같은 이름을 여러 범위에 정의|우선순위 충돌, 의도와 다른 에이전트 실행|

### 검증·디버깅 흐름
1. `claude agents` 또는 `/agents` → 에이전트가 목록에 나타나는지 확인
2. `@<agent-name>`으로 명시 호출 → 의도대로 동작하는지 검증
3. 자동 위임이 안 되면 → description에 "use proactively" + 트리거 키워드 보강
4. 결과가 장황하면 → 본문에 출력 형식·길이 제한 추가
5. 토큰 비용이 크면 → model 다운그레이드 또는 tools 축소

### 한 줄 요약
> **`/agents`로 시작 → description은 "역할 + 트리거 + use proactively" → 도구·모델은 최소·적정으로 → 본문은 "When invoked → 절차 → 출력 형식" → @-mention으로 검증**

## 메인 에이전트와 서브 에이전트
---

### 메인 에이전트
사용자와 직접 대화하는 최상위 에이전트 인스턴스다.

* 사용자 입력을 받는 유일한 에이전트
* `CLAUDE.md`, `MEMORY.md` 등 프로젝트 컨텍스트 로드
* 모든 도구(Read, Write, Edit, Bash, Agent, Skill 등)에 접근 가능
* 작업을 직접 수행할지 서브 에이전트에 위임할지 결정
* 작업 결과를 최종적으로 사용자에게 보고

### 서브 에이전트
메인 에이전트가 특정 작업을 위임하기 위해 spawn하는 격리된 에이전트 인스턴스다.

* 별도의 컨텍스트 윈도우를 가짐 (메인 컨텍스트와 분리)
* 자기만의 시스템 프롬프트, 허용 도구, 모델 설정을 가짐
* 작업이 끝나면 결과 요약만 메인에 반환
* 중간 과정(수많은 파일 읽기 등)은 메인 컨텍스트에 노출되지 않음
* 다른 서브 에이전트를 생성할 수 없음 (중첩 위임 불가)

#### 호출 방법
1. 자동 위임 — Claude가 `description`과 작업을 매칭하여 자동 호출
2. 자연어 — "Use the test-runner subagent to fix failing tests"
3. @-mention — `@code-reviewer (agent)` (반드시 해당 서브 에이전트 실행 보장)
4. 세션 전체 — `claude --agent code-reviewer` (메인 스레드가 해당 에이전트가 됨)

#### 내장 서브 에이전트
|이름|모델|도구|용도|
|-|-|-|-|
|Explore|Haiku|읽기 전용|코드베이스 탐색, 빠른 검색|
|Plan|상속|읽기 전용|plan mode에서 계획 수립 전 조사|
|general-purpose|상속|모든 도구|탐색 + 수정이 모두 필요한 복합 작업|

#### 메인 vs 서브 비교
|구분|메인|서브|
|-|-|-|
|호출 주체|사용자|메인 에이전트|
|개수|1개|N개 (병렬 가능)|
|컨텍스트|사용자 대화 그대로|별도 (격리)|
|결과 처리|사용자에게 응답|메인에 요약 반환|
|종료 조건|세션 종료|작업 완료 후 자동 종료|
|중첩 생성|가능|불가|

## 에이전트 팀?
---

에이전트 팀은 함께 작동하는 여러 Claude Code 인스턴스를 조율하는 기능이다. 한 세션이 팀 리더가 되어 작업을 할당·조율하고, 팀원들은 독립적으로 작동하며 서로 직접 통신한다.

### 서브 에이전트 vs 에이전트 팀
| | 서브 에이전트 | 에이전트 팀 |
|-|-|-|
|컨텍스트|자체 윈도우, 호출자에게 결과 반환|자체 윈도우, 완전히 독립|
|통신|메인 에이전트에만 결과 보고|팀원끼리 서로 직접 메시지|
|조율|메인이 모든 작업 관리|공유 작업 목록을 통한 자체 조율|
|최적 용도|결과만 중요한 집중된 작업|논의·협업이 필요한 복잡한 작업|
|토큰 비용|낮음 (결과만 요약)|높음 (팀원 수만큼 인스턴스)|

### 구성 요소
|요소|역할|
|-|-|
|팀 리더|팀 생성·팀원 spawn·작업 조율을 담당하는 메인 세션|
|팀원|할당된 작업을 각자 수행하는 별도 Claude Code 인스턴스|
|작업 목록|팀원들이 요청·완료하는 공유 작업 항목 목록|
|메일박스|에이전트 간 통신을 위한 메시징 시스템|

### 언제 쓰는가
* 여러 팀원이 문제의 다른 측면을 동시에 조사하고 발견을 공유·도전해야 할 때
* 새 모듈·기능에서 팀원들이 각자 다른 부분을 소유하며 간섭하지 않을 때
* 경쟁하는 가설로 디버깅하여 더 빠르게 답에 수렴할 때
* 프론트엔드·백엔드·테스트에 걸친 변경을 각자 다른 팀원이 소유할 때

### 팀원과 서브 에이전트 정의 재사용
팀원을 생성할 때 기존 서브 에이전트 타입을 참조할 수 있어, 동일 역할을 위임 작업과 팀 작업 모두에 재사용할 수 있다.


## 언제 무엇을 사용해야하는가
---

### 의사결정 흐름
```
질문이 단순 / 기존 컨텍스트 안에서 답이 나오는가?
  └─ Yes → 메인 에이전트
  └─ No  → 출력이 많거나 격리가 필요한가?
            └─ Yes → 서브 에이전트
            └─ No  → 메인 에이전트

작업이 병렬 탐색 + 에이전트 간 논의가 필요한가?
  └─ Yes → 에이전트 팀
  └─ No  → 서브 에이전트 또는 메인

재사용 가능한 절차·지침이 필요한가?
  └─ Yes → Skill (메인 컨텍스트에 로드)
```

### 비교
|상황|선택|이유|
|-|-|-|
|빠른 코드 탐색|Explore 서브 에이전트|Haiku로 빠르고 저렴, 메인 컨텍스트 보호|
|PR 코드 리뷰|code-reviewer 서브 에이전트|읽기 전용 도구로 격리, 결과만 요약|
|여러 관점의 PR 리뷰 (보안·성능·테스트)|에이전트 팀|팀원이 서로 발견 공유·도전|
|반복적인 코드 수정|메인 에이전트|컨텍스트 공유, 빠른 왕복|
|TDD 워크플로우|Skill|재사용 가능한 절차로 메인이 직접 수행|
|버그 가설 다중 조사|에이전트 팀|병렬 토론으로 앵커링 회피|
|대량 로그·테스트 출력 처리|서브 에이전트|상세 출력을 격리, 요약만 반환|

### 예시 1 - 코드 리뷰어
`.claude/agents/code-reviewer.md`

```markdown
---
name: code-reviewer
description: 코드 품질·보안·유지보수성을 검토하는 전문 코드 리뷰어. 코드 작성·수정 직후 즉시 사용 (use proactively).
model: sonnet
tools: Read, Grep, Glob, Bash
---

당신은 높은 수준의 코드 품질과 보안을 보장하는 시니어 코드 리뷰어입니다.

호출 시:
1. git diff로 최근 변경 사항 확인
2. 수정된 파일에 집중
3. 즉시 리뷰 시작

리뷰 체크리스트:
- 코드가 명확하고 가독성이 좋은가
- 함수와 변수 이름이 잘 지어졌는가
- 중복 코드가 없는가
- 적절한 에러 핸들링이 있는가
- 노출된 시크릿이나 API 키가 없는가
- 입력 검증이 구현되어 있는가
- 충분한 테스트 커버리지가 있는가
- 성능이 고려되었는가

다음 우선순위로 피드백을 제공합니다:
- Critical 이슈 (반드시 수정)
- Warning (수정 권장)
- Suggestion (개선 고려)

이슈를 어떻게 수정할지 구체적인 예시를 포함하세요.
```

### 예시 2 - 디버거
`.claude/agents/debugger.md`

```markdown
---
name: debugger
description: 에러·테스트 실패·예기치 못한 동작을 분석하는 디버깅 전문가. 문제 발생 시 즉시 사용 (use proactively).
model: sonnet
tools: Read, Edit, Bash, Grep, Glob
---

당신은 근본 원인 분석에 특화된 디버깅 전문가입니다.

호출 시:
1. 에러 메시지와 스택 트레이스 수집
2. 재현 단계 식별
3. 실패 지점 격리
4. 최소한의 수정 구현
5. 솔루션 검증

각 이슈마다 다음을 제공합니다:
- 근본 원인 설명
- 진단을 뒷받침하는 증거
- 구체적인 코드 수정
- 테스트 접근법
- 재발 방지 권고

증상이 아닌 근본 원인을 수정하는 데 집중하세요.
```

### 예시 3 - 읽기 전용 DB 분석가
`.claude/agents/db-reader.md`

```markdown
---
name: db-reader
description: 읽기 전용 데이터베이스 쿼리를 실행하는 분석가. 데이터 분석이나 리포트 생성 시 사용.
model: haiku
tools: Bash
hooks:
  PreToolUse:
    - matcher: "Bash"
      hooks:
        - type: command
          command: "./scripts/validate-readonly-query.sh"
---

당신은 읽기 전용 권한을 가진 데이터베이스 분석가입니다. SELECT 쿼리를 실행하여 데이터에 관한 질문에 답합니다.

데이터 분석 요청을 받으면:
1. 관련 데이터가 어느 테이블에 있는지 식별
2. 적절한 필터를 적용한 효율적인 SELECT 쿼리 작성
3. 결과를 컨텍스트와 함께 명확하게 제시

다음 형식으로 결과를 보고합니다:
- Query: 실행한 SQL
- Row count: 반환된 행 수
- Summary: 2-3문장 해석

데이터를 수정할 수 없습니다. INSERT, UPDATE, DELETE, 스키마 변경 요청 시 읽기 전용 권한임을 설명하세요.
```

### 예시 4 - 에이전트 팀 (병렬 PR 리뷰)
에이전트 팀은 별도의 frontmatter 파일이 아니라, 메인 세션의 리더가 자연어로 팀을 생성하는 방식이다.

#### 호출 예시
리더(메인 세션)에게 자연어로 팀 생성을 요청한다.

```text
PR #142를 리뷰할 에이전트 팀을 만들어줘. 다음 세 명의 팀원을 생성해:
- 보안 영향에 집중하는 security-reviewer
- 성능 영향을 확인하는 performance-reviewer
- 테스트 커버리지를 검증하는 test-coverage-reviewer

각 팀원은 같은 PR을 다른 관점으로 동시에 리뷰하고, 발견 사항을 서로 공유한 뒤
최종 통합 리포트를 보고해줘.
```

#### 기존 서브 에이전트 정의 재사용
이미 정의된 서브 에이전트가 있다면 팀원으로 그대로 활용 가능하다.

```text
security-reviewer 에이전트 타입을 사용해서 팀원을 생성하고,
auth 모듈을 감사하게 해줘.
```

이 경우 서브 에이전트 정의의 `tools`·`model`이 적용되고, 정의의 본문이 팀원의 시스템 프롬프트에 **추가**된다 (대체되지 않음).

#### 다각도 디버깅 팀 예시
경쟁하는 가설을 병렬로 검증하는 케이스다.

```text
사용자가 한 메시지 후 앱이 종료된다는 버그가 있어.
5명의 팀원을 spawn해서 각기 다른 가설을 조사하게 해줘.
팀원들끼리 서로의 이론을 반박하도록 토론시키고,
합의된 결론을 findings doc에 업데이트해줘.
```

#### 작업 후 정리
완료되면 반드시 리더에게 정리를 지시한다 (팀원은 정리를 실행하면 안 된다).

```text
Clean up the team
```

#### 슬래시 커맨드로 팀 spawn 자동화하기
에이전트 팀 자체는 파일로 정의할 수 없지만, **자연어 프롬프트가 매번 같다면 슬래시 커맨드로 감싸 재사용**할 수 있다. 이 패턴은 공식 정의는 아니지만 실용적인 표준 활용법이다.

##### PR 다각도 리뷰 팀 커맨드
`.claude/commands/pr-review-team.md`

```markdown
---
description: PR을 보안·성능·테스트 세 관점으로 병렬 리뷰하는 팀 생성
---

PR #$ARGUMENTS를 리뷰할 에이전트 팀을 만들어주세요. 다음 세 명의 팀원을 spawn합니다:
- security-reviewer: 보안 영향과 인증·인가 취약점에 집중
- performance-reviewer: 쿼리·메모리·렌더링 성능에 집중
- test-coverage-reviewer: 누락된 테스트 케이스와 엣지 케이스에 집중

각 팀원은 같은 PR을 다른 렌즈로 동시에 리뷰하고, 발견 사항을 서로 공유합니다.
완료되면 리더가 세 명의 발견을 종합하여 우선순위별 통합 리포트를 작성합니다.
작업이 끝나면 팀을 자동 정리합니다.
```

사용:
```
/pr-review-team 142
```

`$ARGUMENTS`가 `142`로 치환되며 매번 같은 팀 구조가 spawn된다.

##### 가설 토론 팀 커맨드
`.claude/commands/hypothesis-debate.md`

```markdown
---
description: 버그 가설을 병렬로 조사하고 토론으로 수렴하는 팀 생성
---

다음 버그를 5가지 가설로 병렬 조사하는 에이전트 팀을 만들어주세요.

버그: $ARGUMENTS

5명의 팀원을 spawn하고 각자 다른 가설을 할당합니다.
팀원들끼리 서로의 이론을 반박하도록 토론시키고,
합의된 결론을 findings.md에 업데이트합니다.
한 가설에 앵커링되지 않도록 적대적 토론을 유도하세요.
```

##### 권장 조합 패턴
가장 강력한 형태는 **서브 에이전트 + 슬래시 커맨드**를 함께 쓰는 것이다.

```
서브 에이전트(역할 정의) + 슬래시 커맨드(팀 spawn 트리거) + 자연어(상황별 조정)
```

| 파일 | 역할 |
|-|-|
|`.claude/agents/security-reviewer.md`|보안 리뷰어 **역할** 정의|
|`.claude/agents/performance-reviewer.md`|성능 리뷰어 **역할** 정의|
|`.claude/commands/pr-review-team.md`|위 두 역할을 팀원으로 호출하는 **트리거**|

이러면 팀의 **역할 정의(에이전트)** 와 **팀 구성(커맨드)** 이 분리되어 각각 독립적으로 재사용·수정할 수 있다.

> 💡 슬래시 커맨드와 Skill의 차이는 [claude_skill.md](claude_skill.md#command-vs-skill---무엇이-다른가)에서 자세히 다룬다.
