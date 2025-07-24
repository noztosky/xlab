# 카메라 설정 시스템 사용 가이드

## 개요

새로운 카메라 설정 시스템은 다양한 PTZ 카메라를 쉽게 관리하고 제어할 수 있도록 설계되었습니다. 현재 C12 카메라를 지원하며, 추후 다른 카메라 모델들을 쉽게 추가할 수 있는 확장 가능한 구조입니다.

## 주요 기능

### 1. 카메라 목록 관리
- 사용 가능한 카메라 목록 조회
- 카메라별 지원 기능 확인
- 제조사별 카메라 필터링

### 2. 카메라 설정
- ID 기반 카메라 설정
- 사용자 정의 연결 설정
- 기존 방식 호환성 유지

### 3. 카메라 제어
- PTZ (Pan, Tilt, Zoom) 제어
- 사진 촬영
- 비디오 녹화
- 프리셋 위치 이동 (지원하는 카메라만)

## 사용 방법

### 1. 기본 사용법

```kotlin
val xlabPlayer = XlabPlayer(context)

// 사용 가능한 카메라 목록 조회
val cameras = xlabPlayer.getAvailableCameras()
cameras.forEach { camera ->
    Log.d("Camera", "${camera.name}: ${camera.capabilities}")
}

// C12 카메라 설정 (기본 설정 사용)
xlabPlayer.setupCameraById("c12_ptz")

// PTZ 제어
xlabPlayer.setCameraPan(45.0f) { success, message ->
    Log.d("PTZ", "팬 설정: $success - $message")
}

xlabPlayer.setCameraTilt(-30.0f) { success, message ->
    Log.d("PTZ", "틸트 설정: $success - $message")
}
```

### 2. 사용자 정의 설정

```kotlin
// 사용자 정의 연결 설정
val customSettings = CameraConnectionSettings(
    baseUrl = "http://192.168.1.100",
    username = "admin",
    password = "12345",
    timeout = 10000
)

// 사용자 정의 설정으로 카메라 설정
xlabPlayer.setupCameraById("c12_ptz", customSettings)
```

### 3. 기존 방식 (하위 호환성)

```kotlin
// 기존 C12 설정 방식도 계속 사용 가능
xlabPlayer.setupC12Camera("http://192.168.144.108", "admin", "")
xlabPlayer.setC12Pan(90.0f) { success, message ->
    Log.d("C12", "팬 설정: $message")
}
```

### 4. 카메라 기능 확인

```kotlin
// 현재 카메라 정보 확인
val currentCamera = xlabPlayer.getCurrentCameraInfo()
Log.d("Camera", "현재 카메라: ${currentCamera?.name}")

// 특정 기능 지원 여부 확인
if (xlabPlayer.isCameraCapabilitySupported(CameraCapability.PTZ_CONTROL)) {
    // PTZ 제어 가능
    xlabPlayer.setCameraPan(0.0f)
}

if (xlabPlayer.isCameraCapabilitySupported(CameraCapability.PHOTO_CAPTURE)) {
    // 사진 촬영 가능
    xlabPlayer.capturePhoto { success, message ->
        Log.d("Photo", "촬영 결과: $message")
    }
}
```

## 새로운 카메라 추가 방법

### 1. CameraSettings.kt에 카메라 정보 추가

```kotlin
// CameraSettingsManager의 availableCameras 리스트에 추가
CameraInfo(
    id = "hikvision_dome",
    name = "Hikvision Dome Camera",
    manufacturer = "Hikvision",
    model = "DS-2DE4A425IW-DE",
    capabilities = listOf(
        CameraCapability.PTZ_CONTROL,
        CameraCapability.PRESET_POSITIONS,
        CameraCapability.AUTO_TRACKING,
        CameraCapability.ZOOM_CONTROL
    ),
    defaultSettings = CameraConnectionSettings(
        baseUrl = "http://192.168.1.100",
        username = "admin",
        password = "12345"
    )
)
```

### 2. 카메라 컨트롤러 구현

```kotlin
class HikvisionCameraController : CameraController {
    
    override fun setupCamera(settings: CameraConnectionSettings): Boolean {
        // Hikvision 카메라 연결 로직 구현
        return true
    }
    
    override fun panTo(angle: Float, callback: ((Boolean, String) -> Unit)?) {
        // Hikvision PTZ API 호출
        // 예: /PSIA/PTZCtrl/channels/1/continuous
    }
    
    override fun moveToPreset(presetId: Int, callback: ((Boolean, String) -> Unit)?) {
        // Hikvision 프리셋 이동 API 호출
        // 예: /PSIA/PTZCtrl/channels/1/presets/$presetId/goto
    }
    
    // 기타 메서드들 구현...
}
```

### 3. XlabPlayer.kt에 컨트롤러 등록

```kotlin
// setupCamera 메서드의 when 블록에 추가
currentCameraController = when (cameraInfo.id) {
    "c12_ptz" -> C12CameraController()
    "hikvision_dome" -> HikvisionCameraController()  // 새로 추가
    "axis_ptz" -> AxisCameraController()              // 새로 추가
    else -> {
        Log.e(TAG, "지원되지 않는 카메라 타입: ${cameraInfo.id}")
        return this
    }
}
```

## 지원하는 카메라 기능

### CameraCapability 열거형

- `PTZ_CONTROL`: 팬, 틸트, 줌 제어
- `PHOTO_CAPTURE`: 사진 촬영
- `VIDEO_RECORDING`: 비디오 녹화
- `ZOOM_CONTROL`: 줌 제어 (PTZ와 별도)
- `FOCUS_CONTROL`: 포커스 제어
- `PRESET_POSITIONS`: 프리셋 위치
- `AUTO_TRACKING`: 자동 추적

## API 메서드 목록

### 카메라 관리
- `getAvailableCameras()`: 사용 가능한 카메라 목록
- `getCamerasByCapability()`: 특정 기능을 지원하는 카메라 목록
- `setupCameraById()`: ID로 카메라 설정
- `setupCamera()`: 카메라 정보로 카메라 설정
- `getCurrentCameraInfo()`: 현재 카메라 정보
- `isCameraCapabilitySupported()`: 기능 지원 여부 확인

### 카메라 제어
- `setCameraPan()`: 팬 각도 설정
- `setCameraTilt()`: 틸트 각도 설정
- `setCameraZoom()`: 줌 레벨 설정
- `capturePhoto()`: 사진 촬영
- `startCameraRecording()`: 녹화 시작
- `stopCameraRecording()`: 녹화 정지
- `getCameraPTZStatus()`: PTZ 상태 조회
- `moveCameraToPreset()`: 프리셋 위치로 이동

### 하위 호환성
- `setupC12Camera()`: C12 카메라 빠른 설정
- `setC12Pan()`, `setC12Tilt()`, `setC12Yaw()`: 기존 C12 메서드

## 예제 코드

완전한 사용 예제는 `CameraTestActivity.kt` 파일을 참조하세요.

```kotlin
// 카메라 목록에서 선택하여 설정
val cameras = xlabPlayer.getAvailableCameras()
val selectedCamera = cameras.first { it.id == "c12_ptz" }

xlabPlayer.setupCamera(selectedCamera)

// 카메라 제어
xlabPlayer.setCameraPan(90.0f) { success, message ->
    if (success) {
        xlabPlayer.setCameraTilt(45.0f) { tiltSuccess, tiltMessage ->
            if (tiltSuccess) {
                xlabPlayer.capturePhoto { photoSuccess, photoMessage ->
                    Log.d("Camera", "사진 촬영: $photoMessage")
                }
            }
        }
    }
}
```

## 장점

1. **확장성**: 새로운 카메라 모델을 쉽게 추가
2. **일관성**: 모든 카메라에 대해 동일한 인터페이스 사용
3. **호환성**: 기존 C12 코드와 완전 호환
4. **유연성**: 카메라별 다른 설정과 기능 지원
5. **안전성**: 지원하지 않는 기능 호출 시 명확한 오류 메시지

이 시스템을 통해 C12 카메라뿐만 아니라 향후 추가될 다양한 PTZ 카메라들을 효율적으로 관리할 수 있습니다. 