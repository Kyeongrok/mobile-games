# 게임

YoutubeMp3 안드로이드 앱에서 분리한 게임 모음입니다. Android 10 이상, Kotlin + Jetpack Compose.

- 스도쿠 (난이도별 생성, 메모, 기록)
- 상하이 (마작 패 맞추기, 힌트·섞기)
- 프리셀 (실행취소, 두 번 눌러 프리셀로)
- 루미큐브 (컴퓨터 1~3명 상대, 튜토리얼·연습)
- 스플렌더 (컴퓨터 상대)
- 리치마작 (4인, 컴퓨터 3명 상대)

진행 중인 판은 자동 저장되어 다시 켜면 이어서 합니다.

## 빌드

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 리치마작 점수 계산 테스트
```

JDK 17이 필요합니다(GraalVM의 jlink로는 빌드가 실패하니 Corretto/Temurin 등을 쓰세요).
릴리즈 서명은 환경변수 `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASS`(, `ANDROID_KEY_ALIAS`, 기본 `games`)로 넘깁니다.
