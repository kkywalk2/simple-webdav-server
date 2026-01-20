# WebDAV Server Specification (Kotlin/Ktor)

## 1. 개요

### 1.1 목적
본 문서는 Windows 환경에서 동작하는 자체 WebDAV 서버 구현을 위한 기술 스펙을 정의한다.

주요 목표는 다음과 같다.

- Raidrive 클라이언트에서 안정적으로 사용 가능한 WebDAV 서버 구현
- 파일/폴더 단위 권한 관리 기능 제공
- macOS / Linux WebDAV 클라이언트와의 호환성 확보
- Kotlin + Ktor 기반의 유지보수 가능한 구조 설계
- 임시 공유 링크 기능 제공

---

## 2. 프로젝트 목표

### 2.1 핵심 목표

- Windows에서 실행되는 WebDAV 서버 구축
- Raidrive에서 네트워크 드라이브처럼 정상 동작
- 다음 기능의 완전 지원
    - 탐색
    - 파일 업로드/다운로드
    - 삭제
    - 폴더 생성
- 사용자 인증 및 권한 관리

### 2.2 비목표 (초기 버전)

다음 항목은 MVP 범위에서 제외한다.

- DeltaV (WebDAV 버전 관리)
- 완전한 LOCK/UNLOCK semantics
- WebDAV ACL 확장(RFC3744) 완전 지원
- 대규모 분산 스토리지 지원

---

## 3. 시스템 구성

### 3.1 전체 구조

시스템은 다음 레이어로 구성된다.

- WebDAV HTTP Layer (Ktor)
- Storage Layer (파일 시스템)
- Authentication Layer
- Authorization Layer
- Path Resolver

### 3.2 저장소 정책

- 서버는 단일 루트 디렉터리만 외부에 노출한다.
- 예시:

serverRoot = D:\dav-root

yaml
코드 복사

- 모든 요청 경로는 위 루트 하위로만 매핑된다.
- 루트 외부 접근은 완전히 차단한다.

---

## 4. Path & Encoding 규칙

### 4.1 URL 처리 원칙

- 모든 경로는 UTF-8 기반으로 처리한다.
- Percent-encoding 디코딩을 반드시 수행한다.
- 경로 정규화를 필수로 수행한다.

### 4.2 보안 규칙

- `..` 경로 이동을 완전히 차단한다.
- 정규화 후 serverRoot 내부인지 반드시 검증한다.
- 외부 경로 접근 시 403 Forbidden을 반환한다.

### 4.3 대소문자 정책

- 내부 권한 체크는 정규화된 경로 기준으로 수행한다.
- 파일 시스템 정책을 따른다.
    - Windows: 대소문자 비민감
    - Linux: 대소문자 민감

---

## 5. 인증(Authentication)

### 5.1 지원 방식

- HTTP Basic Authentication을 지원한다.
- HTTPS 사용을 권장한다.

### 5.2 실패 응답

인증 실패 시 다음을 반환한다.

401 Unauthorized
WWW-Authenticate: Basic realm="WebDAV"

yaml
코드 복사

---

## 6. 권한 관리(Authorization)

### 6.1 기본 원칙

- OS ACL이 아닌 애플리케이션 권한 모델을 사용한다.
- 권한은 DB 또는 설정 기반으로 관리한다.

### 6.2 권한 종류

- LIST
- READ
- WRITE
- DELETE
- MKCOL
- MOVE (선택)
- COPY (선택)

### 6.3 권한 평가 규칙

- 더 구체적인 규칙이 우선한다.
- deny 우선 정책을 적용한다.
- 기본 정책은 deny이다.

---

## 7. WebDAV 메서드 지원 범위

### 7.1 필수 구현 메서드

| Method | 용도 |
|------|------|
| OPTIONS | 기능 탐지 |
| PROPFIND | 목록 조회 |
| GET | 다운로드 |
| PUT | 업로드 |
| DELETE | 삭제 |
| MKCOL | 폴더 생성 |

---

### 7.2 OPTIONS

응답 예시

DAV: 1
Allow: OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL

yaml
코드 복사

---

### 7.3 PROPFIND

- Depth: 0, 1 지원
- Depth: infinity → 403 또는 501

반환 필수 속성

- displayname
- resourcetype
- getcontentlength
- getlastmodified
- getetag

---

### 7.4 GET / HEAD

- 파일 다운로드 지원
- Range 요청 지원 권장

---

### 7.5 PUT

- 스트리밍 업로드 필수
- 원자적 저장 권장
- 조건부 요청 지원

If-Match
If-None-Match

yaml
코드 복사

---

### 7.6 DELETE

- 성공: 204
- 없음: 404
- 폴더 비어있지 않으면: 409

---

### 7.7 MKCOL

- 성공: 201
- 부모 없음: 409

---

## 8. 메타데이터

### 8.1 ETag

- 최소 정책

ETag = hash(size + lastModified)

yaml
코드 복사

### 8.2 Last-Modified

- RFC1123 형식
- UTC 기준

---

## 9. Locking 정책

- LOCK/UNLOCK 요청에 대해 최소 지원
- 초기에는 논리적 성공만 반환

---

## 10. 에러 코드 표준

| 상황 | 코드 |
|----|----|
| 성공 | 200 |
| 생성 | 201 |
| 삭제 성공 | 204 |
| 목록 조회 | 207 |
| 인증 실패 | 401 |
| 권한 없음 | 403 |
| 없음 | 404 |
| 충돌 | 409 |
| 조건 실패 | 412 |
| 미지원 | 501 |

---

# 11. 공유 링크 기능 스펙

## 11.1 목적

- 로그인 없이 특정 파일/폴더에 대한 임시 접근 제공
- 만료 시간과 권한을 통한 접근 제어

---

## 11.2 공유 링크 모델

필수 속성

- token
- resourcePath
- resourceType (FILE | FOLDER)
- permissions
- expiresAt
- createdBy

선택 속성

- password
- maxAccessCount
- accessCount

---

## 11.3 정책

- 기본은 READ-only
- 기본 만료 시간 설정 권장
- 토큰은 추측 불가능해야 함

---

## 11.4 공유 API

### 생성

POST /api/shares

shell
코드 복사

### 조회

GET /api/shares/{id}

shell
코드 복사

### 폐기

DELETE /api/shares/{id}

yaml
코드 복사

---

## 11.5 공유 링크 접근

### 파일

GET /s/{token}

yaml
코드 복사

### 폴더

- 옵션 A: HTML 목록 제공 (MVP 권장)
- 옵션 B: 읽기 전용 WebDAV 제공 (확장 기능)

---

# 12. MVP 준수 체크리스트

## 필수 기능

- [ ] Raidrive 연결 가능
- [ ] Basic Auth 정상 동작
- [ ] 폴더 탐색
- [ ] 파일 다운로드
- [ ] 파일 업로드
- [ ] 파일 삭제
- [ ] 폴더 생성

---

## PROPFIND

- [ ] Depth 0 지원
- [ ] Depth 1 지원
- [ ] infinity 거부 처리
- [ ] 207 Multi-Status 형식
- [ ] 필수 속성 반환

---

## 메타데이터

- [ ] ETag 반환
- [ ] Last-Modified 반환
- [ ] Content-Length 정확

---

## 업로드/다운로드

- [ ] 스트리밍 지원
- [ ] Range GET
- [ ] If-Match 처리
- [ ] If-None-Match 처리

---

## 권한

- [ ] 폴더 단위 권한
- [ ] 파일 단위 권한
- [ ] 권한 없는 항목 숨김

---

## 공유 링크

- [ ] 공유 생성
- [ ] 만료 처리
- [ ] 파일 다운로드
- [ ] 폴더 목록
- [ ] 토큰 보안
- [ ] 접근 로그

---

# 13. 테스트 매트릭스

## 클라이언트

- Raidrive
- macOS Finder
- Linux davfs2
- Cyberduck

---

## 시나리오

1. 연결
2. 탐색
3. 업로드
4. 수정
5. 삭제
6. 권한 테스트
7. 대용량 파일

---

# 14. 의사결정 포인트

- Depth=infinity 처리 방식
- DELETE 재귀 허용 여부
- LOCK 강제 여부
- 권한 없는 리소스 노출 정책
- ETag 생성 전략
- 공유 링크 기본 만료 시간

---

# 15. 관리자 대시보드

## 15.1 개요

### 15.1.1 목적

관리자 대시보드는 WebDAV 서버의 핵심 관리 기능을 제공한다.

- 사용자 및 권한 관리
- 파일 브라우저를 통한 서버 파일 탐색
- 공유 링크 관리
- 시스템 통계 및 모니터링

### 15.1.2 아키텍처 결정

| 항목 | 결정 | 근거 |
|------|------|------|
| 구현 방식 | API + 웹 UI (SPA) | 유연성과 사용 편의성 동시 제공 |
| 프론트엔드 | 정적 HTML/JS | 별도 빌드 없이 Ktor에서 직접 서빙 |
| 인증 | HTTP Basic Auth | 기존 WebDAV 인증과 일관성 유지 |
| 권한 | 관리자 전용 | Users 테이블의 `isAdmin` 필드로 확인 |

---

## 15.2 데이터베이스 스키마 변경

### 15.2.1 Users 테이블 확장

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| is_admin | BOOLEAN | 관리자 여부 (기본값: false) |
| created_at | DATETIME | 계정 생성 시간 |
| last_login_at | DATETIME | 마지막 로그인 시간 |

### 15.2.2 AccessLogs 테이블 (신규)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | INTEGER | 자동 증가 기본키 |
| timestamp | DATETIME | 접근 시간 |
| username | VARCHAR(50) | 사용자명 (nullable) |
| method | VARCHAR(20) | HTTP 메서드 |
| path | VARCHAR(500) | 요청 경로 |
| status_code | INTEGER | HTTP 상태 코드 |
| ip_address | VARCHAR(45) | IP 주소 |
| user_agent | VARCHAR(500) | User-Agent (nullable) |

---

## 15.3 사용자 관리 API

### 15.3.1 엔드포인트

| Method | 경로 | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/admin/users | 사용자 목록 조회 | admin |
| GET | /api/admin/users/{username} | 사용자 상세 조회 | admin |
| POST | /api/admin/users | 사용자 생성 | admin |
| PUT | /api/admin/users/{username} | 사용자 수정 | admin |
| DELETE | /api/admin/users/{username} | 사용자 삭제 | admin |
| PUT | /api/admin/users/{username}/password | 비밀번호 변경 | admin |

### 15.3.2 사용자 목록 조회

요청

GET /api/admin/users
Authorization: Basic {credentials}

응답 (200 OK)

```json
{
  "users": [
    {
      "username": "admin",
      "displayName": "Administrator",
      "enabled": true,
      "isAdmin": true,
      "createdAt": "2026-01-15T10:30:00",
      "lastLoginAt": "2026-01-20T09:15:00",
      "permissionCount": 3
    }
  ],
  "total": 1
}
```

### 15.3.3 사용자 생성

요청

POST /api/admin/users
Content-Type: application/json

```json
{
  "username": "newuser",
  "password": "securePassword123",
  "displayName": "신규 사용자",
  "isAdmin": false
}
```

응답 (201 Created)

```json
{
  "username": "newuser",
  "displayName": "신규 사용자",
  "enabled": true,
  "isAdmin": false,
  "createdAt": "2026-01-20T10:00:00"
}
```

---

## 15.4 권한 규칙 관리 API

### 15.4.1 엔드포인트

| Method | 경로 | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/admin/permissions | 전체 권한 규칙 조회 | admin |
| GET | /api/admin/permissions/user/{username} | 사용자별 권한 조회 | admin |
| POST | /api/admin/permissions | 권한 규칙 생성 | admin |
| PUT | /api/admin/permissions/{id} | 권한 규칙 수정 | admin |
| DELETE | /api/admin/permissions/{id} | 권한 규칙 삭제 | admin |

### 15.4.2 권한 규칙 생성

요청

POST /api/admin/permissions
Content-Type: application/json

```json
{
  "username": "user1",
  "path": "/documents",
  "canList": true,
  "canRead": true,
  "canWrite": true,
  "canDelete": false,
  "canMkcol": true,
  "deny": false
}
```

응답 (201 Created)

```json
{
  "id": 4,
  "username": "user1",
  "path": "/documents",
  "canList": true,
  "canRead": true,
  "canWrite": true,
  "canDelete": false,
  "canMkcol": true,
  "deny": false
}
```

---

## 15.5 파일 브라우저 API

### 15.5.1 엔드포인트

| Method | 경로 | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/admin/files | 디렉토리 목록 조회 | admin |
| GET | /api/admin/files/info | 파일/폴더 정보 조회 | admin |
| POST | /api/admin/files/mkdir | 폴더 생성 | admin |
| DELETE | /api/admin/files | 파일/폴더 삭제 | admin |
| POST | /api/admin/files/move | 파일/폴더 이동 | admin |
| POST | /api/admin/files/copy | 파일/폴더 복사 | admin |

### 15.5.2 디렉토리 목록 조회

요청

GET /api/admin/files?path=/documents
Authorization: Basic {credentials}

쿼리 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| path | string | / | 조회할 경로 |
| sort | string | name | 정렬 기준 (name, size, modified) |
| order | string | asc | 정렬 순서 (asc, desc) |

응답 (200 OK)

```json
{
  "path": "/documents",
  "parentPath": "/",
  "entries": [
    {
      "name": "reports",
      "path": "/documents/reports",
      "type": "FOLDER",
      "size": 0,
      "lastModified": "2026-01-19T15:30:00",
      "childCount": 5
    },
    {
      "name": "readme.txt",
      "path": "/documents/readme.txt",
      "type": "FILE",
      "size": 1024,
      "lastModified": "2026-01-18T10:00:00",
      "mimeType": "text/plain"
    }
  ],
  "totalCount": 2,
  "folderCount": 1,
  "fileCount": 1
}
```

---

## 15.6 공유 링크 관리 API (확장)

### 15.6.1 관리자용 엔드포인트

| Method | 경로 | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/admin/shares | 모든 공유 링크 조회 | admin |
| GET | /api/admin/shares/stats | 공유 링크 통계 | admin |
| DELETE | /api/admin/shares/{id} | 모든 공유 링크 삭제 가능 | admin |
| DELETE | /api/admin/shares/expired | 만료된 링크 일괄 삭제 | admin |

### 15.6.2 공유 링크 통계

요청

GET /api/admin/shares/stats

응답 (200 OK)

```json
{
  "totalShares": 50,
  "activeShares": 42,
  "expiredShares": 8,
  "fileShares": 35,
  "folderShares": 15,
  "totalAccessCount": 1250,
  "passwordProtected": 20
}
```

---

## 15.7 통계 및 모니터링 API

### 15.7.1 엔드포인트

| Method | 경로 | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/admin/stats/overview | 시스템 개요 통계 | admin |
| GET | /api/admin/stats/storage | 스토리지 통계 | admin |
| GET | /api/admin/logs | 접근 로그 조회 | admin |

### 15.7.2 시스템 개요 통계

요청

GET /api/admin/stats/overview

응답 (200 OK)

```json
{
  "users": {
    "total": 10,
    "active": 8,
    "admin": 2
  },
  "shares": {
    "total": 50,
    "active": 42
  },
  "storage": {
    "totalSize": 10737418240,
    "totalSizeFormatted": "10.0 GB",
    "fileCount": 1500,
    "folderCount": 200
  }
}
```

---

## 15.8 보안 고려사항

### 15.8.1 인증 및 권한

| 항목 | 구현 방법 |
|------|----------|
| 관리자 인증 | 기존 HTTP Basic Auth 활용 |
| 관리자 확인 | Users 테이블의 isAdmin 필드 확인 |
| 세션 관리 | Stateless (매 요청 인증) |

### 15.8.2 입력 검증

- 경로 탈출 방지 (`..` 포함 여부 확인)
- 유효한 경로 형식 검증 (`/`로 시작)
- 사용자명 형식 검증 (영문/숫자/언더스코어, 3-50자)

### 15.8.3 감사 로깅

모든 관리 작업은 로그에 기록한다.

| 기록 대상 | 예시 |
|----------|------|
| 사용자 생성/수정/삭제 | "Created user: newuser" |
| 권한 규칙 변경 | "Modified permission rule #5" |
| 공유 링크 삭제 | "Deleted share link #12" |
| 파일/폴더 삭제 | "Deleted file: /documents/secret.txt" |

---

## 15.9 웹 UI 설계

### 15.9.1 라우팅 구조

| URL | 설명 |
|-----|------|
| /admin | 대시보드 메인 (리다이렉트) |
| /admin/dashboard | 시스템 개요 |
| /admin/users | 사용자 관리 |
| /admin/permissions | 권한 관리 |
| /admin/files | 파일 브라우저 |
| /admin/shares | 공유 링크 관리 |
| /admin/logs | 접근 로그 |

### 15.9.2 기술 스택

| 항목 | 선택 | 근거 |
|------|------|------|
| 프레임워크 | Vanilla JS 또는 Alpine.js | 빌드 없이 직접 서빙 가능 |
| CSS | Tailwind CSS (CDN) | 유틸리티 기반, 빠른 개발 |
| 아이콘 | Lucide Icons (CDN) | 가벼움, MIT 라이선스 |

---

## 15.10 구현 우선순위

### Phase 1 (MVP)

- Users 테이블 isAdmin 컬럼 추가
- 사용자 관리 API (CRUD)
- 권한 규칙 관리 API (CRUD)
- 파일 브라우저 API (조회, 삭제)
- 기본 웹 UI

### Phase 2

- 관리자용 공유 링크 관리 API
- 파일 이동/복사 API
- 시스템 통계 API
- 개선된 웹 UI

### Phase 3

- 접근 로그 테이블 및 API
- 감사 로깅
- 비밀번호 해시 (BCrypt)

---

## 15.11 에러 응답 표준

| 에러 코드 | HTTP 상태 | 설명 |
|----------|----------|------|
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 관리자 권한 필요 |
| NOT_FOUND | 404 | 리소스를 찾을 수 없음 |
| VALIDATION_ERROR | 400 | 입력 검증 실패 |
| CONFLICT | 409 | 중복 또는 충돌 |

---

## 15.12 테스트 체크리스트

### 사용자 관리

- [ ] 사용자 목록 조회
- [ ] 사용자 생성 (유효/무효 입력)
- [ ] 사용자 수정
- [ ] 사용자 삭제
- [ ] 중복 사용자명 처리

### 권한 관리

- [ ] 권한 규칙 목록 조회
- [ ] 권한 규칙 생성
- [ ] 권한 규칙 수정
- [ ] 권한 규칙 삭제

### 파일 브라우저

- [ ] 루트 디렉토리 조회
- [ ] 하위 디렉토리 탐색
- [ ] 폴더 생성
- [ ] 파일 삭제
- [ ] 경로 탈출 방지

### 보안

- [ ] 비인증 접근 차단
- [ ] 비관리자 접근 차단
- [ ] 경로 탈출 시도 차단

---

## 마무리

본 문서는 MVP 구현 기준이며, 향후 기능 확장을 고려한 기반 문서로 활용한다.
