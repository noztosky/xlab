package com.xlab.debug

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xlab.Player.XLABPlayer
import com.xlab.Player.XLABPlayerButton
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
    private lateinit var buttonContainer1: LinearLayout
    private lateinit var playerLayout1: LinearLayout
    
    // 두 번째 플레이어 UI 컴포넌트들
    private lateinit var videoContainer2: FrameLayout
    private lateinit var buttonContainer2: LinearLayout
    private lateinit var playerLayout2: LinearLayout
    
    // 전체화면 상태 관리
    private var originalLayoutParams1: LinearLayout.LayoutParams? = null
    private var originalLayoutParams2: LinearLayout.LayoutParams? = null
    private var currentFullscreenPlayer: Int = 0 // 0: 없음, 1: 플레이어1, 2: 플레이어2
    
    // 루트 레이아웃 관리
    private lateinit var rootLayout: LinearLayout
    private var originalRootPadding: IntArray = intArrayOf(0, 0, 0, 0) // left, top, right, bottom
    
    // XLABPlayer 인스턴스들
    private var xlabPlayer1: XLABPlayer? = null
    private var xlabPlayer2: XLABPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 시스템 UI 완전 숨기기 (상태바, 네비게이션 바, 액션바)
        enableFullscreenMode()
        
        setContentView(R.layout.activity_dual_player)
        
        initViews()
        setupPlayers()
    }
    
    private fun enableFullscreenMode() {
        // 액션바 숨기기
        supportActionBar?.hide()
        
        // 상태바 및 네비게이션 바 완전 숨기기
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        
        // 시스템 UI가 다시 나타나는 것을 방지
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                // 시스템 UI가 나타나면 다시 숨기기
                enableFullscreenMode()
            }
        }
    }
    
    /**
     * UI 컴포넌트 초기화
     */
    private fun initViews() {
        // 루트 레이아웃
        rootLayout = findViewById(R.id.root_layout)
        originalRootPadding = intArrayOf(
            rootLayout.paddingLeft,
            rootLayout.paddingTop, 
            rootLayout.paddingRight,
            rootLayout.paddingBottom
        )
        
        // 첫 번째 플레이어 UI
        videoContainer1 = findViewById(R.id.video_container_1)
        buttonContainer1 = findViewById(R.id.button_container_1)
        playerLayout1 = videoContainer1.parent as LinearLayout
        
        // 두 번째 플레이어 UI
        videoContainer2 = findViewById(R.id.video_container_2)
        buttonContainer2 = findViewById(R.id.button_container_2)
        playerLayout2 = videoContainer2.parent as LinearLayout
        
        // 원본 레이아웃 파라미터 저장
        originalLayoutParams1 = playerLayout1.layoutParams as LinearLayout.LayoutParams
        originalLayoutParams2 = playerLayout2.layoutParams as LinearLayout.LayoutParams
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
        xlabPlayer1 = createPlayer("플레이어1", 1).also { player ->
            initializePlayer(player, videoContainer1, buttonContainer1, "플레이어1", 1)
        }
    }
    
    /**
     * 두 번째 플레이어 설정
     */
    private fun setupPlayer2() {
        xlabPlayer2 = createPlayer("플레이어2", 2).also { player ->
            initializePlayer(player, videoContainer2, buttonContainer2, "플레이어2", 2)
        }
    }
    
    /**
     * 공통 플레이어 생성
     */
    private fun createPlayer(playerName: String, cameraId: Int): XLABPlayer? {
        return try {
            XLABPlayer(this).apply {
                setCallback(object : XLABPlayer.PlayerCallback {
                    override fun onPlayerReady() = runOnUiThread {
                        Log.d(TAG, "$playerName 준비됨")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 준비됨", Toast.LENGTH_SHORT).show()
                    }
                    override fun onPlayerConnected() = runOnUiThread {
                        Log.d(TAG, "$playerName 연결됨")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 연결됨", Toast.LENGTH_SHORT).show()
                    }
                    override fun onPlayerDisconnected() = runOnUiThread {
                        Log.d(TAG, "$playerName 연결 해제됨")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 연결 해제됨", Toast.LENGTH_SHORT).show()
                    }
                    override fun onPlayerPlaying() = runOnUiThread {
                        Log.d(TAG, "$playerName 재생 중")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 재생 중", Toast.LENGTH_SHORT).show()
                    }
                    override fun onPlayerPaused() = runOnUiThread {
                        Log.d(TAG, "$playerName 일시정지됨")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 일시정지됨", Toast.LENGTH_SHORT).show()
                    }
                    override fun onPlayerError(error: String) = runOnUiThread {
                        Log.e(TAG, "$playerName 오류: $error")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 오류: $error", Toast.LENGTH_LONG).show()
                    }
                    override fun onVideoSizeChanged(width: Int, height: Int) = Unit
                    override fun onPtzCommand(command: String, success: Boolean) = Unit
                    override fun onFullscreenEntered() = runOnUiThread {
                        Log.d(TAG, "$playerName 전체화면 진입")
                        // Activity 레벨에서 레이아웃 조작
                        handleFullscreenEntered(playerName, cameraId)
                    }
                    override fun onFullscreenExited() = runOnUiThread {
                        Log.d(TAG, "$playerName 전체화면 종료")
                        // Activity 레벨에서 레이아웃 복원
                        handleFullscreenExited(playerName, cameraId)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "$playerName 설정 실패: ${e.message}")
            Toast.makeText(this, "$playerName 설정 실패: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }
    
    /**
     * 공통 플레이어 초기화
     */
    private fun initializePlayer(
        player: XLABPlayer?, 
        container: FrameLayout, 
        buttonContainer: LinearLayout,
        playerName: String, 
        cameraId: Int
    ) {
        Log.d(TAG, "$playerName 초기화 시작...")
        
        if (player == null) {
            Log.e(TAG, "$playerName 플레이어가 null입니다")
            Toast.makeText(this, "$playerName 플레이어가 null입니다", Toast.LENGTH_LONG).show()
            return
        }
        
        val success = player.initialize(container)
        Log.d(TAG, "$playerName 초기화 결과: $success")
        
        if (!success) {
            Log.e(TAG, "$playerName 초기화 실패")
            Toast.makeText(this, "$playerName 초기화 실패", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "$playerName 초기화 성공, 버튼 설정 중...")
            player.apply {
                // 내장 버튼 컨테이너 추가
                addButtonContainer(buttonContainer)
                
                // 4개 기본 버튼 추가
                addButton("연결", XLABPlayerButton.ButtonType.PRIMARY) { 
                    try {
                        Log.d(TAG, "$playerName 연결 시도 중...")
                        val url = if (cameraId == 1) "rtsp://192.168.144.108:554/stream=1" else "rtsp://192.168.144.108:555/stream=2"
                        Log.d(TAG, "$playerName URL: $url")
                        val result = connectAndPlay(url)
                        Log.d(TAG, "$playerName 연결 결과: $result")
                        if (!result) {
                            Toast.makeText(this@DualPlayerActivity, "$playerName 연결 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "$playerName 연결 중 예외: ${e.message}")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                addButton("재생", XLABPlayerButton.ButtonType.SUCCESS) { 
                    try {
                        Log.d(TAG, "$playerName 재생 시도 중...")
                        val result = play()
                        Log.d(TAG, "$playerName 재생 결과: $result")
                        if (!result) {
                            Toast.makeText(this@DualPlayerActivity, "$playerName 재생 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "$playerName 재생 중 예외: ${e.message}")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 재생 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                addButton("일시정지", XLABPlayerButton.ButtonType.WARNING) { 
                    try {
                        Log.d(TAG, "$playerName 일시정지 시도 중...")
                        val result = pause()
                        Log.d(TAG, "$playerName 일시정지 결과: $result")
                        if (!result) {
                            Toast.makeText(this@DualPlayerActivity, "$playerName 일시정지 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "$playerName 일시정지 중 예외: ${e.message}")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 일시정지 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                addButton("해제", XLABPlayerButton.ButtonType.DANGER) { 
                    try {
                        Log.d(TAG, "$playerName 해제 시도 중...")
                        val result = disconnect()
                        Log.d(TAG, "$playerName 해제 결과: $result")
                        if (!result) {
                            Toast.makeText(this@DualPlayerActivity, "$playerName 해제 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "$playerName 해제 중 예외: ${e.message}")
                        Toast.makeText(this@DualPlayerActivity, "$playerName 해제 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // 추가 기능 버튼들
                Log.d(TAG, "$playerName 추가 기능 버튼 추가 중...")
                addFullscreenButton()
                addRecordButton()
                addCaptureButton()
                
                // 카메라 설정
                Log.d(TAG, "$playerName 카메라 서버 설정 중... (cameraId: $cameraId)")
                setCameraServer("c12", "http://192.168.144.108:5000", cameraId)
                
                Log.d(TAG, "$playerName PTZ 컨트롤 표시 중...")
                showPtzControl()
                
                Log.d(TAG, "$playerName 설정 완료!")
            }
        }
    }
    
    /**
     * 전체화면 진입 처리
     */
    private fun handleFullscreenEntered(playerName: String, cameraId: Int) {
        // 다른 플레이어가 이미 전체화면인 경우 먼저 복원
        if (currentFullscreenPlayer != 0 && currentFullscreenPlayer != cameraId) {
            handleFullscreenExited("", currentFullscreenPlayer)
        }
        
        // 루트 레이아웃의 padding 제거 (진짜 전체화면)
        rootLayout.setPadding(0, 0, 0, 0)
        
        when (cameraId) {
            1 -> {
                // 플레이어1 전체화면: margin 제거
                val fullscreenParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, 0, 0, 0) // margin 모두 제거
                }
                playerLayout1.layoutParams = fullscreenParams
                playerLayout2.visibility = android.view.View.GONE
                currentFullscreenPlayer = 1
                Log.d(TAG, "플레이어1이 전체화면으로 전환됨")
            }
            2 -> {
                // 플레이어2 전체화면: margin 제거
                val fullscreenParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, 0, 0, 0) // margin 모두 제거
                }
                playerLayout2.layoutParams = fullscreenParams
                playerLayout1.visibility = android.view.View.GONE
                currentFullscreenPlayer = 2
                Log.d(TAG, "플레이어2가 전체화면으로 전환됨")
            }
        }

        // 시스템 UI 숨기기 (상태바, 네비게이션 바) - 이미 onCreate에서 설정됨
        // window.decorView.systemUiVisibility = (...) // 중복 제거
        
        Toast.makeText(this, "$playerName 전체화면", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 전체화면 종료 처리
     */
    private fun handleFullscreenExited(playerName: String, cameraId: Int) {
        // 시스템 UI는 그대로 유지 (빈 액티비티 형태 유지)
        // window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        
        // 루트 레이아웃의 padding 복원
        rootLayout.setPadding(
            originalRootPadding[0], // left
            originalRootPadding[1], // top  
            originalRootPadding[2], // right
            originalRootPadding[3]  // bottom
        )
        
        when (cameraId) {
            1 -> {
                // 플레이어1 원본 복원
                originalLayoutParams1?.let { params ->
                    playerLayout1.layoutParams = params
                }
                playerLayout2.visibility = android.view.View.VISIBLE
                Log.d(TAG, "플레이어1이 원본 크기로 복원됨")
            }
            2 -> {
                // 플레이어2 원본 복원
                originalLayoutParams2?.let { params ->
                    playerLayout2.layoutParams = params
                }
                playerLayout1.visibility = android.view.View.VISIBLE
                Log.d(TAG, "플레이어2가 원본 크기로 복원됨")
            }
        }
        
        currentFullscreenPlayer = 0
        Toast.makeText(this, "$playerName 원본 크기", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 백 버튼 처리 - 전체화면에서는 전체화면 종료
     */
    override fun onBackPressed() {
        if (currentFullscreenPlayer != 0) {
            // 전체화면 상태에서는 전체화면 종료
            when (currentFullscreenPlayer) {
                1 -> xlabPlayer1?.toggleFullscreen()
                2 -> xlabPlayer2?.toggleFullscreen()
            }
        } else {
            // 일반 상태에서는 기본 백 버튼 동작
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 앱이 다시 활성화될 때 전체화면 모드 재적용
        enableFullscreenMode()
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