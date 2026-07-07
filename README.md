Minecraft Paper 서버에서 그림판 기능을 제공하는 플러그인입니다.

## 목차

- [주요 기능](#주요-기능)
- [스크린샷](#스크린샷)
- [요구 사항](#요구-사항)
- [플레이어 사용법](#플레이어-사용법)
- [도구](#도구)
- [색상 팔레트](#색상-팔레트)
- [저장과 갤러리](#저장과-갤러리)
- [전시](#전시)
- [운영 모드](#운영-모드)
- [명령어](#명령어)
- [권한](#권한)
- [설정](#설정)
- [저장 구조](#저장-구조)
- [개발자 API](#개발자-api)

## 주요 기능

- 지도 기반 캔버스 생성 및 그림 그리기
- 연필, 지우개, 스프레이, 페인트 통, 스포이드, 도형 도구 제공
- RGB 색상 팔레트와 브러시 크기 조절
- 레이어 기반 편집 데이터 저장
- 그림 PNG로 저장, 내 갤러리에서 다시 불러오기
- 저장한 그림을 벽, 바닥, 천장에 전시
- 자유 설치 모드와 관리자 지정 수동 스테이션 모드 지원

## 스크린샷

### 메인 메뉴

<img width="2560" height="1440" alt="2026-07-01_01 30 32" src="https://github.com/user-attachments/assets/869b9384-b38d-4120-a5ec-dc8a5bea4542" />

### 캔버스 설치 프리뷰

<img width="2560" height="1440" alt="2026-07-01_01 31 22" src="https://github.com/user-attachments/assets/a4740925-78af-49ec-b94d-43f2a255f298" />
<img width="2560" height="1440" alt="2026-07-01_01 31 52" src="https://github.com/user-attachments/assets/c08d6665-b5ed-4850-9472-17730ea0c10d" />

### 그림 그리기

<img width="2560" height="1440" alt="2026-07-01_01 37 00" src="https://github.com/user-attachments/assets/260136a9-5e8e-4231-8b23-9b3a499db87d" />

### 색상 팔레트

<img width="2560" height="1440" alt="2026-07-01_01 37 57" src="https://github.com/user-attachments/assets/a3185aa1-a8e9-4f46-bec8-bea0864bc908" />

### 갤러리

<img width="2560" height="1440" alt="2026-07-01_01 38 37" src="https://github.com/user-attachments/assets/8ac488bc-2969-4434-8817-a10bc00ad75b" />

### 작품 전시

<img width="2560" height="1440" alt="2026-07-01_01 39 47" src="https://github.com/user-attachments/assets/3e64383a-3e4d-466d-8d8e-eb8ce8a55790" />
<img width="2560" height="1440" alt="2026-07-01_01 40 00" src="https://github.com/user-attachments/assets/78e8fd06-68ce-4685-a0d4-57e20b7117a5" />
<img width="2560" height="1440" alt="2026-07-01_01 40 15" src="https://github.com/user-attachments/assets/6b1894a1-c1fd-43e2-9c32-8e70711ca629" />

### display-rgb-mode: true일 때의 전시 상태 (쉐이더 상태에서 동작 X)
<img width="854" height="480" alt="image" src="https://github.com/user-attachments/assets/879f23f9-265a-4196-9959-d927809ce43c" />
<img width="854" height="480" alt="image" src="https://github.com/user-attachments/assets/0a67ecc8-ef55-4320-b8e8-29ed64e90fd0" />

### display-rgb-mode: false일 때의 전시 상태 (쉐이더 상태에서 동작 O)
<img width="854" height="480" alt="image" src="https://github.com/user-attachments/assets/2c0a1adb-3cae-4ecf-9cb0-6ea02c4fa9ee" />
<img width="854" height="480" alt="image" src="https://github.com/user-attachments/assets/b999ba54-ae4b-4ae0-81ec-a762cd5a09d4" />

## 요구 사항

| 항목 | 요구 사항 |
| --- | --- |
| 서버 | Paper 1.21 이상 |
| 플러그인 파일 | `Paint.jar` |

## 플레이어 사용법

일반 플레이어는 기본적으로 `/paint`만 사용합니다.

```text
/paint
```

`/paint`를 입력하면 Paint 메인 메뉴가 열립니다. 메뉴에서 새 캔버스, 내 갤러리, 그림 전시 같은 기능을 선택할 수 있습니다.

기본 흐름은 다음과 같습니다.

1. `/paint`로 메인 메뉴를 엽니다.
2. 새 캔버스를 선택합니다.
3. 보이는 프리뷰를 확인하고 원하는 위치에 캔버스를 설치합니다.
4. 지급된 도구를 사용해 그림을 그립니다.
5. 색상 팔레트로 색상과 브러시 크기를 조절합니다.
6. 저장 버튼으로 그림을 저장합니다.
7. 갤러리에서 저장한 그림을 다시 열거나 전시합니다.

도구를 들고 있을 때 `F` 키를 누르면 기본 도구와 고급 도구가 전환됩니다.

## 도구

모든 Paint 도구 아이템은 방패로 지급됩니다. 도구는 기본 도구와 고급 도구로 나뉩니다.

### 기본 도구

| 슬롯 | 도구 | 설명 |
| --- | --- | --- |
| 1 | 색상 팔레트 | 색상과 브러시 크기를 선택합니다. |
| 2 | 연필 | 우클릭을 누르고 있으면 현재 색상으로 그립니다. |
| 3 | 지우개 | 우클릭을 누르고 있으면 픽셀을 지웁니다. |
| 4 | 스프레이 | 우클릭을 누르고 있으면 흩뿌리듯 그립니다. |
| 5 | 페인트 통 | 닫힌 영역을 현재 색상으로 채웁니다. |
| 6 | 스포이드 | 캔버스의 색상을 가져옵니다. |
| 7 | 전체 지우기 | 내 캔버스를 비웁니다. |
| 8 | 실행 취소 | 이전 작업을 되돌립니다. |
| 9 | 다시 실행 | 되돌린 작업을 다시 실행합니다. |

### 고급 도구

| 슬롯 | 도구 | 설명 |
| --- | --- | --- |
| 1 | 직선 | 드래그해서 직선을 그립니다. |
| 2 | 사각형 | 드래그해서 사각형 테두리를 그립니다. |
| 3 | 채운 사각형 | 드래그해서 사각형을 채웁니다. |
| 4 | 삼각형 | 드래그해서 삼각형 테두리를 그립니다. |
| 5 | 채운 삼각형 | 드래그해서 삼각형을 채웁니다. |
| 6 | 원/타원 | 드래그해서 원 또는 타원 테두리를 그립니다. |
| 7 | 채운 원/타원 | 드래그해서 원 또는 타원을 채웁니다. |
| 8 | 선택 이동 | 영역을 선택한 뒤 다시 드래그해서 옮깁니다. |

## 색상 팔레트

Paint의 팔레트는 지도 화면으로 열립니다. 기본 팔레트 모드는 RGB입니다.

- 팔레트 도구를 사용하면 색상 팔레트를 열 수 있습니다.
- 팔레트 설치가 필요한 상황에서는 캔버스 설치처럼 프리뷰가 먼저 표시됩니다.
- 팔레트 프리뷰는 플레이어 기준 5칸 앞에 표시됩니다.

## 저장과 갤러리

그림은 PNG 이미지와 편집용 레이어 데이터로 저장됩니다.

저장된 그림은 갤러리에서 확인할 수 있습니다.

- 내 그림 목록 조회
- 그림 검색
- 저장한 그림 다시 불러오기
- 그림 전시
- 그림 삭제

갤러리에서 그림을 삭제할 때는 실수 방지를 위해 삭제 버튼을 두 번 눌러야 합니다.

## 전시

저장한 그림은 월드에 전시할 수 있습니다.

- 벽, 바닥, 천장에 전시할 수 있습니다.
- 설치 가능한 상태는 파란색 프리뷰로 표시됩니다.
- 설치 불가능한 상태는 빨간색 프리뷰로 표시됩니다.
- 기본 전시 거리는 10칸입니다.
- `map-render.display-rgb-mode`가 `true`이면 리소스팩 기반 RGB 표현을 사용합니다.
- `map-render.display-rgb-mode`가 `false`이면 Oklab 기반으로 마인크래프트 기본 지도 색상에 맞춰 전시합니다.

전시품 제거 모드는 다음 명령어로 시작합니다.

```text
/paint exhibits remove
```

일반 플레이어는 자신이 만든 전시품만 삭제할 수 있고, `paint.admin` 권한이 있는 플레이어는 모든 전시품을 삭제할 수 있습니다.

## 운영 모드

Paint는 자유 모드와 수동 모드를 지원합니다.

### 자유 모드

`config.yml`에서 `free-mode: true`일 때 사용하는 기본 모드입니다.

```yaml
free-mode: true
```

자유 모드에서는 플레이어가 `/paint` 메뉴를 열고 직접 캔버스, 팔레트, 전시 위치를 설치합니다.

### 수동 모드

`config.yml`에서 `free-mode: false`일 때 사용하는 모드입니다.

```yaml
free-mode: false
```

수동 모드에서는 관리자가 미리 캔버스, 갤러리, 조작판 위치를 지정합니다. 일반 플레이어는 관리자가 설치한 자리에서만 그림을 그리고 저장합니다.

이 때, 캔버스 위치 정보는 `plugins/Paint/manual-stations.yml`에 저장됩니다.

## 명령어

일반 플레이어는 기본적으로 `/paint`만 사용할 수 있습니다. 아래 관리자 명령어는 `paint.admin` 권한이 필요합니다.

| 명령어 | 설명 |
| --- | --- |
| `/paint` | Paint 메인 메뉴를 엽니다. 이미 열려 있으면 현재 위치로 다시 소환합니다. |
| `/paint new [너비] [높이]` | 새 캔버스 설치 프리뷰를 시작합니다. |
| `/paint clear` | 현재 캔버스를 비웁니다. |
| `/paint remove` | 현재 캔버스를 제거합니다. |
| `/paint save [이름]` | 현재 캔버스를 PNG 그림으로 저장합니다. |
| `/paint list` | 저장한 그림 목록을 엽니다. |
| `/paint show` | 저장한 그림 전시 흐름을 시작합니다. |
| `/paint gallery <플레이어명>` | 해당 플레이어의 갤러리를 엽니다. |
| `/paint brush <크기>` | 브러시 반지름을 변경합니다. |
| `/paint color <색상>` | 등록된 팔레트 색상으로 현재 색상을 바꿉니다. |
| `/paint exhibits reload` | 전시품 데이터를 다시 불러옵니다. |
| `/paint exhibits remove` | 바라보고 있는 전시품을 제거하는 모드에 들어갑니다. |

### 수동 자리 지정 명령어

| 명령어 | 설명 |
| --- | --- |
| `/paint station canvas <번호> <너비> <높이>` | 해당 번호의 캔버스 설치 프리뷰를 시작합니다. |
| `/paint station gallery <번호>` | 해당 번호의 갤러리 설치 프리뷰를 시작합니다. |
| `/paint station control <번호> [horizontal|vertical]` | 해당 번호의 조작판 설치 프리뷰를 시작합니다. |
| `/paint station list` | 등록된 수동 자리 목록을 봅니다. |
| `/paint station remove <번호>` | 해당 번호의 수동 자리를 삭제하고 설치된 캔버스 블록은 복구합니다. |

캔버스 크기는 명령어 기준 1x1부터 10x10까지 지정할 수 있습니다.

## 권한

| 권한 | 기본값 | 설명 |
| --- | --- | --- |
| `paint.use` | 모든 유저 | `/paint` 사용 권한입니다. |
| `paint.admin` | OP | 관리자 명령어, 수동 자리 설정, 다른 플레이어 갤러리 조회 권한입니다. |

## 설정

기본 설정 파일은 `plugins/Paint/config.yml`입니다.

```yaml
map-render:
  canvas-rgb-mode: true
  display-rgb-mode: false

free-mode: true
```

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `map-render.canvas-rgb-mode` | `true` | 그림을 그리는 캔버스에서 리소스팩 기반 RGB 색상 표현을 사용합니다. |
| `map-render.display-rgb-mode` | `false` | 저장한 그림을 전시할 때 리소스팩 기반 RGB 색상 표현을 사용합니다. `false`면 Oklab 기반 기본 지도 색상으로 변환합니다. |
| `free-mode` | `true` | `true`면 자유 설치 모드, `false`면 수동 모드입니다. |

## 저장 구조

플러그인 데이터는 서버의 `plugins/Paint` 아래에 저장됩니다.

```text
plugins/Paint/
├─ config.yml
├─ exhibits.yml
├─ manual-stations.yml
└─ artworks/
   ├─ metadata/
   │  └─ <플레이어명>-<UUID>.yml
   └─ images/
      ├─ <플레이어명>-<UUID>/
      │  └─ <그림>.png
      └─ layers/
         └─ <플레이어명>-<UUID>/
            └─ <그림>.layers.dat
```

| 경로 | 설명 |
| --- | --- |
| `config.yml` | 플러그인 기본 설정입니다. |
| `exhibits.yml` | 전시품 위치, 방향, 테두리 정보입니다. |
| `manual-stations.yml` | 수동 자리 모드의 캔버스, 갤러리, 조작판 위치입니다. |
| `artworks/metadata` | 그림 이름, 작가, 크기, 이미지 경로 같은 메타데이터입니다. |
| `artworks/images` | 실제 PNG 그림 파일입니다. |
| `artworks/images/layers` | 편집 복원을 위한 레이어 데이터입니다. |

## 개발자 API

다른 플러그인은 서버에 설치된 Paint 플러그인을 찾아 `PaintService`를 사용할 수 있습니다.

```text
Paint paint = (Paint) Bukkit.getPluginManager().getPlugin("Paint");
PaintService paintService = paint.paintService();
```

### 캔버스 생성

```text
paintService.createCanvas(
        player.getUniqueId(),
        origin,
        facing,
        right,
        5,
        5
);
```

### 캔버스 제거와 초기화

```text
paintService.removeCanvas(player.getUniqueId());
paintService.clearCanvas(player.getUniqueId());
```

### 색상과 브러시

```text
paintService.selectColor(player.getUniqueId(), Color.RED);
paintService.selectedColor(player.getUniqueId());
paintService.brushRadius(player.getUniqueId());
```

### 캔버스 저장

```text
paintService.saveCanvas(player.getUniqueId(), "Round 1");
```

### 이미지 데이터로 그림 저장

```text
paintService.saveArtwork(
        player.getUniqueId(),
        player.getName(),
        "Imported Image",
        width,
        height,
        pixels
);
```

### 유저 그림 목록 조회

```text
List<PaintArtwork> artworks = paintService.artworks(player.getUniqueId());
```

### 그림 전시

```text
paintService.displayArtwork(
        artworkId,
        origin,
        facing,
        right,
        up,
        5,
        5
);
```

### 테두리 포함 전시

```text
paintService.displayArtwork(
        artworkId,
        origin,
        facing,
        right,
        up,
        5,
        5,
        Material.DARK_OAK_PLANKS
);
```

### 전시 제거

```text
paintService.removeExhibit(exhibitId);
```
