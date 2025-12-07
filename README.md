# KTB-Community - 곽소리
------

## Back-end 소개

- 할말 못할말 하고싶은 말 하고 서로의 생각과 고민을 공유하는 커뮤니티 곽소리입니다.
- Vanila JS를 이용해서 구현했고 Express를 사용해서 웹서빙을 합니다.
- 기능 부터 화면까지 모두 직접 구현했습니다.

## 개발 인원 및 기간
- 개발 기간 : 2025-09-15 ~ 2025-12-07
- 개발 인원 : FE/BE 1명 (곽희상)

## 📚 사용 기술

- Java

- Database
  - MySQL (RDS)
  - Redis (캐싱 및 세션 관리)

- Template Engine
  - Thymeleaf

    
- Cloud Platform (AWS)
  - ECR (Public Registry) - 컨테이너 이미지 저장소
  - EC2 - 애플리케이션 서버
  - S3 - 이미지 파일 저장
  - RDS (MySQL) - 데이터베이스
  - ElastiCache (Redis) - 캐싱

- CI/CD
  - GitHub Actions
  - AWS Systems Manager (SSM) - 자동 배포
  - 자동화된 테스트 → 빌드 → 푸시 → 배포 파이프라인

## DB 구성

<img width="1405" height="578" alt="image" src="https://github.com/user-attachments/assets/5d9d9688-d9ba-48e1-b250-6b3e7483a293" />

## 인프라 구성도
<img width="500" height="600" alt="커뮤니티 배포 drawio (5)" src="https://github.com/user-attachments/assets/40e0b628-154f-449d-ae73-d80bc93a2283" />


## 주요 컴포넌트

1. Controller Layer
- AuthController - 인증/인가
- UserController - 회원 관리
- PostController - 게시글 CRUD
- ImageController - 이미지 업로드/조회
- HealthController - 헬스체크
- ViewController - 뷰 렌더링

2. Service Layer
- AuthService - 인증 로직
- UserService - 회원 관리
- PostService - 게시글 비즈니스 로직
- CommentService - 댓글 관리
- LikeService - 좋아요 기능
- ImageService, ImageValidationService - 이미지 처리
- S3Service - AWS S3 연동
- RefreshTokenService - 토큰 갱신
- RedisSingleDataService - Redis 캐싱

3. Repository Layer
- UserRepository
- PostRepository
- CommentRepository
- LikeRepository
- ImageRepository
- RefreshRepository
- CountRepository

4. Entity
- User - 사용자
- Post - 게시글
- Comment - 댓글
- Like - 좋아요
- Image - 이미지
- Refresh - 리프레시 토큰
- Count - 조회수

5. Security & Middleware
- SecurityConfig - Spring Security 설정
- JwtAuthenticationFilter - JWT 필터
- JwtUtil - JWT 유틸리티
- GlobalExceptionHandler - 전역 예외 처리

6. Configuration
- RedisConfig - Redis 설정
- S3Config - AWS S3 설정
- SecurityConfig - 보안 설정

## 트러블 슈팅
작성중 ..

## 프로젝트 회고

작성중..



