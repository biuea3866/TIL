# 클로드 스킬
## 클로드 스킬이란
---

skill은 SKILL.md 파일이 포함된 디렉토리로 claude가 특정 작업 수행 시 언제든지 참조하여 일관된 작업을 진행하도록 하는 파일이다.

## 구조

### 기본 구조
---
```
name: my-skill-name
description: 이 skill은 무엇을 해야하는지, 언제 사용해야하는지

# Skill Name

## Instructions
단계별 지침
1. ~
2. ~
```

클로드가 SKILL.md를 로드하면서 다음의 정보들을 로드한다.
1. 메타데이터 (시작 시): name, description만 로드 (frontmatter)
2. 본문 (트리거 시): SKILL.md의 마크다운 내용 로드
3. 추가 리소스 (참조 시): scripts/, references/, assets/ 등의 파일들은 명시적으로 참조할 때 로드

### Frontmatter
프론트 매터는 스킬이 어떻게 실행되는지를 구성(권한, 모델, 메타데이터)하고, 본문은 무엇을 해야하는지 알려주는 포맷이다.

#### 1. name
skill의 식별자이며, / 명령어로 사용된다.
```
---
name: kotlin-spring-impl
---
```

#### 2. description
클로드가 skill을 자동 트리거할지 결정하는 기준이 된다. 

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
---
```

무엇을 하는지, 언제 사용하는지를 포함하고, 로드 시점에 모든 skill의 description이 시스템 프롬프트에 미리 로드된다.


#### 3. disable-model-invocation

클로드가 이 skill을 사용하지 못하도록 하며, 오직 사용자만이 호출할 수 있는 옵션이다.

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
---
```

#### 4. user-invocable
false라면 클로드만 skill을 호출할 수 있도로록 하는 옵션이다. 만약 user-invocable: false + disable-model-invocation: true면 클로드가 해당 skill을 전혀 사용할 수 없으므로 주의해야한다.

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
user-invocable: false
---
```

#### 5. allowed-tools
skill이 활성화되어있는 동안 클로드가 사용할 수 있는 도구를 제한한다. 도구에 대한 권한을 제어함으로써 강제 제어 장치를 구성할 수 있다. 하지만 skill에 제한해두었다고 해서 프롬프트이므로 언제나 무시될 가능성이 높다. 따라서 시스템 상에서 강제할 수 있는 장치가 필요하다. (쉘 스크립트, pre-commit 등)

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
user-invocable: false
allowed-tools: Read Grep Glob
---
```

#### 6. model
skill 사용 시 사용할 모델을 지정한다.

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
user-invocable: false
allowed-tools: Read Grep Glob
model: sonnet
---
```

#### 7. context
* context: fork는 메인 에이전트와 분리된 서브 에이전트가 실행되며, 최종 결과를 메인 대화로 반환한다.

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
user-invocable: false
allowed-tools: Read Grep Glob
model: sonnet
context: fork
---
```

#### 8. agent
context: fork와 함께 사용되며, 서브에이전트 타입을 지정한다.

|Agent|Model|Tools|Use for|
|-|-|-|-|
|Explore|Haiku|Read-only|빠른 코드 탐색 및 분석|
|Plan|Inherits|Read-only|리서치 전에 계획|
|general-purpose|Inherits|All tools|복합 다중 업무|

```
---
name: kotlin-spring-impl
description: Kotlin/Spring Boot 백엔드 구현 스킬. Kotlin 문법 적극 활용, 디자인 패턴 기반, OOP 책임 분리, Rich Domain Model + 얇은 Service, 풀네임 변수, Enum 상태 전이 규칙까지 강제. be-implementer 에이전트가 TDD 구현 시 참조.
disable-model-invocation: false
user-invocable: false
allowed-tools: Read Grep Glob
model: sonnet
context: fork
agent: general-purpose
---
```

#### 9. 동적 인자 처리
```
---
name: session-logger
decription: 세션의 활동 로그
---

logs/${ARGUMENTS}.log에 로그를 기록해주세요.
```

```
/session-logger unique_session_id
```

와 같이 호출한다면, unique_session_id가 ARGUMENTS로 매핑되어 동적으로 인자를 처리할 수 있다.

#### 10. 동적 명령어 주입
```
---
name: summarize-changes
description: 커밋되지 않은 변경사항 요약
---

## Current changes
!`git diff HEAD`

## Instructions
위 변경사항을 요약하세요.
```

!command는 skill 로드 시 동적으로 셀 명령으로 변환되어 동적으로 명령을 실행할 수 있다.

## 클로드 스킬 사용방법
---
### 예시 1
가령 PDF skill이 있다고 할 때 디렉토리 구조는 다음과 같이 구성할 수 있다.
```
.claude/skills/
└── pdf-processing/
    ├── SKILL.md          # 핵심 지침 + frontmatter
    ├── forms.md          # 폼 작성 시에만 로드
    ├── reference.md      # 상세 참조 시에만 로드
    ├── scripts/
    │   └── extract.py    # 실행 가능한 코드
    └── assets/
        └── template.pdf  # 리소스 파일
```

### 예시 2
가벼운 모델로 빠른 검색을 위한 skill

```
---
name: quick-lookup
description: 코드베이스 전반 빠른 키워드 검색
model: haiku
allowed-tools: Read Grep Glob
---
```

### 예시 3
코드 베이스 조사 skill
```
---
name: deep-research
description: 코드베이스 전반에 걸쳐 주제를 철저히 조사
context: fork
agent: Explore
---

$ARGUMENTS 주제 조사:
1. Glob과 Grep으로 관련 파일 찾기
2. 코드 분석
3. 결과 보고
```

```
/deep-research user-service
```

## Command vs Skill - 무엇이 다른가
---
슬래시 커맨드와 Skill은 둘 다 `.claude/` 하위에 마크다운으로 정의되고 `/`로 호출되어 헷갈리기 쉽다. 하지만 **로딩되는 방식과 의도가 완전히 다르다**.

### 핵심 차이
> **Command = "유저 턴으로 제출되는 프롬프트 템플릿(트리거)"**
> **Skill = "컨텍스트에 주입되는 지식·절차(매뉴얼)"**

### 비교
|구분|슬래시 커맨드|Skill|
|-|-|-|
|본질|**프롬프트 템플릿**|**지침·절차서**|
|호출 시 동작|본문이 **사용자 메시지**로 제출됨|본문이 **시스템 컨텍스트에 주입**됨|
|역할|"이거 해줘"라고 **요청**|"이렇게 해라"라고 **방법 알려줌**|
|자동 트리거|불가 (사용자가 `/`로 호출)|가능 (description 매칭 시 자동 로드)|
|상태성|일회성 (제출 후 끝)|로드되면 세션 동안 유지|
|동적 인자|`$ARGUMENTS` 지원|`${ARGUMENTS}` 지원|
|동적 셸 실행|`!command` 지원|`!command` 지원|
|보조 리소스|없음 (단일 .md)|`scripts/`, `references/`, `assets/` 가능|

### 비유로 보면
* **커맨드** = "버튼" — 누르면 정해진 행동을 **실행**
* **Skill** = "사용 설명서" — 펼쳐서 **참조**하며 작업

### 같은 작업을 둘로 만들면?
"테스트 실행 후 실패 리포트" 작업을 두 방식으로 만들어 비교해본다.

#### Command로 구현
`.claude/commands/run-tests.md`

```markdown
---
description: 테스트 실행 후 실패 보고
---

`pnpm test`를 실행하고, 실패한 테스트의 파일 경로와 에러 메시지만 보고해주세요.
통과한 테스트는 생략합니다.
```

`/run-tests` 입력 → 본문이 **사용자 메시지로 제출** → Claude가 그 메시지를 받고 즉시 실행.

#### Skill로 구현
`.claude/skills/testing-workflow/SKILL.md`

```markdown
---
name: testing-workflow
description: 테스트 실행과 실패 분석을 위한 표준 절차
---

# Testing Workflow

테스트를 실행할 때는 다음 절차를 따릅니다.

## Instructions
1. `pnpm test`로 테스트 실행
2. 실패한 테스트만 식별
3. 파일 경로 + 에러 메시지 + 추정 원인을 정리
4. 수정 제안 포함

## 출력 형식
- File: <path>
- Error: <message>
- Likely cause: <reason>
```

`/testing-workflow` 또는 description 매칭으로 자동 로드 → Skill 본문이 **시스템 컨텍스트에 주입** → Claude가 이후 모든 테스트 관련 작업에서 이 절차를 참조.

### 언제 무엇을 쓰는가
|상황|선택|
|-|-|
|"매번 같은 프롬프트를 짧게 호출하고 싶다"|Command|
|"특정 단어가 나오면 자동으로 행동을 바꾸고 싶다"|Skill|
|"파라미터 받아서 일회성으로 작업 트리거"|Command|
|"여러 단계의 작업에서 일관된 절차 강제"|Skill|
|"에이전트 팀 spawn 같은 트리거 행위"|Command|
|"코드 리뷰·테스트 작성 등 방법론 정의"|Skill|

### 에이전트 팀에서는 왜 Command가 적합한가
에이전트 팀 spawn은 **"이 구조로 팀을 만들어줘"라는 1회성 요청**이다. 매번 같은 요청을 짧게 트리거하고 싶고, 작업이 끝나면 컨텍스트에 남아있을 필요가 없다. 이 특성이 정확히 슬래시 커맨드의 본질과 일치한다.

반면 "팀을 만들 때 항상 지켜야 할 원칙·체크리스트"는 Skill로 정의하는 것이 적합하다. 이 경우 두 가지를 조합한다 — 커맨드가 트리거하고, Skill이 백그라운드에서 원칙을 강제한다.