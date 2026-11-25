# iOS customization

User-approved scope (2026-11-25). The checklist describes implemented code; device verification is listed separately below.

- [x] Korean/English language selection and translation of iOS-specific screens.
- [x] App-local WireGuard, HTTP, HTTPS and SOCKS5; settings entry, proxy-first onboarding like 04pril/BitChord-Android, signed-out entry; API/audio/downloads and Google login WebKit coverage.
- [x] Lyrics fonts: shared or Korean/Latin size and weight, live preview; unified source/change visibility; existing blur setting.
- [x] Simple footer enabled by default; hide Listen Together and video buttons by default; background-free output icon with AirPods variants. Hiding a button does not disable its feature.
- [x] Open full player on explicit song selection (option); downward dismissal in both expanded and inline mini-player layouts, preserving horizontal skips and upward expansion.
- [x] Queue multi-selection, remove and undo; playlist picker search; liked-song downloads matching our BitChord iOS (initial liked-library sync and new likes, default off, no deletion on unlike).
- [x] Independent library removal and owned source-playlist entry removal, respecting duplicate entry IDs and account scope.
- [x] Additional colored medium/small widgets; artwork under clear/tinted styles; signed App Group intersection handling. Preserve current widgets.
- [x] AirPods output symbols and keep iPhone screen awake while CarPlay is connected; no CarPlay browsing UI.

Do not import BitChord's playback modes, buffering policies or other omitted features.

## 사용

- iOS 17 이상. `설정 → 플레이어 및 편의 설정`에서 플레이어·가사·다운로드·CarPlay 옵션을 설정합니다.
- 앱 언어는 한국어/English 두 가지이며, 선택 즉시 UI를 다시 구성합니다. 재생은 같은 플레이어를 유지합니다.
- WireGuard/프록시는 첫 실행의 로그인 전 단계, 로그인 진입 창, 일반 설정에서 엽니다. 연결 설정을 바꾼 뒤 앱을 완전히 종료하고 다시 실행해야 합니다.
- 간단한 하단 버튼, 함께 듣기 숨김, 비디오 숨김, 곡 선택 시 전체 플레이어 열기는 기본 켜짐입니다. 좋아요 자동 다운로드와 CarPlay 화면 유지는 기본 꺼짐입니다.
- 좋아요 자동 다운로드는 켤 때 현재 계정의 좋아요 목록을 조회하며 새 좋아요에도 적용합니다. 기존 다운로드는 중복으로 받지 않고, 좋아요 취소 시 파일을 삭제하지 않습니다. Wi-Fi 전용 설정을 따릅니다.
- 큐의 ‘여러 곡 선택’으로 앞으로 재생할 항목만 선택합니다. 삭제 후 큐 수정이나 현재 곡 변경이 있으면 오래된 삭제를 되돌릴 수 없습니다.
- 플레이리스트 삭제는 현재 계정 소유임을 서버 응답으로 확인한 원본 플레이리스트에 한정합니다. 원래 항목 ID가 없으면 중복 곡을 임의로 선택하지 않습니다.
- 컬러 위젯 4×2·2×2가 기존 위젯에 추가됩니다. 앱과 위젯에 실제로 공통 부여된 App Group이 있어야 공유가 작동합니다.

## 빌드와 검사

```sh
# Mac, Xcode, JDK 21, Go (WireGuard/go.mod), XcodeGen
bash iosApp/scripts/make-ipa.sh
bash iosApp/scripts/test-native-customization.sh
./gradlew :shared:jvmTest --tests '*IosCustomizationTest' --tests '*QueueBatchRemovalTest'
(cd WireGuard && go test ./...)
```

IPA는 `build/Laya-1.9.6-custom41.ipa`에 생성됩니다. 배포용 Apple 서명은 포함하지 않으며 설치 도구에서 다시 서명합니다.

자동 검사 범위: iOS Kotlin 컴파일, 앱/위젯 빌드, 설정 저장/범위 검증, 중복 항목 큐 삭제와 되돌리기 무효화, 한국어/영어 런타임 전환, 서명된 Mach-O의 App Group 읽기, Go 프록시 엔진 테스트.

실기기 확인이 필요한 항목: 두 미니플레이어 상태의 제스처와 스크롤, 실제 Google 로그인/프록시 출구/다운로드, 투명·틴트 홈 화면 위젯, 재서명 도구별 App Group, 실제 CarPlay 연결/해제. 로컬에는 iOS Simulator 런타임이 없습니다.

## 빌드 39 · 실기기 오류 수정

- 빌드 38의 실기기 크래시 로그에서 `customizedLyricStyle → TextStyle.copy` 경로를 확인했습니다. 한 줄 가사의 자동 줄 높이(`TextUnit.Unspecified`)를 숫자로 변환해 NaN 줄 높이를 만들던 처리를 수정했습니다. 자동 높이를 유지하며, 명시한 줄 높이의 sp/em 단위도 보존합니다.
- 재현 테스트에서 `lineHeight can't be negative (NaN)` 예외를 확인한 뒤, 자동 높이·18~52 크기 범위·sp/em 단위·잘못된 배율 검사를 통과했습니다.
- 하단 출력 아이콘의 `UIImageView`를 제거했습니다. 시스템 헤드폰·AirPods 심벌을 투명 PNG로 한 번 렌더링하고 Compose 이미지로 표시하여, 배경 흐림/전환과 겹치는 별도 UIKit 이미지 레이어를 없앴습니다.
- 원본 실기기의 재생 지속 및 아이콘 표시는 빌드 39 설치 후 추가 확인이 필요합니다.

## 독립 테스트 앱

`layatest-1.9.6-custom41.ipa`는 같은 수정본을 별도 앱으로 설치하는 변형입니다. 앱 식별자는 `com.music.layatest`, 위젯은 `com.music.layatest.widgets`, App Group은 `group.com.music.layatest`, URL 스킴은 `layatest`입니다. 기본 앱의 데이터 컨테이너와 Keychain 서비스, 프록시 비밀정보, 위젯 저장소·링크를 공유하지 않습니다. Laya와 동시에 설치할 수 있습니다.

```sh
python3 iosApp/scripts/make-test-ipa.py build/Laya-1.9.6-custom41.ipa build/layatest-1.9.6-custom41.ipa
```

## 빌드 40 · 화면과 위젯 보완

- 가사 출처·변경 버튼을 하나의 설정으로 통합했습니다. 기존 두 설정 중 하나라도 꺼져 있었다면 통합 설정도 꺼진 상태로 이전합니다. 끄면 출처 줄과 그 패딩을 통째로 제거합니다.
- 추가 설정에 기존 `SettingsGroup`/`SettingsRow`의 둥근 카드, 구분선, 스위치 색과 여백을 적용했습니다.
- 프록시 초기 화면을 원래 온보딩 안으로 통합해 이름 입력 단계와 같은 움직이는 배경·제목·캡슐 버튼을 사용합니다. 연결 설정 후 재시작하여 실제 연결이 준비되면 이름 단계로 자동 이동합니다. 미설정/실패/재시작 필요 상태는 자동으로 넘기지 않습니다. 직접 연결은 계속 버튼으로 진행합니다.
- 잠금화면·다이나믹 아일랜드의 시스템 나우플레잉에 빗코드의 실행 대상 연결 보완을 이식했습니다. `Info.plist`의 실제 설치 식별자를 사용하므로 Laya·layatest 및 재서명 식별자를 구분합니다. 선택적 MediaRemote API 부재·예외가 재생에 영향을 주지 않도록 처리했습니다. 탭으로 앱 열기는 실기기와 최종 서명 상태에서 확인이 필요합니다.
- 임의로 만들었던 컬러 위젯을 빗코드 `App/Shared/PosterWidgetViews.swift`의 WidgetPod 스타일 4×2·2×2로 교체했습니다. 338×158/158×158 기준 배치, 곡선 앨범아트, 원본 `WidgetMusicGlyph` 자산과 Liquid Artwork 필터값을 가져왔습니다. 기존 위젯 종류 식별자를 유지하며 Laya 재생 명령과 독립 앱 저장소를 연결합니다.
- 검증: iOS Kotlin 컴파일, 설정 이전 및 가사 폰트 회귀 검사, 네이티브 언어·서명 그룹 검사, 세 가지 앱 식별자의 나우플레잉 연결과 API 부재·예외 격리 검사. UI와 실제 잠금화면 탭 동작은 로컬 Simulator 런타임 부재로 실기기 확인이 필요합니다.

## 빌드 41 · 플레이어 하단 기준 맞춤

- 빗코드의 세로 플레이어와 동일하게 안전 영역 아래쪽 추가 여백을 18dp에서 4dp로, 하단 버튼과 출력 기기 이름 사이를 18dp에서 12dp로 조정했습니다. 출력 이름도 20dp 슬롯의 위쪽 대신 중앙에 정렬합니다. 제목·재생 컨트롤·하단 버튼이 과하게 올라가 보이던 배치를 낮추되 시스템 안전 영역은 유지합니다. 플랫폼 호스트의 레이아웃 값으로 구분합니다.
- AirPods 이어버드 출력 아이콘만 1.18배 확대했습니다. 헤드폰/AirPods Max 아이콘과 48dp 출력 선택 터치 영역은 유지합니다. 투명 PNG를 Compose 안에서 그리는 기존 경로도 유지합니다.
- 검증: iOS Kotlin 컴파일, iPhone Release 앱·위젯 빌드와 서명 검사, 일반/독립 테스트 IPA 식별자·App Group·체크섬 검사. 화면 위치의 최종 비교는 실기기 확인이 필요합니다.
