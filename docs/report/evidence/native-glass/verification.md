# 유리 하단 내비게이션 검증

2026-11-09. 색 틴트가 있던 `e2efef0`의 이전 기록입니다. 최신 폰트·중성 유리 바는 [후속 검증](../native-neutral/verification.md)을 따릅니다. `9227dc6` 이후의 하단 유리 표면과 이전 태양·드래그 패널을 함께 확인했습니다. 최종 소스의 app/ Git tree는 `bc6eba8d6aa8026525a33550c7f13e47610629df`, 최종 APK SHA-256은 `0768b3801a0ffb0f98b8afa3b6186efe6c6a31b3575842e4e304b5afa15d910b`입니다. 제품 데이터·계정·날씨 API를 연결한 검증은 아닙니다.

## 변경

XML의 하단 바를 본문 위 오버레이로 배치하고 딥 그린 82% 틴트, 얇은 흰 테두리, 24dp 모서리를 적용했습니다. API 31 이상은 본문만 샘플링한 `GlassBackdropView`에 12dp 블러를 적용합니다. 아이콘·라벨은 블러 레이어 밖에 있습니다. API 30 이하는 캡처와 블러를 실행하지 않고 틴트만 사용합니다.

목록의 아래 패딩은 바의 실제 높이·하단 margin·시스템 inset을 반영합니다. 시스템 버튼 아래에는 불투명한 배경을 두었습니다. 태양의 단색 채운 원·균등 광선, 실제 패널 핸들·클릭·드래그·뒤로가기 계약은 유지합니다.

## 환경과 빌드

macOS arm64 / Zulu JDK 17 / 저장소 Gradle Wrapper. 전용 read-only·no-snapshot AVD, Android API 36.1 arm64, 기본 1080 × 2400 / 420dpi. 작은 화면은 945 × 2100 / 420dpi(360dp), 글자 배율은 200%입니다.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

빌드·Lint 성공. Lint 0 errors / 15 warnings는 기존 도구·라이브러리 새 버전 안내입니다. `testDebugUnitTest`는 **NO-SOURCE**이며 제품 단위 테스트가 없는 상태입니다. XML·문서 링크·diff도 확인했습니다. 로그는 `/tmp/home-timing-glass-build.log`, `/tmp/home-timing-glass-qa.log`에 있습니다.

해당 headless AVD에서 패널 전환 뒤 고정 바의 재그리기가 누락되는 현상을 관찰했습니다. 이 전용 기기의 `debug.hwui.use_partial_updates`와 `debug.hwui.use_buffer_age`를 false로 설정한 환경에서 후속 검증을 수행했습니다. 이 설정을 앱이나 저장소 설정에 넣지 않았으며, 일반 기기·기본 렌더링 설정에서의 실기기 확인은 남아 있습니다.

## 수행한 확인

유리 바와 패널 구조가 같은 APK에서 핸들 클릭·양방향 드래그, 접힌 목록의 위 스크롤 시 먼저 펼치기, 뒤로가기, 용품·설정 탭 조작과 예시 안내창, 탭 이동 후 패널 상태 유지, 회전 후 펼침·접근성 설명 복원, 360dp·200%에서 마지막 항목 접근을 확인했습니다.

이후 눌림 대비를 위해 라벨·ripple 색만 조정한 최종 APK에서 홈·용품·설정의 선택 상태와 펼치기·접기를 다시 확인했습니다. 최종 화면 4장은 아래 기본 크기 이미지입니다. 회전·200% 이미지는 동일 구조에서 라벨 색 조정 전에 캡처한 동작 검증 근거입니다.

| 대비 조합 | 계산 결과 |
| --- | --- |
| 기본 / 선택 라벨 | 7.22:1 |
| 기본 / 비선택 라벨 | 5.78:1 |
| 눌림 / 선택 라벨 | 5.92:1 |
| 눌림 / 비선택 라벨 | 4.74:1 |

가장 밝은 흰 배경 위의 틴트와 ripple을 합성해 계산했습니다. 네 조합 모두 일반 텍스트 목표 4.5:1 이상입니다. 실제 사진·다른 테마 전반의 접근성 감사를 완료했다는 뜻은 아닙니다.

## 화면

| 이미지 | 조건 |
| --- | --- |
| [home-collapsed.png](home-collapsed.png) | 최종 홈, 새 태양·핸들·유리 바 |
| [home-expanded.png](home-expanded.png) | 최종 홈 펼침, 고정된 유리 바 |
| [items.png](items.png) | 최종 용품 탭 |
| [settings.png](settings.png) | 최종 설정 탭 |
| [home-360-font200-expanded.png](home-360-font200-expanded.png) | 360dp·200%, 마지막 항목 스크롤 접근 |
| [home-landscape-expanded.png](home-landscape-expanded.png) | 가로 회전 후 펼친 패널 |

## 한계와 인계

API 26~30에서의 틴트 대체 외관, 실기기 성능·프레임 시간, 태블릿, 제스처 내비게이션, 실제 TalkBack 음성 탐색은 미검증입니다. 예시 데이터 밖의 긴 목록·동적 갱신도 후속 제품 구현에서 확인합니다. API 31 블러 분기와 UI 상태는 이 API 36.1 AVD에서 확인했습니다.

사용자(성명 확인 필요)가 하단 유리 스타일과 앞선 태양·핸들·행동 계약을 지정했습니다. Codex 도구가 XML·Java·규격·검증을 수행했습니다. 팀원의 리뷰·승인·실기기 확인을 대신하지 않았습니다. 로컬 브랜치는 `feature/design-system-foundations`이며 원격 접근 실패로 이슈·PR 생성, push·병합은 하지 않았습니다.
