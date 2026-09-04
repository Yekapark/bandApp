# 밴듈 (Bandule) 브랜드 자산

"Stick Check" — 드럼스틱 두 개로 그린 체크 표시. 합주를 확인·기록하는 앱이라는 뜻.

| 파일 | 용도 |
|---|---|
| `logo-mark.svg` | 컬러 마크(60×60). 앱 내부·문서용 |
| `logo-mono.svg` | 단색 마크. `currentColor` 를 따라가므로 어디에나 얹을 수 있다 |
| `app-icon-1024.svg` | 스토어용 앱 아이콘 원본(배경 그라디언트 포함) |
| `app-icon-1024.png` | 위를 래스터라이즈한 것. 스토어 제출용 |

## 색

| | |
|---|---|
| 주 색 | `#FF6A2B` (`AppColors.primary` 와 동일) |
| 보조 | `#A06BFF` / 끝점 `#FF8B5C`, `#C8AAFF` |
| 아이콘 배경 | `#2A1420` → `#0E0E13` 대각 그라디언트 |

## 리소스 다시 만들기

안드로이드 mipmap·적응형 전경·웹 아이콘은 이 SVG 에서 생성한 것이다.
도형이 선 2개 + 원 2개뿐이라 SVG 파서를 들이지 않고 Pillow 로 같은 도형을 그린다.
생성 스크립트는 `tools/render_icons.py`.

```bash
python tools/render_icons.py
```
