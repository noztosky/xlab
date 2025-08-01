package com.xlab.Player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.min

class XLABPlayer(private val context: Context) : LifecycleObserver {
    // VLC 미디어 관련
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var media: Media? = null
    private var videoLayout: VLCVideoLayout? = null
    private var parentViewGroup: ViewGroup? = null
    private var activity: Activity? = null

    // 기본 상태 변수들
    private var isInitialized = false
    private var isPlaying = false
    private var isConnected = false
    private var currentUrl = ""
    private var isFullscreen = false
    private var isRecording = false
    private var isPtzVisible = false
    private var currentCameraId = 1
    private var isReceiverRegistered = false
    
    // UI 컴포넌트들
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var fullscreenLayoutParams: ViewGroup.LayoutParams? = null
    private var fullscreenButton: XLABPlayerButton? = null
    private var recordButton: XLABPlayerButton? = null
    private var captureButton: XLABPlayerButton? = null
    private var ptzContainer: FrameLayout? = null
    private var configurationReceiver: BroadcastReceiver? = null
    
    // 전체화면 버튼 마진 상수 (픽셀 단위)
    private val FULLSCREEN_BUTTON_MARGIN = 10
    private var currentCameraController: CameraController? = null
    private var currentCameraInfo: CameraInfo? = null
    private var ptzController: C12PTZController? = null // C12 전용 컨트롤러
    
    // PTZ 조종 속도 설정 (기본값: 10.0f, 범위: 0.5f ~ 10.0f)
    private var ptzMoveSpeed: Float = 10.0f

    interface PlayerCallback {
        fun onPlayerReady()
        fun onPlayerConnected()
        fun onPlayerDisconnected()
        fun onPlayerPlaying()
        fun onPlayerPaused()
        fun onPlayerError(error: String)
        fun onVideoSizeChanged(width: Int, height: Int)
        fun onPtzCommand(command: String, success: Boolean)
        fun onFullscreenEntered()
        fun onFullscreenExited()
    }
    private var playerCallback: PlayerCallback? = null
    private var isLifecycleRegistered = false
    private var buttonContainer: LinearLayout? = null
    private val playerButtons = mutableListOf<XLABPlayerButton>()
    
    init {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isLifecycleRegistered = true
        } catch (e: Exception) { }
        
        configurationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                    videoLayout?.post {
                        if (isFullscreen) activity?.let { adjustVideoScaleForFullscreen(videoLayout!!, it) }
                        else setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        updatePtzPosition()
                    }
                }
            }
        }
    }

    /**
     * 카메라 서버 설정 (카메라 타입 선택 가능)
     * @param cameraType 카메라 타입 ("c12", "hikvision", "axis" 등)
     * @param serverUrl 카메라 서버 URL (예: "http://192.168.144.108:5000")
     * @param cameraId 카메라 ID (기본값: 1)
     * @param username 사용자명 (기본값: "admin")
     * @param password 비밀번호 (기본값: "")
     */
    fun setCameraServer(
        cameraType: String = "c12", 
        serverUrl: String, 
        cameraId: Int = 1,
        username: String = "admin",
        password: String = ""
    ) {
        this.currentCameraId = cameraId
        
        // 카메라 타입에 따른 컨트롤러 생성
        when (cameraType.lowercase()) {
            "c12" -> {
                val c12Controller = C12PTZController().apply {
                    configureWithUrl(serverUrl, username, password)
                    connect(object : C12PTZController.ConnectionCallback {
                        override fun onSuccess(message: String) {
                            android.util.Log.d("XLABPlayer", "C12 PTZ 연결 성공: $message")
                            
                            // 연결 성공 후 녹화 해상도를 최대(4K)로 설정
                            setVideoResolution(3, object : C12PTZController.ResolutionCallback {
                                override fun onSuccess(resolution: Int, message: String) {
                                    android.util.Log.d("XLABPlayer", "C12 해상도 설정 성공: $message")
                                }
                                override fun onError(error: String) {
                                    android.util.Log.w("XLABPlayer", "C12 해상도 설정 실패: $error")
                                }
                            })
                        }
                        override fun onError(error: String) {
                            android.util.Log.w("XLABPlayer", "C12 PTZ 연결 실패: $error")
                        }
                    })
                }
                currentCameraController = null // C12는 별도 처리
                currentCameraInfo = CameraInfo(
                    id = "c12_ptz",
                    name = "C12 PTZ Camera",
                    manufacturer = "C12",
                    model = "PTZ-001",
                    capabilities = listOf(
                        CameraCapability.PTZ_CONTROL,
                        CameraCapability.ZOOM_CONTROL,
                        CameraCapability.PRESET_POSITIONS
                    ),
                    defaultSettings = CameraConnectionSettings(serverUrl, username, password)
                )
                // C12 전용 컨트롤러 저장 (기존 방식 유지)
                ptzController = c12Controller
            }
            else -> {
                android.util.Log.w("XLABPlayer", "지원되지 않는 카메라 타입: $cameraType")
                return
            }
        }
        
        android.util.Log.d("XLABPlayer", "카메라 설정 완료: $cameraType - $serverUrl")
    }

    /**
     * 기존 방식 호환성 유지 (C12 전용)
     */
    @Deprecated("setCameraServer를 사용하세요", ReplaceWith("setCameraServer(\"c12\", serverUrl, cameraId)"))
    fun setPtzServer(serverUrl: String, cameraId: Int = 1) {
        setCameraServer("c12", serverUrl, cameraId)
    }

    /**
     * PTZ 컨트롤 표시/숨김 토글
     */
    fun togglePtzControl() = if (isPtzVisible) hidePtzControl() else showPtzControl()

    /**
     * PTZ 컨트롤 표시
     */
    fun showPtzControl() {
        if (ptzContainer != null || parentViewGroup == null) return
        (parentViewGroup as? FrameLayout)?.let {
            createPtzControl()
            isPtzVisible = true
        }
    }

    /**
     * PTZ 컨트롤 숨김
     */
    fun hidePtzControl() {
        ptzContainer?.let { container ->
            parentViewGroup?.removeView(container)
            ptzContainer = null
            isPtzVisible = false
        }
    }

    /**
     * PTZ 컨트롤 생성 (사각형 형태)
     */
    private fun createPtzControl() {
        val parent = parentViewGroup as? FrameLayout ?: return
        
        ptzContainer = FrameLayout(context).apply {
            // 투명 배경 (클릭해도 사라지지 않음)
            setBackgroundColor(0x00000000)
            
            // PTZ 버튼 컨테이너 (배경 제거)
            val ptzLayout = RelativeLayout(context).apply {
                val size = 350 // PTZ 컨트롤 전체 크기
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(30, 30, 30, 30)
                }
                // 배경 제거 (투명하게)
                setBackgroundColor(0x00000000)
            }
            
            addView(ptzLayout)
            createPtzButtons(ptzLayout)
        }
        
        parent.addView(ptzContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    /**
     * PTZ 버튼들 생성 (사각형 배치)
     */
    private fun createPtzButtons(container: RelativeLayout) {
        val centerX = 175 // 중심 X 좌표
        val centerY = 175 // 중심 Y 좌표
        val buttonSize = 88 // 개별 버튼 크기 (80 → 88, 10% 증가)
        val distance = 132 // 버튼 중심 간 거리 (88/2 + 5px 간격 + 88/2 = 132)
        
        val callback = object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                // 팝업 제거 - 빠른 응답을 위해 콜백 생략
            }
            
            override fun onError(error: String) {
                // 에러만 로그로 기록
                android.util.Log.w("XLABPlayer", "PTZ 제어 실패: $error")
            }
        }
        
        // 위쪽 버튼 (UP) - 틸트 증가
        createPtzButton(container, "▲", centerX, centerY - distance, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(0f, ptzMoveSpeed, callback) }
        }
        
        // 아래쪽 버튼 (DOWN) - 틸트 감소
        createPtzButton(container, "▼", centerX, centerY + distance, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(0f, -ptzMoveSpeed, callback) }
        }
        
        // 왼쪽 버튼 (LEFT) - 팬 감소
        createPtzButton(container, "◀", centerX - distance, centerY, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(-ptzMoveSpeed, 0f, callback) }
        }
        
        // 오른쪽 버튼 (RIGHT) - 팬 증가
        createPtzButton(container, "▶", centerX + distance, centerY, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(ptzMoveSpeed, 0f, callback) }
        }
        
        // 중앙 홈 버튼 (HOME)
        createPtzButton(container, "■", centerX, centerY, buttonSize) {
            ensurePtzConnection { ptzController?.moveToHome(callback) }
        }
    }

    /**
     * PTZ 연결 상태 확인 및 자동 연결
     */
    private fun ensurePtzConnection(action: () -> Unit) {
        val controller = ptzController
        if (controller == null) {
            android.util.Log.w("XLABPlayer", "PTZ 컨트롤러가 초기화되지 않았습니다")
            return
        }
        
        if (controller.isConnected()) {
            action()
        } else {
            android.util.Log.d("XLABPlayer", "PTZ 재연결 시도 중...")
            controller.connect(object : C12PTZController.ConnectionCallback {
                override fun onSuccess(message: String) {
                    android.util.Log.d("XLABPlayer", "PTZ 재연결 성공: $message")
                    action()
                }
                override fun onError(error: String) {
                    android.util.Log.w("XLABPlayer", "PTZ 재연결 실패: $error")
                }
            })
        }
    }

    /**
     * 개별 PTZ 버튼 생성 (XLABPlayerButton 사용)
     */
    private fun createPtzButton(
        container: RelativeLayout,
        text: String,
        x: Int,
        y: Int,
        buttonSize: Int,
        onClick: () -> Unit
    ) {
        val ptzButton = XLABPlayerButton.create(
            context, 
            text, 
            XLABPlayerButton.ButtonType.SECONDARY,
            onClick
        ).apply {
            // PTZ 전용 스타일링
            setAsPtzButton()
        }
        
        val layoutParams = RelativeLayout.LayoutParams(buttonSize, buttonSize).apply {
            leftMargin = x - buttonSize / 2
            topMargin = y - buttonSize / 2
        }
        
        ptzButton.buttonView.layoutParams = layoutParams
        container.addView(ptzButton.buttonView)
    }

    /**
     * PTZ 위치 업데이트 (화면 회전 시)
     */
    private fun updatePtzPosition() {
        if (isPtzVisible) {
            hidePtzControl()
            showPtzControl()
        }
    }

    /**
     * 카메라 변경
     */
    fun setCameraId(cameraId: Int) {
        this.currentCameraId = cameraId
    }
    


    /**
     * PTZ 버튼 추가 (메인 컨트롤에)
     */
    fun addPtzToggleButton(): XLABPlayerButton {
        return addButton("PTZ", XLABPlayerButton.ButtonType.SECONDARY) {
            togglePtzControl()
        }
    }
    


    fun initialize(parent: ViewGroup): Boolean {
        return try {
            this.parentViewGroup = parent
            
            if (context is Activity) {
                this.activity = context
                try {
                    if (!isReceiverRegistered && configurationReceiver != null) {
                        context.registerReceiver(configurationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
                        isReceiverRegistered = true
                    }
                } catch (e: Exception) { }
            }

            if (context == null) {
                playerCallback?.onPlayerError("Context가 null입니다")
                return false
            }
            
            libVLC = LibVLC(context, arrayListOf(
                "--intf=dummy",
                "--network-caching=0",    // 비디오 속도 최적화: 네트워크 캐싱 최소화
                "--no-audio",
                "--rtsp-caching=0",
                "--drop-late-frames",
                "--skip-frames", 
                "--avcodec-fast",
                "--live-caching=0",
                "--codec=avcodec",
                "--avcodec-hw=any",
                "--no-stats",
                "--no-osd",                
                "--avcodec-threads=0",
                "--file-caching=0",       // 파일 캐싱 최소화
                "--sout-mux-caching=0"    // 출력 캐싱 최소화


            ))
            mediaPlayer = MediaPlayer(libVLC)

            videoLayout?.let { parent.removeView(it) }
            videoLayout = VLCVideoLayout(context).apply { 
                parent.addView(this)
                
                // 원본 및 전체화면 레이아웃 파라미터 설정
                originalLayoutParams = this.layoutParams
                fullscreenLayoutParams = when (parent) {
                    is FrameLayout -> FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    is ViewGroup -> ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    else -> originalLayoutParams
                }
                
                addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val newWidth = right - left
                    val newHeight = bottom - top
                    val oldWidth = oldRight - oldLeft
                    val oldHeight = oldBottom - oldTop
                    
                    if (newWidth != oldWidth || newHeight != oldHeight) {
                        view.post {
                            if (isFullscreen) activity?.let { adjustVideoScaleForFullscreen(view as VLCVideoLayout, it) }
                            else setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        }
                    }
                }
            }
            
            mediaPlayer?.attachViews(videoLayout!!, null, true, false)
            mediaPlayer?.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            isPlaying = true
                            isConnected = true
                            updateButtonStates()
                            playerCallback?.onPlayerPlaying()
                            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        }
                        MediaPlayer.Event.Paused -> {
                            isPlaying = false
                            updateButtonStates()
                            playerCallback?.onPlayerPaused()
                        }
                        MediaPlayer.Event.Stopped -> {
                            isPlaying = false
                            isConnected = false
                            updateButtonStates()
                            playerCallback?.onPlayerDisconnected()
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            isPlaying = false
                            isConnected = false
                            playerCallback?.onPlayerError("재생 오류")
                        }
                        MediaPlayer.Event.Vout -> {
                            if (event.voutCount > 0) setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        }
                    }
                }
            })
            
            isInitialized = true
            playerCallback?.onPlayerReady()
            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
            true
        } catch (e: Exception) {
            playerCallback?.onPlayerError("초기화 실패: ${e.message ?: "알 수 없는 오류 - ${e.javaClass.simpleName}"}")
            false
        }
    }

    fun connectAndPlay(url: String = "rtsp://192.168.144.108:554/stream=1"): Boolean {
        return try {
            if (!isInitialized) {
                playerCallback?.onPlayerError("플레이어가 초기화되지 않음")
                return false
            }
            
            try { media?.release() } catch (e: Exception) { }
            media = null
            
            media = Media(libVLC, Uri.parse(url)).apply { 
                // 비디오 속도 최적화: 캐싱 완전 제거 - 즉시 재생
                addOption(":network-caching=0")
                addOption(":file-caching=0") 
                addOption(":live-caching=0")
                addOption(":rtsp-caching=0")
                addOption(":sout-mux-caching=0")

                // 오디오 제거
                addOption(":no-audio")

                // 즉시 재생 시작
                addOption(":start-time=0")
                addOption(":run-time=0") 
                addOption(":no-video-title-show")
                addOption(":no-snapshot-preview")

                // 최소 버퍼링으로 빠른 재생
                addOption(":avcodec-fast")
                addOption(":avcodec-skiploopfilter=all")
                addOption(":avcodec-skip-frame=0")     // 프레임 스킵 최소화
                addOption(":avcodec-threads=1")

                // 동기화 무시하고 빠른 재생
                addOption(":no-audio-sync") 
                addOption(":clock-jitter=0")
                addOption(":fps-trust")

                // RTSP 최적화
                addOption(":rtsp-frame-buffer-size=0")

                // 즉시 디스플레이
                addOption(":vout-display-delay=0")
                addOption(":audio-delay=0")
                addOption(":drop-late-frames")          // 지연된 프레임만 드랍
            }
            mediaPlayer?.media = media
            mediaPlayer?.play()
            
            currentUrl = url
            isConnected = true
            playerCallback?.onPlayerConnected()
            true
        } catch (e: Exception) {
            playerCallback?.onPlayerError("연결 실패: ${e.message}")
            false
        }
    }

    fun play(): Boolean = executeAction(isConnected && !isPlaying) {
        mediaPlayer?.play()
        isPlaying = true
        playerCallback?.onPlayerPlaying()
    }

    fun pause(): Boolean = executeAction(isPlaying) {
        mediaPlayer?.pause()
        isPlaying = false
        playerCallback?.onPlayerPaused()
    }

    fun stop(): Boolean = executeAction(true) {
        mediaPlayer?.stop()
        isPlaying = false
        isConnected = false
        playerCallback?.onPlayerDisconnected()
    }

    private fun executeAction(condition: Boolean, action: () -> Unit): Boolean {
        return try {
            if (condition) { action(); true } else false
        } catch (e: Exception) { false }
    }

    fun disconnect(): Boolean {
        return try {
            stop()
            
            // disconnect는 비디오 연결만 해제, PTZ는 유지
            // (PTZ는 release()에서만 해제)
            
            try { media?.release() } catch (e: Exception) { }
            media = null
            isConnected = false
            currentUrl = ""
            android.util.Log.d("XLABPlayer", "비디오 연결 해제됨 (PTZ 연결 유지)")
            true
        } catch (e: Exception) { false }
    }
    
    /**
     * PTZ 연결만 해제 (필요시 수동 호출)
     */
    fun disconnectPTZ(): Boolean {
        return try {
            ptzController?.disconnect()
            ptzController = null
            android.util.Log.d("XLABPlayer", "PTZ 연결 해제됨")
            true
        } catch (e: Exception) {
            android.util.Log.w("XLABPlayer", "PTZ 해제 중 오류: ${e.message}")
            false
        }
    }

    fun release() {
        try {
            if (isFullscreen) exitFullscreen()
            hidePtzControl()
            
            // PTZ 컨트롤러 연결 해제 (중요!)
            try {
                ptzController?.disconnect()
                ptzController = null
                android.util.Log.d("XLABPlayer", "PTZ 컨트롤러 연결 해제 완료")
            } catch (e: Exception) {
                android.util.Log.w("XLABPlayer", "PTZ 컨트롤러 해제 중 오류: ${e.message}")
            }
            
            try {
                if (isReceiverRegistered && configurationReceiver != null) {
                    context.unregisterReceiver(configurationReceiver)
                    isReceiverRegistered = false
                }
            } catch (e: Exception) { }
            
            if (isPlaying || isConnected) {
                mediaPlayer?.stop()
                Thread.sleep(200)
            }
            
            try { media?.release() } catch (e: Exception) { }
            media = null
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            mediaPlayer = null
            libVLC?.release()
            libVLC = null
            
            videoLayout?.let { parentViewGroup?.removeView(it) }
            videoLayout = null
            parentViewGroup = null
            
            if (isLifecycleRegistered) {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                isLifecycleRegistered = false
            }
            
            isInitialized = false
            isPlaying = false
            isConnected = false
            currentUrl = ""
        } catch (e: Exception) { }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackground() { if (isPlaying) pause() }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForeground() { }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppDestroy() { release() }

    fun setCallback(callback: PlayerCallback) { this.playerCallback = callback }

    fun addButtonContainer(parent: ViewGroup): LinearLayout {
        buttonContainer?.let { parent.removeView(it) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 8, 16, 8)
            buttonContainer = this
            parent.addView(this)
        }
    }
    
    fun addButton(
        text: String,
        type: XLABPlayerButton.ButtonType = XLABPlayerButton.ButtonType.PRIMARY,
        clickListener: (() -> Unit)? = null
    ): XLABPlayerButton {
        return XLABPlayerButton.create(context, text, type, clickListener).also {
            playerButtons.add(it)
            buttonContainer?.addView(it.buttonView)
        }
    }
    
    fun addButtons(vararg buttonConfigs: Triple<String, XLABPlayerButton.ButtonType, (() -> Unit)?>): List<XLABPlayerButton> {
        return buttonConfigs.map { (text, type, listener) -> addButton(text, type, listener) }
    }
    
    fun addDefaultControlButtons(): Map<String, XLABPlayerButton> {
        return mapOf(
            "connect" to addButton("연결", XLABPlayerButton.ButtonType.PRIMARY) { connectAndPlay() },
            "play" to addButton("재생", XLABPlayerButton.ButtonType.SUCCESS) { play() },
            "pause" to addButton("일시정지", XLABPlayerButton.ButtonType.WARNING) { pause() },
            "stop" to addButton("정지", XLABPlayerButton.ButtonType.SECONDARY) { stop() },
            "disconnect" to addButton("해제", XLABPlayerButton.ButtonType.DANGER) { disconnect() }
        )
    }
    
    fun addScalingControlButton(): XLABPlayerButton {
        var currentMode = VideoScaleMode.FIT_WINDOW
        return addButton("맞춤", XLABPlayerButton.ButtonType.SECONDARY) {
            currentMode = when (currentMode) {
                VideoScaleMode.FIT_WINDOW -> VideoScaleMode.FILL_WINDOW
                VideoScaleMode.FILL_WINDOW -> VideoScaleMode.STRETCH
                VideoScaleMode.STRETCH -> VideoScaleMode.ORIGINAL_SIZE
                VideoScaleMode.ORIGINAL_SIZE -> VideoScaleMode.FIT_WINDOW
            }
            setVideoScaleMode(currentMode)
        }
    }
    
    fun updateButtonStates() {
        playerButtons.forEach { button ->
            button.isEnabled = when (button.buttonView.text.toString()) {
                "연결" -> isInitialized && !isConnected
                "재생" -> isConnected && !isPlaying
                "일시정지" -> isConnected && isPlaying
                "정지", "해제" -> isConnected
                else -> true
            }
        }
    }
    
    fun clearButtons() {
        buttonContainer?.removeAllViews()
        playerButtons.clear()
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        videoLayout?.let { layout ->
            val params = layout.layoutParams
            when (mode) {
                VideoScaleMode.FIT_WINDOW -> {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layout.layoutParams = params
                    
                    // 일반 모드에서는 컨테이너에 맞춰 1.0 스케일 적용
                    layout.scaleX = 1.0f
                    layout.scaleY = 1.0f
                    
                    android.util.Log.d("XLABPlayer", "FIT_WINDOW 모드: 컨테이너 기준 스케일 1.0 적용")
                }
                VideoScaleMode.FILL_WINDOW -> {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layout.layoutParams = params
                    layout.scaleX = 3.0f
                    layout.scaleY = 3.0f
                }
                VideoScaleMode.ORIGINAL_SIZE -> {
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    layout.layoutParams = params
                    layout.scaleX = 1f
                    layout.scaleY = 1f
                }
                VideoScaleMode.STRETCH -> {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layout.layoutParams = params
                    layout.scaleX = 4.0f
                    layout.scaleY = 4.0f
                }
            }
        }
    }
    
    enum class VideoScaleMode { FIT_WINDOW, FILL_WINDOW, ORIGINAL_SIZE, STRETCH }

    fun isPlayerReady(): Boolean = isInitialized
    fun isPlayerPlaying(): Boolean = isPlaying
    fun isPlayerConnected(): Boolean = isConnected
    fun getCurrentUrl(): String = currentUrl

    fun toggleFullscreen() {
        if (!isInitialized || videoLayout == null || activity == null) return
        try {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        } catch (e: Exception) {
            isFullscreen = false
            updateFullscreenButtonIcon()
        }
    }
    
    private fun enterFullscreen() {
        activity?.let { act ->
            videoLayout?.let { layout ->
                // UI 요소들 숨기기
                hideUIElements()
                
                // 전체화면 콜백 호출
                playerCallback?.onFullscreenEntered()
                
                layout.post {
                    adjustVideoScaleForFullscreen(layout, act)
                }
                
                isFullscreen = true
                updateFullscreenButtonIcon()
            }
        }
    }
    
    private fun exitFullscreen() {
        activity?.let { act ->
            // 시스템 UI 복원
            act.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            
            videoLayout?.let { layout ->
                // VandiPlay 방식: 원본 레이아웃 파라미터로 복원 (영상 끊김 없음)
                originalLayoutParams?.let { params ->
                    layout.layoutParams = params
                }
                
                // UI 요소들 다시 표시
                showUIElements()
                
                // 전체화면 종료 콜백 호출
                playerCallback?.onFullscreenExited()
                
                layout.post {
                    setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                }
                
                isFullscreen = false
                updateFullscreenButtonIcon()
            }
        }
    }
    
    /**
     * 전체화면 모드에서 UI 요소들 숨기기 (전체화면 버튼은 유지)
     */
    private fun hideUIElements() {
        buttonContainer?.visibility = android.view.View.GONE
        // 전체화면 버튼은 종료를 위해 항상 표시
        recordButton?.buttonView?.visibility = android.view.View.GONE
        captureButton?.buttonView?.visibility = android.view.View.GONE
    }
    
    /**
     * 일반 모드에서 UI 요소들 다시 표시
     */
    private fun showUIElements() {
        buttonContainer?.visibility = android.view.View.VISIBLE
        // 전체화면 버튼은 항상 표시
        recordButton?.buttonView?.visibility = android.view.View.VISIBLE
        captureButton?.buttonView?.visibility = android.view.View.VISIBLE
    }

    private fun updateFullscreenButtonIcon() {
        val icon = if (isFullscreen) "⧉" else "⧈"
        fullscreenButton?.setAsFullscreenButton(icon)
    }
    
    fun isInFullscreen(): Boolean = isFullscreen
    
    fun addFullscreenButton(): XLABPlayerButton {
        val button = XLABPlayerButton.create(context, "⧈", XLABPlayerButton.ButtonType.SECONDARY, ::toggleFullscreen)
        button.setAsFullscreenButton("⧈")
        
        parentViewGroup?.let { parent ->
            button.setFrameLayoutMargin(0, FULLSCREEN_BUTTON_MARGIN, FULLSCREEN_BUTTON_MARGIN, 0, 
                android.view.Gravity.END or android.view.Gravity.TOP)
            parent.addView(button.buttonView)
        }
        
        fullscreenButton = button
        return button
    }
    
    /**
     * 녹화 버튼 추가 (왼쪽 아래 빨간색 원형)
     */
    fun addRecordButton(): XLABPlayerButton {
        val button = XLABPlayerButton.create(context, "●", XLABPlayerButton.ButtonType.DANGER, ::toggleRecording)
        button.setAsRecordButton()
        
        parentViewGroup?.let { parent ->
            button.setFrameLayoutMargin(FULLSCREEN_BUTTON_MARGIN, 0, 0, FULLSCREEN_BUTTON_MARGIN, 
                android.view.Gravity.START or android.view.Gravity.BOTTOM)
            parent.addView(button.buttonView)
        }
        
        recordButton = button
        return button
    }
    
    /**
     * 사진 촬영 버튼 추가 (녹화 버튼 오른쪽)
     */
    fun addCaptureButton(): XLABPlayerButton {
        val button = XLABPlayerButton.create(context, "📷", XLABPlayerButton.ButtonType.WARNING, ::capturePhoto)
        button.setAsCaptureButton()
        
        parentViewGroup?.let { parent ->
            val buttonWidth = 40
            val margin = FULLSCREEN_BUTTON_MARGIN
            val leftMargin = margin + buttonWidth + 10
            
            button.setFrameLayoutMargin(leftMargin, 0, 0, margin, 
                android.view.Gravity.START or android.view.Gravity.BOTTOM)
            parent.addView(button.buttonView)
        }
        
        captureButton = button
        return button
    }
    
    /**
     * 녹화 토글
     */
    private fun toggleRecording() {
        isRecording = !isRecording
        updateRecordButtonState()
        
        if (isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }
    
    /**
     * 녹화 시작
     */
    private fun startRecording() {
        ptzController?.startRecording(object : C12PTZController.RecordingCallback {
            override fun onSuccess(message: String) {
                android.util.Log.d("XLABPlayer", "녹화 시작 성공: $message")
                playerCallback?.onPtzCommand("RECORD_START", true)

            }
            
            override fun onError(error: String) {
                android.util.Log.w("XLABPlayer", "녹화 시작 실패: $error")
                playerCallback?.onPtzCommand("RECORD_START", false)
            }
        })
    }
    
    /**
     * 녹화 중지
     */
    private fun stopRecording() {
        ptzController?.stopRecording(object : C12PTZController.RecordingCallback {
            override fun onSuccess(message: String) {
                android.util.Log.d("XLABPlayer", "녹화 중지 성공: $message")
                playerCallback?.onPtzCommand("RECORD_STOP", true)
            }
            
            override fun onError(error: String) {
                android.util.Log.w("XLABPlayer", "녹화 중지 실패: $error")
                playerCallback?.onPtzCommand("RECORD_STOP", false)
            }
        })
    }
    
    /**
     * 사진 촬영
     */
    private fun capturePhoto() {
        ptzController?.capturePhoto(object : C12PTZController.RecordingCallback {
            override fun onSuccess(message: String) {
                android.util.Log.d("XLABPlayer", "사진 촬영 성공: $message")
                playerCallback?.onPtzCommand("CAPTURE_PHOTO", true)
                
                // 사진 촬영 성공 시 버튼 깜빡임 효과
                captureButton?.let { button ->
                    button.buttonView.alpha = 0.3f
                    button.buttonView.postDelayed({
                        button.buttonView.alpha = 1.0f
                    }, 200)
                }
            }
            
            override fun onError(error: String) {
                android.util.Log.w("XLABPlayer", "사진 촬영 실패: $error")
                playerCallback?.onPtzCommand("CAPTURE_PHOTO", false)
            }
        })
    }
    
    /**
     * 녹화 버튼 상태 업데이트
     */
    private fun updateRecordButtonState() {
        recordButton?.let { button ->
            if (isRecording) {
                button.setText("■")  // 정지 아이콘
                button.setAsRecordButtonRecording()
            } else {
                button.setText("●")  // 녹화 아이콘
                button.setAsRecordButton()
            }
        }
    }

    private fun adjustVideoScaleForFullscreen(layout: VLCVideoLayout, activity: Activity) {
        try {
            val displayMetrics = activity.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            val videoWidth = layout.width.toFloat()
            val videoHeight = layout.height.toFloat()

            // 디버그 출력 - 화면 사이즈와 영상 사이즈
            android.util.Log.d("XLABPlayer", "=== 화면/영상 사이즈 디버그 ===")
            android.util.Log.d("XLABPlayer", "화면 사이즈: ${screenWidth.toInt()} x ${screenHeight.toInt()}")
            android.util.Log.d("XLABPlayer", "영상 사이즈: ${videoWidth.toInt()} x ${videoHeight.toInt()}")
            android.util.Log.d("XLABPlayer", "화면 비율: ${String.format("%.2f", screenWidth/screenHeight)}")
            android.util.Log.d("XLABPlayer", "영상 비율: ${String.format("%.2f", videoWidth/videoHeight)}")

            if (videoWidth > 0 && videoHeight > 0) {
                // 영상 전체가 보이도록 더 작은 스케일 사용 (letterbox/pillarbox)
                val scaleWidth = screenWidth / videoWidth   // 너비 기준 스케일
                val scaleHeight = screenHeight / videoHeight // 높이 기준 스케일
                val scale = minOf(scaleWidth, scaleHeight)   // 더 작은 스케일 선택

                val finalScale = scale.coerceIn(0.1f, 3.0f)

                android.util.Log.d("XLABPlayer", "너비 기준 스케일: ${String.format("%.2f", scaleWidth)}")
                android.util.Log.d("XLABPlayer", "높이 기준 스케일: ${String.format("%.2f", scaleHeight)}")
                android.util.Log.d("XLABPlayer", "계산된 스케일: ${String.format("%.2f", scale)}")
                android.util.Log.d("XLABPlayer", "최종 스케일: ${String.format("%.2f", finalScale)}")
                android.util.Log.d("XLABPlayer", "=== 디버그 끝 ===")

                layout.scaleX = finalScale
                layout.scaleY = finalScale
            } else {
                android.util.Log.d("XLABPlayer", "영상 사이즈가 0입니다. 기본 스케일 1.5 적용")
                layout.scaleX = 1.5f
                layout.scaleY = 1.5f
            }
        } catch (e: Exception) {
            android.util.Log.e("XLABPlayer", "스케일 조정 중 오류: ${e.message}")
            layout.scaleX = 1.5f
            layout.scaleY = 1.5f
        }
    }
} 