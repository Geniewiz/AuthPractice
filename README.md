# authPractice: Spring Boot JWT Authentication Example

## 1. 프로젝트 개요

이 프로젝트는 Spring Boot와 Spring Security, JWT(JSON Web Token)를 사용하여 안전한 인증 시스템을 구현한 예제 애플리케이션입니다. 사용자 회원가입, 로그인, 로그아웃 및 토큰 기반의 API 인증 흐름을 학습하고 실습하는 것을 목표로 합니다.

## 2. 핵심 기능

- **회원가입**: 이메일과 비밀번호를 사용한 사용자 등록 기능.
- **로그인**: 인증 성공 시, Access Token과 Refresh Token을 발급.
- **API 인증**: Access Token을 사용하여 보호된 API에 대한 접근 제어.
- **토큰 재발급**: 만료된 Access Token을 Refresh Token을 사용하여 갱신 (Refresh Token Rotation 적용).
- **로그아웃**: 서버에 저장된 Refresh Token을 무효화하여 세션을 종료.
- **API 문서화**: Swagger (Springdoc OpenAPI)를 이용한 API 명세 자동화.

## 3. 사용 기술

- **언어**: Java 17
- **프레임워크**: Spring Boot 3.4.0
- **보안**: Spring Security
- **데이터베이스**: MySQL, Spring Data JPA
- **인증**: JSON Web Token (JWT) - `jjwt` 라이브러리 사용
- **테스팅**: JUnit 5, Testcontainers (MySQL 연동 테스트 자동화)
- **API 문서화**: Springdoc OpenAPI (Swagger UI)
- **빌드 도구**: Gradle

## 4. 인증 흐름 상세

### 1) 회원가입

- **Endpoint**: `POST /auth/signup`
- 사용자는 `email`과 `password`를 제출하여 회원가입을 요청합니다.
- 서버는 `BCryptPasswordEncoder`를 사용하여 비밀번호를 안전하게 해싱하여 데이터베이스에 저장합니다.
- 이메일은 정규화(소문자 변환, 공백 제거) 과정을 거쳐 중복 가입을 방지합니다.

### 2) 로그인 및 토큰 발급

- **Endpoint**: `POST /auth/login`
- 사용자는 `email`과 `password`로 로그인을 요청합니다.
- 서버는 제출된 비밀번호와 데이터베이스에 저장된 해시를 비교하여 자격 증명을 검증합니다.
- 인증에 성공하면, 서버는 다음과 같은 두 종류의 토큰을 생성하여 클라이언트에게 전달합니다.
  - **Access Token**: 수명이 짧으며(예: 15분), API 요청 시 `Authorization` 헤더에 담아 전송됩니다.
  - **Refresh Token**: 수명이 길며(예: 7일), Access Token이 만료되었을 때 새 토큰을 발급받기 위해 사용됩니다. 보안을 위해 DB에 해싱하여 저장됩니다.

### 3) API 접근 (Access Token 사용)

- 클라이언트는 보호된 API를 호출할 때 HTTP 헤더에 Access Token을 포함하여 전송합니다.
  ```
  Authorization: Bearer <Access-Token>
  ```
- 서버의 `JwtAuthFilter`는 모든 요청을 가로채 토큰의 유효성(서명, 만료 시간 등)을 검증합니다.
- 토큰이 유효하면 `SecurityContextHolder`에 인증 정보를 설정하여 해당 요청이 인증된 사용자의 요청임을 시스템에 알립니다.

### 4) 토큰 재발급 (Refresh Token Rotation)

- **Endpoint**: `POST /auth/refresh`
- Access Token이 만료되면, 클라이언트는 보관하고 있던 Refresh Token을 서버로 보내 새 Access Token 발급을 요청합니다.
- 서버는 전달받은 Refresh Token이 데이터베이스에 저장된 토큰과 일치하며 유효한지 확인합니다.
- **Refresh Token Rotation (RTR)** 전략을 채택하여 보안을 강화했습니다.
  - 기존 Refresh Token은 재사용이 불가능하도록 즉시 무효화(또는 삭제)됩니다.
  - 새로운 Access Token과 **새로운 Refresh Token**이 함께 발급됩니다.
  - 이 방식은 Refresh Token이 탈취되더라도 공격자가 토큰을 무한정 사용하는 것을 방지합니다.

### 5) 로그아웃

- **Endpoint**: `POST /auth/logout`
- 클라이언트가 로그아웃을 요청하면, 현재 사용 중인 Refresh Token을 서버로 전송합니다.
- 서버는 데이터베이스에서 해당 Refresh Token을 찾아 'revoked'(무효화) 상태로 변경하여 더 이상 토큰 재발급에 사용할 수 없도록 만듭니다.

## 5. 데이터베이스 스키마

- **`users` 테이블**: 사용자 정보를 저장합니다.
  - `id`: 기본 키
  - `email`, `email_normalized`: 사용자 이메일 (중복 방지를 위해 정규화된 이메일 사용)
  - `password_hash`: BCrypt로 해싱된 비밀번호
  - `status`: 사용자 계정 상태 (e.g., ACTIVE, LOCKED)
- **`refresh_tokens` 테이블**: 발급된 Refresh Token 정보를 관리합니다.
  - `id`: 기본 키
  - `user_id`: 토큰 소유자 ID
  - `token_hash`: SHA-256 등으로 해싱된 Refresh Token 값 (원본 토큰을 저장하지 않음)
  - `expires_at`: 토큰 만료 시간
  - `revoked_at`: 토큰 무효화 시간 (로그아웃 또는 재발급 시 기록)

## 6. API 엔드포인트

자세한 명세는 애플리케이션 실행 후 `http://localhost:8080/swagger-ui/index.html`에서 확인할 수 있습니다.

- `POST /auth/signup`: 회원가입
- `POST /auth/login`: 로그인 (Access/Refresh Token 발급)
- `POST /auth/refresh`: Access Token 및 Refresh Token 재발급
- `POST /auth/logout`: 로그아웃 (Refresh Token 무효화)
- `GET /me`: 인증된 사용자의 정보 조회 (인증 테스트용)

## 7. 실행 및 테스트 방법

### 1) Docker를 이용한 실행

1.  프로젝트 루트 디렉토리에서 아래 명령어를 실행하여 MySQL 데이터베이스를 시작합니다.
    ```bash
    docker-compose up -d
    ```
2.  Spring Boot 애플리케이션을 실행합니다. `spring-boot-docker-compose` 의존성 덕분에 별도 설정 없이 `compose.yaml`에 정의된 서비스에 연결됩니다.

### 2) 테스트 실행

- 프로젝트의 모든 단위 및 통합 테스트를 실행합니다.
- Testcontainers가 자동으로 Docker를 사용하여 테스트용 MySQL 컨테이너를 실행하므로 별도의 DB 설정이 필요 없습니다.
  ```bash
  ./gradlew test
  ```
