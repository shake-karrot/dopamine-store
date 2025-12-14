# dopamine-store

## 작업 관리 (Task Management)

### 워크플로우

```
Issue 생성 → GitHub Project 트래킹 → Branch 상태에 따른 Status 관리
```

### 1. Issue 생성

- 새로운 작업이 필요할 때 GitHub Issue를 생성합니다.
- Issue 제목과 설명에 작업 내용을 명확히 기록합니다.
- 적절한 라벨을 지정합니다 (예: `feature`, `bug`, `docs` 등).

### 2. GitHub Project 연동

- 생성된 Issue를 GitHub Project에 추가합니다.
- Project Board에서 작업 현황을 시각적으로 트래킹합니다.

### 3. Branch 생성 및 네이밍 규칙

Issue 기반으로 브랜치를 생성합니다:

```
<type>/#<issue-number>/<description>
```

**예시:**
- `feature/#12/user-authentication`
- `bugfix/#45/login-error-fix`
- `docs/#7/readme-update`

**Type 종류:**
| Type | 설명 |
|------|------|
| `feature` | 새로운 기능 개발 |
| `bugfix` | 버그 수정 |
| `hotfix` | 긴급 버그 수정 |
| `docs` | 문서 작업 |
| `refactor` | 코드 리팩토링 |
| `init` | 초기 설정 |

### 4. Project Task Status 관리

Branch 및 PR 상태에 따라 Project의 Task Status가 변경됩니다:

| Branch/PR 상태 | Project Status |
|----------------|----------------|
| Branch 생성 | `In Progress` |
| PR 생성 (Draft) | `In Review` |
| PR 생성 (Ready) | `In Review` |
| PR Merged | `Done` |
| PR Closed (미병합) | `Todo` 또는 `Closed` |

### 5. Commit 메시지 규칙

```
<type>: <description> (#<issue-number>)
```

**예시:**
```
feat: 로그인 기능 구현 (#12)
fix: 로그인 오류 수정 (#45)
docs: README 업데이트 (#7)
```