# 앱 원본 실행 영상

[MP4 다운로드](https://github.com/kw-mobile/home-timing/releases/download/v0.5.2/home-timing-demo-raw.mp4) · [APK 다운로드 QR](../apk-download-v0.5.2-qr.png)

사용자 요청에 따라 자막·편집·음성 없이 한 번에 녹화한 Android 실행 원본입니다. 제품 소스는 `f35d335535b6f3b1ccea7623c87c737a2940187d`이며 v0.5.2의 제품 코드와 같습니다. 일반 화면은 debug 디자인 미리보기의 예시 데이터이고 실제 계정 가입·운영 날씨·가족 서버 동기화 시연은 아닙니다.

홈·용품 목록·상세·등록·날씨·가족·알림 설정을 탐색합니다. 사진 구간은 전용 테스트 코드로 파일 또는 합성 문구를 실제 기기 내 인식기에 전달하고 결과를 기존 후보 UI에 표시합니다. 물리 카메라 촬영·실물 정확도 검증이 아닙니다. 공개 칫솔 사진의 인식 실패도 원본에서 제거하지 않았습니다. OCR 합성 문구 입력은 별도 경로입니다. 상세 결과와 영상 검증은 [이슈 #122](https://github.com/kw-mobile/home-timing/issues/122)에 기록합니다.

입력 사진은 [Steve Buissinne의 Toothbrush, Toothpaste, Dental Care](https://commons.wikimedia.org/wiki/File:Toothbrush,_Toothpaste,_Dental_Care_(571741).jpg), CC0입니다. 원본의 사진 노출과 기기 내 추론 외에 운영 사용자 데이터 쓰기는 없습니다.

QR은 v0.5.2의 고정 APK URL을 담은 PNG이고 생성 후 Apple Vision으로 해독해 동일 주소임을 확인했습니다. 향후 새 버전은 새 QR이 필요합니다. 영상은 Git 이력에 대용량 MP4를 누적하지 않도록 기존 릴리스의 별도 자산으로 게시합니다. APK·체크섬·update.json은 변경하지 않습니다.
