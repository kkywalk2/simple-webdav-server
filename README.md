# Simple WebDAV Server

Kotlin + Ktor 기반의 경량 WebDAV 서버입니다. Windows, macOS, Linux 환경에서 네트워크 드라이브처럼 사용할 수 있으며, 파일/폴더 단위의 세밀한 권한 관리와 임시 공유 링크 기능을 제공합니다.

## 주요 기능

- **WebDAV 프로토콜 지원** - RaiDrive, Cyberduck, macOS Finder, Linux davfs2 등 다양한 클라이언트와 호환
- **사용자 인증** - HTTP Basic Authentication
- **세밀한 권한 관리** - 파일/폴더 단위로 List, Read, Write, Delete, Mkcol 권한 설정
- **공유 링크** - 로그인 없이 파일/폴더 공유 (만료 시간, 비밀번호, 접근 횟수 제한)
- **관리자 대시보드** - 웹 기반 관리 UI

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 2.2 |
| 프레임워크 | Ktor 2.3 |
| 데이터베이스 | SQLite + Exposed ORM |
| 빌드 도구 | Gradle |
| JDK | 17+ |

## 빠른 시작

### 요구 사항

- JDK 17 이상

### 실행

```bash
# 저장소 클론
git clone https://github.com/your-repo/simple-webdav-server.git
cd simple-webdav-server

# 실행
./gradlew run
```

서버가 `http://localhost:8080`에서 시작됩니다.

### 기본 계정

- **사용자명**: `admin`
- **비밀번호**: `admin`

## 설정

`config.yaml` 파일로 서버 설정을 변경할 수 있습니다:

```yaml
server:
  port: 8080
  host: 0.0.0.0

storage:
  root: ./dav-root  # WebDAV 루트 디렉토리

database:
  path: ./webdav.db
```

## WebDAV 클라이언트 연결

### RaiDrive (Windows)

1. RaiDrive 실행
2. 새 드라이브 추가 > WebDAV 선택
3. 주소: `http://localhost:8080`
4. 계정: `admin` / `admin`

### macOS Finder

1. Finder > 이동 > 서버에 연결
2. 주소: `http://localhost:8080`
3. 인증 정보 입력

### Cyberduck

1. 새 연결 > WebDAV (HTTP)
2. 서버: `localhost`
3. 포트: `8080`
4. 인증 정보 입력

## 관리자 대시보드

브라우저에서 `http://localhost:8080/admin/` 접속

### 기능

| 탭 | 설명 |
|----|------|
| Dashboard | 시스템 통계 (사용자 수, 권한 규칙 수, 공유 링크 수) |
| Users | 사용자 생성/수정/삭제 |
| Permissions | 경로별 권한 규칙 관리 |
| Shares | 모든 공유 링크 조회 및 관리 |
| Files | 파일 브라우저 (폴더 생성, 삭제, 공유 링크 생성) |

## API 엔드포인트

### WebDAV

| Method | 경로 | 설명 |
|--------|------|------|
| OPTIONS | /* | 지원 메서드 조회 |
| PROPFIND | /* | 파일/폴더 목록 조회 |
| GET | /* | 파일 다운로드 |
| PUT | /* | 파일 업로드 |
| DELETE | /* | 파일/폴더 삭제 |
| MKCOL | /* | 폴더 생성 |

### 공유 링크

| Method | 경로 | 설명 |
|--------|------|------|
| POST | /api/shares | 공유 링크 생성 |
| GET | /api/shares | 내 공유 링크 목록 |
| DELETE | /api/shares/{id} | 공유 링크 삭제 |
| GET | /s/{token} | 공유 링크 접근 |

### 관리자 API

| Method | 경로 | 설명 |
|--------|------|------|
| GET/POST/PUT/DELETE | /api/admin/users | 사용자 관리 |
| GET/POST/PUT/DELETE | /api/admin/permissions | 권한 관리 |
| GET/POST/DELETE | /api/admin/files | 파일 관리 |
| GET/DELETE | /api/admin/shares | 공유 링크 관리 |

## 권한 시스템

### 권한 종류

| 권한 | WebDAV 메서드 | 설명 |
|------|---------------|------|
| LIST | PROPFIND | 디렉토리 목록 조회 |
| READ | GET | 파일 다운로드 |
| WRITE | PUT | 파일 업로드/수정 |
| DELETE | DELETE | 파일/폴더 삭제 |
| MKCOL | MKCOL | 폴더 생성 |

### 권한 평가 규칙

1. 더 구체적인 경로의 규칙이 우선 적용
2. `deny` 규칙이 `allow`보다 우선
3. 기본 정책은 거부 (명시적 허용 필요)

### 예시

```
사용자: user1
규칙 1: /documents → READ, WRITE
규칙 2: /documents/private → DENY

결과:
- /documents/readme.txt → 읽기/쓰기 가능
- /documents/private/secret.txt → 접근 불가
```

## 공유 링크

### 기능

- 만료 시간 설정 (1시간, 24시간, 7일, 30일, 무제한)
- 비밀번호 보호
- 최대 접근 횟수 제한
- 읽기/쓰기 권한 설정

### 생성 예시

```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"path":"/documents/report.pdf","expiresInHours":24}' \
  http://localhost:8080/api/shares
```

응답:
```json
{
  "id": 1,
  "token": "abc123...",
  "url": "http://localhost:8080/s/abc123...",
  "expiresAt": "2026-01-21T10:00:00"
}
```

## 프로젝트 구조

```
src/main/kotlin/
├── Application.kt          # 메인 진입점
├── admin/                   # 관리자 핸들러
│   ├── AdminAuthorization.kt
│   ├── FileAdminHandler.kt
│   ├── PermissionAdminHandler.kt
│   ├── ShareAdminHandler.kt
│   └── UserAdminHandler.kt
├── config/                  # 설정
├── db/                      # 데이터베이스
│   ├── repositories/
│   └── tables/
├── path/                    # 경로 처리
├── routes/                  # 라우팅
├── share/                   # 공유 링크
├── storage/                 # 파일 시스템
└── webdav/                  # WebDAV 핸들러

src/main/resources/
└── admin/
    └── index.html          # 관리자 대시보드 UI
```

## 빌드

```bash
# 빌드
./gradlew build

# 배포용 아카이브 생성
./gradlew distZip
```

`build/distributions/` 디렉토리에 배포용 zip 파일이 생성됩니다.

## 라이선스

MIT License
