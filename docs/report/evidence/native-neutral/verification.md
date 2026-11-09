# 폰트·중성 유리 바 검증

이 기록은 `8356b09` 시점의 이력입니다. 현재 규격과 렌더링 검증은 [후속 검증](../native-refined/verification.md)을 확인합니다.

2026-11-09. `e2efef0` 이후의 Pretendard 공통 폰트, 색 틴트를 제거한 유리 바, Apple HIG에서 참고한 글자 위계·중성 콘텐츠 표면을 확인했습니다. 검증 소스 app/ Git tree는 `011848763c6347b199374d3d08810da0b048b60a`, 최종 APK SHA-256은 `283ff7dda22c40dd6fdfff5dbba40c2039a1536e2836d880a8c7788d46c2e878`입니다. 계정·날씨 API 등 제품 연동 확인은 아닙니다.

## 폰트와 재질

[공식 Pretendard v1.3.9](https://github.com/orioncactus/pretendard/releases/tag/v1.3.9)의 수정하지 않은 OTF를 사용합니다. 아래 Git blob SHA를 공식 태그의 원본과 비교했고, 각 파일의 OS/2 weight metadata가 400·500·600·700인지 확인했습니다. 실행 중 폰트 다운로드 없이 앱에 포함하며 [OFL 원문](../../../../app/src/main/assets/licenses/pretendard-OFL.txt)을 APK assets에도 넣었습니다.

| Weight | 파일 | 원본 Git blob SHA |
| --- | --- | --- |
| 400 | `pretendard_400_data.otf` | `08bf4cfc2164a0bff74a4bf844128d3b843bbf87` |
| 500 | `pretendard_500_data.otf` | `057506983f0a8b0438fc5f24382db64497bf6eae` |
| 600 | `pretendard_600_data.otf` | `e7e36abc474796c8b6c383380ddd9f87655682ec` |
| 700 | `pretendard_700_data.otf` | `8e5e30a28b4435359945cf08b261c86d0de8da53` |

[타이포그래피 규격](../../../DESIGN_SYSTEM.md)에 따라 제목 34/41·600, 본문 17/24·400 또는 500, 설명 15/21·400, 보조 정보 13/18·400, 탭 12/16·500/600을 연결했습니다. 워드마크는 20/26·700입니다. 안내창 제목·본문·확인 버튼도 공통 폰트를 사용합니다.

유리 표면은 `#B8FFFFFF`이며 브랜드 색 틴트가 없습니다. 선택 탭은 중성 검정의 채운 아이콘과 600 라벨, 비선택은 회색 선형 아이콘과 500 라벨로 구분합니다. 기존 배경 전용 블러·입력 영역·하단 여백과 실제 패널 동작은 유지합니다. 본문은 중성 회색, 20dp 좌우 여백, 24dp 패널 모서리로 정리했습니다.

## 빌드와 검증 범위

macOS arm64 / Zulu JDK 17 / 저장소 Gradle Wrapper. 전용 read-only AVD, Android API 36.1 arm64, 기본 1080 × 2400 / 420dpi. 작은 화면 945 × 2100 / 420dpi(360dp), 글자 배율 200%, 가로 회전을 확인했습니다. 이전 기록과 같은 AVD의 부분 재그리기 설정을 false로 사용했습니다. [렌더링 환경의 한계](../native-glass/verification.md)를 함께 참고합니다.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

빌드·Lint 성공. Lint 0 errors / 15 warnings는 기존 도구·라이브러리 새 버전 안내입니다. `testDebugUnitTest`는 **NO-SOURCE**이며 제품 단위 테스트가 없는 상태입니다. XML 파싱·변경 문서 링크·`git diff --check`도 확인했습니다. APK 내부에서 4개 OTF와 font-family XML·라이선스 포함을 확인했습니다.

ADB와 UI Automator로 핸들 클릭·양방향 드래그, 접힌 목록의 위 스크롤 시 펼치기, 뒤로가기, 3탭 이동·패널 상태 유지, 화면 회전 후 상태·설명 복원, 안내창 표시·닫기, 360dp·200% 마지막 항목 접근을 확인했습니다. 마지막 안내창 표면의 중성색 조정 뒤에는 최종 APK에서 홈·패널·안내창을 다시 확인했습니다. 최종 안내창에서는 시스템 내비게이션 버튼도 어두운 아이콘으로 표시됩니다.

폰트 적용 중 안내창의 widget style 자리에 TextAppearance를 직접 지정해 layout_width 누락으로 열리지 않는 오류를 발견했습니다. 원래 Material dialog widget을 상속하고 그 안의 TextAppearance와 font-family를 바꾸는 방식으로 수정한 후 실제 열기·닫기를 재검증했습니다.

| 대비 조합 | 계산값 |
| --- | --- |
| 기본 / 선택 탭 | 8.58:1 |
| 기본 / 비선택 탭 | 5.52:1 |
| 눌림 / 선택 탭 | 7.60:1 |
| 눌림 / 비선택 탭 | 4.88:1 |
| 흰 Surface / 보조 글자 | 5.95:1 |
| 콘텐츠 배경 / 보조 글자 | 5.33:1 |

유리 바는 가장 어두운 검정 배경과 흰 표면·검정 ripple의 합성값을 기준으로 계산했습니다. 모두 일반 텍스트 목표 4.5:1 이상입니다. 실제 TalkBack 사용자 평가나 실기기 접근성 감사를 대신하지 않습니다.

## 실제 화면

| 이미지 | 조건 |
| --- | --- |
| [home-collapsed.png](home-collapsed.png) | 최종 홈, 폰트·중성 유리 바 |
| [home-expanded.png](home-expanded.png) | 펼친 홈 패널 |
| [items.png](items.png) | 용품 탭, 채운 선택 아이콘 |
| [settings.png](settings.png) | 설정 탭 |
| [dialog.png](dialog.png) | 최종 폰트·흰 표면의 안내창 |
| [home-360-font200-expanded.png](home-360-font200-expanded.png) | 360dp·200%, 마지막 항목 접근 |
| [home-landscape-expanded.png](home-landscape-expanded.png) | 가로 화면, 펼침 상태 유지 |

## 인계

실기기·API 26~30·태블릿·제스처 내비게이션·실제 TalkBack·성능 프로파일링은 미검증입니다. 200%·가로 이미지와 용품·설정 이미지는 같은 폰트·레이아웃에서 마지막 안내창 표면만 조정하기 전의 캡처입니다. 제품 데이터·로그인·알림·공유는 연결하지 않았습니다.

사용자(성명 확인 필요)는 폰트 지정·Apple 스타일과 유리 바의 색 틴트 제거를 요청했습니다. Codex 도구가 구체 폰트 선정·XML·Java·규격과 검증을 수행했습니다. 팀원의 리뷰·승인·실기기 확인은 수행하지 않았습니다. 로그는 `/tmp/home-timing-neutral-build.log`, `/tmp/home-timing-neutral-qa.log`에 있습니다. 로컬 브랜치는 `feature/design-system-foundations`이며 원격 저장소 접근 오류로 이슈·PR·push·병합은 진행하지 못했습니다.
