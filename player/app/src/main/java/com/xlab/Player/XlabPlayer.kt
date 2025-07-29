package com.xlab.Player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * XLAB RTSP 플레이어 (VLCLib 기반)
 * AAR 배포용 플레이어 클래스
 * 자동 라이프사이클 관리 지원
 */
class XLABPlayer(private val context: Context) : LifecycleObserver {
    companion object {
        private const val TAG = "XLABPlayer"
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.144.108:554/stream=1"
    }

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var media: Media? = null
    private var videoLayout: VLCVideoLayout? = null
    private var parentViewGroup: ViewGroup? = null

    private var isInitialized = false
    private var isPlaying = false
    private var isConnected = false
    private var currentUrl = ""
    
    // 전체화면 관련
    private var isFullscreen = false
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var fullscreenButton: Any? = null // XLABPlayerButton 또는 SimpleFullscreenButton
    private var activity: Activity? = null
    
    // WindowManager를 통한 전체화면 구현
    private var windowManager: WindowManager? = null
    private var fullscreenContainer: FrameLayout? = null
    private var fullscreenLayoutParams: WindowManager.LayoutParams? = null
    


    interface PlayerCallback {
        fun onPlayerReady()
        fun onPlayerConnected()
        fun onPlayerDisconnected()
        fun onPlayerPlaying()
        fun onPlayerPaused()
        fun onPlayerError(error: String)
        fun onVideoSizeChanged(width: Int, height: Int)
    }
    private var playerCallback: PlayerCallback? = null
    
    // 자동 라이프사이클 관리
    private var isLifecycleRegistered = false
    

    
    // 버튼 관리
    private var buttonContainer: LinearLayout? = null
    private val playerButtons = mutableListOf<XLABPlayerButton>()
    
    init {
        // 앱 라이프사이클 감지 시작
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isLifecycleRegistered = true
    // 라이프사이클 감지 시작
        } catch (e: Exception) {
            // 라이프사이클 등록 실패 (무시)
        }
    }

    /**
     * 플레이어 초기화 (ViewGroup 컨테이너에 VLCVideoLayout을 동적으로 추가)
     */
    fun initialize(parent: ViewGroup): Boolean {
        try {
    // XLABPlayer 초기화 시작
            this.parentViewGroup = parent
            
            // Activity 참조 저장 (전체화면 모드를 위해)
            if (context is Activity) {
                this.activity = context as Activity
                windowManager = activity?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            }

            val options = mutableListOf(
                "--intf=dummy",
                "--network-caching=1000",
                "--no-audio",
                "--avcodec-hw=any"
            )
            libVLC = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVLC)

            // 기존 레이아웃 제거
            videoLayout?.let { parent.removeView(it) }
            videoLayout = VLCVideoLayout(context)
            parent.addView(videoLayout)
            
            // MediaPlayer를 VideoLayout에 연결 (하드웨어 가속)
            mediaPlayer?.attachViews(videoLayout!!, null, true, false)
            
            setupEventListeners()
            isInitialized = true
            playerCallback?.onPlayerReady()
            
            // 자동으로 플레이어 창 크기에 맞춤
            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
            
            // 크기 정보 출력
            logSizeInfo()
            return true
        } catch (e: Exception) {
            playerCallback?.onPlayerError("초기화 실패: ${e.message}")
            return false
        }
    }

    private fun setupEventListeners() {
        mediaPlayer?.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        isPlaying = true
                        isConnected = true
                        updateButtonStates()
                        playerCallback?.onPlayerPlaying()
                        // 자동으로 플레이어 창 크기에 맞춤
                        setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        // 재생 시작 시 크기 정보 출력
                        logSizeInfo()
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
                        val errorMsg = when (event.type) {
                            266 -> "H.265 코덱 지원 불가 또는 스트림 포맷 문제"
                            else -> "재생 오류 (코드: ${event.type})"
                        }
                        isPlaying = false
                        isConnected = false
                        playerCallback?.onPlayerError(errorMsg)
                    }
                    MediaPlayer.Event.Vout -> {
                        if (event.voutCount > 0) {
                            // 자동으로 플레이어 창 크기에 맞춤
                            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                            // 비디오 출력 시작 시 크기 정보 출력
                            logSizeInfo()
                        }
                    }
                }
            }
        })
    }

    fun connectAndPlay(url: String = DEFAULT_RTSP_URL): Boolean {
        try {
            if (!isInitialized) {
                playerCallback?.onPlayerError("플레이어가 초기화되지 않음")
                return false
            }
            
            releaseMedia()
            
            // Media 객체 생성 (URI로 명시적 생성)
            media = Media(libVLC, android.net.Uri.parse(url))
            if (media == null) {
                playerCallback?.onPlayerError("Media 객체 생성 실패")
                return false
            }
// Media 객체 생성 성공
            
            // RTSP 옵션 설정 (단순화)
            media?.addOption(":network-caching=1000")
            
            // Media를 MediaPlayer에 설정
            mediaPlayer?.media = media
            if (mediaPlayer?.media == null) {
                playerCallback?.onPlayerError("Media 설정 실패")
                return false
            }
            
            // 재생 시작
            val playResult = mediaPlayer?.play()
            
            currentUrl = url
            isConnected = true
            playerCallback?.onPlayerConnected()
            return true
        } catch (e: Exception) {
            playerCallback?.onPlayerError("연결 실패: ${e.message}")
            return false
        }
    }

    fun play(): Boolean {
        return try {
            if (isConnected && !isPlaying) {
                mediaPlayer?.play()
                isPlaying = true
                playerCallback?.onPlayerPlaying()
        // 재생 시작
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun pause(): Boolean {
        return try {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
                playerCallback?.onPlayerPaused()
        // 일시정지
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun stop(): Boolean {
        return try {
            mediaPlayer?.stop()
            isPlaying = false
            isConnected = false
            playerCallback?.onPlayerDisconnected()
    // 정지
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect(): Boolean {
        return try {
            stop()
            releaseMedia()
            isConnected = false
            currentUrl = ""
    // 연결 해제
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun releaseMedia() {
        try {
            media?.release()
            media = null
        } catch (e: Exception) {
            // 미디어 해제 실패 (무시)
        }
    }

    fun release() {
        try {
    // 플레이어 해제 시작
            
            // 전체화면 해제
            if (isFullscreen) {
                try {
                    exitFullscreen()
                } catch (e: Exception) {
                    // 전체화면 해제 실패 (무시)
                }
            }
            
            // 강제 정지로 RTSP 세션 정리
            if (isPlaying || isConnected) {
                try {
                    mediaPlayer?.stop()
                    Thread.sleep(200) // RTSP TEARDOWN 대기
                            } catch (e: Exception) {
                // 정지 중 오류 (무시)
            }
            }
            
            // 미디어 해제
            releaseMedia()
            
            // MediaPlayer 완전 해제
            try {
                mediaPlayer?.detachViews()
                mediaPlayer?.release()
            } catch (e: Exception) {
                // MediaPlayer 해제 중 오류 (무시)
            }
            mediaPlayer = null
            
            // LibVLC 해제
            try {
                libVLC?.release()
            } catch (e: Exception) {
                // LibVLC 해제 중 오류 (무시)
            }
            libVLC = null
            
            // UI 정리
            videoLayout?.let { layout ->
                try {
                    parentViewGroup?.removeView(layout)
                } catch (e: Exception) {
                    // VideoLayout 제거 중 오류 (무시)
                }
            }
            videoLayout = null
            parentViewGroup = null
            
            // 라이프사이클 감지 해제
            if (isLifecycleRegistered) {
                try {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                    isLifecycleRegistered = false
        // 라이프사이클 감지 해제
                } catch (e: Exception) {
                    // 라이프사이클 해제 실패 (무시)
                }
            }
            
            // 상태 초기화
            isInitialized = false
            isPlaying = false
            isConnected = false
            currentUrl = ""
            
// 플레이어 해제 완료
        } catch (e: Exception) {
            // 플레이어 해제 실패 (무시)
        }
    }

    /**
     * 앱이 백그라운드로 이동할 때
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackground() {
// 앱 백그라운드 - 재생 일시정지
        if (isPlaying) {
            try {
                pause()
            } catch (e: Exception) {
                // 백그라운드 일시정지 실패 (무시)
            }
        }
    }
    
    /**
     * 앱이 포그라운드로 복귀할 때
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForeground() {
// 앱 포그라운드 복귀
        // 필요시 재생 재개 로직 추가 가능
    }
    
    /**
     * 앱 프로세스가 종료될 때
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppDestroy() {
// 앱 프로세스 종료 감지 - 자동 해제
        try {
            release()
        } catch (e: Exception) {
            // 자동 해제 실패 (무시)
        }
    }

    fun setCallback(callback: PlayerCallback) {
        this.playerCallback = callback
    }



    /**
     * 버튼 컨테이너 추가 (플레이어 아래쪽)
     */
    fun addButtonContainer(parent: ViewGroup): LinearLayout {
        // 기존 버튼 컨테이너 제거
        buttonContainer?.let { parent.removeView(it) }
        
        // 새 버튼 컨테이너 생성
        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)
        }
        
        parent.addView(buttonContainer)
        return buttonContainer!!
    }
    
    /**
     * 단일 버튼 추가
     */
    fun addButton(
        text: String,
        type: XLABPlayerButton.ButtonType = XLABPlayerButton.ButtonType.PRIMARY,
        clickListener: (() -> Unit)? = null
    ): XLABPlayerButton {
        val button = XLABPlayerButton.create(context, text, type, clickListener)
        playerButtons.add(button)
        
        buttonContainer?.addView(button.buttonView)
        return button
    }
    
    /**
     * 여러 버튼 한 번에 추가
     */
    fun addButtons(vararg buttonConfigs: Triple<String, XLABPlayerButton.ButtonType, (() -> Unit)?>): List<XLABPlayerButton> {
        val buttons = buttonConfigs.map { (text, type, listener) ->
            addButton(text, type, listener)
        }
        return buttons
    }
    
    /**
     * 기본 플레이어 컨트롤 버튼들 추가
     */
    fun addDefaultControlButtons(): Map<String, XLABPlayerButton> {
        val buttons = mutableMapOf<String, XLABPlayerButton>()
        
        // 연결 버튼
        buttons["connect"] = addButton("연결", XLABPlayerButton.ButtonType.PRIMARY) {
            connectAndPlay()
        }
        
        // 재생 버튼
        buttons["play"] = addButton("재생", XLABPlayerButton.ButtonType.SUCCESS) {
            play()
        }
        
        // 일시정지 버튼
        buttons["pause"] = addButton("일시정지", XLABPlayerButton.ButtonType.WARNING) {
            pause()
        }
        
        // 정지 버튼
        buttons["stop"] = addButton("정지", XLABPlayerButton.ButtonType.SECONDARY) {
            stop()
        }
        

        
        // 해제 버튼
        buttons["disconnect"] = addButton("해제", XLABPlayerButton.ButtonType.DANGER) {
            disconnect()
        }
        
        return buttons
    }
    
    /**
     * 스케일링 컨트롤 버튼 추가
     */
    fun addScalingControlButton(): XLABPlayerButton {
        var currentMode = VideoScaleMode.FIT_WINDOW
        
        val scaleButton = addButton("맞춤", XLABPlayerButton.ButtonType.SECONDARY) {
            // 순환적으로 모드 변경
            currentMode = when (currentMode) {
                VideoScaleMode.FIT_WINDOW -> VideoScaleMode.FILL_WINDOW
                VideoScaleMode.FILL_WINDOW -> VideoScaleMode.STRETCH
                VideoScaleMode.STRETCH -> VideoScaleMode.ORIGINAL_SIZE
                VideoScaleMode.ORIGINAL_SIZE -> VideoScaleMode.FIT_WINDOW
            }
            
            setVideoScaleMode(currentMode)
            
            // 로그로 현재 모드 표시
// 스케일링 모드 변경
        }
        
        return scaleButton
    }
    
    /**
     * 버튼 상태 자동 업데이트
     */
    fun updateButtonStates() {
        playerButtons.forEach { button ->
            when (button.buttonView.text.toString()) {
                "연결" -> button.isEnabled = isInitialized && !isConnected
                "재생" -> button.isEnabled = isConnected && !isPlaying
                "일시정지" -> button.isEnabled = isConnected && isPlaying
                "정지" -> button.isEnabled = isConnected

                "해제" -> button.isEnabled = isConnected
            }
        }
    }
    
    /**
     * 모든 버튼 제거
     */
    fun clearButtons() {
        buttonContainer?.removeAllViews()
        playerButtons.clear()
    }

    /**
     * 비디오 스케일링 모드 설정
     */
    fun setVideoScaleMode(mode: VideoScaleMode) {
        videoLayout?.let { layout ->
            val params = layout.layoutParams
            when (mode) {
                VideoScaleMode.FIT_WINDOW -> {
                    // 전체화면 상태가 아닐 때만 스케일링 조정
                    if (!isFullscreen) {
                        // 플레이어 크기에 맞춤 (강제 확대)
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT
                        layout.layoutParams = params
                        
                        // 기본 2배 확대 (일반 모드에서 적당한 크기)
                        layout.scaleX = 2.0f
                        layout.scaleY = 2.0f
                    }
// 창에 맞춤 (강제 확대)
                }
                VideoScaleMode.FILL_WINDOW -> {
                    // 플레이어 크기를 완전히 채움
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layout.layoutParams = params
                    layout.scaleX = 3.0f // 3배 확대
                    layout.scaleY = 3.0f
// 창 채움
                }
                VideoScaleMode.ORIGINAL_SIZE -> {
                    // 원본 크기 유지
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    layout.layoutParams = params
                    layout.scaleX = 1f
                    layout.scaleY = 1f
// 원본 크기
                }
                VideoScaleMode.STRETCH -> {
                    // 늘려서 완전히 채움
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layout.layoutParams = params
                    layout.scaleX = 4.0f // 4배 확대
                    layout.scaleY = 4.0f
// 늘림
                }
            }
        }
    }
    
    /**
     * 비디오 스케일링 모드 열거형
     */
    enum class VideoScaleMode {
        FIT_WINDOW,    // 창에 맞춤 (비율 유지)
        FILL_WINDOW,   // 창 채움 (16:9 강제)
        ORIGINAL_SIZE, // 원본 크기
        STRETCH        // 늘려서 채움
    }

    fun isPlayerReady(): Boolean = isInitialized
    fun isPlayerPlaying(): Boolean = isPlaying
    fun isPlayerConnected(): Boolean = isConnected
    fun getCurrentUrl(): String = currentUrl
    
    /**
     * 오버레이 권한 체크
     */
    fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // API 23 미만에서는 권한 불필요
        }
    }
    
    /**
     * 오버레이 권한 요청 (설정 화면으로 이동)
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                // 권한 요청 실패
                playerCallback?.onPlayerError("오버레이 권한 설정을 열 수 없습니다")
            }
        }
    }
    
    /**
     * 전체화면 사용 가능 여부 체크 (권한 포함)
     */
    fun canUseFullscreen(): Boolean {
        return checkOverlayPermission() && windowManager != null
    }

    /**
     * 전체화면 토글 (플레이어 창을 전체화면 크기로 확장)
     */
    fun toggleFullscreen() {
        if (!isInitialized || videoLayout == null) {
            return
        }
        
        // Activity가 필요함
        if (activity == null) {
            playerCallback?.onPlayerError("Activity 컨텍스트가 필요합니다")
            return
        }
        
        try {
            if (isFullscreen) {
                // 전체화면 해제
                exitFullscreen()
            } else {
                // 전체화면 진입
                enterFullscreen()
            }
        } catch (e: Exception) {
            // 토글 실패 시 상태 리셋
            isFullscreen = false
            val button = fullscreenButton
            when (button) {
                is XLABPlayerButton -> button.setAsTransparentIconButton("⧈")
                is SimpleFullscreenButton -> button.updateIcon()
            }
        }
    }
    
        /**
     * 전체화면 진입 (Activity 전체 영역을 강제로 차지)
     */
    private fun enterFullscreen() {
        try {
            activity?.let { act ->
                videoLayout?.let { layout ->
                    // 현재 레이아웃 파라미터 저장
                    originalLayoutParams = layout.layoutParams
                    
                    // 부모에서 VideoLayout 제거
                    parentViewGroup?.removeView(layout)
                    
                    // 전체화면 컨테이너 생성
                    fullscreenContainer = FrameLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        
                        // VideoLayout을 컨테이너에 추가 (비율 유지하면서 컨테이너에 맞춤)
                        val videoParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                        addView(layout, videoParams)
                        
                        // 전체화면 해제 버튼 추가
                        addFullscreenExitButton()
                    }
                    
                    // Activity의 DecorView에 직접 추가 (전체 화면 영역 강제 차지)
                    val decorView = act.window.decorView as ViewGroup
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    decorView.addView(fullscreenContainer, layoutParams)
                    
                    // View가 완전히 레이아웃된 후 Surface 재연결 및 스케일 조정
                    layout.post {
                        mediaPlayer?.attachViews(layout, null, true, false)
                        // 전체화면에 맞게 비디오 스케일 조정
                        adjustVideoScaleForFullscreen(layout, act)
                    }
                    
                    isFullscreen = true
                    
                    // 버튼 아이콘 업데이트
                    val button = fullscreenButton
                    when (button) {
                        is XLABPlayerButton -> button.setAsTransparentIconButton("⧉")
                        is SimpleFullscreenButton -> button.updateIcon()
                    }
                }
            }
        } catch (e: Exception) {
            // 전체화면 진입 실패 시 안전하게 처리
            isFullscreen = false
        }
    }
    
    /**
     * 전체화면 해제 (VideoLayout을 원래 부모로 복원)
     */
    private fun exitFullscreen() {
        try {
            activity?.let { act ->
                fullscreenContainer?.let { container ->
                    videoLayout?.let { layout ->
                        // 전체화면 컨테이너에서 VideoLayout 제거
                        container.removeView(layout)
                        
                        // DecorView에서 전체화면 컨테이너 제거
                        val decorView = act.window.decorView as ViewGroup
                        decorView.removeView(container)
                        
                        // VideoLayout을 원래 부모에 복원
                        parentViewGroup?.let { parent ->
                            originalLayoutParams?.let { params ->
                                layout.layoutParams = params
                            }
                            parent.addView(layout)
                        }
                        
                        // View가 완전히 레이아웃된 후 Surface 재연결
                        layout.post {
                            mediaPlayer?.attachViews(layout, null, true, false)
                        }
                        
                        // 전체화면 관련 변수 정리
                        fullscreenContainer = null
                        isFullscreen = false
                        
                        // 버튼 아이콘 업데이트 및 원래 스케일링 모드 적용
                        layout.post {
                            val button = fullscreenButton
                            when (button) {
                                is XLABPlayerButton -> button.setAsTransparentIconButton("⧈")
                                is SimpleFullscreenButton -> button.updateIcon()
                            }
                            // 일반 모드 스케일링 적용
                            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 전체화면 해제 실패 시 안전하게 처리
            isFullscreen = false
            val button = fullscreenButton
            when (button) {
                is XLABPlayerButton -> button.setAsTransparentIconButton("⧈")
                is SimpleFullscreenButton -> button.updateIcon()
            }
        }
    }
    
    /**
     * 전체화면 상태 확인
     */
    fun isInFullscreen(): Boolean = isFullscreen
    
    /**
     * 전체화면 버튼 추가 (비디오 위에 오버레이, 오른쪽 위)
     */
    fun addFullscreenButton(): XLABPlayerButton {
        parentViewGroup?.let { parent ->
            // 부모가 FrameLayout인 경우에만 오버레이 방식 사용
            if (parent is FrameLayout) {
                // LayoutParams 충돌을 방지하기 위해 Button을 직접 생성
                val button = android.widget.Button(parent.context).apply {
                    text = "⧈"
                    textSize = 16f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(8, 8, 8, 8)
                    
                    // 클릭 리스너 설정
                    setOnClickListener {
                        toggleFullscreen()
                    }
                }
                
                // FrameLayout에 맞는 LayoutParams 설정
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END or android.view.Gravity.TOP
                ).apply {
                    setMargins(0, 10, 10, 0) // left, top, right, bottom
                }
                
                button.layoutParams = layoutParams
                
                // 부모 컨테이너에 직접 추가
                parent.addView(button)
                
                // 간단한 래퍼 생성
                val fullscreenBtn = SimpleFullscreenButton(button)
                fullscreenButton = fullscreenBtn
                
                // XLABPlayerButton 인터페이스 호환성을 위한 더미 반환 객체
                return XLABPlayerButton.create(context, "⧈", XLABPlayerButton.ButtonType.SECONDARY)
            }
        }
        
        // fallback: 기존 방식 (부모가 FrameLayout이 아닌 경우)
        val fallbackBtn = addButton("⧈", XLABPlayerButton.ButtonType.SECONDARY) {
            toggleFullscreen()
        }
        fallbackBtn.setAsTransparentIconButton("⧈")
        fullscreenButton = fallbackBtn
        return fallbackBtn
    }
    
    /**
     * 간단한 전체화면 버튼 래퍼 클래스
     */
    private inner class SimpleFullscreenButton(val button: android.widget.Button) {
        fun setAsTransparentIconButton(icon: String) {
            button.text = icon
        }
        
        fun updateIcon() {
            button.text = if (isFullscreen) "⧉" else "⧈"
        }
    }
    
    /**
     * 전체화면 컨테이너에 해제 버튼 추가
     */
    private fun FrameLayout.addFullscreenExitButton() {
        val exitButton = android.widget.Button(context).apply {
            text = "⧉"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                exitFullscreen()
            }
        }
        
        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.TOP
        ).apply {
            setMargins(0, 20, 20, 0)
        }
        
        addView(exitButton, buttonParams)
    }



    /**
     * 전체화면에서 비디오 스케일 자동 조정
     */
    private fun adjustVideoScaleForFullscreen(layout: VLCVideoLayout, activity: Activity) {
        try {
            // 화면 크기 가져오기
            val displayMetrics = activity.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            
            // VideoLayout의 원본 크기 (일반적으로 16:9 비율 가정)
            val videoWidth = layout.width.toFloat()
            val videoHeight = layout.height.toFloat()
            
            if (videoWidth > 0 && videoHeight > 0) {
                // 화면 비율과 비디오 비율 계산
                val screenRatio = screenWidth / screenHeight
                val videoRatio = videoWidth / videoHeight
                
                val scale = if (videoRatio > screenRatio) {
                    // 비디오가 더 가로로 길 때 - 화면 너비에 맞춤
                    screenWidth / videoWidth
                } else {
                    // 비디오가 더 세로로 길 때 - 화면 높이에 맞춤
                    screenHeight / videoHeight
                }
                
                // 최소 1.0배, 최대 3.0배로 제한
                val finalScale = scale.coerceIn(1.0f, 3.0f)
                
                layout.scaleX = finalScale
                layout.scaleY = finalScale
                
                Log.d(TAG, "전체화면 스케일 조정: ${finalScale}x (화면: ${screenWidth}x${screenHeight}, 비디오: ${videoWidth}x${videoHeight})")
            } else {
                // 크기를 알 수 없는 경우 기본 스케일링
                layout.scaleX = 1.5f
                layout.scaleY = 1.5f
                Log.d(TAG, "전체화면 기본 스케일 적용: 1.5x")
            }
        } catch (e: Exception) {
            // 스케일 조정 실패 시 기본값
            layout.scaleX = 1.5f
            layout.scaleY = 1.5f
        }
    }

    /**
     * 플레이어 창 크기와 영상 크기 정보 출력
     */
    private fun logSizeInfo() {
        videoLayout?.let { layout ->
            // 플레이어 창 크기
            val playerWidth = layout.width
            val playerHeight = layout.height
            
            // 영상 크기는 VLC에서 직접 가져오기 어려움
            // 대신 실제 렌더링 크기 확인
            val videoWidth = 0  // VLC API 한계로 임시값
            val videoHeight = 0
            
            Log.d(TAG, "========== 크기 정보 ==========")
            Log.d(TAG, "플레이어 창: ${playerWidth} x ${playerHeight}")
            Log.d(TAG, "VLC 레이아웃: ${layout.scaleX}x, ${layout.scaleY}x")
            
            if (playerWidth > 0 && playerHeight > 0) {
                val playerRatio = playerWidth.toFloat() / playerHeight.toFloat()
                Log.d(TAG, "플레이어 비율: %.2f".format(playerRatio))
                
                if (playerRatio > 1.5) {
                    Log.d(TAG, "가로형 플레이어")
                } else if (playerRatio < 0.8) {
                    Log.d(TAG, "세로형 플레이어")
                } else {
                    Log.d(TAG, "정사각형 플레이어")
                }
            }
            Log.d(TAG, "=============================")
        }
    }
} 