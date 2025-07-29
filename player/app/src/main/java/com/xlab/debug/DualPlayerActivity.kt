package com.xlab.debug

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xlab.Player.XLABPlayer
import com.xlab.Player.R

/**
 * 듀얼 XLABPlayer 테스트 액티비티
 * 2개의 독립된 플레이어로 동시 스트리밍 테스트
 */
class DualPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "DualPlayerActivity"
    }
    
    // 첫 번째 플레이어 UI 컴포넌트들
    private lateinit var videoContainer1: FrameLayout
    private lateinit var connectButton1: Button
    private lateinit var playButton1: Button
    private lateinit var pauseButton1: Button
    private lateinit var stopButton1: Button
    private lateinit var disconnectButton1: Button
    
    // 두 번째 플레이어 UI 컴포넌트들
    private lateinit var videoContainer2: FrameLayout
    private lateinit var connectButton2: Button
    private lateinit var playButton2: Button
    private lateinit var pauseButton2: Button
    private lateinit var stopButton2: Button
    private lateinit var disconnectButton2: Button
    
    // XLABPlayer 인스턴스들
    private var xlabPlayer1: XLABPlayer? = null
    private var xlabPlayer2: XLABPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dual_player)
        
        initViews()
        setupPlayers()
        setupButtons()
    }
    
    /**
     * UI 컴포넌트 초기화
     */
    private fun initViews() {
        // 첫 번째 플레이어 UI
        videoContainer1 = findViewById(R.id.video_container_1)
        connectButton1 = findViewById(R.id.connect_button_1)
        playButton1 = findViewById(R.id.play_button_1)
        pauseButton1 = findViewById(R.id.pause_button_1)
        stopButton1 = findViewById(R.id.stop_button_1)
        disconnectButton1 = findViewById(R.id.disconnect_button_1)
        
        // 두 번째 플레이어 UI
        videoContainer2 = findViewById(R.id.video_container_2)
        connectButton2 = findViewById(R.id.connect_button_2)
        playButton2 = findViewById(R.id.play_button_2)
        pauseButton2 = findViewById(R.id.pause_button_2)
        stopButton2 = findViewById(R.id.stop_button_2)
        disconnectButton2 = findViewById(R.id.disconnect_button_2)
    }
    
    /**
     * XLABPlayer 설정
     */
    private fun setupPlayers() {
        setupPlayer1()
        setupPlayer2()
    }
    
    /**
     * 첫 번째 플레이어 설정
     */
    private fun setupPlayer1() {
        try {
            xlabPlayer1 = XLABPlayer(this)
            
            xlabPlayer1?.setCallback(object : XLABPlayer.PlayerCallback {
                override fun onPlayerReady() {
                    runOnUiThread { updateButtonStates1() }
                }
                
                override fun onPlayerConnected() {
                    runOnUiThread { updateButtonStates1() }
                }
                
                override fun onPlayerDisconnected() {
                    runOnUiThread { updateButtonStates1() }
                }
                
                override fun onPlayerPlaying() {
                    runOnUiThread { updateButtonStates1() }
                }
                
                override fun onPlayerPaused() {
                    runOnUiThread { updateButtonStates1() }
                }
                
                override fun onPlayerError(error: String) {
                    runOnUiThread {
                        updateButtonStates1()
                        Toast.makeText(this@DualPlayerActivity, "플레이어1 오류: $error", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    // 비디오 크기 변경 처리
                }
                
                override fun onPtzCommand(command: String, success: Boolean) {
                    // PTZ 명령 처리
                }
            })
            
            val success = xlabPlayer1?.initialize(videoContainer1) ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 초기화 실패", Toast.LENGTH_LONG).show()
            } else {
                xlabPlayer1?.addFullscreenButton()
                xlabPlayer1?.addRecordButton()
                xlabPlayer1?.addCaptureButton()
                xlabPlayer1?.setCameraServer("c12", "http://192.168.144.108:5000", 1)
                xlabPlayer1?.showPtzControl()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 설정 실패", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 두 번째 플레이어 초기화 및 설정
     */
    private fun setupPlayer2() {
        try {
            xlabPlayer2 = XLABPlayer(this)
            
            // 두 번째 플레이어 콜백 설정
            xlabPlayer2?.setCallback(object : XLABPlayer.PlayerCallback {
                override fun onPlayerReady() {
                    runOnUiThread { updateButtonStates2() }
                }
                
                override fun onPlayerConnected() {
                    runOnUiThread { updateButtonStates2() }
                }
                
                override fun onPlayerDisconnected() {
                    runOnUiThread { updateButtonStates2() }
                }
                
                override fun onPlayerPlaying() {
                    runOnUiThread { updateButtonStates2() }
                }
                
                override fun onPlayerPaused() {
                    runOnUiThread { updateButtonStates2() }
                }
                
                override fun onPlayerError(error: String) {
                    runOnUiThread {
                        updateButtonStates2()
                        Toast.makeText(this@DualPlayerActivity, "플레이어2 오류: $error", Toast.LENGTH_LONG).show()
                    }
                }
                
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    // 비디오 크기 변경 처리
                }
                
                override fun onPtzCommand(command: String, success: Boolean) {
                    // PTZ 명령 처리
                }
            })
            
            // 두 번째 플레이어 초기화
            val success = xlabPlayer2?.initialize(videoContainer2) ?: false
            if (success) {
                xlabPlayer2?.addFullscreenButton()
                xlabPlayer2?.addRecordButton()
                xlabPlayer2?.addCaptureButton()
                xlabPlayer2?.showPtzControl()
                
                // 카메라 서버 설정
                xlabPlayer2?.setCameraServer("c12", "http://192.168.144.108:5000", 2)
            } else {
                Toast.makeText(this, "플레이어2 초기화 실패", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 설정 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 버튼 이벤트 설정
     */
    private fun setupButtons() {
        // 첫 번째 플레이어 버튼들
        connectButton1.setOnClickListener { connectPlayer1() }
        playButton1.setOnClickListener { playPlayer1() }
        pauseButton1.setOnClickListener { pausePlayer1() }
        stopButton1.setOnClickListener { stopPlayer1() }
        disconnectButton1.setOnClickListener { disconnectPlayer1() }
        
        // 두 번째 플레이어 버튼들
        connectButton2.setOnClickListener { connectPlayer2() }
        playButton2.setOnClickListener { playPlayer2() }
        pauseButton2.setOnClickListener { pausePlayer2() }
        stopButton2.setOnClickListener { stopPlayer2() }
        disconnectButton2.setOnClickListener { disconnectPlayer2() }
        
        // 초기 버튼 상태 설정
        updateButtonStates1()
        updateButtonStates2()
    }
    
    // 첫 번째 플레이어 제어 메서드들
    private fun connectPlayer1() {
        try {
            // 첫 번째 플레이어는 기본 주소 사용
            val success = xlabPlayer1?.connectAndPlay("rtsp://192.168.144.108:554/stream=1") ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 연결 실패", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playPlayer1() {
        try {
            val success = xlabPlayer1?.play() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 재생 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 재생 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pausePlayer1() {
        try {
            val success = xlabPlayer1?.pause() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 일시정지 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 일시정지 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopPlayer1() {
        try {
            val success = xlabPlayer1?.stop() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 정지 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 정지 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun disconnectPlayer1() {
        try {
            val success = xlabPlayer1?.disconnect() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어1 연결 해제 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어1 연결 해제 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 두 번째 플레이어 제어 메서드들
    private fun connectPlayer2() {
        try {
            // 두 번째 플레이어는 555 포트 사용
            val success = xlabPlayer2?.connectAndPlay("rtsp://192.168.144.108:555/stream=2") ?: false
            if (!success) {
                Toast.makeText(this, "플레이어2 연결 실패", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playPlayer2() {
        try {
            val success = xlabPlayer2?.play() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어2 재생 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 재생 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pausePlayer2() {
        try {
            val success = xlabPlayer2?.pause() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어2 일시정지 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 일시정지 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopPlayer2() {
        try {
            val success = xlabPlayer2?.stop() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어2 정지 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 정지 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun disconnectPlayer2() {
        try {
            val success = xlabPlayer2?.disconnect() ?: false
            if (!success) {
                Toast.makeText(this, "플레이어2 연결 해제 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "플레이어2 연결 해제 실패", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 첫 번째 플레이어 버튼 상태 업데이트
     */
    private fun updateButtonStates1() {
        xlabPlayer1?.let { player ->
            val isReady = player.isPlayerReady()
            val isConnected = player.isPlayerConnected()
            val isPlaying = player.isPlayerPlaying()
            
            connectButton1.isEnabled = isReady && !isConnected
            playButton1.isEnabled = isConnected && !isPlaying
            pauseButton1.isEnabled = isConnected && isPlaying
            stopButton1.isEnabled = isConnected
            disconnectButton1.isEnabled = isConnected
        }
    }
    
    /**
     * 두 번째 플레이어 버튼 상태 업데이트
     */
    private fun updateButtonStates2() {
        xlabPlayer2?.let { player ->
            val isReady = player.isPlayerReady()
            val isConnected = player.isPlayerConnected()
            val isPlaying = player.isPlayerPlaying()
            
            connectButton2.isEnabled = isReady && !isConnected
            playButton2.isEnabled = isConnected && !isPlaying
            pauseButton2.isEnabled = isConnected && isPlaying
            stopButton2.isEnabled = isConnected
            disconnectButton2.isEnabled = isConnected
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 플레이어들 해제
        xlabPlayer1?.release()
        xlabPlayer1 = null
        
        xlabPlayer2?.release()
        xlabPlayer2 = null
    }
} 