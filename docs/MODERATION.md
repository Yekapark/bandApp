# 신고가 들어왔을 때

> 신고 접수 푸시를 받은 뒤 무엇을 확인하고 무엇을 할 수 있는지.
> 마지막 갱신: **2026-09-06**

---

## 0. 먼저 알아야 할 것 — 이 앱은 열린 광장이 아니다

밴드는 **초대코드로만** 들어온다. 모르는 사람이 남의 밴드 글을 볼 수 없고, 검색으로
찾아지지도 않는다. 그래서 신고는 대부분 **서로 아는 사람 사이에서** 나온다.

이 점이 조치 방법을 정한다. **낯선 사람의 악의적 도배가 아니라 밴드 안의 다툼**이라면,
계정 정지 같은 큰 수단보다 **밴드장이 정리하는 것**이 맞고 실제로 그게 가능하다.

---

## 1. 알림을 받으면 — 내용 확인

푸시에 신고 번호가 찍힌다. 그 번호로 본다.

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 "cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres psql -U bandapp -d bandapp -c \"SELECT id, target_type, target_id, reason, status, created_at FROM reports WHERE status='OPEN' ORDER BY created_at DESC LIMIT 20;\""
```

`target_type` 이 `POST` 면 그 글의 내용까지 본다 (`target_id` 를 아래 `?` 에):

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 "cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres psql -U bandapp -d bandapp -c \"SELECT p.id, p.band_id, b.name AS band, u.name AS author, p.content, p.created_at FROM board_posts p JOIN bands b ON b.id=p.band_id JOIN users u ON u.id=p.author_id WHERE p.id=?;\""
```

> **여기서 남의 글을 읽는다.** 신고 처리에 필요한 최소한만 보고, 다른 목적으로 쓰지 않는다.
> 개인정보처리방침에 "신고 처리를 위해 확인할 수 있다" 는 취지가 들어가야 한다.

---

## 2. 할 수 있는 일 — 그리고 없는 것

| | 지금 되나 |
|---|---|
| 이용자가 상대를 **차단** (서로 글이 안 보임) | ✅ 앱에서. **즉시 효력, 가장 빠른 해결** |
| **밴드장**이 글 삭제 | ✅ 앱에서 |
| **밴드장**이 멤버 내보내기 | ✅ 앱에서 |
| 운영자가 남의 밴드 글 삭제 | ❌ **앱으로는 안 된다** — 그 밴드 멤버가 아니라 API 가 막는다 |
| 계정 정지 | ❌ **기능이 없다** — 테이블에 그런 상태가 없다 |

**즉 운영자가 앱으로 할 수 있는 일은 없다.** 남은 건 아래 둘이다.

### (가) 밴드장에게 연락한다 — 기본값

대부분의 신고는 이쪽이 맞다. 밴드장은 글을 지우고 멤버를 내보낼 수 있고, **밴드 안의
사정을 우리보다 잘 안다.** 신고자에게도 "차단하면 즉시 안 보인다" 를 안내한다.

### (나) DB 에서 직접 — 불법 촬영물·명백한 범죄 등 급한 경우만

**되돌리기 어렵다. 확인한 뒤에만 한다.**

```sql
-- 게시글 감추기 (soft delete — 데이터는 남고 앱에서만 안 보인다)
UPDATE board_posts SET deleted_at = now() WHERE id = ?;

-- 처리 완료 표시
UPDATE reports SET status = 'RESOLVED' WHERE id = ?;
```

> 첨부 파일은 위 SQL 로 지워지지 않는다. 저장소에서 실제로 없애야 하면 R2 에서 따로 지운다.

**계정 정지는 흉내조차 내지 말 것.** `users.deleted_at` 을 손으로 채우면 탈퇴 처리로
취급돼 90일 뒤 개인정보가 파기된다. 정지가 아니라 **탈퇴**가 된다.

---

## 3. 처리 후

신고를 `RESOLVED` 로 바꾼다. **안 바꾸면 다음에 볼 때 또 목록에 나온다.**

```sql
UPDATE reports SET status = 'RESOLVED' WHERE id = ?;
```

---

## 4. 언제 더 만들까

지금은 **신고가 실제로 들어오지도 않는 단계**다. 운영자 도구를 미리 만드는 것은 이르다.

만들 때가 되는 신호:

- 신고가 **주 단위로** 들어온다 → 신고 목록 화면
- 밴드장이 손쓸 수 없는 신고가 나온다(밴드장이 가해자 등) → 운영자 강제 삭제 API
- 같은 사람이 여러 밴드에서 반복 신고된다 → **그때 계정 정지를 설계한다**

마지막 것은 설계가 필요하다 — 정지 기간, 본인 고지, 이의 절차, 정지 중 데이터 취급.
**약관에 근거 조항도 함께 있어야 한다.** 대충 만들면 나중에 분쟁의 원인이 된다.
