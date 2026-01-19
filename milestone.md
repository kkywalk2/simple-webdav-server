# WebDAV Server 구현 진행 상황

**프로젝트**: Kotlin/Ktor 기반 WebDAV 서버
**마지막 업데이트**: 2026-01-20

---

## 전체 진행률: 6/8 마일스톤 완료 (75%)

---

## ✅ 마일스톤 1: 프로젝트 기반 설정 (완료)

**완료일**: 2026-01-19

### 구현된 기능
- [x] Ktor + Netty 서버 설정
- [x] SQLite + Exposed ORM 의존성 추가
- [x] 기본 애플리케이션 구조 생성
- [x] 서버 부트스트랩 완료

### 생성된 파일
```
src/main/kotlin/
├── Application.kt              # 서버 진입점
├── config/
│   └── ServerConfig.kt         # 서버 설정 (포트: 8080, 루트: ~/webdav-root)
├── plugins/
│   └── Plugins.kt              # Ktor 플러그인 (ContentNegotiation)
└── routes/
    └── WebDavRoutes.kt         # 라우팅 정의

build.gradle.kts                # 의존성 설정
```

### 의존성
- Ktor Server 2.3.12 (Netty, Auth, ContentNegotiation)
- Exposed ORM 0.54.0 + SQLite JDBC
- Logback 1.4.14
- Kotlin 2.2.21 + JVM 17

### 검증
- ✅ `./gradlew build` 성공
- ✅ `./gradlew run` 서버 정상 실행
- ✅ `curl http://localhost:8080/` 응답 확인

---

## ✅ 마일스톤 2: 핵심 레이어 구현 (완료)

**완료일**: 2026-01-19

### 구현된 기능
- [x] Storage Layer 인터페이스 및 구현
- [x] Path Resolver (보안 강화)
- [x] WebDAV 유틸리티 (리소스 모델, XML 빌더)
- [x] 단위 테스트 작성 및 통과

### 생성된 파일
```
src/main/kotlin/
├── storage/
│   ├── StorageService.kt       # 스토리지 인터페이스
│   └── FileSystemStorage.kt    # 파일시스템 구현체
├── path/
│   └── PathResolver.kt         # 경로 정규화 및 보안 검증
└── webdav/
    ├── WebDavResource.kt       # WebDAV 리소스 모델
    └── XmlBuilder.kt           # Multi-Status XML 생성기

src/test/kotlin/
├── path/
│   └── PathResolverTest.kt     # 보안 테스트 (경로 탐색 차단)
└── storage/
    └── FileSystemStorageTest.kt # 스토리지 기능 테스트
```

### 주요 기능

#### StorageService
- 파일/폴더 존재 확인
- 메타데이터 조회 (크기, 수정시간, ETag 생성)
- 디렉터리 목록 조회
- 파일 읽기/쓰기 (원자적 저장)
- 파일/폴더 삭제
- 폴더 생성

#### PathResolver
- **보안**: `..` 경로 차단
- **보안**: serverRoot 외부 접근 차단
- UTF-8 + Percent-encoding 디코딩
- 경로 정규화

#### WebDAV 유틸리티
- ETag 생성 (size + lastModified 기반)
- RFC 1123 날짜 포맷팅
- Multi-Status XML 응답 빌더

### 검증
- ✅ `./gradlew test` 모든 테스트 통과
- ✅ PathResolver 보안 테스트 통과
- ✅ FileSystemStorage 기능 테스트 통과

---

## ✅ 마일스톤 3: 기본 WebDAV 메서드 (읽기) - 완료

**완료일**: 2026-01-19

**목표**: 탐색 및 다운로드 기능 구현

### 구현된 기능
- [x] OPTIONS 핸들러 (`DAV: 1`, `Allow` 헤더, `MS-Author-Via: DAV`)
- [x] PROPFIND 핸들러 (Depth 0, 1, infinity 거부 with 403)
- [x] GET / HEAD 핸들러 (파일 다운로드, ETag, Last-Modified)
- [x] 207 Multi-Status XML 응답
- [x] Content-Type 자동 감지 (txt, html, json, pdf, 이미지 등)

### 생성된 파일
```
src/main/kotlin/webdav/handlers/
├── OptionsHandler.kt       # WebDAV 기능 탐지
├── PropfindHandler.kt      # 디렉터리 목록 조회
└── GetHandler.kt           # 파일 다운로드 및 메타데이터

src/main/kotlin/routes/
└── WebDavRoutes.kt         # WebDAV 라우팅 통합
```

### 검증
- ✅ `curl -X OPTIONS /webdav/` - DAV, Allow 헤더 반환
- ✅ `curl -X PROPFIND -H "Depth: 0"` - 단일 리소스 조회
- ✅ `curl -X PROPFIND -H "Depth: 1"` - 디렉터리 + 자식 조회
- ✅ `curl -X PROPFIND -H "Depth: infinity"` - 403 거부
- ✅ `curl http://localhost:8080/webdav/test.txt` - 파일 다운로드
- ✅ `curl -I /webdav/test.txt` - HEAD 요청 (메타데이터만)

### XML 응답 예시
```xml
<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/test.txt</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>test.txt</D:displayname>
        <D:resourcetype></D:resourcetype>
        <D:getcontentlength>14</D:getcontentlength>
        <D:getlastmodified>Mon, 19 Jan 2026 07:23:44 GMT</D:getlastmodified>
        <D:getetag>"-2329f72f"</D:getetag>
        <D:getcontenttype>text/plain</D:getcontenttype>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>
```

---

## ✅ 마일스톤 4: 쓰기 WebDAV 메서드 - 완료

**완료일**: 2026-01-19

**목표**: 업로드, 삭제, 폴더 생성 구현

### 구현된 기능
- [x] PUT 핸들러 (스트리밍 업로드, 조건부 요청 지원)
  - 201 Created (새 파일)
  - 204 No Content (기존 파일 덮어쓰기)
  - If-Match, If-None-Match 지원
  - 원자적 저장 (임시파일 → atomic move)
- [x] DELETE 핸들러
  - 파일 삭제: 204 No Content
  - 빈 폴더 삭제: 204 No Content
  - 비어있지 않은 폴더: 409 Conflict
  - 없는 리소스: 404 Not Found
- [x] MKCOL 핸들러 (폴더 생성)
  - 성공: 201 Created
  - 부모 없음: 409 Conflict
  - 이미 존재: 405 Method Not Allowed

### 생성된 파일
```
src/main/kotlin/webdav/handlers/
├── PutHandler.kt           # 파일 업로드
├── DeleteHandler.kt        # 파일/폴더 삭제
└── MkcolHandler.kt         # 폴더 생성
```

### 검증
- ✅ `curl -X PUT --data-binary @file.txt /webdav/file.txt` - 201 Created
- ✅ `curl -X PUT --data-binary @file.txt /webdav/file.txt` - 204 No Content (덮어쓰기)
- ✅ `curl -X DELETE /webdav/file.txt` - 204 No Content
- ✅ `curl -X MKCOL /webdav/newdir` - 201 Created
- ✅ `curl -X DELETE /webdav/emptydir` - 204 No Content
- ✅ `curl -X DELETE /webdav/nonemptydir` - 409 Conflict ✓

---

## ✅ 마일스톤 5: 인증 및 권한 - 완료

**완료일**: 2026-01-19

**목표**: Basic Auth와 권한 관리 시스템 구현

### 구현된 기능
- [x] HTTP Basic Authentication (Ktor Auth 플러그인)
  - 401 Unauthorized + WWW-Authenticate 헤더
  - Realm: "WebDAV Server"
- [x] SQLite 기반 사용자/권한 관리
  - Users 테이블 (username, password, displayName, enabled)
  - PermissionRules 테이블 (path별 권한: LIST, READ, WRITE, DELETE, MKCOL, deny)
  - 기본 admin 사용자 자동 생성 (admin/admin)
- [x] Authorization Layer
  - 더 구체적인 경로 우선
  - deny 플래그 우선 적용
  - 기본 정책: deny (권한 없으면 차단)
- [x] 권한 없는 리소스 숨김 처리
  - PROPFIND에서 LIST 권한 없는 자식 리소스 필터링
  - 모든 핸들러에 권한 체크 통합

### 생성된 파일
```
src/main/kotlin/
├── db/
│   ├── DatabaseFactory.kt              # SQLite 초기화
│   ├── tables/
│   │   ├── Users.kt                    # 사용자 테이블
│   │   └── PermissionRules.kt          # 권한 규칙 테이블
│   └── repositories/
│       ├── UserRepository.kt           # 사용자 조회/인증
│       └── PermissionRepository.kt     # 권한 규칙 조회
├── auth/
│   ├── Permission.kt                   # Permission enum
│   └── AuthorizationService.kt         # 권한 체크 서비스
└── plugins/
    └── Plugins.kt                      # Basic Auth 설정
```

### 검증
- ✅ 인증 없이 접근 → 401 Unauthorized
- ✅ 잘못된 인증 → 401 Unauthorized
- ✅ 올바른 인증 (admin/admin) → 정상 작동
- ✅ PROPFIND로 디렉터리 목록 조회 (인증 필요)
- ✅ PUT로 파일 업로드 (인증 + WRITE 권한 필요)
- ✅ GET로 파일 다운로드 (인증 + READ 권한 필요)

---

## ⏳ 마일스톤 6: 고급 기능 - 대기 중

**목표**: Range 요청, 조건부 요청, LOCK 최소 지원

### 계획된 작업
- [ ] Range GET (206 Partial Content)
- [ ] If-Match / If-None-Match
- [ ] LOCK/UNLOCK 최소 지원

---

## ✅ 마일스톤 7: 공유 링크 기능 - 완료

**완료일**: 2026-01-20

**목표**: 로그인 없이 임시 접근 가능한 공유 링크

### 구현된 기능
- [x] 공유 링크 모델 (ShareLinks 테이블)
  - token (32자 랜덤 토큰)
  - resourcePath, resourceType (FILE/FOLDER)
  - createdBy, createdAt, expiresAt
  - password (선택적 비밀번호 보호)
  - maxAccessCount, accessCount (접근 횟수 제한)
  - canRead, canWrite (권한)
- [x] 공유 링크 API (인증 필요)
  - `POST /api/shares` - 공유 링크 생성
  - `GET /api/shares` - 내 공유 링크 목록
  - `GET /api/shares/{id}` - 공유 링크 조회
  - `DELETE /api/shares/{id}` - 공유 링크 삭제
- [x] 공유 링크 접근 (인증 불필요)
  - `GET /s/{token}` - 파일 다운로드 / 폴더 HTML 목록
  - `GET /s/{token}/file?path=...` - 공유 폴더 내 파일 다운로드
- [x] 보안 기능
  - 토큰 만료 시간 검증
  - 접근 횟수 제한
  - 비밀번호 보호 (선택)
  - 경로 탐색 공격 차단

### 생성된 파일
```
src/main/kotlin/
├── db/tables/
│   └── ShareLinks.kt               # 공유 링크 테이블
├── db/repositories/
│   └── ShareLinkRepository.kt      # 공유 링크 CRUD
└── share/
    ├── ShareLinkService.kt         # 토큰 생성, 유효성 검증
    └── ShareLinkHandler.kt         # API 및 접근 핸들러
```

### API 사용 예시

#### 공유 링크 생성
```bash
curl -X POST http://localhost:8080/api/shares \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"path": "/documents/report.pdf", "expiresInHours": 24}'
```

#### 응답
```json
{
  "id": 1,
  "token": "aBcDeFgHiJkLmNoPqRsTuVwXyZ123456",
  "path": "/documents/report.pdf",
  "resourceType": "FILE",
  "url": "http://localhost:8080/s/aBcDeFgHiJkLmNoPqRsTuVwXyZ123456",
  "createdAt": "2026-01-20T10:30:00",
  "expiresAt": "2026-01-21T10:30:00",
  "hasPassword": false,
  "maxAccessCount": null,
  "accessCount": 0,
  "canRead": true,
  "canWrite": false
}
```

#### 공유 링크로 파일 접근 (인증 불필요)
```bash
curl http://localhost:8080/s/aBcDeFgHiJkLmNoPqRsTuVwXyZ123456
```

### 검증
- ✅ 공유 링크 생성 (POST /api/shares)
- ✅ 공유 링크 목록 조회 (GET /api/shares)
- ✅ 공유 링크 삭제 (DELETE /api/shares/{id})
- ✅ 파일 공유 링크 접근 (GET /s/{token})
- ✅ 폴더 공유 링크 접근 (HTML 목록)
- ✅ 만료된 링크 접근 거부
- ✅ 비밀번호 보호 동작

---

## ⏳ 마일스톤 8: 클라이언트 호환성 테스트 - 대기 중

**목표**: 주요 WebDAV 클라이언트 호환성 검증

### 테스트 대상
- [ ] Raidrive (Windows)
- [ ] macOS Finder
- [ ] Linux davfs2
- [ ] Cyberduck

---

## 의사결정 사항

| 항목 | 결정 사항 |
|------|----------|
| 저장소 | SQLite DB (사용자/권한/공유링크) |
| DELETE 정책 | 비어있지 않은 폴더 → 409 Conflict |
| 권한 정책 | 권한 없는 리소스는 목록에서 숨김 |

---

## 다음 단계

**우선순위**: 마일스톤 6 (고급 기능) 또는 마일스톤 8 (클라이언트 호환성)

### 옵션 A: 마일스톤 6 (고급 기능)
1. Range GET 구현 (206 Partial Content)
2. If-Match / If-None-Match 조건부 요청 (이미 부분 구현됨)
3. LOCK/UNLOCK 최소 지원

### 옵션 B: 마일스톤 8 (클라이언트 테스트)
1. Raidrive 연결 테스트
2. macOS Finder 테스트
3. Cyberduck 테스트

---

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 서버 실행
./gradlew run

# 접속
curl http://localhost:8080/
```

**서버 설정**
- Host: 0.0.0.0
- Port: 8080
- WebDAV Root: ~/webdav-root

---

## 참고 문서

- [스펙 문서](spec.md)
- [계획 파일](/Users/user/.claude/plans/recursive-riding-stonebraker.md)
