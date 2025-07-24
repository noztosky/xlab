package com.xlab.Player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager

/**
 * H.265 비디오 플레이어 데모 액티비티 (테스트용)
 */
class H265DemoActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "H265DemoActivity"
        private const val PREFS_NAME = "XlabPlayerPrefs"
        private const val PREF_LAST_URL = "last_url"
        
        // 테스트용 RTSP URL 예제들
        private val TEST_RTSP_URLS = arrayOf(
            "rtsp://192.168.144.108:554/stream=1",
            "rtsp://192.168.144.108:555/stream=2"
        )
    }
    
    // UI 컴포넌트
    private lateinit var urlEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var versionTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    
    // PTZ 슬라이더 컴포넌트
    private lateinit var panSeekBar: SeekBar
    private lateinit var tiltSeekBar: SeekBar
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var panValueText: TextView
    private lateinit var tiltValueText: TextView
    private lateinit var zoomValueText: TextView
    private lateinit var ptzResetButton: Button
    

    
    // 슬라이더 프로그래밍적 업데이트 플래그
    private var isUpdatingSliders = false
    
    // 비디오 플레이어 Fragment
    private var videoPlayerFragment: VideoPlayerFragment? = null
    
    // SharedPreferences for URL memory
    private lateinit var prefs: SharedPreferences
    
    // 전체화면 상태 추적
    private var isFullscreenMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "H265DemoActivity onCreate 시작")
        setContentView(R.layout.activity_h265_demo)
        
        // SharedPreferences 초기화
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        Log.d(TAG, "initViews() 호출")
        initViews()
        Log.d(TAG, "setupListeners() 호출")
        setupListeners()
        Log.d(TAG, "setupVideoPlayerFragment() 호출")
        setupVideoPlayerFragment()
        Log.d(TAG, "H265DemoActivity onCreate 완료")
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "화면 방향 변경: ${newConfig.orientation}")
        
        // 전체화면 모드에서 회전 시에도 전체화면 유지
        if (isFullscreenMode) {
            // 회전 후에도 전체화면 상태 유지
            window.decorView.post {
                expandToFullscreen()
                // Fragment의 컨트롤 버튼들이 회전 후에도 제대로 표시되도록 강제로 레이아웃 조정
                videoPlayerFragment?.let { fragment ->
                    fragment.adjustVideoAspectRatioForFullscreen()
                }
            }
        } else {
            // 일반 모드에서 회전 시 UI 요소들 표시
            window.decorView.post {
                restoreNormalLayout()
            }
        }
    }
    
    private fun initViews() {
        urlEditText = findViewById(R.id.url_edit_text)
        statusTextView = findViewById(R.id.status_text_view)
        versionTextView = findViewById(R.id.version_text_view)
        connectButton = findViewById(R.id.connect_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        playButton = findViewById(R.id.play_button)
        pauseButton = findViewById(R.id.pause_button)
        
        // PTZ 슬라이더 초기화
        panSeekBar = findViewById(R.id.pan_seek_bar)
        tiltSeekBar = findViewById(R.id.tilt_seek_bar)
        zoomSeekBar = findViewById(R.id.zoom_seek_bar)
        panValueText = findViewById(R.id.pan_value_text)
        tiltValueText = findViewById(R.id.tilt_value_text)
        zoomValueText = findViewById(R.id.zoom_value_text)
        ptzResetButton = findViewById(R.id.ptz_reset_button)
        

        
        // 마지막 사용한 URL 복원 또는 기본 URL 설정
        val lastUrl = prefs.getString(PREF_LAST_URL, TEST_RTSP_URLS[0])
        urlEditText.setText(lastUrl)
        
        // 초기 상태 설정
        disconnectButton.isEnabled = false
        playButton.isEnabled = false
        pauseButton.isEnabled = false
        
        // PTZ 슬라이더 초기 설정
        setupPTZSliders()
    }
    
    private fun setupListeners() {
        connectButton.setOnClickListener { connectToStream() }
        disconnectButton.setOnClickListener { disconnectFromStream() }
        playButton.setOnClickListener { playStream() }
        pauseButton.setOnClickListener { pauseStream() }
        
        // PTZ 리셋 버튼 리스너
        ptzResetButton.setOnClickListener {
            videoPlayerFragment?.let { fragment ->
                // VideoPlayerFragment의 홈 이동 메서드 호출
                fragment.moveToHome()
                updateStatus("PTZ 홈 포지션으로 이동 요청됨")
            }
        }
    }
    
    private fun setupVideoPlayerFragment() {
        Log.d(TAG, "VideoPlayerFragment 생성 시작")
        // VideoPlayerFragment 추가
        videoPlayerFragment = VideoPlayerFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.video_player_container, videoPlayerFragment!!)
            .commitNow()  // commitNow()를 사용하여 즉시 실행
        
        Log.d(TAG, "Fragment 트랜잭션 완료")
        
        // Fragment가 완전히 생성된 후 콜백 설정을 위해 post 사용
        videoPlayerFragment?.view?.post {
            Log.d(TAG, "Fragment 완전히 생성됨, 콜백 설정 시작")
            setupCallbacks()
        }
        
        // 추가: 즉시 콜백 설정도 시도
        setupCallbacks()
        
    }
    
    private fun setupCallbacks() {
        Log.d(TAG, "!!! setupCallbacks() 함수 실행됨 !!!")
        // 상태 콜백 설정
        videoPlayerFragment?.setPlayerStateCallback(object : VideoPlayerFragment.PlayerStateCallback {
            override fun onPlayerReady() {
                runOnUiThread {
                    updateStatus("플레이어 준비 완료")
                }
            }
            
            override fun onPlayerConnected() {
                runOnUiThread {
                    // 연결 후 버튼 상태 업데이트
                    connectButton.isEnabled = false
                    disconnectButton.isEnabled = true
                    playButton.isEnabled = true
                    pauseButton.isEnabled = false
                    updateStatus("연결됨 - 재생 준비 완료")
                    
                    // 연결 후 PTZ 슬라이더 초기화
                    updatePTZSliders(0.0f, 0.0f, 1.0f)
                }
            }
            
            override fun onPlayerDisconnected() {
                runOnUiThread {
                    // 연결 해제 후 버튼 상태 업데이트
                    connectButton.isEnabled = true
                    disconnectButton.isEnabled = false
                    playButton.isEnabled = false
                    pauseButton.isEnabled = false
                    updateStatus("연결 해제됨")
                }
            }
            
            override fun onPlayerPlaying() {
                runOnUiThread {
                    // 재생 중 버튼 상태 업데이트
                    playButton.isEnabled = false
                    pauseButton.isEnabled = true
                    updateStatus("재생 중")
                }
            }
            
            override fun onPlayerPaused() {
                runOnUiThread {
                    // 일시정지 후 버튼 상태 업데이트
                    playButton.isEnabled = true
                    pauseButton.isEnabled = false
                    updateStatus("일시정지됨")
                }
            }
            
            override fun onPlayerError(error: String) {
                runOnUiThread {
                    // 오류 발생 시 버튼 상태 업데이트
                    connectButton.isEnabled = true
                    disconnectButton.isEnabled = false
                    playButton.isEnabled = false
                    pauseButton.isEnabled = false
                    updateStatus("오류 발생: $error")
                }
            }
        })
        
        // PTZ 제어 콜백 설정
        videoPlayerFragment?.setPTZControlCallback(object : VideoPlayerFragment.PTZControlCallback {
            override fun onPTZMove(direction: String, value: Int) {
                runOnUiThread {
                    when (direction) {
                        "SYNC" -> {
                            updateStatus("PTZ 초기 각도 동기화 완료")
                            
                            // 초기 동기화 시 현재 각도로 슬라이더 업데이트
                            videoPlayerFragment?.let { fragment ->
                                val (pan, tilt, zoom) = fragment.getCurrentPTZAngles()
                                updatePTZSliders(pan, tilt, zoom)
                            }
                        }
                        else -> {
                            updateStatus("PTZ 이동: $direction, 값: $value")
                            
                            // PTZ 버튼 동작 후 현재 각도로 슬라이더 업데이트
                            videoPlayerFragment?.let { fragment ->
                                val (pan, tilt, zoom) = fragment.getCurrentPTZAngles()
                                updatePTZSliders(pan, tilt, zoom)
                            }
                        }
                    }
                }
            }
            
            override fun onPTZHome() {
                runOnUiThread {
                    updateStatus("PTZ 홈 포지션으로 이동")
                    
                    // 홈 이동 후 슬라이더를 0도로 리셋
                    updatePTZSliders(0.0f, 0.0f, 1.0f)
                }
            }
            
            override fun onRecordToggle() {
                runOnUiThread {
                    updateStatus("녹화 토글")
                }
            }
            
            override fun onPhotoCapture() {
                runOnUiThread {
                    updateStatus("사진 촬영")
                }
            }
        })
        
        // 전체화면 콜백 설정 (Fragment에서 Activity로 요청)
        Log.d(TAG, "전체화면 콜백 설정 시작")
        val callback = object : VideoPlayerFragment.FullscreenCallback {
            override fun onToggleFullscreen() {
                Log.d(TAG, "!!! onToggleFullscreen() 콜백 함수 실행됨 !!!")
                try {
                    Log.d(TAG, "runOnUiThread 시작")
                    runOnUiThread {
                        Log.d(TAG, "Fragment에서 전체화면 토글 요청 받음")
                        handleFullscreenToggle()
                    }
                    Log.d(TAG, "runOnUiThread 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "onToggleFullscreen 오류", e)
                }
            }
        }
        Log.d(TAG, "콜백 객체 생성됨: ${callback.javaClass.simpleName}")
        videoPlayerFragment?.setFullscreenCallback(callback)
        Log.d(TAG, "전체화면 콜백 설정 완료")
    }
    

    
    /**
     * PTZ 슬라이더 초기 설정
     */
    private fun setupPTZSliders() {
        // 팬 슬라이더 설정 (-90 ~ 90도)
        panSeekBar.max = 180
        panSeekBar.progress = 90   // 0도 위치 (중앙)
        panValueText.text = "0°"
        
        panSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingSliders) {
                    val panAngle = 90 - progress  // 왼쪽(-90°) ~ 오른쪽(90°)로 변환
                    Log.d(TAG, "사용자 팬 슬라이더 조작: ${panAngle}°")
                    panValueText.text = "${-panAngle}°"  // 표시값만 반대로
                    
                    // VideoPlayerFragment에 팬 각도 전송
                    videoPlayerFragment?.let { fragment ->
                        fragment.setPanAngle(panAngle.toFloat())
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "팬 슬라이더 터치 시작")
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "팬 슬라이더 터치 종료")
            }
        })
        
        // 틸트 슬라이더 설정 (-90 ~ 90도)
        tiltSeekBar.max = 180
        tiltSeekBar.progress = 90   // 0도 위치 (중앙)
        tiltValueText.text = "0°"
        
        tiltSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingSliders) {
                    val tiltAngle = progress - 90  // -90 ~ 90도로 변환
                    Log.d(TAG, "사용자 틸트 슬라이더 조작: ${tiltAngle}°")
                    tiltValueText.text = "${tiltAngle}°"
                    
                    // VideoPlayerFragment에 틸트 각도 전송
                    videoPlayerFragment?.let { fragment ->
                        fragment.setTiltAngle(tiltAngle.toFloat())
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "틸트 슬라이더 터치 시작")
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "틸트 슬라이더 터치 종료")
            }
        })
        
        // 줌 슬라이더 설정 (1.0 ~ 10.0배)
        zoomSeekBar.max = 90
        zoomSeekBar.progress = 0   // 1.0배 위치
        zoomValueText.text = "1.0x"
        
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingSliders) {
                    val zoomLevel = 1.0f + (progress / 10.0f)  // 1.0 ~ 10.0배로 변환
                    Log.d(TAG, "사용자 줌 슬라이더 조작: ${String.format("%.1f", zoomLevel)}x")
                    zoomValueText.text = "${String.format("%.1f", zoomLevel)}x"
                    
                    // VideoPlayerFragment에 줌 레벨 전송
                    videoPlayerFragment?.let { fragment ->
                        fragment.setZoomLevel(zoomLevel)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "줌 슬라이더 터치 시작")
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "줌 슬라이더 터치 종료")
            }
        })
    }
    
    /**
     * VideoPlayerFragment의 현재 PTZ 각도로 슬라이더 업데이트
     */
    private fun updatePTZSliders(pan: Float, tilt: Float, zoom: Float) {
        Log.d(TAG, "슬라이더 업데이트: pan=${pan}°, tilt=${tilt}°, zoom=${zoom}x")
        
        // 프로그래밍적 업데이트 플래그 설정
        isUpdatingSliders = true
        
        try {
            // 팬 슬라이더 업데이트 (-90 ~ 90도 -> 180 ~ 0)
            val panProgress = (90 - pan).toInt().coerceIn(0, 180)
            if (panSeekBar.progress != panProgress) {
                panSeekBar.progress = panProgress
                panValueText.text = "${-pan.toInt()}°"  // 표시값만 반대로
                Log.d(TAG, "팬 슬라이더 업데이트: ${-pan.toInt()}°")
            }
            
            // 틸트 슬라이더 업데이트 (-90 ~ 90도 -> 0 ~ 180)
            val tiltProgress = (tilt + 90).toInt().coerceIn(0, 180)
            if (tiltSeekBar.progress != tiltProgress) {
                tiltSeekBar.progress = tiltProgress
                tiltValueText.text = "${tilt.toInt()}°"
                Log.d(TAG, "틸트 슬라이더 업데이트: ${tilt.toInt()}°")
            }
            
            // 줌 슬라이더 업데이트 (1.0 ~ 10.0배 -> 0 ~ 90)
            val zoomProgress = ((zoom - 1.0f) * 10).toInt().coerceIn(0, 90)
            if (zoomSeekBar.progress != zoomProgress) {
                zoomSeekBar.progress = zoomProgress
                zoomValueText.text = "${String.format("%.1f", zoom)}x"
                Log.d(TAG, "줌 슬라이더 업데이트: ${String.format("%.1f", zoom)}x")
            }
        } finally {
            // 플래그 해제
            isUpdatingSliders = false
        }
    }
    
    private fun connectToStream() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        // URL을 SharedPreferences에 저장
        prefs.edit().putString(PREF_LAST_URL, url).apply()
        
        // VideoPlayerFragment에 URL 전달하여 재생
        videoPlayerFragment?.let { fragment ->
            fragment.playRtspStream(url)
            updateStatus("비디오 플레이어에 연결 요청됨: $url")
        }
    }
    
    private fun disconnectFromStream() {
        // VideoPlayerFragment의 연결 해제
        videoPlayerFragment?.let { fragment ->
            fragment.disconnectStream()
            updateStatus("비디오 플레이어 연결 해제 요청됨")
        }
    }
    
    private fun playStream() {
        // VideoPlayerFragment의 재생 재개
        videoPlayerFragment?.let { fragment ->
            fragment.resumeStream()
            updateStatus("비디오 재생 요청됨")
        }
    }
    
    private fun pauseStream() {
        // VideoPlayerFragment의 일시정지
        videoPlayerFragment?.let { fragment ->
            fragment.pauseStream()
            updateStatus("비디오 일시정지 요청됨")
        }
    }
    
    private fun updateStatus(message: String) {
        val timestamp = System.currentTimeMillis() / 1000 // 초 단위로 표시
        val statusWithTime = "[$timestamp] $message"
        statusTextView.text = statusWithTime
        Log.d(TAG, "상태 업데이트: $message")
    }
    
    /**
     * Fragment에서 직접 호출할 수 있는 전체화면 토글 함수
     */
    fun handleFullscreenToggle() {
        Log.d(TAG, "handleFullscreenToggle() 호출됨")
        runOnUiThread {
            toggleFullscreenMode()
        }
    }
    
    /**
     * 전체화면 모드 토글
     */
    private fun toggleFullscreenMode() {
        try {
            Log.d(TAG, "전체화면 모드 토글")

            if (isFullscreenMode) {
                // 전체화면 모드 해제
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                supportActionBar?.show()  // 액션바 다시 표시
                updateStatus("전체화면 모드 해제")

                // Fragment 컨테이너를 원래 크기로 복원
                restoreNormalLayout()
                isFullscreenMode = false
            } else {
                // 전체화면 모드 설정 - 화면 회전과 동일한 효과
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
                supportActionBar?.hide()  // 액션바 숨기기
                updateStatus("전체화면 모드 활성화")

                // Fragment 컨테이너를 전체화면 크기로 확장
                expandToFullscreen()
                isFullscreenMode = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "전체화면 모드 토글 실패", e)
            updateStatus("전체화면 모드 토글 실패: ${e.message}")
        }
    }
    
    private fun expandToFullscreen() {
        try {
            Log.d(TAG, "expandToFullscreen() 함수 시작")
            val videoContainer = findViewById<View>(R.id.video_player_container)
            
            // 현재 화면 방향 확인
            val orientation = resources.configuration.orientation
            Log.d(TAG, "현재 화면 방향: ${if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "가로모드" else "세로모드"}")
            
            // Activity의 UI 요소들만 숨기기 (Fragment는 유지)
            val elementsToHide = listOf(
                R.id.url_edit_text,
                R.id.connect_button,
                R.id.disconnect_button,
                R.id.play_button,
                R.id.pause_button,
                R.id.pan_seek_bar,
                R.id.tilt_seek_bar,
                R.id.zoom_seek_bar,
                R.id.status_text_view,
                R.id.version_text_view
            )
            
            elementsToHide.forEach { id ->
                findViewById<View>(id)?.visibility = View.GONE
            }
            
            // 비디오 컨테이너를 전체화면으로 설정
            Log.d(TAG, "비디오 컨테이너 크기 변경 전: ${videoContainer.width}x${videoContainer.height}")
            val layoutParams = videoContainer.layoutParams
            Log.d(TAG, "현재 레이아웃 파라미터 타입: ${layoutParams?.javaClass?.simpleName}")
            
            // 비디오 컨테이너 크기 확인
            videoContainer.post {
                Log.d(TAG, "비디오 컨테이너 크기 변경 후: ${videoContainer.width}x${videoContainer.height}")
            }
            
            // Fragment 컨테이너 자체의 패딩도 제거
            videoContainer.setPadding(0, 0, 0, 0)
            
            // weight 제거하여 전체화면으로 설정
            when (layoutParams) {
                is LinearLayout.LayoutParams -> {
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.weight = 0f  // weight를 0으로 설정
                    videoContainer.layoutParams = layoutParams
                    Log.d(TAG, "LinearLayout.LayoutParams로 설정 완료 (weight=0)")
                }
                else -> {
                    val newParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        weight = 0f  // weight를 0으로 설정
                    }
                    videoContainer.layoutParams = newParams
                    Log.d(TAG, "새로운 LinearLayout.LayoutParams로 설정 완료 (weight=0)")
                }
            }
            
            // ScrollView도 전체화면으로 설정
            val scrollView = findViewById<ScrollView>(R.id.scroll_view)
            scrollView?.let { sv ->
                val scrollParams = sv.layoutParams
                scrollParams?.let { params ->
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    sv.layoutParams = params
                    Log.d(TAG, "ScrollView 크기를 전체화면으로 설정")
                }
            }
            
            // LinearLayout도 전체화면으로 설정 (wrap_content 제거)
            val linearLayout = scrollView?.getChildAt(0) as? LinearLayout
            linearLayout?.let { ll ->
                val linearParams = ll.layoutParams
                linearParams?.let { params ->
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    ll.layoutParams = params
                    Log.d(TAG, "LinearLayout 크기를 전체화면으로 설정")
                }
            }
            
            // 가로모드에서 추가 처리
            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                Log.d(TAG, "가로모드 추가 처리 시작")
                
                // 가로모드에서는 높이를 화면 높이로 강제 설정
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                Log.d(TAG, "화면 높이: ${screenHeight}")
                
                // ScrollView와 LinearLayout의 높이를 화면 높이로 강제 설정
                scrollView?.let { sv ->
                    val scrollParams = sv.layoutParams
                    scrollParams.height = screenHeight
                    sv.layoutParams = scrollParams
                    Log.d(TAG, "ScrollView 높이를 화면 높이로 강제 설정: ${screenHeight}")
                }
                
                linearLayout?.let { ll ->
                    val linearParams = ll.layoutParams
                    linearParams.height = screenHeight
                    ll.layoutParams = linearParams
                    Log.d(TAG, "LinearLayout 높이를 화면 높이로 강제 설정: ${screenHeight}")
                }
                
                // 비디오 컨테이너도 화면 높이로 설정
                val containerParams = videoContainer.layoutParams
                containerParams.height = screenHeight
                videoContainer.layoutParams = containerParams
                Log.d(TAG, "비디오 컨테이너 높이를 화면 높이로 강제 설정: ${screenHeight}")
            }
            
            // 강제로 레이아웃 다시 계산
            scrollView?.requestLayout()
            linearLayout?.requestLayout()
            videoContainer.requestLayout()
            Log.d(TAG, "레이아웃 강제 재계산 요청")
            
            // 모든 부모 뷰의 패딩과 마진 제거 (화면 회전과 동일한 효과)
            var parent = videoContainer.parent
            while (parent is ViewGroup) {
                parent.setPadding(0, 0, 0, 0)
                parent.clipToPadding = false
                parent.clipChildren = false
                Log.d(TAG, "부모 뷰 패딩 제거: ${parent.javaClass.simpleName}")
                parent = parent.parent
            }
            
            // Activity의 루트 뷰 패딩도 제거
            findViewById<View>(android.R.id.content)?.setPadding(0, 0, 0, 0)
            window.decorView.setPadding(0, 0, 0, 0)
            
            // 추가: 화면 회전과 동일한 레이아웃 효과 적용
            val rootView = findViewById<View>(android.R.id.content)
            rootView?.let { view ->
                if (view is ViewGroup) {
                    view.clipToPadding = false
                    view.clipChildren = false
                }
            }
            
            // Activity 크기 로그 출력 (즉시)
            val activityRoot = findViewById<View>(android.R.id.content)
            activityRoot?.let { root ->
                Log.d(TAG, "Activity 루트 크기 - 너비: ${root.width}, 높이: ${root.height}")
            }
            
            // 화면 크기 로그 출력 (즉시)
            val displayMetrics = resources.displayMetrics
            Log.d(TAG, "화면 크기 - 너비: ${displayMetrics.widthPixels}, 높이: ${displayMetrics.heightPixels}")
            
            // 비디오 컨테이너 크기 로그 출력 (즉시)
            val containerView = findViewById<View>(R.id.video_player_container)
            containerView?.let { container ->
                Log.d(TAG, "비디오 컨테이너 크기 - 너비: ${container.width}, 높이: ${container.height}")
            }
            
            // VideoPlayerFragment에 전체화면 모드 알림
            videoPlayerFragment?.let { fragment ->
                // Fragment의 비디오 비율 조정을 강제로 호출
                fragment.adjustVideoAspectRatioForFullscreen()
                
                // Fragment의 컨트롤 버튼들이 제대로 표시되도록 추가 조정
                fragment.view?.post {
                    Log.d(TAG, "Fragment view.post 콜백 실행")
                    fragment.adjustVideoAspectRatioForFullscreen()
                    
                    // Fragment의 레이아웃을 강제로 다시 그리기
                    fragment.view?.requestLayout()
                    fragment.view?.invalidate()
                    
                    // Fragment 크기 로그 출력
                    fragment.view?.let { fragmentView ->
                        Log.d(TAG, "Activity에서 Fragment 크기 - 너비: ${fragmentView.width}, 높이: ${fragmentView.height}")
                        Log.d(TAG, "Activity에서 Fragment 부모 크기 - 너비: ${(fragmentView.parent as? View)?.width}, 높이: ${(fragmentView.parent as? View)?.height}")
                    }
                    
                    // 한 번 더 post로 재확인
                    fragment.view?.post {
                        Log.d(TAG, "Fragment 최종 크기 확인: ${fragment.view?.width}x${fragment.view?.height}")
                        fragment.adjustVideoAspectRatioForFullscreen()
                    }
                }
            }
            
            Log.d(TAG, "Fragment 전체화면 확장 완료 (Fragment 컨트롤 버튼들 유지)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fragment 전체화면 확장 실패", e)
        }
    }
    

    
    /**
     * Fragment를 원래 크기로 복원
     */
    private fun restoreNormalLayout() {
        try {
            val videoContainer = findViewById<View>(R.id.video_player_container)
            
            // Activity의 UI 요소들을 다시 표시
            val elementsToShow = listOf(
                R.id.url_edit_text,
                R.id.connect_button,
                R.id.disconnect_button,
                R.id.play_button,
                R.id.pause_button,
                R.id.pan_seek_bar,
                R.id.tilt_seek_bar,
                R.id.zoom_seek_bar,
                R.id.status_text_view,
                R.id.version_text_view
            )
            
            elementsToShow.forEach { id ->
                findViewById<View>(id)?.visibility = View.VISIBLE
            }
            
            // 비디오 컨테이너를 원래 크기로 복원
            val layoutParams = videoContainer.layoutParams as? LinearLayout.LayoutParams
            layoutParams?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0  // weight를 사용하는 경우
                params.weight = 1.0f  // 원래 weight 값으로 복원
                videoContainer.layoutParams = params
            }
            
            // Fragment 컨테이너의 패딩도 복원
            videoContainer.setPadding(0, 0, 0, 0)
            
            // 부모 뷰들의 패딩 복원
            var parent = videoContainer.parent
            while (parent is ViewGroup) {
                parent.setPadding(0, 0, 0, 0)
                parent.clipToPadding = true
                parent.clipChildren = true
                parent = parent.parent
            }
            
            // Activity의 루트 뷰 패딩 복원
            findViewById<View>(android.R.id.content)?.setPadding(0, 0, 0, 0)
            window.decorView.setPadding(0, 0, 0, 0)
            
            Log.d(TAG, "Fragment 원래 크기 복원 완료 (테두리 복원)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fragment 원래 크기 복원 실패", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "액티비티 종료 - 리소스 해제 완료")
    }
} 