package com.xlab.Player

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * 비디오 플레이어 Fragment (순수 비디오 플레이어만 포함)
 */
class VideoPlayerFragment : Fragment() {
    
    companion object {
        private const val TAG = "VideoPlayerFragment"
        private const val PREFS_NAME = "VideoPlayerPrefs"
        private const val PREF_BUFFER_TIME = "buffer_time"
        private const val DEFAULT_BUFFER_TIME = 0  // 기본 0초
        
        fun newInstance(): VideoPlayerFragment {
            return VideoPlayerFragment()
        }
    }
    
    // 상태 변경 콜백 인터페이스
    interface PlayerStateCallback {
        fun onPlayerReady()
        fun onPlayerConnected()
        fun onPlayerDisconnected()
        fun onPlayerPlaying()
        fun onPlayerPaused()
        fun onPlayerError(error: String)
    }
    
    // PTZ 제어 콜백 인터페이스
    interface PTZControlCallback {
        fun onPTZMove(direction: String, value: Int)
        fun onPTZHome()
        fun onRecordToggle()
        fun onPhotoCapture()
    }
    
    // 전체화면 콜백 인터페이스
    interface FullscreenCallback {
        fun onToggleFullscreen()
    }
    
    private var xlabPlayer: XlabPlayer? = null
    private lateinit var surfaceView: SurfaceView
    private var playerStateCallback: PlayerStateCallback? = null
    private var ptzControlCallback: PTZControlCallback? = null
    private var fullscreenCallback: FullscreenCallback? = null
    private var isConnected = false
    private var isPlaying = false
    private var isRecording = false
    
    // C12 PTZ 제어기
    private var c12PTZController: C12PTZController? = null
    
    // 현재 PTZ 각도 추적
    private var currentPan: Float = 0.0f
    private var currentTilt: Float = 0.0f
    private var currentZoom: Float = 1.0f
    
    // 버퍼 설정
    private var bufferTime: Int = DEFAULT_BUFFER_TIME
    
    // SharedPreferences for buffer time persistence
    private lateinit var prefs: SharedPreferences
    
    // PTZ 제어 버튼들
    private lateinit var upButton: Button
    private lateinit var downButton: Button
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var homeButton: Button
    private lateinit var recordButton: Button
    private lateinit var photoButton: Button
    
    // 전체화면 버튼
    private lateinit var fullscreenButton: Button
    
    // 전체화면 상태 추적
    private var isFullscreenMode = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_player, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews()
        setupVideoPlayer()
        setupPTZControls()
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Fragment 화면 방향 변경: ${newConfig.orientation}")
        
        // 비디오 플레이어가 재생 중이면 화면 비율 조정
        if (isPlaying && xlabPlayer != null) {
            adjustVideoAspectRatio()
        }
    }
    
    private fun adjustVideoAspectRatio() {
        try {
            // 현재 화면 방향과 전체화면 모드 상태에 따라 비디오 비율 조정
            val orientation = resources.configuration.orientation
            val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            if (isLandscape || isFullscreenMode) {
                // 가로 모드이거나 전체화면 모드에서는 비디오가 전체 화면을 차지하도록
                surfaceView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // 전체화면 모드에서 추가 레이아웃 조정
                if (isFullscreenMode) {
                    // SurfaceView 자체의 패딩만 제거
                    surfaceView.setPadding(0, 0, 0, 0)
                }
                
                Log.d(TAG, "전체화면 비율 적용 (가로모드 또는 전체화면모드)")
            } else {
                // 세로 모드에서는 원래 비율 유지
                surfaceView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                Log.d(TAG, "일반 비율 적용 (세로모드)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "비디오 비율 조정 실패", e)
        }
    }
    
    /**
     * 전체화면 모드를 위한 강제 비디오 비율 조정
     */
    fun adjustVideoAspectRatioForFullscreen() {
        try {
            Log.d(TAG, "전체화면 모드 비디오 비율 강제 조정")
            
            // Fragment의 루트 뷰를 전체 화면으로 설정
            view?.let { fragmentView ->
                val rootParams = fragmentView.layoutParams
                rootParams?.let { params ->
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    fragmentView.layoutParams = params
                }
                
                // Fragment의 크기 로그 출력
                fragmentView.post {
                    Log.d(TAG, "Fragment 크기 - 너비: ${fragmentView.width}, 높이: ${fragmentView.height}")
                    Log.d(TAG, "Fragment 레이아웃 파라미터 - 너비: ${rootParams.width}, 높이: ${rootParams.height}")
                }
            }
            
            // SurfaceView를 전체 화면으로 설정
            surfaceView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // SurfaceView 자체의 패딩만 제거
            surfaceView.setPadding(0, 0, 0, 0)
            
            // SurfaceView 크기 로그 출력
            surfaceView.post {
                Log.d(TAG, "SurfaceView 크기 - 너비: ${surfaceView.width}, 높이: ${surfaceView.height}")
            }
            
            Log.d(TAG, "전체화면 모드 비디오 비율 조정 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "전체화면 모드 비디오 비율 조정 실패", e)
        }
    }
    
    /**
     * Fragment 자체에서 전체화면 모드 토글 (외부에서 호출 가능)
     */
    fun toggleFullscreenMode() {
        toggleFullscreen()
    }
    
    /**
     * Fragment 자체에서 전체화면 모드 설정
     */
    fun setFullscreenMode(fullscreen: Boolean) {
        if (isFullscreenMode != fullscreen) {
            isFullscreenMode = fullscreen
            adjustVideoAspectRatio()
            updateFullscreenButtonPosition()
        }
    }
    
    /**
     * 현재 전체화면 모드 상태 반환
     */
    fun isInFullscreenMode(): Boolean {
        return isFullscreenMode
    }
    
    private fun initViews() {
        // 비디오 플레이어 관련
        surfaceView = requireView().findViewById(R.id.video_surface_view)
        
        // PTZ 제어 버튼들
        upButton = requireView().findViewById(R.id.ptz_up_button)
        downButton = requireView().findViewById(R.id.ptz_down_button)
        leftButton = requireView().findViewById(R.id.ptz_left_button)
        rightButton = requireView().findViewById(R.id.ptz_right_button)
        homeButton = requireView().findViewById(R.id.ptz_home_button)
        recordButton = requireView().findViewById(R.id.record_button)
        photoButton = requireView().findViewById(R.id.photo_button)
        
        // 전체화면 버튼
        fullscreenButton = requireView().findViewById(R.id.fullscreen_button)
    }
    
    private fun setupVideoPlayer() {
        try {
            Log.d(TAG, "비디오 플레이어 설정")
            
            // SurfaceView 설정
            surfaceView.holder.setKeepScreenOn(true)
            
            // 16:9 비율 설정 (1280x720)
            setupAspectRatio()
            
            // XlabPlayer 생성 및 설정
            xlabPlayer = XlabPlayer(requireContext())
            xlabPlayer!!
                .initializeWithSurfaceView(surfaceView)
                .setPlaybackListener(createPlaybackListener())
                .setErrorListener(createErrorListener())
            
            // C12 PTZ 제어기 초기화
            c12PTZController = C12PTZController()
            
            // SharedPreferences 초기화
            prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            bufferTime = prefs.getInt(PREF_BUFFER_TIME, DEFAULT_BUFFER_TIME)
            Log.d(TAG, "버퍼시간 복구: ${bufferTime}ms")
            
            Log.d(TAG, "비디오 플레이어 설정 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "비디오 플레이어 설정 실패", e)
        }
    }
    
    private fun setupPTZControls() {
        // PTZ 버튼 터치 리스너 설정 (눌려있는 동안 파란색 배경)
        upButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "상 버튼 터치업")
                    movePTZ("UP", 5)
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        downButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "하 버튼 터치업")
                    movePTZ("DOWN", -5)
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        leftButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "좌 버튼 터치업")
                    movePTZ("LEFT", 5)
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        rightButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "우 버튼 터치업")
                    movePTZ("RIGHT", -5)
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        homeButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "홈 버튼 터치업")
                    moveToHome()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        recordButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "녹화 버튼 터치업")
                    toggleRecord()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        photoButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "사진 버튼 터치업")
                    capturePhoto()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
        
        // 초기 상태 설정 - 항상 활성화
        setPTZButtonsEnabled(true)
        
        // 전체화면 버튼 설정
        setupFullscreenButton()
    }
    
    /**
     * 전체화면 버튼 설정
     */
    private fun setupFullscreenButton() {
        fullscreenButton.setOnTouchListener { button, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "전체화면 버튼 터치업")
                    toggleFullscreen()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 전체화면 토글
     */
    private fun toggleFullscreen() {
        try {
            Log.d(TAG, "전체화면 토글")
            
            // 전체화면 상태 토글
            isFullscreenMode = !isFullscreenMode
            
            // 전체화면 버튼 위치 업데이트
            updateFullscreenButtonPosition()
            
            // 비디오 비율 조정 (화면 회전과 동일한 효과)
            adjustVideoAspectRatio()
            
            // 부모 액티비티에 전체화면 모드 전환 요청
            Log.d(TAG, "전체화면 콜백 상태 확인: ${fullscreenCallback != null}")
            Log.d(TAG, "전체화면 콜백 객체 타입: ${fullscreenCallback?.javaClass?.simpleName}")
            fullscreenCallback?.let { callback ->
                Log.d(TAG, "전체화면 콜백 호출 시작")
                try {
                    Log.d(TAG, "콜백 함수 호출 직전")
                    
                    callback.onToggleFullscreen()
                    
                    Log.d(TAG, "콜백 함수 호출 직후")
                    Log.d(TAG, "전체화면 콜백 호출 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "전체화면 콜백 호출 실패", e)
                }
            } ?: run {
                Log.w(TAG, "전체화면 콜백이 설정되지 않음")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "전체화면 토글 실패", e)
        }
    }
    

    
    /**
     * 전체화면 버튼 위치 업데이트
     */
    private fun updateFullscreenButtonPosition() {
        try {
            // 전체화면 모드에서는 버튼을 항상 우측 위에 유지
            // 위치 변경 없이 그대로 유지
            Log.d(TAG, "전체화면 버튼 위치 유지 (우측 위)")
            
        } catch (e: Exception) {
            Log.e(TAG, "전체화면 버튼 위치 업데이트 실패", e)
        }
    }
    
    /**
     * 16:9 비율 설정 (1280x720)
     */
    private fun setupAspectRatio() {
        try {
            // SurfaceView의 부모 컨테이너에 16:9 비율 설정
            val container = surfaceView.parent as? ViewGroup
            container?.let { parent ->
                parent.post {
                    val width = parent.width
                    val height = (width * 9) / 16  // 16:9 비율 계산
                    
                    Log.d(TAG, "16:9 비율 설정: ${width}x${height}")
                    
                    // SurfaceView 크기 설정
                    val layoutParams = surfaceView.layoutParams
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.height = height
                    surfaceView.layoutParams = layoutParams
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "비율 설정 실패", e)
        }
    }
    
    /**
     * 상태 콜백 설정
     */
    fun setPlayerStateCallback(callback: PlayerStateCallback) {
        this.playerStateCallback = callback
    }
    
    /**
     * PTZ 제어 콜백 설정
     */
    fun setPTZControlCallback(callback: PTZControlCallback) {
        this.ptzControlCallback = callback
    }
    
    /**
     * 전체화면 콜백 설정
     */
    fun setFullscreenCallback(callback: FullscreenCallback) {
        Log.d(TAG, "전체화면 콜백 설정됨: ${callback.javaClass.simpleName}")
        this.fullscreenCallback = callback
    }
    
    /**
     * RTSP URL로 스트림 재생
     */
    fun playRtspStream(url: String) {
        try {
            Log.d(TAG, "RTSP 스트림 재생 시작: $url")
            
            // 저장된 버퍼시간을 XlabPlayer에 적용
            val bufferTimeSeconds = bufferTime / 1000.0f
            Log.d(TAG, "버퍼시간 적용: ${bufferTimeSeconds}초")
            xlabPlayer?.setNetworkCaching(bufferTimeSeconds)
            xlabPlayer?.setLiveCaching(bufferTimeSeconds.coerceIn(0.1f, 5.0f))
            
            // RTSP 스트림 재생
            xlabPlayer?.playRtspStreamCompatible(url)
            
            // 연결 상태 업데이트
            isConnected = true
            playerStateCallback?.onPlayerConnected()
            
            // PTZ 제어기 연결 시도 (RTSP URL에서 호스트 추출)
            connectPTZController(url)
            
            Log.d(TAG, "RTSP 스트림 재생 요청 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "RTSP 스트림 재생 실패", e)
            playerStateCallback?.onPlayerError("재생 실패: ${e.message}")
        }
    }
    
    /**
     * 재생 일시정지
     */
    fun pauseStream() {
        try {
            Log.d(TAG, "스트림 일시정지")
            xlabPlayer?.pause()
            
            // 재생 상태 업데이트
            isPlaying = false
            playerStateCallback?.onPlayerPaused()
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 일시정지 실패", e)
        }
    }
    
    /**
     * 재생 재개
     */
    fun resumeStream() {
        try {
            Log.d(TAG, "스트림 재개")
            xlabPlayer?.play()
            
            // 재생 상태 업데이트
            isPlaying = true
            playerStateCallback?.onPlayerPlaying()
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 재개 실패", e)
        }
    }
    
    /**
     * 스트림 연결 해제
     */
    fun disconnectStream() {
        try {
            Log.d(TAG, "스트림 연결 해제")
            xlabPlayer?.pause()
            
            // PTZ 제어기 연결 해제
            c12PTZController?.disconnect()
            
            // 연결 상태 업데이트
            isConnected = false
            isPlaying = false
            playerStateCallback?.onPlayerDisconnected()
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 연결 해제 실패", e)
        }
    }
    
    /**
     * PTZ 이동 (상대적 이동)
     */
    private fun movePTZ(direction: String, deltaValue: Int) {
        try {
            Log.d(TAG, "PTZ 이동: $direction, 델타값: $deltaValue (현재 팬: $currentPan°, 틸트: $currentTilt°)")
            
            // PTZ 제어기 연결 확인
            if (c12PTZController?.isConnected() == true) {
                when (direction) {
                    "UP" -> {
                        // 틸트 증가 (위로 올림)
                        val newTilt = (currentTilt + deltaValue).coerceIn(-90.0f, 90.0f)
                        Log.d(TAG, "틸트 이동: $currentTilt° + $deltaValue° = $newTilt°")
                        
                        // 즉시 현재 값 업데이트
                        currentTilt = newTilt
                        
                        c12PTZController?.setTilt(newTilt, object : C12PTZController.PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                Log.d(TAG, "PTZ 상 이동 성공: $message (새 틸트: $newTilt°)")
                                ptzControlCallback?.onPTZMove(direction, deltaValue)
                            }
                            override fun onError(error: String) {
                                Log.e(TAG, "PTZ 상 이동 실패: $error")
                                // 실패 시 원래 값으로 되돌리기
                                currentTilt -= deltaValue
                            }
                        })
                    }
                    "DOWN" -> {
                        // 틸트 감소 (아래로 내림)
                        val newTilt = (currentTilt + deltaValue).coerceIn(-90.0f, 90.0f)
                        Log.d(TAG, "틸트 이동: $currentTilt° + $deltaValue° = $newTilt°")
                        
                        // 즉시 현재 값 업데이트
                        currentTilt = newTilt
                        
                        c12PTZController?.setTilt(newTilt, object : C12PTZController.PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                Log.d(TAG, "PTZ 하 이동 성공: $message (새 틸트: $newTilt°)")
                                ptzControlCallback?.onPTZMove(direction, deltaValue)
                            }
                            override fun onError(error: String) {
                                Log.e(TAG, "PTZ 하 이동 실패: $error")
                                // 실패 시 원래 값으로 되돌리기
                                currentTilt -= deltaValue
                            }
                        })
                    }
                    "LEFT" -> {
                        // 팬 증가 (왼쪽으로 회전)
                        val newPan = (currentPan + deltaValue).coerceIn(-90.0f, 90.0f)
                        Log.d(TAG, "팬 이동: $currentPan° + $deltaValue° = $newPan°")
                        
                        // 즉시 현재 값 업데이트
                        currentPan = newPan
                        
                        c12PTZController?.setPan(newPan, object : C12PTZController.PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                Log.d(TAG, "PTZ 좌 이동 성공: $message (새 팬: $newPan°)")
                                ptzControlCallback?.onPTZMove(direction, deltaValue)
                            }
                            override fun onError(error: String) {
                                Log.e(TAG, "PTZ 좌 이동 실패: $error")
                                // 실패 시 원래 값으로 되돌리기
                                currentPan -= deltaValue
                            }
                        })
                    }
                    "RIGHT" -> {
                        // 팬 감소 (오른쪽으로 회전)
                        val newPan = (currentPan + deltaValue).coerceIn(-90.0f, 90.0f)
                        Log.d(TAG, "팬 이동: $currentPan° + $deltaValue° = $newPan°")
                        
                        // 즉시 현재 값 업데이트
                        currentPan = newPan
                        
                        c12PTZController?.setPan(newPan, object : C12PTZController.PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                Log.d(TAG, "PTZ 우 이동 성공: $message (새 팬: $newPan°)")
                                ptzControlCallback?.onPTZMove(direction, deltaValue)
                            }
                            override fun onError(error: String) {
                                Log.e(TAG, "PTZ 우 이동 실패: $error")
                                // 실패 시 원래 값으로 되돌리기
                                currentPan -= deltaValue
                            }
                        })
                    }
                }
            } else {
                Log.w(TAG, "PTZ 제어기 미연결 상태")
                ptzControlCallback?.onPTZMove(direction, deltaValue)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTZ 이동 실패", e)
        }
    }
    
    /**
     * 슬라이더에서 팬 각도 설정
     */
    fun setPanAngle(angle: Float) {
        try {
            Log.d(TAG, "슬라이더에서 팬 각도 설정 요청: ${angle}° (현재: ${currentPan}°)")
            
            // 현재 값과 동일하면 무시
            if (currentPan == angle) {
                Log.d(TAG, "팬 각도가 동일하여 무시: ${angle}°")
                return
            }
            
            currentPan = angle.coerceIn(-90.0f, 90.0f)
            Log.d(TAG, "팬 각도 업데이트: ${currentPan}°")
            
            c12PTZController?.setPan(currentPan, object : C12PTZController.PTZMoveCallback {
                override fun onSuccess(message: String) {
                    Log.d(TAG, "슬라이더 팬 설정 성공: $message")
                }
                override fun onError(error: String) {
                    Log.e(TAG, "슬라이더 팬 설정 실패: $error")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "슬라이더 팬 설정 실패", e)
        }
    }
    
    /**
     * 슬라이더에서 틸트 각도 설정
     */
    fun setTiltAngle(angle: Float) {
        try {
            Log.d(TAG, "슬라이더에서 틸트 각도 설정 요청: ${angle}° (현재: ${currentTilt}°)")
            
            // 현재 값과 동일하면 무시
            if (currentTilt == angle) {
                Log.d(TAG, "틸트 각도가 동일하여 무시: ${angle}°")
                return
            }
            
            currentTilt = angle.coerceIn(-90.0f, 90.0f)
            Log.d(TAG, "틸트 각도 업데이트: ${currentTilt}°")
            
            c12PTZController?.setTilt(currentTilt, object : C12PTZController.PTZMoveCallback {
                override fun onSuccess(message: String) {
                    Log.d(TAG, "슬라이더 틸트 설정 성공: $message")
                }
                override fun onError(error: String) {
                    Log.e(TAG, "슬라이더 틸트 설정 실패: $error")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "슬라이더 틸트 설정 실패", e)
        }
    }
    
    /**
     * 슬라이더에서 줌 레벨 설정
     */
    fun setZoomLevel(level: Float) {
        try {
            Log.d(TAG, "슬라이더에서 줌 레벨 설정 요청: ${level}x (현재: ${currentZoom}x)")
            
            // 현재 값과 동일하면 무시
            if (currentZoom == level) {
                Log.d(TAG, "줌 레벨이 동일하여 무시: ${level}x")
                return
            }
            
            currentZoom = level.coerceIn(1.0f, 10.0f)
            Log.d(TAG, "줌 레벨 업데이트: ${currentZoom}x")
            
            c12PTZController?.setZoom(currentZoom, object : C12PTZController.PTZMoveCallback {
                override fun onSuccess(message: String) {
                    Log.d(TAG, "슬라이더 줌 설정 성공: $message")
                }
                override fun onError(error: String) {
                    Log.e(TAG, "슬라이더 줌 설정 실패: $error")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "슬라이더 줌 설정 실패", e)
        }
    }
    
    /**
     * 현재 PTZ 각도 반환
     */
    fun getCurrentPTZAngles(): Triple<Float, Float, Float> {
        return Triple(currentPan, currentTilt, currentZoom)
    }
    
    /**
     * 버퍼시간 설정 (밀리초 단위)
     */
    fun setBufferTime(timeMs: Int) {
        try {
            val clampedTime = timeMs.coerceIn(0, 5000)  // 0~5초 범위
            bufferTime = clampedTime
            val bufferTimeSeconds = clampedTime / 1000.0f
            Log.d(TAG, "버퍼시간 설정: ${bufferTimeSeconds}초 (${clampedTime}ms)")
            
            // 연결된 상태에서만 버퍼시간 적용
            if (isConnected && xlabPlayer != null) {
                Log.d(TAG, "연결된 상태에서 버퍼시간 적용")
                xlabPlayer?.applyBufferTime(bufferTimeSeconds, bufferTimeSeconds.coerceIn(0.1f, 5.0f))
            } else {
                Log.d(TAG, "연결되지 않은 상태이므로 버퍼시간만 저장")
            }
            
            // SharedPreferences에 저장
            prefs.edit().putInt(PREF_BUFFER_TIME, clampedTime).apply()
            Log.d(TAG, "버퍼시간 SharedPreferences에 저장: ${clampedTime}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "버퍼시간 설정 실패", e)
        }
    }
    
    /**
     * 현재 버퍼시간 반환 (밀리초 단위)
     */
    fun getBufferTime(): Int {
        return bufferTime
    }
    
    /**
     * 홈 포지션으로 이동
     */
    fun moveToHome() {
        try {
            Log.d(TAG, "홈 포지션으로 이동 시도 (현재 팬: $currentPan°, 틸트: $currentTilt°)")
            
            if (c12PTZController?.isConnected() == true) {
                Log.d(TAG, "PTZ 제어기 연결됨 - 직접 홈 이동 명령 전송")
                
                // 즉시 로컬 각도 리셋
                currentPan = 0.0f
                currentTilt = 0.0f
                currentZoom = 1.0f
                
                // 팬과 틸트를 개별적으로 빠르게 설정
                c12PTZController?.setPan(0.0f, object : C12PTZController.PTZMoveCallback {
                    override fun onSuccess(message: String) {
                        Log.d(TAG, "팬 홈 설정 성공: $message")
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "팬 홈 설정 실패: $error")
                    }
                })
                
                c12PTZController?.setTilt(0.0f, object : C12PTZController.PTZMoveCallback {
                    override fun onSuccess(message: String) {
                        Log.d(TAG, "틸트 홈 설정 성공: $message")
                        Log.d(TAG, "홈 이동 완료: 팬=0°, 틸트=0°, 줌=1.0x")
                        ptzControlCallback?.onPTZHome()
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "틸트 홈 설정 실패: $error")
                        // 실패해도 로컬 각도는 이미 리셋됨
                        Log.d(TAG, "홈 이동 실패했지만 로컬 각도 리셋됨: 팬=0°, 틸트=0°, 줌=1.0x")
                        ptzControlCallback?.onPTZHome()
                    }
                })
            } else {
                Log.w(TAG, "PTZ 제어기 미연결 상태 - 로컬 각도만 리셋")
                // 연결되지 않았어도 로컬 각도는 리셋
                currentPan = 0.0f
                currentTilt = 0.0f
                currentZoom = 1.0f
                Log.d(TAG, "로컬 각도 리셋 완료: 팬=0°, 틸트=0°, 줌=1.0x")
                ptzControlCallback?.onPTZHome()
            }
        } catch (e: Exception) {
            Log.e(TAG, "홈 이동 실패", e)
            // 예외 발생해도 로컬 각도는 리셋
            currentPan = 0.0f
            currentTilt = 0.0f
            currentZoom = 1.0f
            Log.d(TAG, "예외 발생했지만 로컬 각도 리셋: 팬=0°, 틸트=0°, 줌=1.0x")
            ptzControlCallback?.onPTZHome()
        }
    }
    
    /**
     * 녹화 토글
     */
    private fun toggleRecord() {
        try {
            isRecording = !isRecording
            Log.d(TAG, "녹화 토글: ${if (isRecording) "시작" else "중지"}")
            
            // 버튼 텍스트 업데이트
            recordButton.text = if (isRecording) "⏹" else "⏺"
            
            ptzControlCallback?.onRecordToggle()
        } catch (e: Exception) {
            Log.e(TAG, "녹화 토글 실패", e)
        }
    }
    
    /**
     * 사진 촬영
     */
    private fun capturePhoto() {
        try {
            Log.d(TAG, "사진 촬영")
            ptzControlCallback?.onPhotoCapture()
        } catch (e: Exception) {
            Log.e(TAG, "사진 촬영 실패", e)
        }
    }
    
    /**
     * PTZ 버튼 활성화/비활성화
     */
    private fun setPTZButtonsEnabled(enabled: Boolean) {
        upButton.isEnabled = enabled
        downButton.isEnabled = enabled
        leftButton.isEnabled = enabled
        rightButton.isEnabled = enabled
        homeButton.isEnabled = enabled
        recordButton.isEnabled = enabled
        photoButton.isEnabled = enabled
    }
    
    private fun createPlaybackListener(): XlabPlayer.PlaybackListener {
        return object : XlabPlayer.PlaybackListener {
            override fun onPlayerReady() {
                Log.d(TAG, "플레이어 준비 완료")
                playerStateCallback?.onPlayerReady()
            }
            
            override fun onBuffering() {
                Log.d(TAG, "버퍼링 중...")
            }
            
            override fun onPlaying() {
                Log.d(TAG, "재생 중")
                isPlaying = true
                playerStateCallback?.onPlayerPlaying()
            }
            
            override fun onPaused() {
                Log.d(TAG, "일시정지됨")
                isPlaying = false
                playerStateCallback?.onPlayerPaused()
            }
            
            override fun onEnded() {
                Log.d(TAG, "재생 완료")
                isPlaying = false
                playerStateCallback?.onPlayerPaused()
            }
            
            override fun onVideoSizeChanged(width: Int, height: Int) {
                Log.d(TAG, "비디오 크기 변경: ${width}x${height}")
            }
        }
    }
    
    private fun createErrorListener(): XlabPlayer.ErrorListener {
        return object : XlabPlayer.ErrorListener {
            override fun onError(error: String, exception: Exception?) {
                Log.e(TAG, "재생 오류: $error", exception)
                playerStateCallback?.onPlayerError(error)
            }
        }
    }
    
    /**
     * PTZ 제어기 연결
     */
    private fun connectPTZController(rtspUrl: String) {
        try {
            Log.d(TAG, "PTZ 제어기 연결 시도")
            
            // RTSP URL에서 호스트 추출
            val host = extractHostFromRtspUrl(rtspUrl)
            if (host != null) {
                c12PTZController?.configure(
                    host = host,
                    ptzPort = 5000,
                    username = "admin",
                    password = "",
                    timeout = 5000
                )
                
                c12PTZController?.connect(object : C12PTZController.ConnectionCallback {
                    override fun onSuccess(message: String) {
                        Log.d(TAG, "PTZ 제어기 연결 성공: $message")
                        
                        // 연결 성공 시 기본값으로 초기화
                        currentPan = 0.0f
                        currentTilt = 0.0f
                        currentZoom = 1.0f
                        
                        Log.d(TAG, "PTZ 제어기 초기화 완료: 팬=0°, 틸트=0°, 줌=1.0x")
                        
                        // PTZ 제어 콜백으로 초기화 이벤트 발생
                        ptzControlCallback?.onPTZMove("SYNC", 0)
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "PTZ 제어기 연결 실패: $error")
                    }
                })
            } else {
                Log.w(TAG, "RTSP URL에서 호스트를 추출할 수 없음: $rtspUrl")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "PTZ 제어기 연결 실패", e)
        }
    }
    
    /**
     * RTSP URL에서 호스트 추출
     */
    private fun extractHostFromRtspUrl(rtspUrl: String): String? {
        return try {
            val uri = java.net.URI(rtspUrl)
            uri.host
        } catch (e: Exception) {
            Log.e(TAG, "RTSP URL 파싱 실패: $rtspUrl", e)
            null
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        xlabPlayer?.release()
        c12PTZController?.release()
        xlabPlayer = null
        c12PTZController = null
        Log.d(TAG, "비디오 플레이어 Fragment 종료")
    }
} 