# iOS customization

User-approved scope (2026-11-25). The checklist describes implemented code; device verification is listed separately below.

- [x] Korean/English language selection and translation of iOS-specific screens.
- [x] App-local WireGuard, HTTP, HTTPS and SOCKS5; settings entry, proxy-first onboarding like 04pril/BitChord-Android, signed-out entry; API/audio/downloads and Google login WebKit coverage.
- [x] Lyrics fonts: shared or Korean/Latin size and weight, live preview; unified source/change visibility; existing blur setting.
- [x] Background-free native output glyph with AirPods variants; opens the in-app Audio Output drawer. Hide Listen Together and video buttons by default. Hiding a button does not disable its feature.
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
- 하단 출력 버튼은 배경 없는 기기별 아이콘으로 통일했습니다. 함께 듣기 숨김, 비디오 숨김, 곡 선택 시 전체 플레이어 열기는 기본 켜짐입니다. 좋아요 자동 다운로드와 CarPlay 화면 유지는 기본 꺼짐입니다.
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

IPA는 `build/Laya-1.9.6.ipa`에 생성됩니다. 배포용 Apple 서명은 포함하지 않으며 설치 도구에서 다시 서명합니다.

자동 검사 범위: iOS Kotlin 컴파일, 앱/위젯 빌드, 설정 저장/범위 검증, 중복 항목 큐 삭제와 되돌리기 무효화, 한국어/영어 런타임 전환, 서명된 Mach-O의 App Group 읽기, Go 프록시 엔진 테스트.

실기기 확인이 필요한 항목: 두 미니플레이어 상태의 제스처와 스크롤, 실제 Google 로그인/프록시 출구/다운로드, 투명·틴트 홈 화면 위젯, 재서명 도구별 App Group, 실제 CarPlay 연결/해제. 로컬에는 iOS Simulator 런타임이 없습니다.

## 빌드 39 · 실기기 오류 수정

- 빌드 38의 실기기 크래시 로그에서 `customizedLyricStyle → TextStyle.copy` 경로를 확인했습니다. 한 줄 가사의 자동 줄 높이(`TextUnit.Unspecified`)를 숫자로 변환해 NaN 줄 높이를 만들던 처리를 수정했습니다. 자동 높이를 유지하며, 명시한 줄 높이의 sp/em 단위도 보존합니다.
- 재현 테스트에서 `lineHeight can't be negative (NaN)` 예외를 확인한 뒤, 자동 높이·18~52 크기 범위·sp/em 단위·잘못된 배율 검사를 통과했습니다.
- 하단 출력 아이콘의 `UIImageView`를 제거했습니다. 시스템 헤드폰·AirPods 심벌을 투명 PNG로 한 번 렌더링하고 Compose 이미지로 표시하여, 배경 흐림/전환과 겹치는 별도 UIKit 이미지 레이어를 없앴습니다.
- 원본 실기기의 재생 지속 및 아이콘 표시는 빌드 39 설치 후 추가 확인이 필요합니다.

## 독립 테스트 앱

`layatest-1.9.6-custom42.ipa`는 같은 수정본을 별도 앱으로 설치하는 변형입니다. 앱 식별자는 `com.music.layatest`, 위젯은 `com.music.layatest.widgets`, App Group은 `group.com.music.layatest`, URL 스킴은 `layatest`입니다. 기본 앱의 데이터 컨테이너와 Keychain 서비스, 프록시 비밀정보, 위젯 저장소·링크를 공유하지 않습니다. Laya와 동시에 설치할 수 있습니다.

```sh
python3 iosApp/scripts/make-test-ipa.py build/Laya-1.9.6-custom42.ipa build/layatest-1.9.6-custom42.ipa
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

## 빌드 42 · 출력 버튼 통일

- ‘간단한 하단 버튼 구성’ 설정과 저장 상태를 읽는 경로를 제거했습니다. 이전에 해당 옵션을 꺼 두었어도 iOS에서는 항상 하나의 출력 버튼 형태를 사용합니다.
- 헤드폰·AirPods·AirPods Max는 기존 네이티브 기기별 심벌을 표시합니다. 빌드 41의 AirPods 이어버드 확대도 적용됩니다. 출력 아이콘의 회색 캡슐 및 터치 시 배경을 그리지 않습니다.
- 아이콘을 누르면 앱의 ‘오디오 출력’ 창을 먼저 엽니다. 하단 아이콘 위에 있던 투명 AVRoutePickerView를 제거했으며, 시스템 기기 선택기는 출력 창의 Bluetooth/AirPlay 항목에서 엽니다. 출력 이름을 누르는 것과 동일한 창입니다.
- 함께 듣기/비디오 버튼 숨김 옵션과 48dp 출력 버튼 터치 영역, 버튼 접근성 설명·역할을 유지합니다.
- 검증: 설정 저장 회귀 검사, iOS Kotlin 컴파일 및 iPhone Release 앱·위젯 빌드, 일반/독립 테스트 IPA 무결성·버전·식별자·App Group 분리 확인. 실제 기기에서 창 열기와 표시 확인이 필요합니다.

## 빌드 43 · 정식 릴리즈

- 애플뮤직 참조 화면에 맞춰 iOS 출력 기기 이름을 11sp에서 13sp 세미볼드로 키우고 가독성을 높였습니다. 20dp 이름 슬롯은 유지하며 아이콘 아래 간격을 12dp에서 0dp로 줄였습니다.
- 세로 플레이어의 추가 바닥 여백을 4dp에서 0dp로 줄이고, 홈 인디케이터 안전 영역 중 10dp를 더 활용합니다. 일반적인 34dp 하단 인셋에서는 24dp를 남기며, 인셋 차감은 0 아래로 내려가지 않습니다. 출력 이름은 약 14dp, 버튼·상단 컨트롤은 약 26dp 내려가는 하단 기준입니다. 확보한 26dp를 재생 버튼 주변 간격이 다시 흡수하지 않도록 앨범아트·제목 위 공간에 우선 배분합니다. 실제 배치는 화면 크기와 가사 줄 등 기존 가변 배치를 따릅니다.
- 일반 Laya IPA와 SHA-256만 정식 릴리즈에 제공합니다. 빌드 43의 layatest 변형은 만들지 않습니다. 위 독립 테스트 앱 설명과 예제는 마지막 테스트 배포인 빌드 42를 가리킵니다.
- 공개 릴리즈 설명은 요청한 전체 기능 목록을 사용합니다. 삭제된 ‘간단한 하단 버튼 구성’ 설정은 현재 동작인 ‘하단 버튼 구성을 간결하게 통일’로 표기합니다.
- 검증: iOS Kotlin 컴파일, 작은 화면·일반 화면·큰 화면에서 제목이 26dp 내려가면서 재생 버튼 주변 간격이 늘지 않는 배치 회귀 검사, 세 기본 옵션 및 설정 저장 검사, iPhone Release 앱·위젯 빌드와 서명·IPA 버전·식별자·App Group·체크섬 검사. 최종 화면과 터치 동작은 실기기 확인이 필요합니다.

## 빌드 44 · WidgetPod 배경과 소스 업데이트

- 실기기 비교에서 WidgetPod 배경이 다크 모드에서는 검게, 라이트 모드에서는 하얗게 바뀌는 현상을 확인했습니다. 배경 계산은 빗코드의 `posterWash`와 동일하므로 필터값을 바꾸지 않고 표시 경로를 수정했습니다.
- 커버 색 배경을 원본 색의 불투명 이미지 콘텐츠로 직접 그립니다. 앨범아트와 배경 모두 `.renderingMode(.original)` 및 iOS 18 이상에서 `.widgetAccentedRenderingMode(.fullColor)`를 적용합니다. 시스템 컨테이너에는 투명을 지정하고, 콘텐츠를 위젯 경계로 자릅니다. 커버가 없을 때의 중립 배경도 고정 색 비트맵입니다.
- AltStore/SideStore는 같은 소스 URL을 계속 사용합니다. 소스에 새 `version`/`buildVersion`을 맨 앞에 반영하면 업데이트를 감지합니다. 새 앱 버전 설치와 7일 서명 갱신은 별개입니다. 정식 릴리즈용 소스 갱신 워크플로를 빌드 43으로 실제 실행해 성공을 확인했습니다.
- 정식 빌드 44도 일반 IPA만 배포합니다. WidgetKit의 실제 라이트/다크·틴트/투명 홈 화면 렌더링은 실기기에서 최종 확인해야 합니다.

## 정식 배포 이름

- 현재 정식 릴리즈의 제목은 `Laya iOS 1.9.6`, 태그는 `ios-v1.9.6`, 공개 IPA 이름은 `Laya-1.9.6.ipa`입니다. 빌드 번호는 릴리즈 설명과 앱 내부 버전 정보에 기록합니다. 현재 내부 빌드는 47입니다.
- 같은 릴리즈의 IPA를 바꿀 때 소스는 새 `buildVersion`을 반영하며, 같은 다운로드 URL을 가리키는 구빌드 항목은 제거합니다. 예전 이름의 태그·파일 주소가 소스에 남지 않도록 정리합니다.

## 빌드 45 · 위젯 합성 이미지와 iPad 지원

- 빌드 44의 배경 표시 수정 뒤에도 실기기에서 모드별 차이가 남았고, 사용자가 위젯 재추가·앱 재설치·재부팅 후에도 재현됨을 확인했습니다. 캐시 문제로 처리하지 않습니다.
- 이번에는 커버와 커버 색 배경을 **하나의 불투명 미디어 이미지**로 합성합니다. 이미지가 위젯의 루트 콘텐츠이고, 글자와 App Intent 버튼만 그 위에 따로 배치됩니다. 커버만 원본 색으로 유지하면서 배경을 별도로 변환할 수 있던 레이어 구분을 제거합니다.
- 합성기는 8비트 sRGB를 명시하고 모든 픽셀을 불투명하게 만듭니다. 빗코드와 같은 배경 필터 값, 338×158·158×158 기준 커버 위치와 크기·둥근 모서리를 유지합니다. Mac에서 실제 공용 합성기를 실행해 라이트/다크 RGB 바이트 일치, 모든 픽셀 알파 255, 두 크기·소수점 경계 및 이미지 없는 상태를 검사했습니다. 이 검사는 iOS WidgetKit 호스트의 실기기 렌더링 검증을 대신하지 않습니다.
- iPhone 전용 기기 설정(`UIDeviceFamily = [1]`)을 iPhone·iPad 공용(`[1,2]`)으로 바꿉니다. iPad 전용 네 방향 회전을 선언하며 iPhone의 기존 방향은 유지합니다. `UIRequiresFullScreen` 제한이나 최대 창 크기는 추가하지 않습니다. Compose 루트는 현재 창 크기에 맞춰지고 가로 플레이어의 기존 두 열 배치를 사용합니다.
- 같은 정식 릴리즈 `ios-v1.9.6`의 `Laya-1.9.6.ipa`를 빌드 45로 갱신합니다. 정식 제목과 파일명에는 빌드 번호를 넣지 않으며 테스트 IPA는 만들지 않습니다.

## 빌드 46 · iPad 플레이어 배치와 표시 이름

- iPad의 충분히 넓고 높은 창(700×600dp 이상)에서는 기본 화면의 앨범아트·제목·한 줄 가사·진행바·재생 조작·볼륨을 하나의 중앙 열로 배치합니다. 가사 또는 큐를 열면 같은 조작부가 왼쪽에 남고 오른쪽에 패널을 표시합니다. 작은 창과 iPhone은 기존 반응형 배치를 유지합니다.
- 새 그림이나 플레이어 리소스를 추가하지 않고 기존 Laya의 앨범아트, 가사, 제목·메뉴, 진행바, 재생·볼륨 조작부를 재사용합니다. 한 줄 가사는 기본 화면·큐 화면에서 유지하며 iPad에서는 진행바 위 간격을 12dp 추가합니다.
- 셔플·반복은 재생 버튼 양옆에, 출력 선택은 화면 왼쪽 아래에, 자동재생·가사·큐는 오른쪽 아래에 배치합니다. 함께 듣기·비디오 숨김 설정과 출력 창도 유지합니다.
- 프록시 설정의 사용자 표시 이름을 Laya로 정리하고 호환 연결 옵션의 설명을 번역합니다. 저장소·재생 연결에 쓰는 내부 키와 기존 저작권 표시는 유지합니다.
- 검증: iOS Kotlin 컴파일 및 실제 공용 Compose 컴포넌트의 오프스크린 렌더링. 가로/세로 다섯 창 크기와 기본/가사/큐 상태에서 커버·조작부·패널이 겹치지 않는지, 한 줄 가사와 진행바의 간격을 확인합니다. 이는 iPad 실기기의 터치·회전·WidgetKit 동작을 검증한 결과는 아닙니다.
- 같은 정식 릴리즈와 파일 이름으로 빌드 46 IPA만 갱신하며, 테스트 IPA는 만들지 않습니다.

## 빌드 47 · iPad 가사 버튼과 듀오 화면 영역

- iPad 및 넓은 iOS 창에서 노래방·번역 버튼을 가사 오른쪽 위에 세로로 배치합니다. 기존 버튼과 동작을 재사용하며, 선택·공유 중에는 숨깁니다. 로마자 변환과 가사 출처는 하단에 유지하고, 가사는 상단 버튼 열을 침범하지 않도록 여백을 둡니다.
- 하단 자동재생·가사·큐 버튼 사이에 12dp를 추가합니다. 출력 이름은 왼쪽 정렬하고 출력 버튼 영역과의 간격을 4dp로 줄입니다. iPhone의 기존 세로 플레이어 배치는 유지합니다.
- 이슈 #3의 아이폰 듀오 화면을 참고해 넓게 펼친 전화기(창의 짧은 변 600pt 이상)는 UIKit의 현재 창 안전 영역 안에 Compose 뷰를 배치합니다. 회전·창 크기·안전 영역 변경마다 다시 계산하며, 일반 크기 전화기와 iPad의 기존 전체 배경은 유지합니다. 기기 이름 대신 창 크기로 큰 화면 플레이어 적용 여부를 판단합니다.
- iPad에서 저장된 크기 설정이 없을 때만 전체·한글·영문 가사 기본값을 48로 적용합니다. 저장된 값은 34를 직접 선택한 경우까지 그대로 유지합니다. 한 줄 가사는 기본 크기를 유지하고 사용자가 직접 바꾼 경우에만 크기 설정을 따릅니다.
- 왼쪽 커버 영역의 너비 상한을 넓히고, 조작부 아래 여백을 하단 버튼 기준으로 고정해 남는 공간이 아래에 몰리지 않도록 배치합니다.
- 검증: 공용 Compose 렌더 검사, 네이티브 화면 영역 계산 검사, iOS Release 앱·위젯 빌드 및 IPA 버전·무결성 검사. 실제 Duo의 접힘 자세와 시스템 영역 변경 동작은 실기기에서 최종 확인해야 합니다.
