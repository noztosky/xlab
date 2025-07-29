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
        connectButton = findViewById(R.id.connect_button)
        playButton = findViewById(R.id.play_button)
        pauseButton = findViewById(R.id.pause_button)
        stopButton = findViewById(R.id.stop_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        
        // 비디오 컨테이너를 16:9 비율로 설정
        setupVideoContainerAspectRatio()
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
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerConnected() {
                    runOnUiThread {
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerDisconnected() {
                    runOnUiThread {
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerPlaying() {
                    runOnUiThread {
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerPaused() {
                    runOnUiThread {
                        updateButtonStates()
                    }
                }
                
                override fun onPlayerError(error: String) {
                    runOnUiThread {
                        updateButtonStates()
                        
                        Toast.makeText(this@TestPlayerActivity, "오류: $error", Toast.LENGTH_LONG).show()
                    }
                }
                
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    // 비디오 크기 변경 처리 (필요시 구현)
                }
                
                override fun onPtzCommand(command: String, success: Boolean) {
                    // PTZ 명령 처리 (필요시 Toast 표시)
                }
            })
            
            // 플레이어 초기화
            val success = xlabPlayer?.initialize(videoContainer) ?: false
            if (!success) {
                Toast.makeText(this, "플레이어 초기화 실패", Toast.LENGTH_LONG).show()
            } else {
                // 전체화면 버튼 추가 (비디오 위에 오버레이)
                xlabPlayer?.addFullscreenButton()
                
                // 녹화 및 사진 촬영 버튼 추가
                xlabPlayer?.addRecordButton()
                xlabPlayer?.addCaptureButton()
                
                // 카메라 서버 설정 (C12 카메라)
                xlabPlayer?.setCameraServer("c12", "http://192.168.144.108:5000", 1)
                
                // PTZ 컨트롤을 기본적으로 표시
                xlabPlayer?.showPtzControl()                
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어 설정 실패", Toast.LENGTH_LONG).show()
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
            val success = xlabPlayer?.connectAndPlay() ?: false
            
            if (!success) {
                Toast.makeText(this, "RTSP 연결 실패", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "연결 실패", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "재생 실패", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "일시정지 실패", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "정지 실패", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "연결 해제 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 버튼 상태 업데이트
     */
    private fun updateButtonStates() {
        xlabPlayer?.let { player ->
            val isReady = player.isPlayerReady()
            val isConnected = player.isPlayerConnected()
            val isPlaying = player.isPlayerPlaying()
            
            connectButton.isEnabled = isReady && !isConnected
            playButton.isEnabled = isConnected && !isPlaying
            pauseButton.isEnabled = isConnected && isPlaying
            stopButton.isEnabled = isConnected
            disconnectButton.isEnabled = isConnected
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 플레이어 해제
        xlabPlayer?.release()
        xlabPlayer = null       

    }
} 