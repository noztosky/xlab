package com.xlab.Player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
// import org.videolan.libvlc.util.VLCVideoLayout  // VLCVideoLayout이 없는 경우 주석 처리

/**
 * LibVLC Android를 사용한 H.265 비디오 플레이어
 * RTSP 스트림 재생에 특화되어 있으며 ExoPlayer 대안으로 사용
 */
class XlabPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "XlabPlayer"
    }
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    // private var vlcVideoLayout: VLCVideoLayout? = null  // VLCVideoLayout 사용 안함
    
    // 버퍼링 설정 (0.1초 단위, 기본값 2.0초)
    private var networkCachingSeconds: Float = 2.0f
    private var liveCachingSeconds: Float = 0.3f
    
    // 현재 재생 중인 URL 저장
    private var currentPlayingUrl: String? = null
    
    // 카메라 설정 관리
    private val cameraSettingsManager = CameraSettingsManager()
    private var currentCameraController: CameraController? = null
    private var selectedCameraInfo: CameraInfo? = null
    
    // 콜백 인터페이스들
    private var playbackListener: PlaybackListener? = null
    private var errorListener: ErrorListener? = null
    
    interface PlaybackListener {
        fun onPlayerReady()
        fun onBuffering()
        fun onPlaying()
        fun onPaused()
        fun onEnded()
        fun onVideoSizeChanged(width: Int, height: Int)
    }
    
    interface ErrorListener {
        fun onError(error: String, exception: Exception?)
    }
    
    /**
     * SurfaceView를 사용한 LibVLC 초기화
     */
    fun initializeWithSurfaceView(surfaceView: SurfaceView): XlabPlayer {
        this.surfaceView = surfaceView
        setupLibVLC()
        return this
    }
    
    /**
     * 네트워크 캐싱 시간 설정 (0.1초 단위)
     * @param seconds 캐싱 시간 (0.1 ~ 30.0초)
     */
    fun setNetworkCaching(seconds: Float): XlabPlayer {
        networkCachingSeconds = seconds.coerceIn(0.1f, 30.0f)
        Log.d(TAG, "네트워크 캐싱 설정: ${networkCachingSeconds}초")
        return this
    }
    
    /**
     * 라이브 스트림 캐싱 시간 설정 (0.1초 단위)
     * @param seconds 캐싱 시간 (0.1 ~ 5.0초)
     */
    fun setLiveCaching(seconds: Float): XlabPlayer {
        liveCachingSeconds = seconds.coerceIn(0.1f, 5.0f)
        Log.d(TAG, "라이브 캐싱 설정: ${liveCachingSeconds}초")
        return this
    }
    
    /**
     * 캐싱 설정을 한 번에 설정
     * @param networkSeconds 네트워크 캐싱 (0.1 ~ 30.0초)
     * @param liveSeconds 라이브 캐싱 (0.1 ~ 5.0초)
     */
    fun setCachingSettings(networkSeconds: Float, liveSeconds: Float): XlabPlayer {
        setNetworkCaching(networkSeconds)
        setLiveCaching(liveSeconds)
        return this
    }
    
    /**
     * 현재 캐싱 설정 반환
     */
    fun getCachingSettings(): Pair<Float, Float> {
        return Pair(networkCachingSeconds, liveCachingSeconds)
    }
    
    /**
     * 현재 재생 중인 URL 반환
     */
    fun getCurrentPlayingUrl(): String? {
        return currentPlayingUrl
    }
    
    /**
     * 버퍼시간 즉시 적용 (재생 중에도 적용 가능)
     */
    fun applyBufferTime(networkSeconds: Float, liveSeconds: Float) {
        try {
            val clampedNetwork = networkSeconds.coerceIn(0.1f, 30.0f)
            val clampedLive = liveSeconds.coerceIn(0.1f, 5.0f)
            
            networkCachingSeconds = clampedNetwork
            liveCachingSeconds = clampedLive
            
            Log.d(TAG, "버퍼시간 즉시 적용: 네트워크=${clampedNetwork}초, 라이브=${clampedLive}초")
            
            // 현재 재생 중인 URL이 있고, 실제로 재생 중인 경우에만 다시 재생
            currentPlayingUrl?.let { url ->
                if (mediaPlayer?.isPlaying == true) {
                    Log.d(TAG, "새로운 버퍼 설정으로 LibVLC 재초기화 및 스트림 다시 재생: $url")
                    
                    // 기존 미디어 정리
                    mediaPlayer?.stop()
                    
                    // LibVLC 완전 재초기화
                    reinitializeLibVLC()
                    
                    // 잠시 대기 후 다시 재생
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(1000) // 1초 대기 (재초기화 시간 고려)
                        playRtspStreamCompatible(url)
                    }
                } else {
                    Log.d(TAG, "재생 중이 아니므로 버퍼시간만 업데이트")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "버퍼시간 적용 실패", e)
        }
    }
    
    /**
     * LibVLC 완전 재초기화
     */
    private fun reinitializeLibVLC() {
        try {
            Log.d(TAG, "LibVLC 재초기화 시작")
            
            // 기존 리소스 정리
            mediaPlayer?.release()
            libVLC?.release()
            
            // 새로운 LibVLC 인스턴스 생성
            setupLibVLC()
            
            Log.d(TAG, "LibVLC 재초기화 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "LibVLC 재초기화 실패", e)
            errorListener?.onError("LibVLC 재초기화 실패: ${e.message}", e)
        }
    }
    
    /**
     * 빠른 연결 모드 (최소 지연시간)
     */
    fun setFastMode(): XlabPlayer {
        return setCachingSettings(0.3f, 0.1f)
    }
    
    /**
     * 안정적 연결 모드 (기본값)
     */
    fun setStableMode(): XlabPlayer {
        return setCachingSettings(2.0f, 0.3f)
    }
    
    /**
     * 고품질 연결 모드 (높은 안정성)
     */
    fun setHighQualityMode(): XlabPlayer {
        return setCachingSettings(5.0f, 0.5f)
    }
    
    /**
     * VLCVideoLayout을 사용한 LibVLC 초기화 (현재 사용 안함)
     */
    // fun initializeWithVLCVideoLayout(vlcVideoLayout: VLCVideoLayout): LibVLCVideoPlayer {
    //     this.vlcVideoLayout = vlcVideoLayout
    //     setupLibVLCWithVideoLayout()
    //     return this
    // }
    
    /**
     * LibVLC 설정 - SurfaceView 사용
     */
    private fun setupLibVLC() {
        try {
            // LibVLC 옵션 설정 (RTSP 스트림 최적화)
            val networkCachingMs = (networkCachingSeconds * 1000).toInt()
            val liveCachingMs = (liveCachingSeconds * 1000).toInt()
            
            val options = arrayListOf<String>().apply {
                // 네트워크 옵션 (사용자 설정값 사용)
                add("--network-caching=$networkCachingMs")
                add("--rtsp-tcp")
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                
                // 하드웨어 디코딩 옵션
                add("--codec=mediacodec_ndk,mediacodec_jni,all")
                add("--video-filter=")
                
                // RTSP 관련 옵션
                add("--rtsp-frame-buffer-size=500000")
                add("--live-caching=$liveCachingMs")
                
                // 로깅 최소화
                add("--verbose=0")
            }
            
            libVLC = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVLC!!)
            
            // SurfaceView에 연결
            surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface 생성됨")
                    attachVideoSurface(holder)
                }
                
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "Surface 변경됨: ${width}x${height}")
                    mediaPlayer?.getVLCVout()?.setWindowSize(width, height)
                }
                
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface 소멸됨")
                    detachVideoSurface()
                }
            })
            
            // 이벤트 리스너 설정
            setupEventListeners()
            
        } catch (e: Exception) {
            Log.e(TAG, "LibVLC 초기화 실패", e)
            errorListener?.onError("LibVLC 초기화 실패: ${e.message}", e)
        }
    }
    
    // VLCVideoLayout 사용 안함으로 주석 처리
    // private fun setupLibVLCWithVideoLayout() { ... }
    
    /**
     * 비디오 Surface 연결
     */
    private fun attachVideoSurface(holder: SurfaceHolder) {
        try {
            val vlcVout = mediaPlayer?.getVLCVout()
            vlcVout?.setVideoSurface(holder.surface, holder)
            vlcVout?.attachViews()
            Log.d(TAG, "비디오 Surface 연결 완료")
        } catch (e: Exception) {
            Log.e(TAG, "비디오 Surface 연결 실패", e)
        }
    }
    
    /**
     * 비디오 Surface 해제
     */
    private fun detachVideoSurface() {
        try {
            mediaPlayer?.getVLCVout()?.detachViews()
            Log.d(TAG, "비디오 Surface 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "비디오 Surface 해제 실패", e)
        }
    }
    
    /**
     * 이벤트 리스너 설정
     */
    private fun setupEventListeners() {
        mediaPlayer?.setEventListener { event ->
            Log.d(TAG, "LibVLC 이벤트: ${event.type}")
            
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    Log.d(TAG, "스트림 열기 시작")
                    playbackListener?.onBuffering()
                }
                
                MediaPlayer.Event.Buffering -> {
                    val percent = event.buffering
                    // 버퍼링 로그 스팸 방지 - 10% 단위로만 로깅
                    if (percent.toInt() % 10 == 0 || percent == 100f) {
                        Log.d(TAG, "버퍼링: ${percent}%")
                    }
                    if (percent < 100f) {
                        // 버퍼링 상태 업데이트도 10% 단위로만
                        if (percent.toInt() % 20 == 0) {
                            playbackListener?.onBuffering()
                        }
                    } else {
                        Log.d(TAG, "버퍼링 완료 (100%)")
                    }
                }
                
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "재생 시작")
                    playbackListener?.onPlayerReady()
                    playbackListener?.onPlaying()
                }
                
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "재생 일시정지")
                    playbackListener?.onPaused()
                }
                
                MediaPlayer.Event.Stopped -> {
                    Log.d(TAG, "재생 정지")
                    playbackListener?.onEnded()
                }
                
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "재생 완료")
                    playbackListener?.onEnded()
                }
                
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "LibVLC 재생 오류 발생")
                    errorListener?.onError("LibVLC 재생 오류", null)
                }
                
                MediaPlayer.Event.Vout -> {
                    val voutCount = event.voutCount
                    Log.d(TAG, "비디오 출력 초기화됨: $voutCount")
                    if (voutCount > 0) {
                        Log.d(TAG, "비디오 출력 활성화됨 - 영상 표시 시작")
                        // 비디오 크기 정보 가져오기
                        val videoTrack = mediaPlayer?.currentVideoTrack
                        videoTrack?.let { track ->
                            Log.d(TAG, "비디오 트랙 정보: ${track.width}x${track.height}")
                            playbackListener?.onVideoSizeChanged(track.width, track.height)
                        } ?: Log.w(TAG, "비디오 트랙 정보를 가져올 수 없음")
                    } else {
                        Log.w(TAG, "비디오 출력이 비활성화됨")
                    }
                }
                
                MediaPlayer.Event.ESAdded -> {
                    Log.d(TAG, "Elementary Stream 추가됨")
                }
                
                else -> {
                    Log.v(TAG, "기타 이벤트: ${event.type}")
                }
            }
        }
    }
    
    /**
     * RTSP 스트림 재생 - 기본 모드
     */
    fun playRtspStream(rtspUrl: String) {
        try {
            Log.d(TAG, "=== LibVLC RTSP 재생 시작 ===")
            Log.d(TAG, "URL: $rtspUrl")
            
            val media = Media(libVLC, Uri.parse(rtspUrl))
            
            // RTSP 스트림 전용 옵션 추가 (사용자 설정값 사용)
            val networkCachingMs = (networkCachingSeconds * 1000).toInt()
            media.addOption(":network-caching=$networkCachingMs")  // 사용자 설정 네트워크 캐싱
            media.addOption(":rtsp-tcp")  // TCP 모드 강제
            media.addOption(":rtsp-timeout=15")  // 15초 타임아웃
            media.addOption(":no-audio")  // 비디오만 재생
            
            mediaPlayer?.media = media
            mediaPlayer?.play()
            
            Log.d(TAG, "LibVLC RTSP 재생 시작됨")
            
        } catch (e: Exception) {
            Log.e(TAG, "RTSP 재생 실패", e)
            errorListener?.onError("RTSP 재생 실패: ${e.message}", e)
        }
    }
    
    /**
     * RTSP 스트림 재생 - UDP 모드
     */
    fun playRtspStreamUDP(rtspUrl: String) {
        try {
            Log.d(TAG, "=== LibVLC RTSP 재생 시작 (UDP 모드) ===")
            Log.d(TAG, "URL: $rtspUrl")
            
            val media = Media(libVLC, Uri.parse(rtspUrl))
            
            // UDP 모드 옵션 (사용자 설정값 사용)
            val networkCachingMs = (networkCachingSeconds * 1000).toInt()
            media.addOption(":network-caching=$networkCachingMs")  // 사용자 설정 캐싱
            media.addOption(":rtsp-tcp=0")  // UDP 모드
            media.addOption(":rtsp-timeout=10")  // 10초 타임아웃
            media.addOption(":no-audio")
            
            mediaPlayer?.media = media
            mediaPlayer?.play()
            
            Log.d(TAG, "LibVLC RTSP 재생 시작됨 (UDP)")
            
        } catch (e: Exception) {
            Log.e(TAG, "RTSP UDP 재생 실패", e)
            errorListener?.onError("RTSP UDP 재생 실패: ${e.message}", e)
        }
    }
    
    /**
     * RTSP 스트림 재생 - 호환성 모드 (오류 406 해결용)
     */
    fun playRtspStreamCompatible(rtspUrl: String) {
        try {
            Log.d(TAG, "=== LibVLC RTSP 재생 시작 (호환성 모드) ===")
            Log.d(TAG, "URL: $rtspUrl")
            
            // 현재 재생 URL 저장
            currentPlayingUrl = rtspUrl
            
            val media = Media(libVLC, Uri.parse(rtspUrl))
            
            // 호환성 최대화 옵션 (사용자 설정값 사용)
            val networkCachingMs = (networkCachingSeconds * 1000).toInt()
            val liveCachingMs = (liveCachingSeconds * 1000).toInt()
            media.addOption(":network-caching=$networkCachingMs")  // 사용자 설정 캐싱
            media.addOption(":rtsp-tcp")  // TCP 모드
            media.addOption(":rtsp-timeout=30")  // 30초 타임아웃 (충분한 대기)
            media.addOption(":rtsp-frame-buffer-size=000000")  // 1MB 프레임 버퍼
            media.addOption(":no-audio")
            media.addOption(":no-spu")
            media.addOption(":live-caching=$liveCachingMs")  // 사용자 설정 라이브 캐싱
            
            // H.265 디코딩 우선순위
            media.addOption(":codec=mediacodec_ndk,mediacodec_jni,avcodec")
            
            mediaPlayer?.media = media
            
            Log.d(TAG, "LibVLC MediaPlayer에 미디어 설정 완료")
            Log.d(TAG, "LibVLC 재생 시작...")
            
            mediaPlayer?.play()
            
            Log.d(TAG, "LibVLC RTSP 재생 시작됨 (호환성 모드)")
            Log.d(TAG, "LibVLC 상태: Playing=${mediaPlayer?.isPlaying}, Length=${mediaPlayer?.length}")
            
        } catch (e: Exception) {
            Log.e(TAG, "RTSP 호환성 모드 재생 실패", e)
            errorListener?.onError("RTSP 호환성 모드 재생 실패: ${e.message}", e)
        }
    }
    
    /**
     * 미디어 파일 재생
     */
    fun playMediaFile(filePath: String) {
        try {
            Log.d(TAG, "미디어 파일 재생 시작: $filePath")
            
            val media = Media(libVLC, Uri.parse(filePath))
            mediaPlayer?.media = media
            mediaPlayer?.play()
            
        } catch (e: Exception) {
            Log.e(TAG, "파일 재생 실패", e)
            errorListener?.onError("미디어 파일 재생 실패: ${e.message}", e)
        }
    }
    
    /**
     * 재생 시작/재개
     */
    fun play() {
        mediaPlayer?.play()
        Log.d(TAG, "재생 시작/재개")
    }
    
    /**
     * 재생 일시정지
     */
    fun pause() {
        mediaPlayer?.pause()
        Log.d(TAG, "재생 일시정지")
    }
    
    /**
     * 재생 정지
     */
    fun stop() {
        mediaPlayer?.stop()
        Log.d(TAG, "재생 정지")
    }
    
    /**
     * 재생 위치 설정 (밀리초)
     */
    fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }
    
    /**
     * 현재 재생 위치 반환 (밀리초)
     */
    fun getCurrentPosition(): Long {
        return mediaPlayer?.time ?: 0L
    }
    
    /**
     * 전체 재생 시간 반환 (밀리초)
     */
    fun getDuration(): Long {
        return mediaPlayer?.length ?: 0L
    }
    
    /**
     * 재생 중인지 확인
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    /**
     * 볼륨 설정 (0-100)
     */
    fun setVolume(volume: Int) {
        mediaPlayer?.volume = volume.coerceIn(0, 100)
    }
    
    /**
     * 현재 볼륨 반환 (0-100)
     */
    fun getVolume(): Int {
        return mediaPlayer?.volume ?: 0
    }
    
    /**
     * 재생 속도 설정 (0.25 ~ 4.0)
     */
    fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.rate = speed.coerceIn(0.25f, 4.0f)
    }
    
    /**
     * 비디오 정보 반환
     */
    fun getVideoInfo(): String {
        val videoTrack = mediaPlayer?.currentVideoTrack
        return if (videoTrack != null) {
            "해상도: ${videoTrack.width}x${videoTrack.height}\n" +
            "프레임률: ${videoTrack.frameRateNum}/${videoTrack.frameRateDen}\n" +
            "코덱: ${videoTrack.codec}"
        } else {
            "비디오 정보 없음"
        }
    }
    
    /**
     * 콜백 리스너 설정
     */
    fun setPlaybackListener(listener: PlaybackListener): XlabPlayer {
        this.playbackListener = listener
        return this
    }
    
    fun setErrorListener(listener: ErrorListener): XlabPlayer {
        this.errorListener = listener
        return this
    }
    
    /**
     * 사용 가능한 카메라 목록 반환
     */
    fun getAvailableCameras(): List<CameraInfo> {
        return cameraSettingsManager.getAvailableCameras()
    }
    
    /**
     * 특정 기능을 지원하는 카메라 목록 반환
     */
    fun getCamerasByCapability(capability: CameraCapability): List<CameraInfo> {
        return cameraSettingsManager.getCamerasByCapability(capability)
    }
    
    /**
     * 카메라 ID로 카메라 설정
     * @param cameraId 카메라 ID
     * @param customSettings 사용자 정의 설정 (null이면 기본 설정 사용)
     */
    fun setupCameraById(cameraId: String, customSettings: CameraConnectionSettings? = null): XlabPlayer {
        val cameraInfo = cameraSettingsManager.getCameraById(cameraId)
        if (cameraInfo == null) {
            Log.e(TAG, "카메라 ID를 찾을 수 없습니다: $cameraId")
            return this
        }
        
        return setupCamera(cameraInfo, customSettings)
    }
    
    /**
     * 카메라 정보로 카메라 설정
     * @param cameraInfo 카메라 정보
     * @param customSettings 사용자 정의 설정 (null이면 기본 설정 사용)
     */
    fun setupCamera(cameraInfo: CameraInfo, customSettings: CameraConnectionSettings? = null): XlabPlayer {
        val settings = customSettings ?: cameraInfo.defaultSettings
        
        // 카메라 타입에 따른 컨트롤러 생성
        currentCameraController = when (cameraInfo.id) {
            "c12_ptz" -> C12CameraController()
            // 여기에 다른 카메라 컨트롤러들을 추가할 수 있습니다
            // "hikvision_dome" -> HikvisionCameraController()
            // "axis_ptz" -> AxisCameraController()
            else -> {
                Log.e(TAG, "지원되지 않는 카메라 타입: ${cameraInfo.id}")
                return this
            }
        }
        
        val success = currentCameraController?.setupCamera(settings) ?: false
        if (success) {
            selectedCameraInfo = cameraInfo
            Log.d(TAG, "카메라 설정 완료: ${cameraInfo.name} (${settings.baseUrl})")
        } else {
            Log.e(TAG, "카메라 설정 실패: ${cameraInfo.name}")
            currentCameraController = null
        }
        
        return this
    }
    
    /**
     * C12 카메라 빠른 설정 (하위 호환성)
     * @param baseUrl 카메라의 기본 URL (예: "http://192.168.144.108")
     * @param username 카메라 사용자명
     * @param password 카메라 비밀번호
     */
    fun setupC12Camera(baseUrl: String, username: String = "admin", password: String = ""): XlabPlayer {
        val customSettings = CameraConnectionSettings(
            baseUrl = baseUrl.trimEnd('/'),
            username = username,
            password = password
        )
        return setupCameraById("c12_ptz", customSettings)
    }
    
    /**
     * 현재 선택된 카메라 정보 반환
     */
    fun getCurrentCameraInfo(): CameraInfo? {
        return selectedCameraInfo
    }
    
    /**
     * 현재 카메라가 특정 기능을 지원하는지 확인
     */
    fun isCameraCapabilitySupported(capability: CameraCapability): Boolean {
        return selectedCameraInfo?.capabilities?.contains(capability) ?: false
    }
    
    /**
     * 카메라 팬(Pan) 각도 설정
     * @param angle 팬 각도
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setCameraPan(angle: Float, callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.PTZ_CONTROL)) {
            callback?.invoke(false, "현재 카메라는 PTZ 제어를 지원하지 않습니다")
            return
        }
        currentCameraController?.panTo(angle, callback)
    }
    
    /**
     * 카메라 틸트(Tilt) 각도 설정
     * @param angle 틸트 각도
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setCameraTilt(angle: Float, callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.PTZ_CONTROL)) {
            callback?.invoke(false, "현재 카메라는 PTZ 제어를 지원하지 않습니다")
            return
        }
        currentCameraController?.tiltTo(angle, callback)
    }
    
    /**
     * 카메라 줌 레벨 설정
     * @param level 줌 레벨
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setCameraZoom(level: Float, callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.ZOOM_CONTROL) && 
            !isCameraCapabilitySupported(CameraCapability.PTZ_CONTROL)) {
            callback?.invoke(false, "현재 카메라는 줌 제어를 지원하지 않습니다")
            return
        }
        currentCameraController?.zoomTo(level, callback)
    }
    
    /**
     * 카메라 사진 촬영
     * @param callback 결과 콜백 (성공/실패)
     */
    fun capturePhoto(callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.PHOTO_CAPTURE)) {
            callback?.invoke(false, "현재 카메라는 사진 촬영을 지원하지 않습니다")
            return
        }
        currentCameraController?.capturePhoto(callback)
    }
    
    /**
     * 카메라 녹화 시작
     * @param callback 결과 콜백 (성공/실패)
     */
    fun startCameraRecording(callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.VIDEO_RECORDING)) {
            callback?.invoke(false, "현재 카메라는 비디오 녹화를 지원하지 않습니다")
            return
        }
        currentCameraController?.startRecording(callback)
    }
    
    /**
     * 카메라 녹화 정지
     * @param callback 결과 콜백 (성공/실패)
     */
    fun stopCameraRecording(callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.VIDEO_RECORDING)) {
            callback?.invoke(false, "현재 카메라는 비디오 녹화를 지원하지 않습니다")
            return
        }
        currentCameraController?.stopRecording(callback)
    }
    
    /**
     * 카메라 PTZ 상태 조회
     * @param callback 결과 콜백 (성공/실패, 메시지, 상태 정보)
     */
    fun getCameraPTZStatus(callback: ((Boolean, String, Map<String, Any>?) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.PTZ_CONTROL)) {
            callback?.invoke(false, "현재 카메라는 PTZ 제어를 지원하지 않습니다", null)
            return
        }
        currentCameraController?.getPTZStatus(callback)
    }
    
    /**
     * 카메라 프리셋 위치로 이동
     * @param presetId 프리셋 ID
     * @param callback 결과 콜백 (성공/실패)
     */
    fun moveCameraToPreset(presetId: Int, callback: ((Boolean, String) -> Unit)? = null) {
        if (!isCameraCapabilitySupported(CameraCapability.PRESET_POSITIONS)) {
            callback?.invoke(false, "현재 카메라는 프리셋 기능을 지원하지 않습니다")
            return
        }
        currentCameraController?.moveToPreset(presetId, callback)
    }
    
    /**
     * C12 카메라 팬(Pan) 각도 설정 (하위 호환성)
     * @param angle 팬 각도 (-180 ~ 180도)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setC12Pan(angle: Float, callback: ((Boolean, String) -> Unit)? = null) {
        setCameraPan(angle, callback)
    }
    
    /**
     * C12 카메라 틸트(Tilt) 각도 설정 (하위 호환성)
     * @param angle 틸트 각도 (-90 ~ 90도)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setC12Tilt(angle: Float, callback: ((Boolean, String) -> Unit)? = null) {
        setCameraTilt(angle, callback)
    }
    
    /**
     * C12 카메라 요(Yaw) 각도 설정 (Pan과 동일, 하위 호환성)
     * @param angle 요 각도 (-180 ~ 180도)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun setC12Yaw(angle: Float, callback: ((Boolean, String) -> Unit)? = null) {
        setCameraPan(angle, callback)
    }
    
    /**
     * C12 카메라 현재 PTZ 각도 읽어오기 (하위 호환성)
     * @param callback 결과 콜백 (pan, tilt, yaw 각도)
     */
    fun getC12PTZAngles(callback: (Boolean, Float?, Float?, Float?, String) -> Unit) {
        // 새로운 카메라 시스템 사용
        getCameraPTZStatus { success, message, status ->
            if (success && status != null) {
                val pan = status["pan"]?.toString()?.toFloatOrNull() ?: 0.0f
                val tilt = status["tilt"]?.toString()?.toFloatOrNull() ?: 0.0f
                val yaw = pan // yaw는 pan과 동일
                callback(true, pan, tilt, yaw, message)
            } else {
                callback(false, null, null, null, message)
            }
        }
    }
    
    /**
     * C12 카메라 사진 촬영 (하위 호환성)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun captureC12Photo(callback: ((Boolean, String) -> Unit)? = null) {
        capturePhoto(callback)
    }
    
    /**
     * C12 카메라 녹화 시작 (하위 호환성)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun startC12Recording(callback: ((Boolean, String) -> Unit)? = null) {
        startCameraRecording(callback)
    }
    
    /**
     * C12 카메라 녹화 정지 (하위 호환성)
     * @param callback 결과 콜백 (성공/실패)
     */
    fun stopC12Recording(callback: ((Boolean, String) -> Unit)? = null) {
        stopCameraRecording(callback)
    }

    /**
     * 리소스 해제
     */
    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            mediaPlayer = null
            
            libVLC?.release()
            libVLC = null
            
            surfaceView = null
            // vlcVideoLayout = null  // 사용 안함
            playbackListener = null
            errorListener = null
            
            Log.d(TAG, "XlabPlayer 리소스 해제 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "리소스 해제 중 오류", e)
        }
    }
}