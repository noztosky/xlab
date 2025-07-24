package com.xlab.Player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_h265_demo)
        
        // SharedPreferences 초기화
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        setupListeners()
        setupVideoPlayerFragment()
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
        // VideoPlayerFragment 추가
        videoPlayerFragment = VideoPlayerFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.video_player_container, videoPlayerFragment!!)
            .commit()
        
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
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "액티비티 종료 - 리소스 해제 완료")
    }
} 