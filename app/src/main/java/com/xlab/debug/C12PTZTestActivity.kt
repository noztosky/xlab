package com.xlab.debug

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xlab.Player.C12PTZController

/**
 * C12 PTZ 제어기 사용 예제
 */
class C12PTZTestActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "C12PTZTestActivity"
    }
    
    private val ptzController = C12PTZController()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // C12 PTZ 제어 시스템 테스트
        demonstratePTZControl()
    }
    
    private fun demonstratePTZControl() {
        Log.d(TAG, "=== C12 PTZ 제어 시스템 테스트 시작 ===")
        
        // 1. PTZ 제어기 설정
        setupPTZController()
        
        // 2. 기본 PTZ 제어 테스트
        // testBasicPTZControl()
        
        // 3. 고급 PTZ 제어 테스트
        // testAdvancedPTZControl()
    }
    
    /**
     * PTZ 제어기 설정
     */
    private fun setupPTZController() {
        Log.d(TAG, "\n=== PTZ 제어기 설정 ===")
        
        // C12 카메라 연결 설정 (새로운 방식)
        ptzController.configure(
            host = "192.168.144.108",
            ptzPort = 5000,
            username = "admin",
            password = "",
            timeout = 10000
        )
        
        Log.d(TAG, "PTZ 제어기 설정 완료")
        
        // 설정 완료 후 기본 테스트 실행
        testBasicPTZControl()
    }
    
    /**
     * 기본 PTZ 제어 테스트
     */
    private fun testBasicPTZControl() {
        Log.d(TAG, "\n=== 기본 PTZ 제어 테스트 ===")
        
        // 1. 팬 각도 설정 테스트
        ptzController.setPan(45.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "팬 설정 성공: $message")
                showToast("팬 설정: $message")
                
                // 팬 설정 성공 후 틸트 테스트
                testTiltControl()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "팬 설정 실패: $error")
                showToast("팬 설정 실패: $error")
            }
        })
    }
    
    /**
     * 틸트 제어 테스트
     */
    private fun testTiltControl() {
        Log.d(TAG, "\n=== 틸트 제어 테스트 ===")
        
        ptzController.setTilt(-30.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "틸트 설정 성공: $message")
                showToast("틸트 설정: $message")
                
                // 틸트 설정 성공 후 줌 테스트
                testZoomControl()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "틸트 설정 실패: $error")
                showToast("틸트 설정 실패: $error")
            }
        })
    }
    
    /**
     * 줌 제어 테스트
     */
    private fun testZoomControl() {
        Log.d(TAG, "\n=== 줌 제어 테스트 ===")
        
        ptzController.setZoom(3.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "줌 설정 성공: $message")
                showToast("줌 설정: $message")
                
                // 줌 설정 성공 후 고급 테스트
                testAdvancedPTZControl()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "줌 설정 실패: $error")
                showToast("줌 설정 실패: $error")
            }
        })
    }
    
    /**
     * 고급 PTZ 제어 테스트
     */
    private fun testAdvancedPTZControl() {
        Log.d(TAG, "\n=== 고급 PTZ 제어 테스트 ===")
        
        // 1. 팬/틸트 동시 설정 테스트
        testPanTiltSimultaneous()
    }
    
    /**
     * 팬/틸트 동시 설정 테스트
     */
    private fun testPanTiltSimultaneous() {
        Log.d(TAG, "팬/틸트 동시 설정 테스트")
        
        ptzController.setPanTilt(90.0f, 45.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "팬/틸트 동시 설정 성공: $message")
                showToast("팬/틸트 동시: $message")
                
                // 상대적 이동 테스트
                testRelativeMovement()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "팬/틸트 동시 설정 실패: $error")
                showToast("팬/틸트 동시 실패: $error")
            }
        })
    }
    
    /**
     * 상대적 이동 테스트
     */
    private fun testRelativeMovement() {
        Log.d(TAG, "상대적 이동 테스트")
        
        // 현재 위치에서 팬 -45도, 틸트 -15도 이동
        ptzController.moveRelative(-45.0f, -15.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "상대적 이동 성공: $message")
                showToast("상대적 이동: $message")
                
                // PTZ 상태 조회 테스트
                testStatusQuery()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "상대적 이동 실패: $error")
                showToast("상대적 이동 실패: $error")
            }
        })
    }
    
    /**
     * PTZ 상태 조회 테스트
     */
    private fun testStatusQuery() {
        Log.d(TAG, "PTZ 상태 조회 테스트")
        
        // 1. 로컬 상태 조회 (네트워크 요청 없음)
        val (localPan, localTilt, localZoom) = ptzController.getLocalStatus()
        Log.d(TAG, "로컬 PTZ 상태: pan=$localPan, tilt=$localTilt, zoom=$localZoom")
        showToast("로컬 상태: P${localPan}° T${localTilt}° Z${localZoom}x")
        
        // 2. 카메라에서 실제 상태 조회
        ptzController.getCurrentStatus(object : C12PTZController.PTZStatusCallback {
            override fun onSuccess(pan: Float, tilt: Float, zoom: Float) {
                Log.d(TAG, "실제 PTZ 상태 조회 성공: pan=$pan, tilt=$tilt, zoom=$zoom")
                showToast("실제 상태: P${pan}° T${tilt}° Z${zoom}x")
                
                // 홈 포지션 이동 테스트
                testHomePosition()
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "PTZ 상태 조회 실패: $error")
                showToast("상태 조회 실패: $error")
                
                // 실패해도 홈 포지션 테스트 계속
                testHomePosition()
            }
        })
    }
    
    /**
     * 홈 포지션 이동 테스트
     */
    private fun testHomePosition() {
        Log.d(TAG, "홈 포지션 이동 테스트")
        
        // 3초 후 홈 포지션으로 이동
        android.os.Handler(mainLooper).postDelayed({
            ptzController.moveToHome(object : C12PTZController.PTZMoveCallback {
                override fun onSuccess(message: String) {
                    Log.d(TAG, "홈 포지션 이동 성공: $message")
                    showToast("홈 포지션: $message")
                    
                    // 모든 테스트 완료
                    Log.d(TAG, "\n=== 모든 PTZ 테스트 완료 ===")
                    showToast("모든 PTZ 테스트 완료! 🎉")
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "홈 포지션 이동 실패: $error")
                    showToast("홈 포지션 실패: $error")
                }
            })
        }, 3000)
    }
    
    /**
     * PTZ 범위 테스트 (추가 테스트)
     */
    private fun testPTZLimits() {
        Log.d(TAG, "\n=== PTZ 범위 테스트 ===")
        
        // 범위를 벗어나는 값 테스트
        ptzController.setPan(200.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "범위 초과 팬 설정: $message")
            }
            
            override fun onError(error: String) {
                Log.d(TAG, "예상된 범위 초과 오류: $error")
            }
        })
        
        ptzController.setTilt(-100.0f, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "범위 초과 틸트 설정: $message")
            }
            
            override fun onError(error: String) {
                Log.d(TAG, "예상된 범위 초과 오류: $error")
            }
        })
    }
    
    /**
     * PTZ 정지 테스트
     */
    private fun testPTZStop() {
        Log.d(TAG, "\n=== PTZ 정지 테스트 ===")
        
        ptzController.stopMovement(object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "PTZ 정지 성공: $message")
                showToast("PTZ 정지: $message")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "PTZ 정지 실패: $error")
                showToast("PTZ 정지 실패: $error")
            }
        })
    }
    
    /**
     * 연속 제어 테스트 (패턴 이동)
     */
    private fun testPatternMovement() {
        Log.d(TAG, "\n=== 패턴 이동 테스트 ===")
        
        val positions = listOf(
            Pair(0.0f, 0.0f),      // 중앙
            Pair(90.0f, 30.0f),    // 우상
            Pair(-90.0f, 30.0f),   // 좌상
            Pair(-90.0f, -30.0f),  // 좌하
            Pair(90.0f, -30.0f),   // 우하
            Pair(0.0f, 0.0f)       // 다시 중앙
        )
        
        executePatternMovement(positions, 0)
    }
    
    private fun executePatternMovement(positions: List<Pair<Float, Float>>, index: Int) {
        if (index >= positions.size) {
            Log.d(TAG, "패턴 이동 테스트 완료")
            showToast("패턴 이동 완료")
            return
        }
        
        val (pan, tilt) = positions[index]
        Log.d(TAG, "패턴 이동 ${index + 1}/${positions.size}: pan=$pan, tilt=$tilt")
        
        ptzController.setPanTilt(pan, tilt, object : C12PTZController.PTZMoveCallback {
            override fun onSuccess(message: String) {
                Log.d(TAG, "패턴 이동 ${index + 1} 성공: $message")
                
                // 1초 후 다음 위치로
                android.os.Handler(mainLooper).postDelayed({
                    executePatternMovement(positions, index + 1)
                }, 1000)
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "패턴 이동 ${index + 1} 실패: $error")
                // 실패해도 다음 위치로 계속
                executePatternMovement(positions, index + 1)
            }
        })
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ptzController.release()
        Log.d(TAG, "PTZ 테스트 액티비티 종료")
    }
} 