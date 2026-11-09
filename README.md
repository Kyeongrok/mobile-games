# 게임

YoutubeMp3 안드로이드 앱에서 분리한 게임 모음입니다. Android 10 이상, Kotlin + Jetpack Compose.

- 스도쿠 (난이도별 생성, 메모, 기록)
- 상하이 (마작 패 맞추기, 힌트·섞기)
- 프리셀 (실행취소, 두 번 눌러 프리셀로)
- 루미큐브 (컴퓨터 1~3명 상대, 튜토리얼·연습)
- 스플렌더 (컴퓨터 상대)
- 리치마작 (4인, 컴퓨터 3명 상대)
- 원소 짝맞추기 (뒤집힌 카드에서 원소기호와 이름 짝 찾기, 범위·카드 수 선택)
- 벽돌깨기 (끌어서 조준하고 공 여러 개를 쏘아 벽돌 깨기, 턴마다 한 줄씩 내려옴)

진행 중인 판은 자동 저장되어 다시 켜면 이어서 합니다.

홈 위 ⚠ 버튼(또는 설정의 「버그 리포트」)으로 버그를 적어 보내면 앱 버전 · 기기 · 마지막 게임 · 오류 기록 끝이 함께 갑니다. 받는 곳은 [`tools/bug-worker`](tools/bug-worker/README.md)(Cloudflare Worker + D1)입니다.

## 빌드

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 리치마작 점수 계산 테스트
```

JDK 17이 필요합니다(GraalVM의 jlink로는 빌드가 실패하니 Corretto/Temurin 등을 쓰세요).
릴리즈 서명은 환경변수 `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASS`(, `ANDROID_KEY_ALIAS`, 기본 `games`)로 넘깁니다.

## 릴리즈

`v1.0.0`처럼 `v`로 시작하는 태그를 올리면 GitHub Actions가 APK를 빌드해 [Releases](../../releases)에 `Games-v1.0.0.apk`로 올립니다.
`main`에 올리면 빌드·테스트만 하고 APK는 실행 결과의 아티팩트로 남깁니다.

```sh
git tag v1.0.0 && git push origin v1.0.0
```

폰에서 지우지 않고 업데이트하려면 저장소 Secrets에 `ANDROID_KEYSTORE_B64`(키스토어 base64), `ANDROID_KEYSTORE_PASS`, (선택) `ANDROID_KEY_ALIAS`를 넣어 늘 같은 키로 서명해야 합니다.
