# 날씨 실사 사진 개선

2026-11-10 · 앱 코드 `9e8ccd80477665fab8a22da4964e2e32705b3df1`

[사진 6종 화면 모음](overview.png) · [밤](night.png) · [비](rain.png) · [다크 홈](dark-home.png) · [현재 홈](ready-home.png)

별이 빽빽한 밤하늘 대신 달과 구름을 사용합니다. 아침을 낮과, 비를 흐림과 구분해 6종 실사를 표시합니다. 밤 사진의 달은 native matrix crop으로 오른쪽 위에 배치해 중앙 기온과 겹치지 않게 했습니다. 홈·날씨 상세에 같은 선택 규칙을 적용합니다. 수동 시간대는 현재 시각으로 오인하지 않도록 배경 테스트임을 표시합니다.

사진 선택·사용 조건과 촬영자는 [앱 내 출처](../../../../app/src/main/assets/licenses/weather-photographs.txt), 시간대·컴포넌트 규격은 [디자인 시스템](../../../DESIGN_SYSTEM.md)을 따릅니다. 외부 이미지 API·키 없이 APK에 포함한 대표 사진이며 실제 위치 사진·실시간 예보 연결은 아닙니다. 흐림·비는 시간대별 사진을 별도로 두지 않습니다.

별도 문맥의 시각 검토에서 밤 사진과 현재 시각의 혼동을 지적받아 수동 배경 테스트 표시를 보완했습니다. 예보 온도 쌍의 최저/최고 표기 개선 의견은 이번 사진 교체 범위 밖의 후속 항목으로 남깁니다.

## 실제 검증

- macOS arm64, JDK 17, Android Emulator `emulator-5554` API 36·1080×2400·420dpi·fontScale 1.0.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: 빌드 성공, JUnit 10개 실패·오류·건너뜀 0, Lint 오류 0·새 버전 안내 경고 15.
- `./scripts/design/preview.sh --serial emulator-5554 --no-build`: 실제 설치·실행 성공.
- 실제 에뮬레이터의 6종 사진, 다크 밤·홈 캡처 및 날씨 문구 확인. 사진 영역의 고정 높이·글자 노출·달과 기온 분리 시각 확인. 마지막에 라이트·기기 시각 자동 홈으로 복원.
- 변경하지 않은 인증·등록·가족 등 전체 회귀는 이번에 반복하지 않았습니다. [이전 전체 검증](../native-complete/verification.md)과 검증 SHA를 구분합니다. 실기기·다른 API 수준은 미검증입니다.
- APK SHA-256: `a3c22c7546b3eaf8a03637c70b9135ec854bf5053f45e66790b3dfcccace3ee1`.
- 로컬 로그: `app/build/reports/design/weather-photo-build.log`, `weather-photo-ui.log`. APK·전체 로그는 Git에 넣지 않습니다.

사용자(성명 확인 필요)는 디자인 방향을 지정했고 Codex 도구가 사진 선정·출처 확인·구도·리소스·화면 분기 구현과 검증을 수행했습니다. `feature/design-system-foundations`에서 기존 작업을 이어갔습니다. 원격 `git fetch origin main`은 Repository not found로 실패해 이슈·PR·push를 수행하지 못했으며 main 병합은 하지 않았습니다.
