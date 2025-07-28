package com.xlab.Player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
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
        
        // Player 1 전체화면 콜백 설정 제거 (Fragment 내부에서 처리)
        
        // Player 2 전체화면 콜백 설정 제거 (Fragment 내부에서 처리)
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
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "액티비티 종료 - 리소스 해제 완료")
    }
}