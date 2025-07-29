package com.xlab.debug

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xlab.Player.XLABPlayer
import com.xlab.Player.R

/**
 * XLABPlayer 테스트 액티비티
 * Debug용 테스트 앱
 */
class TestPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TestPlayerActivity"
    }
    
    // UI 컴포넌트들
    private lateinit var videoContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var disconnectButton: Button
    
    // XLABPlayer 인스턴스
    private var xlabPlayer: XLABPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_player)
        
        initViews()
        setupPlayer()
        setupButtons()
    }
    
    /**
     * UI 컴포넌트 초기화
     */
    private fun initViews() {
        videoContainer = findViewById(R.id.video_container)
        statusText = findViewById(R.id.status_text)
        connectButton = findViewById(R.id.connect_button)
        playButton = findViewById(R.id.play_button)
        pauseButton = findViewById(R.id.pause_button)
        stopButton = findViewById(R.id.stop_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        
        // 비디오 컨테이너를 16:9 비율로 설정
        setupVideoContainerAspectRatio()
        
        updateStatus("플레이어 준비 중...")
    }
    
    /**
     * 비디오 컨테이너를 16:9 비율로 설정
     */
    private fun setupVideoContainerAspectRatio() {
        videoContainer.post {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels - (32.dpToPx()) // 좌우 패딩 16dp씩 제외
            val videoHeight = (screenWidth * 9) / 16 // 16:9 비율로 높이 계산
            
            val layoutParams = videoContainer.layoutParams
            layoutParams.height = videoHeight
            videoContainer.layoutParams = layoutParams
            
            Log.d(TAG, "비디오 컨테이너 크기 설정: ${screenWidth}x${videoHeight}")
        }
    }
    
    /**
     * dp를 px로 변환
     */
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
    
    /**
     * XLABPlayer 설정
     */
    private fun setupPlayer() {
        try {
            xlabPlayer = XLABPlayer(this)
            
            // 콜백 설정
            xlabPlayer?.setCallback(object : XLABPlayer.PlayerCallback {
                override fun onPlayerReady() {
                    runOnUiThread {
                        updateStatus("플레이어 준비 완료")
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerConnected() {
                    runOnUiThread {
                        updateStatus("RTSP 연결됨")
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerDisconnected() {
                    runOnUiThread {
                        updateStatus("연결 해제됨")
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerPlaying() {
                    runOnUiThread {
                        updateStatus("재생 중")
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerPaused() {
                    runOnUiThread {
                        updateStatus("일시정지됨")
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerError(error: String) {
                    runOnUiThread {
                        updateStatus("오류: $error")
                        updateButtonStates()
                        Toast.makeText(this@TestPlayerActivity, "오류: $error", Toast.LENGTH_LONG).show()
                    }
                }
                
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    runOnUiThread {
                        Log.d(TAG, "비디오 크기 변경: ${width}x${height}")
                    }
                }
            })
            
            // 플레이어 초기화
            val success = xlabPlayer?.initialize(videoContainer) ?: false
            if (!success) {
                updateStatus("플레이어 초기화 실패")
                Toast.makeText(this, "플레이어 초기화 실패", Toast.LENGTH_LONG).show()
            } else {
                // 전체화면 버튼 추가 (비디오 위에 오버레이)
                xlabPlayer?.addFullscreenButton()
                updateStatus("플레이어 준비 완료")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "플레이어 설정 실패", e)
            updateStatus("플레이어 설정 실패: ${e.message}")
            Toast.makeText(this, "플레이어 설정 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 버튼 이벤트 설정
     */
    private fun setupButtons() {
        connectButton.setOnClickListener {
            connectToStream()
        }
        
        playButton.setOnClickListener {
            playStream()
        }
        
        pauseButton.setOnClickListener {
            pauseStream()
        }
        
        stopButton.setOnClickListener {
            stopStream()
        }
        
        disconnectButton.setOnClickListener {
            disconnectStream()
        }
        
        // 초기 버튼 상태 설정
        updateButtonStates()
    }
    
    /**
     * 스트림 연결
     */
    private fun connectToStream() {
        try {
            updateStatus("RTSP 연결 시도 중...")
            val success = xlabPlayer?.connectAndPlay() ?: false
            
            if (!success) {
                updateStatus("연결 실패")
                Toast.makeText(this, "RTSP 연결 실패", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 연결 실패", e)
            updateStatus("연결 실패: ${e.message}")
            Toast.makeText(this, "연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 스트림 재생
     */
    private fun playStream() {
        try {
            val success = xlabPlayer?.play() ?: false
            
            if (!success) {
                Toast.makeText(this, "재생할 수 없는 상태입니다", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 재생 실패", e)
            Toast.makeText(this, "재생 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 스트림 일시정지
     */
    private fun pauseStream() {
        try {
            val success = xlabPlayer?.pause() ?: false
            
            if (!success) {
                Toast.makeText(this, "일시정지할 수 없는 상태입니다", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 일시정지 실패", e)
            Toast.makeText(this, "일시정지 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 스트림 정지
     */
    private fun stopStream() {
        try {
            val success = xlabPlayer?.stop() ?: false
            
            if (!success) {
                Toast.makeText(this, "정지할 수 없는 상태입니다", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 정지 실패", e)
            Toast.makeText(this, "정지 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 스트림 연결 해제
     */
    private fun disconnectStream() {
        try {
            val success = xlabPlayer?.disconnect() ?: false
            
            if (!success) {
                Toast.makeText(this, "연결 해제할 수 없는 상태입니다", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "스트림 연결 해제 실패", e)
            Toast.makeText(this, "연결 해제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 상태 텍스트 업데이트
     */
    private fun updateStatus(status: String) {
        statusText.text = "상태: $status"
        Log.d(TAG, "상태 업데이트: $status")
    }
    
    /**
     * 버튼 상태 업데이트
     */
    private fun updateButtonStates() {
        val isReady = xlabPlayer?.isPlayerReady() ?: false
        val isConnected = xlabPlayer?.isPlayerConnected() ?: false
        val isPlaying = xlabPlayer?.isPlayerPlaying() ?: false
        
        connectButton.isEnabled = isReady && !isConnected
        playButton.isEnabled = isConnected && !isPlaying
        pauseButton.isEnabled = isConnected && isPlaying
        stopButton.isEnabled = isConnected
        disconnectButton.isEnabled = isConnected
        
        Log.d(TAG, "버튼 상태 업데이트 - 준비: $isReady, 연결: $isConnected, 재생: $isPlaying")
    }
    


    override fun onDestroy() {
        super.onDestroy()
        
        // 플레이어 해제
        xlabPlayer?.release()
        xlabPlayer = null
        
        Log.d(TAG, "TestPlayerActivity 종료")
    }
} 