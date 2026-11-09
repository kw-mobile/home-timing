# 홈 전용 패널·앱 아이콘 검증

이 기록은 `ebe1ba8` 시점입니다. 후속 시간대 표현·칫솔·유리 표면 조정은 [현재 검증](../native-time-glass/verification.md)을 확인합니다.

2026-11-09. 기준 커밋 `c5b3aa5` 이후 변경입니다. 검증 app/ Git tree는 `d92e8769c4ba15744354b924a12ebc18c9d1a191`, APK SHA-256은 `c06eb467c9df244fac9ac4d3784835e7afe5133fbbc4b20f1819df2f725566af`입니다. 구현 규격은 [디자인 시스템](../../../DESIGN_SYSTEM.md)을 따릅니다.

## 구현

홈의 교체 목록만 실제 BottomSheet와 핸들을 유지합니다. 용품·설정은 패널·핸들·패널 모서리를 제거한 단일 스크롤 화면입니다. 각 페이지에서 뒤로가기는 홈으로 돌아가며, 홈의 펼침 상태는 다른 탭에서 회전한 뒤에도 복원합니다. 일반 페이지도 마지막 내용을 유리 바 위로 스크롤할 수 있도록 하단 여백을 계산합니다.

로고는 부드러운 집 실루엣에 원형 시계판과 둥근 두 바늘을 통합한 단색 벡터입니다. 앱 상단·Android adaptive 런처·monochrome은 같은 `ic_brand.xml`을 참조합니다. [1024px PNG](../../../design-assets/app-icon.png)·[SVG](../../../design-assets/app-icon.svg)는 iOS 형태를 고려한 마스크 없는 정사각 내보내기이며, [둥근 모서리 미리보기](../../../design-assets/app-icon-preview.png)는 표시용입니다. iOS 앱·Icon Composer 자산 세트를 구현한 것은 아닙니다. [Apple의 앱 아이콘 가이드](https://developer.apple.com/design/human-interface-guidelines/app-icons)의 단순한 중심 형태·배경 원칙을 참고했습니다.

UI UX Pro Max를 적용했습니다. 모달 관련 두 검색은 이 작업에 직접 맞는 네이티브 결과를 찾지 못해 일반적인 내비게이션·터치·안전 영역 지침과 사용자의 명시적 화면 결정을 기준으로 구현했습니다. 새 로고만 전달한 독립 검토에서 집과 시간의 조합으로 읽혔고 수정이 필요한 혼동은 보고되지 않았습니다. 이는 사람의 브랜드 평가나 상표 검토를 뜻하지 않습니다.

## 검증

macOS arm64 / Zulu JDK 17 / 저장소 Gradle Wrapper:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

빌드·Lint 성공. Lint 0 errors / 15 warnings는 기존 도구·의존성 버전 안내입니다. 일반 페이지가 공통 녹색 창 위에 밝은 표면을 그리는 의도된 배경 중첩에는 XML `Overdraw` 경고를 좁게 제외하고 이유를 남겼습니다. `testDebugUnitTest`는 **NO-SOURCE**이며 단위 테스트 실행 성공이 아닙니다. XML 파싱·문서 상대 링크·diff 공백도 확인했습니다.

별도 AVD 부팅은 HVF 비활성화·offline 문제로 실패했습니다. 동작 검증은 실행 중이던 Android API 36.1 arm64 Medium Phone 에뮬레이터에서 수행했습니다. 기본 1080×2400·420dpi(약 411dp), 추가 945×2100(360dp)·폰트 배율 200%입니다. 실기기는 아닙니다.

ADB/UI Automator로 홈 핸들 펼치기·뒤로 접기, 용품·설정의 핸들·BottomSheet 부재, 탭 전환, 설정 안내창, 설정에서 가로/세로 회전 후 선택 탭 유지, 뒤로 홈 복귀 시 펼침 상태 복원, 작은 화면·큰 글자에서 각 페이지의 마지막 항목이 바 위로 올라오는지 확인했습니다. 테스트 뒤 해상도·글자 배율과 세로 방향을 복원했습니다. Android 런처 앱 목록에서 실제 원형 마스크 안의 새 아이콘도 육안 확인했습니다.

| 화면 | 근거 |
| --- | --- |
| 홈 | [home.png](home.png) |
| 일반 페이지 | [용품](items.png), [설정](settings.png) |
| 가로 | [settings-landscape.png](settings-landscape.png) |
| 작은 화면·큰 글자 | [용품](items-360-font200.png), [설정](settings-360-font200.png) |
| 로그 | [빌드](build.log), [상호작용](interactions.log) |

## 한계와 인계

실제 로그인·날씨·저장·가족 공유·알림은 미연결입니다. API 26~30, 실기기, 실제 TalkBack, 제스처 내비게이션과 프레임 성능은 미검증입니다. 이번 화면 구조 변경 후 375/600dp·시스템 다크·애니메이션 배율 0은 다시 실행하지 않았으며 이전 커밋의 검증과 구분합니다.

사용자(성명 확인 필요)는 홈만 패널로 유지하고 나머지 화면을 일반 화면으로 전환하도록 결정했으며 집+시계 로고의 iOS 앱 아이콘 느낌을 요청했습니다. Codex 도구가 설계·XML/Java·로고·로컬 검증을 수행했습니다. 팀원의 리뷰·승인을 대신하지 않았습니다.

`git fetch origin main`은 `Repository not found`로 실패했습니다. `feature/design-system-foundations` 브랜치에 로컬 결과를 보존하며 이슈·PR·push·병합은 진행하지 못했습니다.
