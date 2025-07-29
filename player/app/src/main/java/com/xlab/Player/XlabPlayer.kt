package com.xlab.Player

import android.content.Context
import android.util.Log
import android.view.ViewGroup
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
    private var fullscreenButton: XLABPlayerButton? = null

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
                        
                        // 기본 2배 확대
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
     * 전체화면 토글
     */
    fun toggleFullscreen() {
        if (!isInitialized || videoLayout == null) {
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
            fullscreenButton?.setAsTransparentIconButton("⧈")
        }
    }
    
    /**
     * 전체화면 진입 (스케일링으로만 처리)
     */
    private fun enterFullscreen() {
        try {
            videoLayout?.let { layout ->
                // 현재 레이아웃 파라미터는 변경하지 않고 스케일링만 조정
                originalLayoutParams = layout.layoutParams
                
                // 큰 스케일로 전체화면 효과
                layout.scaleX = 3.0f
                layout.scaleY = 3.0f
                
                isFullscreen = true
                
                // 버튼 아이콘 업데이트 (UI 스레드에서 실행)
                layout.post {
                    fullscreenButton?.setAsTransparentIconButton("⧉") // 복원 아이콘 (작은 사각형)
                }
            }
        } catch (e: Exception) {
            // 전체화면 진입 실패 시 안전하게 처리
            isFullscreen = false
        }
    }
    
    /**
     * 전체화면 해제 (스케일링 복원)
     */
    private fun exitFullscreen() {
        try {
            videoLayout?.let { layout ->
                isFullscreen = false
                
                // 버튼 아이콘 업데이트 (UI 스레드에서 실행)
                layout.post {
                    fullscreenButton?.setAsTransparentIconButton("⧈") // 전체화면 아이콘 (큰 사각형)
                    // 기존 스케일링 모드 재적용 (UI 업데이트 후)
                    setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                }
            }
        } catch (e: Exception) {
            // 전체화면 해제 실패 시 안전하게 처리
            isFullscreen = false
            fullscreenButton?.setAsTransparentIconButton("⧈")
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
            // 전체화면 아이콘 버튼을 비디오 위에 오버레이로 생성
            val fullscreenBtn = XLABPlayerButton.create(
                parent.context,
                "⛶", // 전체화면 아이콘 (유니코드)
                XLABPlayerButton.ButtonType.SECONDARY
            ) {
                toggleFullscreen()
            }
            
            // 투명 아이콘 버튼으로 설정
            fullscreenBtn.setAsTransparentIconButton(if (isFullscreen) "⧉" else "⧈")
            
            // 버튼을 FrameLayout의 오른쪽 위에 배치 (right: 10px, top: 10px)
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.END or android.view.Gravity.TOP
            ).apply {
                setMargins(0, 10, 10, 0) // left, top, right, bottom
            }
            
            fullscreenBtn.buttonView.layoutParams = layoutParams
            
            // 부모 컨테이너에 직접 추가
            parent.addView(fullscreenBtn.buttonView)
            
            fullscreenButton = fullscreenBtn
            return fullscreenBtn
        }
        
        // fallback: 기존 방식
        val fallbackBtn = addButton("⧈", XLABPlayerButton.ButtonType.SECONDARY) {
            toggleFullscreen()
        }
        fallbackBtn.setAsTransparentIconButton("⧈")
        return fallbackBtn
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
                    Log.d(TAG, "📺 가로형 플레이어")
                } else if (playerRatio < 0.8) {
                    Log.d(TAG, "📱 세로형 플레이어")
                } else {
                    Log.d(TAG, "⚖️ 정사각형 플레이어")
                }
            }
            Log.d(TAG, "=============================")
        }
    }
} 