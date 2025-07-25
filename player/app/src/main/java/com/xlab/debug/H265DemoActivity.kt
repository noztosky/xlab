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
    
    // UI 컴포넌트 - Player 1
    private lateinit var urlEditText1: EditText
    private lateinit var connectButton1: Button
    private lateinit var disconnectButton1: Button
    private lateinit var playButton1: Button
    private lateinit var pauseButton1: Button
    
    // UI 컴포넌트 - Player 2
    private lateinit var urlEditText2: EditText
    private lateinit var connectButton2: Button
    private lateinit var disconnectButton2: Button
    private lateinit var playButton2: Button
    private lateinit var pauseButton2: Button
    
    // 비디오 플레이어 Fragment
    private var videoPlayerFragment1: VideoPlayerFragment? = null
    private var videoPlayerFragment2: VideoPlayerFragment? = null
    
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
                videoPlayerFragment1?.let { fragment ->
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
        // Player 1 UI 초기화
        urlEditText1 = findViewById(R.id.url_edit_text1)
        connectButton1 = findViewById(R.id.connect_button1)
        disconnectButton1 = findViewById(R.id.disconnect_button1)
        playButton1 = findViewById(R.id.play_button1)
        pauseButton1 = findViewById(R.id.pause_button1)
        
        // Player 2 UI 초기화
        urlEditText2 = findViewById(R.id.url_edit_text2)
        connectButton2 = findViewById(R.id.connect_button2)
        disconnectButton2 = findViewById(R.id.disconnect_button2)
        playButton2 = findViewById(R.id.play_button2)
        pauseButton2 = findViewById(R.id.pause_button2)
        
        // 마지막 사용한 URL 복원 또는 기본 URL 설정
        val lastUrl1 = prefs.getString(PREF_LAST_URL + "1", TEST_RTSP_URLS[0])
        val lastUrl2 = prefs.getString(PREF_LAST_URL + "2", TEST_RTSP_URLS[1])
        urlEditText1.setText(lastUrl1)
        urlEditText2.setText(lastUrl2)
        
        // 초기 상태 설정 - Player 1
        disconnectButton1.isEnabled = false
        playButton1.isEnabled = false
        pauseButton1.isEnabled = false
        
        // 초기 상태 설정 - Player 2
        disconnectButton2.isEnabled = false
        playButton2.isEnabled = false
        pauseButton2.isEnabled = false
    }
    
    private fun setupListeners() {
        // Player 1 리스너
        connectButton1.setOnClickListener { connectToStream(1) }
        disconnectButton1.setOnClickListener { disconnectFromStream(1) }
        playButton1.setOnClickListener { playStream(1) }
        pauseButton1.setOnClickListener { pauseStream(1) }
        
        // Player 2 리스너
        connectButton2.setOnClickListener { connectToStream(2) }
        disconnectButton2.setOnClickListener { disconnectFromStream(2) }
        playButton2.setOnClickListener { playStream(2) }
        pauseButton2.setOnClickListener { pauseStream(2) }
    }
    
    private fun setupVideoPlayerFragment() {
        Log.d(TAG, "VideoPlayerFragment 생성 시작")
        
        // VideoPlayerFragment1 추가
        videoPlayerFragment1 = VideoPlayerFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.video_player_container1, videoPlayerFragment1!!)
            .commitNow()
        
        // VideoPlayerFragment2 추가
        videoPlayerFragment2 = VideoPlayerFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.video_player_container2, videoPlayerFragment2!!)
            .commitNow()
        
        Log.d(TAG, "Fragment 트랜잭션 완료")
        
        // Fragment가 완전히 생성된 후 콜백 설정을 위해 post 사용
        videoPlayerFragment1?.view?.post {
            Log.d(TAG, "Fragment1 완전히 생성됨, 콜백 설정 시작")
            setupCallbacks()
        }
        
        videoPlayerFragment2?.view?.post {
            Log.d(TAG, "Fragment2 완전히 생성됨, 콜백 설정 시작")
            setupCallbacks()
        }
        
        // 추가: 즉시 콜백 설정도 시도
        setupCallbacks()
    }
    
    private fun setupCallbacks() {
        Log.d(TAG, "!!! setupCallbacks() 함수 실행됨 !!!")
        
        // Player 1 상태 콜백 설정
        videoPlayerFragment1?.setPlayerStateCallback(object : VideoPlayerFragment.PlayerStateCallback {
            override fun onPlayerReady() {
                runOnUiThread {
                    Log.d(TAG, "Player 1 준비 완료")
                }
            }
            
            override fun onPlayerConnected() {
                runOnUiThread {
                    connectButton1.isEnabled = false
                    disconnectButton1.isEnabled = true
                    playButton1.isEnabled = true
                    pauseButton1.isEnabled = false
                    Log.d(TAG, "Player 1 연결됨")
                }
            }
            
            override fun onPlayerDisconnected() {
                runOnUiThread {
                    connectButton1.isEnabled = true
                    disconnectButton1.isEnabled = false
                    playButton1.isEnabled = false
                    pauseButton1.isEnabled = false
                    Log.d(TAG, "Player 1 연결 해제됨")
                }
            }
            
            override fun onPlayerPlaying() {
                runOnUiThread {
                    playButton1.isEnabled = false
                    pauseButton1.isEnabled = true
                    Log.d(TAG, "Player 1 재생 중")
                }
            }
            
            override fun onPlayerPaused() {
                runOnUiThread {
                    playButton1.isEnabled = true
                    pauseButton1.isEnabled = false
                    Log.d(TAG, "Player 1 일시정지됨")
                }
            }
            
            override fun onPlayerError(error: String) {
                runOnUiThread {
                    connectButton1.isEnabled = true
                    disconnectButton1.isEnabled = false
                    playButton1.isEnabled = false
                    pauseButton1.isEnabled = false
                    Log.d(TAG, "Player 1 오류 발생: $error")
                }
            }
        })
        
        // Player 2 상태 콜백 설정
        videoPlayerFragment2?.setPlayerStateCallback(object : VideoPlayerFragment.PlayerStateCallback {
            override fun onPlayerReady() {
                runOnUiThread {
                    Log.d(TAG, "Player 2 준비 완료")
                }
            }
            
            override fun onPlayerConnected() {
                runOnUiThread {
                    connectButton2.isEnabled = false
                    disconnectButton2.isEnabled = true
                    playButton2.isEnabled = true
                    pauseButton2.isEnabled = false
                    Log.d(TAG, "Player 2 연결됨")
                }
            }
            
            override fun onPlayerDisconnected() {
                runOnUiThread {
                    connectButton2.isEnabled = true
                    disconnectButton2.isEnabled = false
                    playButton2.isEnabled = false
                    pauseButton2.isEnabled = false
                    Log.d(TAG, "Player 2 연결 해제됨")
                }
            }
            
            override fun onPlayerPlaying() {
                runOnUiThread {
                    playButton2.isEnabled = false
                    pauseButton2.isEnabled = true
                    Log.d(TAG, "Player 2 재생 중")
                }
            }
            
            override fun onPlayerPaused() {
                runOnUiThread {
                    playButton2.isEnabled = true
                    pauseButton2.isEnabled = false
                    Log.d(TAG, "Player 2 일시정지됨")
                }
            }
            
            override fun onPlayerError(error: String) {
                runOnUiThread {
                    connectButton2.isEnabled = true
                    disconnectButton2.isEnabled = false
                    playButton2.isEnabled = false
                    pauseButton2.isEnabled = false
                    Log.d(TAG, "Player 2 오류 발생: $error")
                }
            }
        })
        
        // Player 1 전체화면 콜백 설정
        Log.d(TAG, "Player 1 전체화면 콜백 설정 시작")
        val callback1 = object : VideoPlayerFragment.FullscreenCallback {
            override fun onToggleFullscreen() {
                runOnUiThread {
                    handleFullscreenToggle(1)
                }
            }
        }
        videoPlayerFragment1?.setFullscreenCallback(callback1)
        
        // Player 2 전체화면 콜백 설정
        val callback2 = object : VideoPlayerFragment.FullscreenCallback {
            override fun onToggleFullscreen() {
                runOnUiThread {
                    handleFullscreenToggle(2)
                }
            }
        }
        videoPlayerFragment2?.setFullscreenCallback(callback2)
    }
    
    private fun connectToStream(playerNum: Int) {
        val url = if (playerNum == 1) urlEditText1.text.toString().trim() else urlEditText2.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        // URL을 SharedPreferences에 저장
        prefs.edit().putString(PREF_LAST_URL + playerNum, url).apply()
        
        // VideoPlayerFragment에 URL 전달하여 재생
        val fragment = if (playerNum == 1) videoPlayerFragment1 else videoPlayerFragment2
        fragment?.let {
            it.playRtspStream(url)
            Log.d(TAG, "Player $playerNum 연결 요청: $url")
        }
    }
    
    private fun disconnectFromStream(playerNum: Int) {
        val fragment = if (playerNum == 1) videoPlayerFragment1 else videoPlayerFragment2
        fragment?.let {
            it.disconnectStream()
            Log.d(TAG, "Player $playerNum 연결 해제 요청")
        }
    }
    
    private fun playStream(playerNum: Int) {
        val fragment = if (playerNum == 1) videoPlayerFragment1 else videoPlayerFragment2
        fragment?.let {
            it.resumeStream()
            Log.d(TAG, "Player $playerNum 재생 요청")
        }
    }
    
    private fun pauseStream(playerNum: Int) {
        val fragment = if (playerNum == 1) videoPlayerFragment1 else videoPlayerFragment2
        fragment?.let {
            it.pauseStream()
            Log.d(TAG, "Player $playerNum 일시정지 요청")
        }
    }
    
    private fun handleFullscreenToggle(playerNum: Int) {
        Log.d(TAG, "Player $playerNum 전체화면 토글")
        toggleFullscreenMode(playerNum)
    }
    
    private fun toggleFullscreenMode(playerNum: Int) {
        try {
            Log.d(TAG, "Player $playerNum 전체화면 모드 토글")

            if (isFullscreenMode) {
                // 전체화면 모드 해제
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                supportActionBar?.show()
                restoreNormalLayout()
                isFullscreenMode = false
            } else {
                // 전체화면 모드 설정
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
                supportActionBar?.hide()
                expandToFullscreen()
                isFullscreenMode = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "전체화면 모드 토글 실패", e)
        }
    }
    
    private fun expandToFullscreen() {
        try {
            Log.d(TAG, "expandToFullscreen() 함수 시작")
            val videoContainer = findViewById<View>(R.id.video_player_container1)
            
            // 현재 화면 방향 확인
            val orientation = resources.configuration.orientation
            Log.d(TAG, "현재 화면 방향: ${if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "가로모드" else "세로모드"}")
            
            // Activity의 UI 요소들만 숨기기 (Fragment는 유지)
            val elementsToHide = listOf(
                R.id.url_edit_text1,
                R.id.connect_button1,
                R.id.disconnect_button1,
                R.id.play_button1,
                R.id.pause_button1,
                R.id.url_edit_text2,
                R.id.connect_button2,
                R.id.disconnect_button2,
                R.id.play_button2,
                R.id.pause_button2
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
            
            // VideoPlayerFragment에 전체화면 모드 알림
            videoPlayerFragment1?.let { fragment ->
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
            val videoContainer1 = findViewById<View>(R.id.video_player_container1)
            val videoContainer2 = findViewById<View>(R.id.video_player_container2)
            
            // Activity의 UI 요소들을 다시 표시
            val elementsToShow = listOf(
                R.id.url_edit_text1,
                R.id.connect_button1,
                R.id.disconnect_button1,
                R.id.play_button1,
                R.id.pause_button1,
                R.id.url_edit_text2,
                R.id.connect_button2,
                R.id.disconnect_button2,
                R.id.play_button2,
                R.id.pause_button2
            )
            
            elementsToShow.forEach { id ->
                findViewById<View>(id)?.visibility = View.VISIBLE
            }
            
            // 비디오 컨테이너를 원래 크기로 복원
            val layoutParams1 = videoContainer1.layoutParams as? LinearLayout.LayoutParams
            layoutParams1?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0  // weight를 사용하는 경우
                params.weight = 1.0f  // 원래 weight 값으로 복원
                videoContainer1.layoutParams = params
            }
            
            val layoutParams2 = videoContainer2.layoutParams as? LinearLayout.LayoutParams
            layoutParams2?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0  // weight를 사용하는 경우
                params.weight = 1.0f  // 원래 weight 값으로 복원
                videoContainer2.layoutParams = params
            }
            
            // Fragment 컨테이너의 패딩도 복원
            videoContainer1.setPadding(0, 0, 0, 0)
            videoContainer2.setPadding(0, 0, 0, 0)
            
            Log.d(TAG, "Fragment 원래 크기 복원 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fragment 원래 크기 복원 실패", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "액티비티 종료 - 리소스 해제 완료")
    }
}