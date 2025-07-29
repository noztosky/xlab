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
        xlabPlayer1 = createPlayer("플레이어1", ::updateButtonStates1).also { player ->
            initializePlayer(player, videoContainer1, "플레이어1", 1)
        }
    }
    
    /**
     * 두 번째 플레이어 설정
     */
    private fun setupPlayer2() {
        xlabPlayer2 = createPlayer("플레이어2", ::updateButtonStates2).also { player ->
            initializePlayer(player, videoContainer2, "플레이어2", 2)
        }
    }
    
    /**
     * 공통 플레이어 생성
     */
    private fun createPlayer(playerName: String, updateStates: () -> Unit): XLABPlayer? {
        return try {
            XLABPlayer(this).apply {
                setCallback(object : XLABPlayer.PlayerCallback {
                    override fun onPlayerReady() = runOnUiThread { updateStates() }
                    override fun onPlayerConnected() = runOnUiThread { updateStates() }
                    override fun onPlayerDisconnected() = runOnUiThread { updateStates() }
                    override fun onPlayerPlaying() = runOnUiThread { updateStates() }
                    override fun onPlayerPaused() = runOnUiThread { updateStates() }
                    override fun onPlayerError(error: String) = runOnUiThread {
                        updateStates()
                        Toast.makeText(this@DualPlayerActivity, "$playerName 오류: $error", Toast.LENGTH_SHORT).show()
                    }
                    override fun onVideoSizeChanged(width: Int, height: Int) = Unit
                    override fun onPtzCommand(command: String, success: Boolean) = Unit
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "$playerName 설정 실패", Toast.LENGTH_LONG).show()
            null
        }
    }
    
    /**
     * 공통 플레이어 초기화
     */
    private fun initializePlayer(player: XLABPlayer?, container: FrameLayout, playerName: String, cameraId: Int) {
        val success = player?.initialize(container) ?: false
        if (!success) {
            Toast.makeText(this, "$playerName 초기화 실패", Toast.LENGTH_LONG).show()
        } else {
            player?.apply {
                addFullscreenButton()
                addRecordButton()
                addCaptureButton()
                setCameraServer("c12", "http://192.168.144.108:5000", cameraId)
                showPtzControl()
            }
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
    
    // 공통 플레이어 제어 헬퍼 메서드
    private fun executePlayerAction(
        player: XLABPlayer?,
        playerName: String,
        action: () -> Boolean?,
        actionName: String
    ) {
        try {
            val success = action() ?: false
            if (!success) {
                Toast.makeText(this, "$playerName $actionName 불가", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "$playerName $actionName 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 첫 번째 플레이어 제어 메서드들
    private fun connectPlayer1() = executePlayerAction(xlabPlayer1, "플레이어1", {
        xlabPlayer1?.connectAndPlay("rtsp://192.168.144.108:554/stream=1")
    }, "연결")
    
    private fun playPlayer1() = executePlayerAction(xlabPlayer1, "플레이어1", {
        xlabPlayer1?.play()
    }, "재생")
    
    private fun pausePlayer1() = executePlayerAction(xlabPlayer1, "플레이어1", {
        xlabPlayer1?.pause()
    }, "일시정지")
    
    private fun stopPlayer1() = executePlayerAction(xlabPlayer1, "플레이어1", {
        xlabPlayer1?.stop()
    }, "정지")
    
    private fun disconnectPlayer1() = executePlayerAction(xlabPlayer1, "플레이어1", {
        xlabPlayer1?.disconnect()
    }, "연결 해제")
    
    // 두 번째 플레이어 제어 메서드들
    private fun connectPlayer2() = executePlayerAction(xlabPlayer2, "플레이어2", {
        xlabPlayer2?.connectAndPlay("rtsp://192.168.144.108:555/stream=2")
    }, "연결")
    
    private fun playPlayer2() = executePlayerAction(xlabPlayer2, "플레이어2", {
        xlabPlayer2?.play()
    }, "재생")
    
    private fun pausePlayer2() = executePlayerAction(xlabPlayer2, "플레이어2", {
        xlabPlayer2?.pause()
    }, "일시정지")
    
    private fun stopPlayer2() = executePlayerAction(xlabPlayer2, "플레이어2", {
        xlabPlayer2?.stop()
    }, "정지")
    
    private fun disconnectPlayer2() = executePlayerAction(xlabPlayer2, "플레이어2", {
        xlabPlayer2?.disconnect()
    }, "연결 해제")
    
    /**
     * 공통 버튼 상태 업데이트 헬퍼 메서드
     */
    private fun updatePlayerButtonStates(
        player: XLABPlayer?,
        connectButton: Button,
        playButton: Button,
        pauseButton: Button,
        stopButton: Button,
        disconnectButton: Button
    ) {
        player?.let { p ->
            val isReady = p.isPlayerReady()
            val isConnected = p.isPlayerConnected()
            val isPlaying = p.isPlayerPlaying()
            
            connectButton.isEnabled = isReady && !isConnected
            playButton.isEnabled = isConnected && !isPlaying
            pauseButton.isEnabled = isConnected && isPlaying
            stopButton.isEnabled = isConnected
            disconnectButton.isEnabled = isConnected
        }
    }
    
    /**
     * 첫 번째 플레이어 버튼 상태 업데이트
     */
    private fun updateButtonStates1() {
        updatePlayerButtonStates(xlabPlayer1, connectButton1, playButton1, 
            pauseButton1, stopButton1, disconnectButton1)
    }
    
    /**
     * 두 번째 플레이어 버튼 상태 업데이트
     */
    private fun updateButtonStates2() {
        updatePlayerButtonStates(xlabPlayer2, connectButton2, playButton2, 
            pauseButton2, stopButton2, disconnectButton2)
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