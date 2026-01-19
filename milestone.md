# WebDAV Server 구현 진행 상황

**프로젝트**: Kotlin/Ktor 기반 WebDAV 서버
**마지막 업데이트**: 2026-01-19

---

## 전체 진행률: 2/8 마일스톤 완료 (25%)

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

## 🔄 마일스톤 3: 기본 WebDAV 메서드 (읽기) - 대기 중

**목표**: 탐색 및 다운로드 기능 구현

### 계획된 작업
- [ ] OPTIONS 핸들러 (`DAV: 1`, `Allow` 헤더)
- [ ] PROPFIND 핸들러 (Depth 0, 1, infinity 거부)
- [ ] GET / HEAD 핸들러 (파일 다운로드, Range 지원)
- [ ] 207 Multi-Status XML 응답

### 예상 파일
```
src/main/kotlin/webdav/handlers/
├── OptionsHandler.kt
├── PropfindHandler.kt
└── GetHandler.kt
```

---

## ⏳ 마일스톤 4: 쓰기 WebDAV 메서드 - 대기 중

**목표**: 업로드, 삭제, 폴더 생성 구현

### 계획된 작업
- [ ] PUT 핸들러 (스트리밍 업로드)
- [ ] DELETE 핸들러 (비어있지 않은 폴더 → 409)
- [ ] MKCOL 핸들러

---

## ⏳ 마일스톤 5: 인증 및 권한 - 대기 중

**목표**: Basic Auth와 권한 관리 시스템 구현

### 계획된 작업
- [ ] HTTP Basic Authentication
- [ ] SQLite 기반 사용자/권한 관리
- [ ] Authorization Layer (LIST, READ, WRITE, DELETE, MKCOL)
- [ ] 권한 없는 리소스 숨김 처리

---

## ⏳ 마일스톤 6: 고급 기능 - 대기 중

**목표**: Range 요청, 조건부 요청, LOCK 최소 지원

### 계획된 작업
- [ ] Range GET (206 Partial Content)
- [ ] If-Match / If-None-Match
- [ ] LOCK/UNLOCK 최소 지원

---

## ⏳ 마일스톤 7: 공유 링크 기능 - 대기 중

**목표**: 로그인 없이 임시 접근 가능한 공유 링크

### 계획된 작업
- [ ] 공유 링크 모델 (token, expiresAt)
- [ ] 공유 API (POST/GET/DELETE /api/shares)
- [ ] 공유 접근 (GET /s/{token})

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

**우선순위**: 마일스톤 3 (WebDAV 읽기 메서드)

1. OPTIONS 핸들러 구현
2. PROPFIND 핸들러 구현 (Depth 0, 1)
3. GET/HEAD 핸들러 구현
4. Raidrive 연결 테스트

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
