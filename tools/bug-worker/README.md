# 버그 리포트 받는 곳 (Cloudflare Worker + D1)

앱의 「버그 리포트」(홈 위 ⚠ 버튼 · 설정의 「버그 리포트」)가 글 쓰는 창을 띄우고, 쓴 글을 여기로 보낸다(`app/src/main/java/com/kyeongrok/games/BugReport.kt`).
못 보내면 앱 데이터의 `bug-queue` 에 두었다가 다음에 켤 때 다시 보낸다. 용의기사2 리메이크(`fd2/fd2-remake/tools/bug-worker`)의 `/v1/bug` 와 같은 꼴이다.

- 주소: `https://mobile-games.oceanfog1.workers.dev`
- `POST /v1/bug` — `{ v: 1, install, version, text, context }`. 글 8000자 · context 32KB · 몸 48KB 까지. 설치 하나가 한 시간에 10 건(모두 합쳐 300 건)을 넘으면 429. `{ ok, id }`.
- `GET /v1/bugs?since=<id>&status=new&limit=100` — 관리자만(`Authorization: Bearer <BUG_ADMIN_KEY>`). `status=all` 이면 모두.
- `POST /v1/bugs/<id>/status` `{ status, note }` — 관리자만. status 는 new · seen · fixed · wontfix · dup …

`context` 에 앱이 붙이는 것: 앱 버전 · Android 버전 · 기기 이름 · 화면 크기(dp) · 테마 · 지금 화면 · 마지막으로 한 게임 ·
오류 기록(앱이 죽을 때 남긴 `crash.txt`)의 끝 100줄. 이름 · 계정 · IP 는 적지 않는다.

## 보기

```
./bugs.ps1                              # 새(new) 리포트
./bugs.ps1 -Status all                  # 모두
./bugs.ps1 -Id 3                        # 하나를 자세히(오류 기록 끝까지)
./bugs.ps1 -Mark 3 -Status fixed -Note "v1.0.4 에서 고침"
```

관리자 열쇠는 `~\.mobile-games-bug-admin-key` 의 첫 줄(worker 비밀 `BUG_ADMIN_KEY` 와 같은 값). 저장소에는 넣지 않는다.

## 올리기(처음 한 번)

D1 은 만들어 두었다(`database_id` 는 `wrangler.toml`).

```
cd tools/bug-worker
npx wrangler d1 execute mobile-games --remote --file=schema.sql
npx wrangler secret put BUG_ADMIN_KEY                             # ~\.mobile-games-bug-admin-key 와 같은 값
npx wrangler deploy
```

고친 뒤에는 `npx wrangler deploy` 만 다시.
