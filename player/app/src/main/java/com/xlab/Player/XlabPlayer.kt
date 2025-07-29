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
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var media: Media? = null
    private var videoLayout: VLCVideoLayout? = null
    private var parentViewGroup: ViewGroup? = null
    private var activity: Activity? = null

    private var isInitialized = false
    private var isPlaying = false
    private var isConnected = false
    private var currentUrl = ""
    
    // 전체화면 관련
    private var isFullscreen = false
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var fullscreenButton: Any? = null
    private var fullscreenContainer: FrameLayout? = null

    // Configuration 변경 감지
    private var configurationReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    // PTZ 제어 관련
    private var ptzContainer: FrameLayout? = null
    private var isPtzVisible = false
    private var currentCameraId = 1
    
    // 전체화면 버튼 마진 상수 (픽셀 단위)
    private val FULLSCREEN_BUTTON_MARGIN = 10
    private var currentCameraController: CameraController? = null
    private var currentCameraInfo: CameraInfo? = null
    private var ptzController: C12PTZController? = null // C12 전용 컨트롤러

    interface PlayerCallback {
        fun onPlayerReady()
        fun onPlayerConnected()
        fun onPlayerDisconnected()
        fun onPlayerPlaying()
        fun onPlayerPaused()
        fun onPlayerError(error: String)
        fun onVideoSizeChanged(width: Int, height: Int)
        fun onPtzCommand(command: String, success: Boolean)
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
    fun togglePtzControl() {
        if (isPtzVisible) hidePtzControl() else showPtzControl()
    }

    /**
     * PTZ 컨트롤 표시
     */
    fun showPtzControl() {
        if (ptzContainer != null || parentViewGroup == null) return
        
        parentViewGroup?.let { parent ->
            if (parent is FrameLayout) {
                createPtzControl()
                isPtzVisible = true
            }
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
        createPtzButton(container, "↑", centerX, centerY - distance, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(0f, 10f, callback) }
        }
        
        // 아래쪽 버튼 (DOWN) - 틸트 감소
        createPtzButton(container, "↓", centerX, centerY + distance, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(0f, -10f, callback) }
        }
        
        // 왼쪽 버튼 (LEFT) - 팬 감소
        createPtzButton(container, "←", centerX - distance, centerY, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(-10f, 0f, callback) }
        }
        
        // 오른쪽 버튼 (RIGHT) - 팬 증가
        createPtzButton(container, "→", centerX + distance, centerY, buttonSize) {
            ensurePtzConnection { ptzController?.moveRelative(10f, 0f, callback) }
        }
        
        // 중앙 홈 버튼 (HOME)
        createPtzButton(container, "⌂", centerX, centerY, buttonSize) {
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
            
            libVLC = LibVLC(context, arrayListOf("--intf=dummy", "--network-caching=0", "--no-audio"))
            mediaPlayer = MediaPlayer(libVLC)

            videoLayout?.let { parent.removeView(it) }
            videoLayout = VLCVideoLayout(context).apply { 
                parent.addView(this)
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
            
            media = Media(libVLC, Uri.parse(url)).apply { addOption(":network-caching=1000") }
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
            try { media?.release() } catch (e: Exception) { }
            media = null
            isConnected = false
            currentUrl = ""
            true
        } catch (e: Exception) { false }
    }

    fun release() {
        try {
            if (isFullscreen) exitFullscreen()
            hidePtzControl()
            
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
                    if (!isFullscreen) {
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT
                        layout.layoutParams = params
                        layout.scaleX = 2.0f
                        layout.scaleY = 2.0f
                    }
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
                originalLayoutParams = layout.layoutParams
                parentViewGroup?.removeView(layout)
                
                fullscreenContainer = FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(layout, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ))
                    
                    // 전체화면 종료 버튼 추가
                    addView(android.widget.Button(context).apply {
                        text = "⧉"
                        textSize = 20f
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(8, 8, 8, 8)
                        setOnClickListener { exitFullscreen() }
                    }, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.END or Gravity.TOP
                    ).apply { setMargins(0, FULLSCREEN_BUTTON_MARGIN, FULLSCREEN_BUTTON_MARGIN, 0) })
                    
                    // PTZ 컨트롤이 보이는 상태에서만 전체화면에 추가
                    if (isPtzVisible) {
                        ptzContainer?.let { ptzControl ->
                            (ptzControl.parent as? ViewGroup)?.removeView(ptzControl)
                            addView(ptzControl)
                        }
                    }
                }
                
                (act.window.decorView as ViewGroup).addView(fullscreenContainer, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                
                layout.post {
                    mediaPlayer?.attachViews(layout, null, true, false)
                    adjustVideoScaleForFullscreen(layout, act)
                }
                
                isFullscreen = true
                updateFullscreenButtonIcon()
            }
        }
    }
    
    private fun exitFullscreen() {
        activity?.let { act ->
            fullscreenContainer?.let { container ->
                videoLayout?.let { layout ->
                    container.removeView(layout)
                    
                    // PTZ 컨트롤이 보이는 상태였다면 전체화면에서 제거 후 원래 위치에 다시 생성
                    if (isPtzVisible) {
                        ptzContainer?.let { ptzControl ->
                            container.removeView(ptzControl)
                            ptzContainer = null // 기존 컨테이너 참조 제거
                        }
                    }
                    
                    (act.window.decorView as ViewGroup).removeView(container)
                    
                    parentViewGroup?.let { parent ->
                        originalLayoutParams?.let { layout.layoutParams = it }
                        parent.addView(layout)
                    }
                    
                    // PTZ 컨트롤이 보이는 상태였다면 원래 위치에 다시 생성
                    if (isPtzVisible) {
                        createPtzControl()
                    }
                    
                    layout.post {
                        mediaPlayer?.attachViews(layout, null, true, false)
                        setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                    }
                    
                    fullscreenContainer = null
                    isFullscreen = false
                    updateFullscreenButtonIcon()
                }
            }
        }
    }

    private fun updateFullscreenButtonIcon() {
        val icon = if (isFullscreen) "⧉" else "⧈"
        when (val button = fullscreenButton) {
            is XLABPlayerButton -> button.setAsTransparentIconButton(icon)
            is SimpleFullscreenButton -> button.updateIcon()
        }
    }
    
    fun isInFullscreen(): Boolean = isFullscreen
    
    fun addFullscreenButton(): XLABPlayerButton {
        parentViewGroup?.let { parent ->
            // FrameLayout인 경우 직접 android.widget.Button 생성
            val button = android.widget.Button(parent.context).apply {
                text = "⧈"
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(4, 4, 4, 4)
                setOnClickListener { toggleFullscreen() }
                
                // PTZ 버튼과 동일한 스타일 적용 (둥근 모서리, 50% 투명도)
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 8f // 둥근 모서리
                    setColor(android.graphics.Color.parseColor("#80444444")) // PTZ와 같은 투명도
                }
                
                val stateDrawable = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 8f
                        setColor(android.graphics.Color.parseColor("#80666666"))
                    })
                    addState(intArrayOf(), drawable)
                }
                
                background = stateDrawable
                setShadowLayer(3f, 2f, 2f, android.graphics.Color.parseColor("#40000000"))
            }
            
            // FrameLayout.LayoutParams를 직접 설정 (정사각형 크기)
            val buttonSize = 80 // 80px 정사각형
            val layoutParams = FrameLayout.LayoutParams(
                buttonSize,
                buttonSize,
                android.view.Gravity.END or android.view.Gravity.TOP
            ).apply { 
                setMargins(0, FULLSCREEN_BUTTON_MARGIN, FULLSCREEN_BUTTON_MARGIN, 0) 
            }
            button.layoutParams = layoutParams
            
            parent.addView(button)
            fullscreenButton = SimpleFullscreenButton(button)
            
            // 더미 XLABPlayerButton 반환 (호환성을 위해)
            return XLABPlayerButton.create(context, "⧈", XLABPlayerButton.ButtonType.SECONDARY)
        }
        
        // parentViewGroup이 null인 경우 기본 방식 사용 (호환성)
        return XLABPlayerButton.create(context, "⧈", XLABPlayerButton.ButtonType.SECONDARY).also {
            it.setAsTransparentIconButton("⧈")
            fullscreenButton = it
        }
    }
    
    private inner class SimpleFullscreenButton(val button: android.widget.Button) {
        fun setAsTransparentIconButton(icon: String) { button.text = icon }
        fun updateIcon() { button.text = if (isFullscreen) "⧉" else "⧈" }
    }

    private fun adjustVideoScaleForFullscreen(layout: VLCVideoLayout, activity: Activity) {
        try {
            val displayMetrics = activity.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            val videoWidth = layout.width.toFloat()
            val videoHeight = layout.height.toFloat()
            
            if (videoWidth > 0 && videoHeight > 0) {
                val screenRatio = screenWidth / screenHeight
                val videoRatio = videoWidth / videoHeight
                val scale = if (videoRatio > screenRatio) screenWidth / videoWidth else screenHeight / videoHeight
                val finalScale = scale.coerceIn(1.0f, 3.0f)
                
                layout.scaleX = finalScale
                layout.scaleY = finalScale
            } else {
                layout.scaleX = 1.5f
                layout.scaleY = 1.5f
            }
        } catch (e: Exception) {
            layout.scaleX = 1.5f
            layout.scaleY = 1.5f
        }
    }
} 