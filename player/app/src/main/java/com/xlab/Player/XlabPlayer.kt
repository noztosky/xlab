package com.xlab.Player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
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

class XLABPlayer(private val context: Context) : LifecycleObserver {
    companion object {
        private const val TAG = "XLABPlayer"
    }

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
        setupLifecycle()
        setupConfigurationChangeListener()
    }

    private fun setupLifecycle() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isLifecycleRegistered = true
        } catch (e: Exception) {
            // 라이프사이클 등록 실패 (무시)
        }
    }

    private fun setupConfigurationChangeListener() {
        configurationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                    handleConfigurationChange()
                }
            }
        }
    }

    private fun handleConfigurationChange() {
        videoLayout?.post {
            if (isFullscreen) {
                activity?.let { adjustVideoScaleForFullscreen(videoLayout!!, it) }
            } else {
                setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
            }
        }
    }

    fun initialize(parent: ViewGroup): Boolean {
        try {
            this.parentViewGroup = parent
            
            if (context is Activity) {
                this.activity = context as Activity
                registerConfigurationChangeReceiver()
            }

            // LibVLC 초기화 (안전한 옵션 사용)
            val options = arrayListOf("--intf=dummy", "--no-audio")
            if (context == null) {
                playerCallback?.onPlayerError("Context가 null입니다")
                return false
            }
            
            libVLC = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVLC)

            // VideoLayout 초기화
            videoLayout?.let { parent.removeView(it) }
            videoLayout = VLCVideoLayout(context)
            parent.addView(videoLayout)
            
            setupLayoutChangeListener()
            mediaPlayer?.attachViews(videoLayout!!, null, true, false)
            setupEventListeners()
            
            isInitialized = true
            playerCallback?.onPlayerReady()
            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
            return true
        } catch (e: Exception) {
            val errorMsg = e.message ?: "알 수 없는 오류 - ${e.javaClass.simpleName}"
            playerCallback?.onPlayerError("초기화 실패: $errorMsg")
            return false
        }
    }

    private fun registerConfigurationChangeReceiver() {
        try {
            if (!isReceiverRegistered && configurationReceiver != null) {
                context.registerReceiver(configurationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
                isReceiverRegistered = true
            }
        } catch (e: Exception) {
            // 등록 실패 무시
        }
    }

    private fun unregisterConfigurationChangeReceiver() {
        try {
            if (isReceiverRegistered && configurationReceiver != null) {
                context.unregisterReceiver(configurationReceiver)
                isReceiverRegistered = false
            }
        } catch (e: Exception) {
            // 해제 실패 무시
        }
    }

    private fun setupLayoutChangeListener() {
        videoLayout?.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newWidth = right - left
            val newHeight = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop
            
            if (newWidth != oldWidth || newHeight != oldHeight) {
                view.post {
                    if (isFullscreen) {
                        activity?.let { adjustVideoScaleForFullscreen(view as VLCVideoLayout, it) }
                    } else {
                        setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                    }
                }
            }
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
                        if (event.voutCount > 0) {
                            setVideoScaleMode(VideoScaleMode.FIT_WINDOW)
                        }
                    }
                }
            }
        })
    }

    fun connectAndPlay(url: String = "rtsp://192.168.144.108:554/stream=1"): Boolean {
        return try {
            if (!isInitialized) {
                playerCallback?.onPlayerError("플레이어가 초기화되지 않음")
                return false
            }
            
            releaseMedia()
            media = Media(libVLC, Uri.parse(url))
            media?.addOption(":network-caching=1000")
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
            if (condition) {
                action()
                true
            } else false
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
            // 무시
        }
    }

    fun release() {
        try {
            if (isFullscreen) exitFullscreen()
            unregisterConfigurationChangeReceiver()
            
            if (isPlaying || isConnected) {
                mediaPlayer?.stop()
                Thread.sleep(200)
            }
            
            releaseMedia()
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
        } catch (e: Exception) {
            // 무시
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackground() {
        if (isPlaying) pause()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForeground() {
        // 필요시 재생 재개 로직 추가 가능
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppDestroy() {
        release()
    }

    fun setCallback(callback: PlayerCallback) {
        this.playerCallback = callback
    }

    fun addButtonContainer(parent: ViewGroup): LinearLayout {
        buttonContainer?.let { parent.removeView(it) }
        
        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 8, 16, 8)
        }
        
        parent.addView(buttonContainer)
        return buttonContainer!!
    }
    
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
    
    enum class VideoScaleMode {
        FIT_WINDOW, FILL_WINDOW, ORIGINAL_SIZE, STRETCH
    }

    fun isPlayerReady(): Boolean = isInitialized
    fun isPlayerPlaying(): Boolean = isPlaying
    fun isPlayerConnected(): Boolean = isConnected
    fun getCurrentUrl(): String = currentUrl

    fun toggleFullscreen() {
        if (!isInitialized || videoLayout == null || activity == null) return
        
        try {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        } catch (e: Exception) {
            resetFullscreenState()
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
                    addFullscreenExitButton()
                }
                
                val decorView = act.window.decorView as ViewGroup
                decorView.addView(fullscreenContainer, FrameLayout.LayoutParams(
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
                    (act.window.decorView as ViewGroup).removeView(container)
                    
                    parentViewGroup?.let { parent ->
                        originalLayoutParams?.let { layout.layoutParams = it }
                        parent.addView(layout)
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

    private fun resetFullscreenState() {
        isFullscreen = false
        updateFullscreenButtonIcon()
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
            if (parent is FrameLayout) {
                val button = android.widget.Button(parent.context).apply {
                    text = "⧈"
                    textSize = 16f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(8, 8, 8, 8)
                    setOnClickListener { toggleFullscreen() }
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.END or android.view.Gravity.TOP
                    ).apply { setMargins(0, 10, 10, 0) }
                }
                
                parent.addView(button)
                fullscreenButton = SimpleFullscreenButton(button)
                return XLABPlayerButton.create(context, "⧈", XLABPlayerButton.ButtonType.SECONDARY)
            }
        }
        
        val fallbackBtn = addButton("⧈", XLABPlayerButton.ButtonType.SECONDARY) { toggleFullscreen() }
        fallbackBtn.setAsTransparentIconButton("⧈")
        fullscreenButton = fallbackBtn
        return fallbackBtn
    }
    
    private inner class SimpleFullscreenButton(val button: android.widget.Button) {
        fun setAsTransparentIconButton(icon: String) { button.text = icon }
        fun updateIcon() { button.text = if (isFullscreen) "⧉" else "⧈" }
    }
    
    private fun FrameLayout.addFullscreenExitButton() {
        addView(android.widget.Button(context).apply {
            text = "⧉"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
            setOnClickListener { exitFullscreen() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.TOP
        ).apply { setMargins(0, 20, 20, 0) })
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