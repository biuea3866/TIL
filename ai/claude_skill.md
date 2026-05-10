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

### 얘시 3
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