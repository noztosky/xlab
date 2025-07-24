# H.265 Video Player AAR 라이브러리

Android용 H.265 (HEVC) 비디오 플레이어 라이브러리입니다. ExoPlayer와 MediaCodec을 사용하여 하드웨어/소프트웨어 H.265 디코딩을 지원합니다.

## 주요 기능

- ✅ **H.265 (HEVC) 지원**: 하드웨어 가속 및 소프트웨어 디코딩
- ✅ **ExoPlayer 통합**: 고성능 비디오 재생
- ✅ **MediaCodec 지원**: 원시 H.265 데이터 디코딩
- ✅ **하드웨어 검출**: 디바이스 H.265 지원 자동 확인
- ✅ **다양한 해상도**: HD부터 4K까지 지원
- ✅ **RTSP 스트림**: 실시간 스트리밍 재생
- ✅ **콜백 시스템**: 재생 상태 및 오류 처리

## 시스템 요구사항

- **Android API 24+** (Android 7.0+)
- **ExoPlayer 2.18.7**
- **Java 11** 이상

## 설치 방법

### 1. AAR 파일 추가

1. `app-release.aar` 파일을 프로젝트의 `libs` 폴더에 복사합니다.

2. `app/build.gradle`에 다음을 추가합니다:

```gradle
dependencies {
    implementation files('libs/app-release.aar')
    
    // ExoPlayer 의존성 (필수)
    implementation 'com.google.android.exoplayer:exoplayer:2.18.7'
    implementation 'com.google.android.exoplayer:exoplayer-core:2.18.7'
    implementation 'com.google.android.exoplayer:exoplayer-ui:2.18.7'
    
    // MediaCodec 지원 (선택사항)
    implementation 'androidx.media:media:1.6.0'
}
```

### 2. 매니페스트 권한

`AndroidManifest.xml`에 필요한 권한을 추가합니다:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 사용 방법

### 기본 사용법 (ExoPlayer)

```kotlin
import com.xlab.Player.H265VideoPlayer
import com.google.android.exoplayer2.ui.PlayerView

class MainActivity : AppCompatActivity() {
    private lateinit var h265Player: H265VideoPlayer
    private lateinit var playerView: PlayerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        playerView = findViewById(R.id.player_view)
        
        // H.265 플레이어 초기화
        h265Player = H265VideoPlayer(this)
            .initializeWithPlayerView(playerView)
            .setPlaybackListener(object : H265VideoPlayer.PlaybackListener {
                override fun onPlayerReady() {
                    // 플레이어 준비 완료
                }
                
                override fun onPlaying() {
                    // 재생 시작
                }
                
                override fun onPaused() {
                    // 일시정지
                }
                
                override fun onEnded() {
                    // 재생 완료
                }
                
                override fun onBuffering() {
                    // 버퍼링 중
                }
                
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    // 비디오 크기 변경
                }
            })
            .setErrorListener(object : H265VideoPlayer.ErrorListener {
                override fun onError(error: String, exception: Exception?) {
                    // 오류 처리
                    Log.e("H265Player", "재생 오류: $error", exception)
                }
            })
        
        // H.265 비디오 파일 재생
        h265Player.playMediaFile("https://example.com/video.mp4")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        h265Player.release()
    }
}
```

### MediaCodec을 사용한 원시 데이터 재생

```kotlin
import android.view.SurfaceView

// SurfaceView를 사용한 초기화
val surfaceView: SurfaceView = findViewById(R.id.surface_view)

h265Player = H265VideoPlayer(this)
    .initializeWithSurfaceView(surfaceView)
    .setPlaybackListener(playbackListener)
    .setErrorListener(errorListener)

// 원시 H.265 데이터 재생
val h265Data: ByteArray = getH265DataFromStream()
h265Player.playRawH265Data(h265Data)
```

### H.265 하드웨어 지원 확인

```kotlin
import com.xlab.Player.H265HardwareUtil

// 기본 지원 확인
val support = H265HardwareUtil.analyzeH265Support()
if (support.isSupported) {
    Log.d("H265", "H.265 지원됨")
    Log.d("H265", "하드웨어 가속: ${support.hardwareAccelerated}")
    Log.d("H265", "권장 코덱: ${support.recommendedCodec}")
} else {
    Log.w("H265", "H.265가 지원되지 않습니다")
}

// 특정 해상도 지원 확인
val canPlay4K = H265HardwareUtil.canPlayH265AtResolution(3840, 2160)
Log.d("H265", "4K 재생 가능: $canPlay4K")

// 상세 디바이스 정보 로깅
H265HardwareUtil.logDeviceH265Info()
```

### 재생 제어

```kotlin
// 재생/일시정지 제어
h265Player.play()
h265Player.pause()
h265Player.stop()

// 탐색 (시크)
h265Player.seekTo(30000) // 30초 위치로 이동

// 재생 상태 확인
val isPlaying = h265Player.isPlaying()
val currentPosition = h265Player.getCurrentPosition()
val duration = h265Player.getDuration()
```

## 레이아웃 예제

### ExoPlayer용 레이아웃

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="200dp">
    
    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="true"
        app:show_buffering="when_playing"
        app:surface_type="surface_view" />
        
</FrameLayout>
```

### MediaCodec용 레이아웃

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:background="@android:color/black">
    
    <SurfaceView
        android:id="@+id/surface_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
        
</FrameLayout>
```

## API 레퍼런스

### H265VideoPlayer

| 메서드 | 설명 |
|--------|------|
| `initializeWithPlayerView(PlayerView)` | ExoPlayer 모드로 초기화 |
| `initializeWithSurfaceView(SurfaceView)` | MediaCodec 모드로 초기화 |
| `playMediaFile(String)` | 미디어 파일 재생 |
| `playRtspStream(String)` | RTSP 스트림 재생 |
| `playRawH265Data(ByteArray)` | 원시 H.265 데이터 재생 |
| `play()` | 재생 시작 |
| `pause()` | 일시정지 |
| `stop()` | 정지 |
| `seekTo(Long)` | 지정 위치로 이동 |
| `getCurrentPosition()` | 현재 재생 위치 |
| `getDuration()` | 전체 재생 시간 |
| `isPlaying()` | 재생 상태 확인 |
| `release()` | 리소스 해제 |

### H265HardwareUtil

| 메서드 | 설명 |
|--------|------|
| `analyzeH265Support()` | H.265 지원 분석 |
| `canPlayH265AtResolution(Int, Int)` | 해상도별 지원 확인 |
| `getRecommendedH265Settings()` | 권장 설정 반환 |
| `logDeviceH265Info()` | 디바이스 정보 로깅 |

## 디버깅 및 로그

라이브러리는 다음 태그로 상세한 로그를 제공합니다:

- `H265VideoPlayer`: 메인 플레이어 동작
- `H265MediaCodecManager`: MediaCodec 디코딩
- `H265HardwareUtil`: 하드웨어 지원 분석

로그 필터링 예제:
```bash
adb logcat | grep -E "(H265VideoPlayer|H265MediaCodecManager|H265HardwareUtil)"
```

## 성능 최적화

### 1. 하드웨어 가속 활용

```kotlin
val support = H265HardwareUtil.analyzeH265Support()
if (support.hardwareAccelerated) {
    // 하드웨어 가속을 사용한 최적화된 설정
    Log.d("H265", "하드웨어 가속 사용 가능")
} else {
    // 소프트웨어 디코딩 최적화
    Log.d("H265", "소프트웨어 디코딩 사용")
}
```

### 2. 메모리 관리

```kotlin
override fun onPause() {
    super.onPause()
    h265Player.pause()
}

override fun onDestroy() {
    super.onDestroy()
    h265Player.release() // 반드시 호출하여 메모리 누수 방지
}
```

## 알려진 제한사항

1. **RTSP 지원**: 현재 버전에서는 제한적으로 지원됩니다.
2. **Android 버전**: API 24 이하에서는 일부 기능이 제한될 수 있습니다.
3. **하드웨어 의존성**: H.265 하드웨어 지원은 디바이스에 따라 다릅니다.

## 문제 해결

### Q: H.265 재생이 안 됩니다
A: `H265HardwareUtil.logDeviceH265Info()`로 디바이스 지원을 먼저 확인해보세요.

### Q: 메모리 누수가 발생합니다
A: `onDestroy()`에서 반드시 `h265Player.release()`를 호출하세요.

### Q: RTSP 스트림이 재생되지 않습니다
A: 현재 버전에서는 RTSP 지원이 제한적입니다. 로컬 파일이나 HTTP 스트림을 시도해보세요.

## 버전 정보

- **버전**: 1.1.0
- **빌드 날짜**: 2025-01-27
- **ExoPlayer**: 2.19.1
- **최소 API**: 24 (Android 7.0)

### 버전 관리

앱 상단에 실시간 버전 정보가 표시됩니다:
```
🔧 v1.2 (빌드 #2) | 2025-01-27 14:30:15
```

소스 변경 후 버전 자동 증가:
```bash
# Linux/Mac
./update_version.sh

# Windows
update_version.bat
```

## 라이선스

이 라이브러리는 개발 및 테스트 목적으로 제공됩니다. 