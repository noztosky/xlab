package com.xlab.Player

// Build: 7
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
import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView

/**
 * 비디오 플레이어 Fragment (순수 비디오 플레이어만 포함)
 */
class VideoPlayerFragment : Fragment() {
    
    private lateinit var urlEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var fullscreenButton: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var ptzController: C12PTZController
    private lateinit var statusText: TextView
    private var xlabPlayer: XlabPlayer? = null
    private var videoUrl: String = ""
    private var isFullscreenMode = false
    private var originalFragmentLayoutParams: ViewGroup.LayoutParams? = null
    private var originalFragmentParentLayoutParams: ViewGroup.LayoutParams? = null
    private var originalFragmentPadding = intArrayOf(0, 0, 0, 0)
    private var originalParentPadding = intArrayOf(0, 0, 0, 0)
    private var originalParentStates = mutableListOf<Pair<ViewGroup, IntArray>>()
    private var originalOtherContainerVisibility = View.VISIBLE
    private var originalSurfaceViewSize = intArrayOf(0, 0)  // width, height
    private var originalSurfaceViewLayoutParams: ViewGroup.LayoutParams? = null
    
    companion object {
        private const val TAG = "VideoPlayerFragment"
        private const val FULLSCREEN_TAG = "FULLSCREEN_STATE"  // 전체화면 전용 로그 태그
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
                    Log.d(TAG, "Fragment 레이아웃 파라미터 - 너비: ${rootParams?.width}, 높이: ${rootParams?.height}")
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
        Log.d(TAG, "전체화면 버튼 설정 시작")
        
        // 버튼이 null인지 확인
        if (fullscreenButton == null) {
            Log.e(TAG, "전체화면 버튼이 null입니다!")
            return
        }
        
        Log.d(TAG, "전체화면 버튼 찾음: ${fullscreenButton.id}")
        
        fullscreenButton.setOnTouchListener { button, event ->
            Log.d(TAG, "전체화면 버튼 터치 이벤트: ${event.action}")
            
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "전체화면 버튼 ACTION_DOWN")
                    button.setBackgroundResource(R.drawable.button_pressed_blue)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "전체화면 버튼 ACTION_UP")
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    Log.d(TAG, "전체화면 버튼 터치업")
                    toggleFullscreen()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "전체화면 버튼 ACTION_CANCEL")
                    button.setBackgroundResource(R.drawable.button_background_10_percent_black)
                    true
                }
                else -> {
                    Log.d(TAG, "전체화면 버튼 기타 이벤트: ${event.action}")
                    false
                }
            }
        }
        
        Log.d(TAG, "전체화면 버튼 설정 완료")
    }
    
    /**
     * 전체화면 토글 (RTSP 플레이어 내부 토글)
     */
    private fun toggleFullscreen() {
        try {
            Log.d(TAG, "RTSP 플레이어 전체화면 토글 - 현재 상태: $isFullscreenMode")
            
            val activity = activity ?: return
            
            if (!isFullscreenMode) {
                Log.d(TAG, "전체화면 모드로 전환")
                enterFullscreen(activity)
            } else {
                Log.d(TAG, "일반 모드로 전환")
                exitFullscreen(activity)
            }
            
            // 전체화면 버튼 아이콘 업데이트 (선택사항)
            updateFullscreenButtonIcon()
            
        } catch (e: Exception) {
            Log.e(TAG, "RTSP 플레이어 전체화면 토글 실패", e)
        }
    }
    
    /**
     * RTSP 플레이어 전체화면 진입
     */
    private fun enterFullscreen(activity: Activity) {
        try {
            // 1. 원본 상태들 저장
            saveOriginalStates()
            
            // 2. 시스템 UI 숨기기 (상태바, 네비게이션바)
            hideSystemUI(activity)
            
            // 3. ActionBar 숨기기
            (activity as? AppCompatActivity)?.supportActionBar?.hide()
            
            // 4. Activity의 다른 UI 요소들 숨기기 (다른 플레이어, 버튼들)
            hideActivityElements()
            
            // 5. 현재 Fragment를 전체화면으로 확장
            expandFragmentToFullscreen()
            
            // 6. 비디오 SurfaceView를 전체화면에 맞게 조정
            view?.post {
                adjustSurfaceViewStyle(true)
            }
            
            isFullscreenMode = true
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "RTSP 플레이어 전체화면 진입 실패", e)
        }
    }
    
    /**
     * RTSP 플레이어 전체화면 해제
     */
    private fun exitFullscreen(activity: Activity) {
        try {
            // 1. 시스템 UI 복원 (상태바, 네비게이션바)
            showSystemUI(activity)
            
            // 2. ActionBar 복원
            (activity as? AppCompatActivity)?.supportActionBar?.show()
            
            // 3. Activity의 다른 UI 요소들 복원
            showActivityElements()
            
            // 4. Fragment를 원래 크기로 복원
            restoreFragmentSize()
            
            // 5. 비디오 SurfaceView를 일반 모드에 맞게 조정
            view?.post {
                adjustSurfaceViewStyle(false)
            }
            
            isFullscreenMode = false
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "RTSP 플레이어 전체화면 해제 실패", e)
        }
    }
    
    /**
     * 원본 상태들 저장 (수정된 버전)
     */
    private fun saveOriginalStates() {
        try {
            val fragmentView = view ?: return
            val parent = fragmentView.parent as? ViewGroup ?: return
            
            // Fragment Container의 원본 레이아웃 파라미터 저장
            val containerParams = parent.layoutParams
            if (containerParams is LinearLayout.LayoutParams) {
                Log.d(FULLSCREEN_TAG, "전체화면 전 Container - width: ${containerParams.width}, height: ${containerParams.height}, weight: ${containerParams.weight}")
            }
            originalFragmentParentLayoutParams = when (containerParams) {
                is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(containerParams)
                is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(containerParams)
                else -> containerParams
            }
            
            // Fragment 자체의 원본 레이아웃 파라미터 저장
            val fragmentParams = fragmentView.layoutParams
            if (fragmentParams != null) {
                Log.d(FULLSCREEN_TAG, "전체화면 전 Fragment - width: ${fragmentParams.width}, height: ${fragmentParams.height}")
            }
            originalFragmentLayoutParams = when (fragmentParams) {
                is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(fragmentParams)
                is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(fragmentParams)
                else -> fragmentParams
            }
            
            // SurfaceView 원본 크기 저장
            originalSurfaceViewSize[0] = surfaceView.width
            originalSurfaceViewSize[1] = surfaceView.height
            Log.d(FULLSCREEN_TAG, "전체화면 전 SurfaceView 크기: ${originalSurfaceViewSize[0]}x${originalSurfaceViewSize[1]}")
            
            // SurfaceView 원본 레이아웃 파라미터 저장
            originalSurfaceViewLayoutParams = when (surfaceView.layoutParams) {
                is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(surfaceView.layoutParams as FrameLayout.LayoutParams)
                is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(surfaceView.layoutParams as LinearLayout.LayoutParams)
                else -> surfaceView.layoutParams
            }
            Log.d(FULLSCREEN_TAG, "전체화면 전 SurfaceView 레이아웃 파라미터: width=${originalSurfaceViewLayoutParams?.width}, height=${originalSurfaceViewLayoutParams?.height}")
            
            // Fragment와 Container의 원본 패딩 저장
            originalFragmentPadding = intArrayOf(
                fragmentView.paddingLeft, fragmentView.paddingTop,
                fragmentView.paddingRight, fragmentView.paddingBottom
            )
            
            originalParentPadding = intArrayOf(
                parent.paddingLeft, parent.paddingTop,
                parent.paddingRight, parent.paddingBottom
            )
            
            // 다른 Fragment 컨테이너의 가시성 저장
            val activity = requireActivity()
            val container1 = activity.findViewById<View>(R.id.video_player_container1)
            val container2 = activity.findViewById<View>(R.id.video_player_container2)
            
            // 현재 전체화면이 되는 컨테이너가 아닌 다른 컨테이너 숨기기
            if (parent.id == R.id.video_player_container1) {
                originalOtherContainerVisibility = container2?.visibility ?: View.VISIBLE
            } else if (parent.id == R.id.video_player_container2) {
                originalOtherContainerVisibility = container1?.visibility ?: View.VISIBLE
            }
            
            // 상위 ViewGroup들의 패딩 저장
            originalParentStates.clear()
            var currentParent = parent.parent as? ViewGroup
            var depth = 0
            while (currentParent != null && currentParent.id != android.R.id.content && depth < 5) {
                val padding = intArrayOf(
                    currentParent.paddingLeft, currentParent.paddingTop,
                    currentParent.paddingRight, currentParent.paddingBottom
                )
                originalParentStates.add(Pair(currentParent, padding))
                currentParent = currentParent.parent as? ViewGroup
                depth++
            }
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "원본 상태 저장 실패", e)
        }
    }
    
    /**
     * 시스템 UI 숨기기 (수정된 버전)
     */
    private fun hideSystemUI(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
            Log.d(TAG, "시스템 UI 숨김 완료")
        } catch (e: Exception) {
            Log.e(TAG, "시스템 UI 숨기기 실패", e)
        }
    }
    
    /**
     * 시스템 UI 복원 (수정된 버전)
     */
    private fun showSystemUI(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            Log.d(TAG, "시스템 UI 복원 완료")
        } catch (e: Exception) {
            Log.e(TAG, "시스템 UI 복원 실패", e)
        }
    }
    
    /**
     * Activity UI 요소들 숨기기 (수정된 버전)
     */
    private fun hideActivityElements() {
        try {
            val activity = activity ?: return
            
            // 모든 컨트롤 UI 요소들 숨기기
            val uiElementIds = listOf(
                R.id.url_edit_text1, R.id.connect_button1, R.id.disconnect_button1,
                R.id.play_button1, R.id.pause_button1,
                R.id.url_edit_text2, R.id.connect_button2, R.id.disconnect_button2,
                R.id.play_button2, R.id.pause_button2
            )
            
            uiElementIds.forEach { id ->
                activity.findViewById<View>(id)?.visibility = View.GONE
            }
            
            // 현재 전체화면이 되지 않는 다른 Fragment 컨테이너 숨기기
            val currentContainer = view?.parent as? ViewGroup
            when (currentContainer?.id) {
                R.id.video_player_container1 -> {
                    // Player 1이 전체화면이면 Player 2 컨테이너 숨기기
                    activity.findViewById<View>(R.id.video_player_container2)?.visibility = View.GONE
                }
                R.id.video_player_container2 -> {
                    // Player 2가 전체화면이면 Player 1 컨테이너 숨기기  
                    activity.findViewById<View>(R.id.video_player_container1)?.visibility = View.GONE
                }
            }
            
            Log.d(TAG, "Activity UI 요소들 숨김 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "Activity UI 요소 숨기기 실패", e)
        }
    }
    
    /**
     * Activity UI 요소들 복원 (수정된 버전)
     */
    private fun showActivityElements() {
        try {
            Log.d(FULLSCREEN_TAG, "showActivityElements() 시작")
            val activity = activity ?: run {
                Log.e(FULLSCREEN_TAG, "activity가 null입니다!")
                return
            }
            Log.d(FULLSCREEN_TAG, "activity 확인 완료")
            
            // 모든 컨트롤 UI 요소들 복원
            val uiElementIds = listOf(
                R.id.url_edit_text1, R.id.connect_button1, R.id.disconnect_button1,
                R.id.play_button1, R.id.pause_button1,
                R.id.url_edit_text2, R.id.connect_button2, R.id.disconnect_button2,
                R.id.play_button2, R.id.pause_button2
            )
            
            Log.d(FULLSCREEN_TAG, "UI 요소들 가시성 복원 시작")
            uiElementIds.forEach { id ->
                activity.findViewById<View>(id)?.visibility = View.VISIBLE
            }
            Log.d(FULLSCREEN_TAG, "UI 요소들 가시성 복원 완료")
            
            // 양쪽 Fragment 컨테이너 모두 복원
            Log.d(FULLSCREEN_TAG, "Container 찾기 시작")
            val container1 = activity.findViewById<ViewGroup>(R.id.video_player_container1)
            val container2 = activity.findViewById<ViewGroup>(R.id.video_player_container2)
            Log.d(FULLSCREEN_TAG, "Container1: ${container1?.javaClass?.simpleName}, Container2: ${container2?.javaClass?.simpleName}")
            
            Log.d(FULLSCREEN_TAG, "Container들 복원 시작")
            
            container1?.let { 
                it.visibility = View.VISIBLE
                Log.d(FULLSCREEN_TAG, "Container1 가시성 복원")
                
                // Container1의 부모 LinearLayout에서 weight 설정
                val parent1 = it.parent as? LinearLayout
                if (parent1 != null) {
                    Log.d(FULLSCREEN_TAG, "Container1의 부모 LinearLayout 발견")
                    // Container1은 원본 weight가 0.0이므로 그대로 유지
                    Log.d(FULLSCREEN_TAG, "Container1 weight는 이미 복원됨")
                } else {
                    Log.w(FULLSCREEN_TAG, "Container1의 부모가 LinearLayout이 아닙니다! 부모 타입: ${it.parent?.javaClass?.simpleName}")
                }
            }
            
            container2?.let { 
                it.visibility = originalOtherContainerVisibility
                Log.d(FULLSCREEN_TAG, "Container2 가시성 복원: $originalOtherContainerVisibility")
                
                // Container2의 부모 LinearLayout에서 weight 설정
                val parent2 = it.parent as? LinearLayout
                if (parent2 != null) {
                    Log.d(FULLSCREEN_TAG, "Container2의 부모 LinearLayout 발견")
                    // Container2는 원래 1.0f였으므로 복원
                    Log.d(FULLSCREEN_TAG, "Container2 weight는 이미 복원됨")
                } else {
                    Log.w(FULLSCREEN_TAG, "Container2의 부모가 LinearLayout이 아닙니다! 부모 타입: ${it.parent?.javaClass?.simpleName}")
                }
            }
            
            Log.d(TAG, "Activity UI 요소들 복원 완료")
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "Activity UI 요소 복원 실패", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Fragment를 전체화면으로 확장 (수정된 버전)
     */
    private fun expandFragmentToFullscreen() {
        try {
            Log.d(FULLSCREEN_TAG, "expandFragmentToFullscreen() 시작")
            
            val fragmentView = view ?: run {
                Log.e(FULLSCREEN_TAG, "fragmentView가 null입니다!")
                return
            }
            Log.d(FULLSCREEN_TAG, "fragmentView 확인 완료")
            
            val parent = fragmentView.parent as? ViewGroup ?: run {
                Log.e(FULLSCREEN_TAG, "parent ViewGroup이 null입니다!")
                return
            }
            Log.d(FULLSCREEN_TAG, "parent ViewGroup 확인 완료: ${parent.javaClass.simpleName}")
            
            Log.d(FULLSCREEN_TAG, "전체화면 확장 시작")
            
            // Activity의 메인 컨테이너 찾기 (LinearLayout)
            Log.d(FULLSCREEN_TAG, "Activity 찾기 시작")
            val activity = requireActivity()
            Log.d(FULLSCREEN_TAG, "Activity 찾기 완료: ${activity.javaClass.simpleName}")
            
            Log.d(FULLSCREEN_TAG, "메인 컨테이너 찾기 시작")
            val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
            Log.d(FULLSCREEN_TAG, "content 뷰 타입: ${contentView?.javaClass?.simpleName}")
            val mainContainer = contentView?.getChildAt(0) as? ViewGroup
            Log.d(FULLSCREEN_TAG, "메인 컨테이너 찾기 완료: ${mainContainer?.javaClass?.simpleName}")
            
            // 현재 Fragment의 container 찾기
            Log.d(FULLSCREEN_TAG, "parent.id: ${parent.id}")
            Log.d(FULLSCREEN_TAG, "R.id.video_player_container1: ${R.id.video_player_container1}")
            Log.d(FULLSCREEN_TAG, "R.id.video_player_container2: ${R.id.video_player_container2}")
            
            val currentContainer = when {
                parent.id == R.id.video_player_container1 -> {
                    Log.d(FULLSCREEN_TAG, "container1로 식별됨")
                    parent
                }
                parent.id == R.id.video_player_container2 -> {
                    Log.d(FULLSCREEN_TAG, "container2로 식별됨")
                    parent
                }
                else -> {
                    Log.d(FULLSCREEN_TAG, "기타 container로 처리됨")
                    parent
                }
            }
            
            Log.d(TAG, "현재 컨테이너 ID: ${currentContainer.id}")
            
            // Fragment Container를 전체화면으로 확장
            Log.d(FULLSCREEN_TAG, "Container 레이아웃 파라미터 확인 시작")
            val containerParams = currentContainer.layoutParams
            Log.d(FULLSCREEN_TAG, "containerParams: ${containerParams?.javaClass?.simpleName}")
            
            if (containerParams != null) {
                Log.d(FULLSCREEN_TAG, "containerParams가 null이 아닙니다. 처리 시작")
                // 원본 저장
                originalFragmentParentLayoutParams = when (containerParams) {
                    is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(containerParams)
                    is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(containerParams)
                    else -> containerParams
                }
                
                // 전체화면으로 설정
                Log.d(FULLSCREEN_TAG, "전체화면 설정 전 Container - width: ${containerParams.width}, height: ${containerParams.height}")
                
                containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                
                Log.d(FULLSCREEN_TAG, "전체화면 설정 후 Container - width: ${containerParams.width}, height: ${containerParams.height}")
                
                // LinearLayout.LayoutParams인 경우에만 weight와 margins 설정
                if (containerParams is LinearLayout.LayoutParams) {
                    Log.d(FULLSCREEN_TAG, "LinearLayout.LayoutParams 처리 - 기존 weight: ${containerParams.weight}")
                    containerParams.weight = 0f
                    containerParams.setMargins(0, 0, 0, 0)
                    Log.d(FULLSCREEN_TAG, "LinearLayout.LayoutParams 처리 완료 - 새 weight: ${containerParams.weight}, 마진: 0,0,0,0")
                } else if (containerParams is ViewGroup.MarginLayoutParams) {
                    Log.d(FULLSCREEN_TAG, "MarginLayoutParams 처리 - 마진을 0,0,0,0으로 설정")
                    containerParams.setMargins(0, 0, 0, 0)
                }
                
                currentContainer.layoutParams = containerParams
                Log.d(FULLSCREEN_TAG, "Container 레이아웃 파라미터 적용 완료")
                
                Log.d(FULLSCREEN_TAG, "Container 전체화면으로 설정 완료")
            } else {
                Log.e(FULLSCREEN_TAG, "containerParams가 null입니다!")
            }
            
            // Fragment 자체를 전체화면으로 설정
            val fragmentParams = fragmentView.layoutParams as? ViewGroup.LayoutParams
            if (fragmentParams != null) {
                // 원본 저장
                originalFragmentLayoutParams = when (fragmentParams) {
                    is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(fragmentParams)
                    is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(fragmentParams)
                    else -> fragmentParams
                }
                
                // 전체화면으로 설정
                Log.d(FULLSCREEN_TAG, "전체화면 설정 전 Fragment - width: ${fragmentParams.width}, height: ${fragmentParams.height}")
                
                fragmentParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                fragmentParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                fragmentView.layoutParams = fragmentParams
                
                Log.d(FULLSCREEN_TAG, "전체화면 설정 후 Fragment - width: ${fragmentParams.width}, height: ${fragmentParams.height}")
                
                Log.d(TAG, "Fragment 전체화면으로 설정 완료")
            }
            
            // 모든 여백 제거
            fragmentView.setPadding(0, 0, 0, 0)
            currentContainer.setPadding(0, 0, 0, 0)
            
            // Activity의 루트 뷰부터 시작해서 모든 패딩과 마진 제거
            val currentActivity = requireActivity()
            val rootView = currentActivity.findViewById<ViewGroup>(android.R.id.content)
            rootView?.setPadding(0, 0, 0, 0)
            
            // DecorView도 패딩 제거
            val decorView = currentActivity.window.decorView as? ViewGroup
            decorView?.setPadding(0, 0, 0, 0)
            
            // 부모들의 여백도 모두 제거
            var parentView: ViewGroup? = currentContainer.parent as? ViewGroup
            var depth = 0
            while (parentView != null && depth < 10) {
                Log.d(TAG, "패딩 제거 중: ${parentView.javaClass.simpleName} (ID: ${parentView.id})")
                
                // 패딩 제거
                parentView.setPadding(0, 0, 0, 0)
                
                // 마진 제거
                val parentLayoutParams = parentView.layoutParams
                if (parentLayoutParams is ViewGroup.MarginLayoutParams) {
                    parentLayoutParams.setMargins(0, 0, 0, 0)
                    parentView.layoutParams = parentLayoutParams
                }
                
                // 만약 이 뷰가 LinearLayout이라면 자식들의 weight도 조정
                if (parentView is LinearLayout) {
                    for (i in 0 until parentView.childCount) {
                        val child = parentView.getChildAt(i)
                        val childParams = child.layoutParams
                        if (childParams is LinearLayout.LayoutParams && child == currentContainer) {
                            childParams.weight = 1.0f
                            childParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            childParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            child.layoutParams = childParams
                        }
                    }
                }
                
                // 다음 부모로 이동
                if (parentView.id == android.R.id.content) break
                parentView = parentView.parent as? ViewGroup
                depth++
            }
            
            // 레이아웃 갱신
            currentContainer.requestLayout()
            fragmentView.requestLayout()
            
            Log.d(TAG, "전체화면 확장 완료")
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "전체화면 확장 실패", e)
            e.printStackTrace()
        }
    }

    /**
     * Fragment 크기 복원 (수정된 버전)
     */
    private fun restoreFragmentSize() {
        try {
            val fragmentView = view ?: return
            val parent = fragmentView.parent as? ViewGroup ?: return
            
            Log.d(FULLSCREEN_TAG, "========== 원래 크기로 복원 시작 ==========")
            
            // Fragment 컨테이너 복원
            originalFragmentParentLayoutParams?.let { originalParams ->
                Log.d(FULLSCREEN_TAG, "Container 복원 시작 - 원본 타입: ${originalParams.javaClass.simpleName}")
                
                // 타입 안전성을 위해 캐스팅
                when (originalParams) {
                    is LinearLayout.LayoutParams -> {
                        val containerParams = parent.layoutParams
                        Log.d(FULLSCREEN_TAG, "현재 Container 파라미터 타입: ${containerParams?.javaClass?.simpleName}")
                        
                        if (containerParams is LinearLayout.LayoutParams) {
                            Log.d(FULLSCREEN_TAG, "복원 전 Container - width: ${containerParams.width}, height: ${containerParams.height}, weight: ${containerParams.weight}")
                            
                            containerParams.width = originalParams.width
                            containerParams.height = originalParams.height
                            containerParams.weight = originalParams.weight
                            containerParams.setMargins(
                                originalParams.leftMargin,
                                originalParams.topMargin,
                                originalParams.rightMargin,
                                originalParams.bottomMargin
                            )
                            parent.layoutParams = containerParams
                            
                            Log.d(FULLSCREEN_TAG, "복원 후 Container - width: ${containerParams.width}, height: ${containerParams.height}, weight: ${containerParams.weight}")
                            Log.d(FULLSCREEN_TAG, "복원 후 Container 마진 - left: ${containerParams.leftMargin}, top: ${containerParams.topMargin}, right: ${containerParams.rightMargin}, bottom: ${containerParams.bottomMargin}")
                        } else {
                            Log.d(FULLSCREEN_TAG, "타입 불일치로 직접 복원")
                            parent.layoutParams = originalParams
                        }
                    }
                    is FrameLayout.LayoutParams -> {
                        val containerParams = parent.layoutParams
                        Log.d(FULLSCREEN_TAG, "현재 Container 파라미터 타입: ${containerParams?.javaClass?.simpleName}")
                        
                        if (containerParams is FrameLayout.LayoutParams) {
                            Log.d(FULLSCREEN_TAG, "복원 전 Container - width: ${containerParams.width}, height: ${containerParams.height}")
                            
                            containerParams.width = originalParams.width
                            containerParams.height = originalParams.height
                            containerParams.setMargins(
                                originalParams.leftMargin,
                                originalParams.topMargin,
                                originalParams.rightMargin,
                                originalParams.bottomMargin
                            )
                            parent.layoutParams = containerParams
                            
                            Log.d(FULLSCREEN_TAG, "복원 후 Container - width: ${containerParams.width}, height: ${containerParams.height}")
                            Log.d(FULLSCREEN_TAG, "복원 후 Container 마진 - left: ${containerParams.leftMargin}, top: ${containerParams.topMargin}, right: ${containerParams.rightMargin}, bottom: ${containerParams.bottomMargin}")
                        } else {
                            Log.d(FULLSCREEN_TAG, "타입 불일치로 직접 복원")
                            parent.layoutParams = originalParams
                        }
                    }
                    else -> {
                        Log.d(FULLSCREEN_TAG, "기타 타입으로 직접 복원: ${originalParams.javaClass.simpleName}")
                        parent.layoutParams = originalParams
                    }
                }
                Log.d(FULLSCREEN_TAG, "Container 레이아웃 파라미터 복원 완료")
            } ?: run {
                Log.w(FULLSCREEN_TAG, "원본 Container 파라미터가 null입니다!")
            }
            
            // Fragment 자체 복원
            originalFragmentLayoutParams?.let { originalParams ->
                Log.d(FULLSCREEN_TAG, "Fragment 복원 시작 - 원본 타입: ${originalParams.javaClass.simpleName}")
                Log.d(FULLSCREEN_TAG, "복원 전 Fragment - width: ${fragmentView.layoutParams?.width}, height: ${fragmentView.layoutParams?.height}")
                
                fragmentView.layoutParams = originalParams
                
                Log.d(FULLSCREEN_TAG, "복원 후 Fragment - width: ${fragmentView.layoutParams?.width}, height: ${fragmentView.layoutParams?.height}")
                Log.d(FULLSCREEN_TAG, "Fragment 레이아웃 파라미터 복원 완료")
            } ?: run {
                Log.w(FULLSCREEN_TAG, "원본 Fragment 파라미터가 null입니다!")
            }
            
            // 원본 padding 복원
            if (originalFragmentPadding.isNotEmpty()) {
                Log.d(FULLSCREEN_TAG, "복원 전 Fragment 패딩 - left: ${fragmentView.paddingLeft}, top: ${fragmentView.paddingTop}, right: ${fragmentView.paddingRight}, bottom: ${fragmentView.paddingBottom}")
                
                fragmentView.setPadding(
                    originalFragmentPadding[0],
                    originalFragmentPadding[1],
                    originalFragmentPadding[2],
                    originalFragmentPadding[3]
                )
                
                Log.d(FULLSCREEN_TAG, "복원 후 Fragment 패딩 - left: ${fragmentView.paddingLeft}, top: ${fragmentView.paddingTop}, right: ${fragmentView.paddingRight}, bottom: ${fragmentView.paddingBottom}")
            } else {
                Log.w(FULLSCREEN_TAG, "원본 Fragment 패딩이 비어있습니다!")
            }
            
            if (originalParentPadding.isNotEmpty()) {
                Log.d(FULLSCREEN_TAG, "복원 전 Container 패딩 - left: ${parent.paddingLeft}, top: ${parent.paddingTop}, right: ${parent.paddingRight}, bottom: ${parent.paddingBottom}")
                
                parent.setPadding(
                    originalParentPadding[0],
                    originalParentPadding[1],
                    originalParentPadding[2],
                    originalParentPadding[3]
                )
                
                Log.d(FULLSCREEN_TAG, "복원 후 Container 패딩 - left: ${parent.paddingLeft}, top: ${parent.paddingTop}, right: ${parent.paddingRight}, bottom: ${parent.paddingBottom}")
            } else {
                Log.w(FULLSCREEN_TAG, "원본 Container 패딩이 비어있습니다!")
            }
            
            // 상위 ViewGroup들의 padding 복원
            Log.d(FULLSCREEN_TAG, "상위 ViewGroup 패딩 복원 시작 - 개수: ${originalParentStates.size}")
            originalParentStates.forEachIndexed { index, (viewGroup, paddings) ->
                if (paddings.size >= 4) {
                    Log.d(FULLSCREEN_TAG, "부모${index} 복원 전 패딩 - left: ${viewGroup.paddingLeft}, top: ${viewGroup.paddingTop}, right: ${viewGroup.paddingRight}, bottom: ${viewGroup.paddingBottom}")
                    
                    viewGroup.setPadding(paddings[0], paddings[1], paddings[2], paddings[3])
                    
                    Log.d(FULLSCREEN_TAG, "부모${index} 복원 후 패딩 - left: ${viewGroup.paddingLeft}, top: ${viewGroup.paddingTop}, right: ${viewGroup.paddingRight}, bottom: ${viewGroup.paddingBottom}")
                } else {
                    Log.w(FULLSCREEN_TAG, "부모${index} 패딩 데이터가 부족합니다: ${paddings.size}")
                }
            }
            
            // 레이아웃 갱신
            Log.d(FULLSCREEN_TAG, "레이아웃 갱신 시작")
            parent.requestLayout()
            fragmentView.requestLayout()
            
            // 약간의 지연 후 한 번 더 갱신 (안정성을 위해)
            fragmentView.post {
                parent.requestLayout()
                fragmentView.requestLayout()
                
                // 복원 후 실제 크기 확인
                fragmentView.post {
                    Log.d(FULLSCREEN_TAG, "복원 후 실제 Fragment 크기: ${fragmentView.width}x${fragmentView.height}")
                    Log.d(FULLSCREEN_TAG, "복원 후 실제 Container 크기: ${parent.width}x${parent.height}")
                    Log.d(FULLSCREEN_TAG, "복원 후 Container 레이아웃 파라미터: width=${parent.layoutParams?.width}, height=${parent.layoutParams?.height}")
                    
                    if (parent.layoutParams is LinearLayout.LayoutParams) {
                        val params = parent.layoutParams as LinearLayout.LayoutParams
                        Log.d(FULLSCREEN_TAG, "복원 후 실제 Container weight: ${params.weight}")
                        Log.d(FULLSCREEN_TAG, "복원 후 실제 Container 마진: left=${params.leftMargin}, top=${params.topMargin}, right=${params.rightMargin}, bottom=${params.bottomMargin}")
                    }
                    
                    // Activity의 전체 크기와 비교
                    val activity = requireActivity()
                    val displayMetrics = activity.resources.displayMetrics
                    Log.d(FULLSCREEN_TAG, "전체 화면 크기: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
                }
                
                Log.d(FULLSCREEN_TAG, "지연된 레이아웃 갱신 완료")
            }
            
            Log.d(FULLSCREEN_TAG, "========== 원래 크기 복원 완료 ==========")
            
        } catch (e: Exception) {
            Log.e(FULLSCREEN_TAG, "원래 크기 복원 실패", e)
        }
    }
    

    
    /**
     * 전체화면 버튼 아이콘 업데이트
     */
    private fun updateFullscreenButtonIcon() {
        try {
            // 전체화면 모드에 따라 버튼 텍스트나 아이콘 변경
            if (isFullscreenMode) {
                // 전체화면 모드일 때는 축소 아이콘 (⛶ 또는 ⧉)
                fullscreenButton.text = "⛶"
            } else {
                // 일반 모드일 때는 확대 아이콘 (⛶ 또는 ⧉)
                fullscreenButton.text = "⛶"
            }
        } catch (e: Exception) {
            Log.e(TAG, "전체화면 버튼 아이콘 업데이트 실패", e)
        }
    }
    
    /**
     * 전체화면 버튼 위치 업데이트
     */
    private fun updateFullscreenButtonPosition() {
        try {
            // RTSP 플레이어 내부에서 전체화면 버튼은 항상 같은 위치 유지
            // Fragment 레이아웃에 고정된 위치에 있으므로 별도 조정 불필요
            Log.d(TAG, "전체화면 버튼 위치 유지")
            
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
    
    /**
     * SurfaceView 크기와 스타일 변경 통합 함수
     * @param isFullscreen true: 전체화면 모드, false: 일반 모드
     */
    private fun adjustSurfaceViewStyle(isFullscreen: Boolean) {
        try {
            if (isFullscreen) {
                // 전체화면 모드 설정
                surfaceView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // 모든 패딩 제거
                surfaceView.setPadding(0, 0, 0, 0)
                
                // SurfaceView의 부모 컨테이너들도 패딩 제거
                var parent = surfaceView.parent as? ViewGroup
                while (parent != null) {
                    parent.setPadding(0, 0, 0, 0)
                    
                    // 부모가 FrameLayout이라면 자식의 마진도 제거
                    val surfaceParams = surfaceView.layoutParams
                    if (surfaceParams is FrameLayout.LayoutParams) {
                        surfaceParams.setMargins(0, 0, 0, 0)
                        surfaceView.layoutParams = surfaceParams
                    }
                    
                    if (parent.id == android.R.id.content) break
                    parent = parent.parent as? ViewGroup
                }
                
                Log.d(FULLSCREEN_TAG, "전체화면 모드로 SurfaceView 설정 완료")
                
            } else {
                // 일반 모드로 복원
                if (originalSurfaceViewLayoutParams != null) {
                    surfaceView.layoutParams = originalSurfaceViewLayoutParams
                    val params = originalSurfaceViewLayoutParams
                    Log.d(FULLSCREEN_TAG, "원본 레이아웃 파라미터로 복원: width=${params?.width}, height=${params?.height}")
                } else {
                    // 원본 파라미터가 없으면 기본값 사용
                    val orientation = resources.configuration.orientation
                    val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    
                    if (isLandscape) {
                        surfaceView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    } else {
                        surfaceView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
                
                // 패딩 제거
                surfaceView.setPadding(0, 0, 0, 0)
                
                Log.d(FULLSCREEN_TAG, "일반 모드로 SurfaceView 복원 완료")
            }
            
            // 강제로 레이아웃 갱신
            surfaceView.requestLayout()
            view?.requestLayout()
            
            // 크기 로그 출력
            surfaceView.post {
                val mode = if (isFullscreen) "전체화면" else "일반"
                Log.d(FULLSCREEN_TAG, "${mode} SurfaceView 크기: ${surfaceView.width}x${surfaceView.height}")
                
                if (!isFullscreen) {
                    Log.d(FULLSCREEN_TAG, "원본 SurfaceView 크기: ${originalSurfaceViewSize[0]}x${originalSurfaceViewSize[1]}")
                    Log.d(FULLSCREEN_TAG, "크기 비교 - 원본: ${originalSurfaceViewSize[0]}x${originalSurfaceViewSize[1]}, 복원: ${surfaceView.width}x${surfaceView.height}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SurfaceView 스타일 변경 실패 (isFullscreen: $isFullscreen)", e)
        }
    }
}