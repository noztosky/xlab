package com.xlab.Player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * 카메라 정보 데이터 클래스
 */
data class CameraInfo(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val capabilities: List<CameraCapability>,
    val defaultSettings: CameraConnectionSettings
)

/**
 * 카메라 기능 목록
 */
enum class CameraCapability {
    PTZ_CONTROL,        // 팬틸트줌 제어
    PHOTO_CAPTURE,      // 사진 촬영
    VIDEO_RECORDING,    // 비디오 녹화
    ZOOM_CONTROL,       // 줌 제어
    FOCUS_CONTROL,      // 포커스 제어
    PRESET_POSITIONS,   // 프리셋 위치
    AUTO_TRACKING       // 자동 추적
}

/**
 * 카메라 연결 설정
 */
data class CameraConnectionSettings(
    val baseUrl: String,
    val username: String = "admin",
    val password: String = "",
    val timeout: Int = 5000,
    val apiVersion: String = "v1"
)

/**
 * PTZ 제어 범위 설정
 */
data class PTZRange(
    val panMin: Float,
    val panMax: Float,
    val tiltMin: Float,
    val tiltMax: Float,
    val zoomMin: Float = 1.0f,
    val zoomMax: Float = 10.0f
)

/**
 * 카메라 설정 매니저
 */
class CameraSettingsManager {
    
    companion object {
        private const val TAG = "CameraSettingsManager"
        
        private val availableCameras = listOf(
            CameraInfo(
                id = "c12_ptz",
                name = "C12 PTZ Camera",
                manufacturer = "XLab",
                model = "C12",
                capabilities = listOf(
                    CameraCapability.PTZ_CONTROL,
                    CameraCapability.PHOTO_CAPTURE,
                    CameraCapability.VIDEO_RECORDING
                ),
                defaultSettings = CameraConnectionSettings(
                    baseUrl = "http://192.168.144.108",
                    username = "admin",
                    password = ""
                )
            )
            // 여기에 다른 카메라들을 추가할 수 있습니다
            // CameraInfo(
            //     id = "hikvision_dome",
            //     name = "Hikvision Dome Camera",
            //     manufacturer = "Hikvision",
            //     model = "DS-2DE4A425IW-DE",
            //     capabilities = listOf(
            //         CameraCapability.PTZ_CONTROL,
            //         CameraCapability.PRESET_POSITIONS,
            //         CameraCapability.AUTO_TRACKING
            //     ),
            //     defaultSettings = CameraConnectionSettings(
            //         baseUrl = "http://192.168.1.100",
            //         username = "admin",
            //         password = "12345"
            //     )
            // )
        )
    }
    
    /**
     * 사용 가능한 카메라 목록 반환
     */
    fun getAvailableCameras(): List<CameraInfo> {
        return availableCameras
    }
    
    /**
     * 카메라 ID로 카메라 정보 검색
     */
    fun getCameraById(id: String): CameraInfo? {
        return availableCameras.find { it.id == id }
    }
    
    /**
     * 제조사별 카메라 목록 반환
     */
    fun getCamerasByManufacturer(manufacturer: String): List<CameraInfo> {
        return availableCameras.filter { it.manufacturer == manufacturer }
    }
    
    /**
     * 특정 기능을 지원하는 카메라 목록 반환
     */
    fun getCamerasByCapability(capability: CameraCapability): List<CameraInfo> {
        return availableCameras.filter { it.capabilities.contains(capability) }
    }
}

/**
 * 카메라 제어 인터페이스
 */
interface CameraController {
    fun setupCamera(settings: CameraConnectionSettings): Boolean
    fun panTo(angle: Float, callback: ((Boolean, String) -> Unit)? = null)
    fun tiltTo(angle: Float, callback: ((Boolean, String) -> Unit)? = null)
    fun zoomTo(level: Float, callback: ((Boolean, String) -> Unit)? = null)
    fun capturePhoto(callback: ((Boolean, String) -> Unit)? = null)
    fun startRecording(callback: ((Boolean, String) -> Unit)? = null)
    fun stopRecording(callback: ((Boolean, String) -> Unit)? = null)
    fun getPTZStatus(callback: ((Boolean, String, Map<String, Any>?) -> Unit)? = null)
    fun moveToPreset(presetId: Int, callback: ((Boolean, String) -> Unit)? = null)
}

/**
 * C12 카메라 컨트롤러 구현
 */
class C12CameraController : CameraController {
    
    companion object {
        private const val TAG = "C12CameraController"
    }
    
    private var settings: CameraConnectionSettings? = null
    private val ptzRange = PTZRange(-180f, 180f, -90f, 90f, 1.0f, 10.0f)
    
    override fun setupCamera(settings: CameraConnectionSettings): Boolean {
        this.settings = settings
        Log.d(TAG, "C12 카메라 설정 완료: ${settings.baseUrl}")
        return true
    }
    
    override fun panTo(angle: Float, callback: ((Boolean, String) -> Unit)?) {
        val clampedAngle = angle.coerceIn(ptzRange.panMin, ptzRange.panMax)
        executeCommand("pan", clampedAngle, callback)
    }
    
    override fun tiltTo(angle: Float, callback: ((Boolean, String) -> Unit)?) {
        val clampedAngle = angle.coerceIn(ptzRange.tiltMin, ptzRange.tiltMax)
        executeCommand("tilt", clampedAngle, callback)
    }
    
    override fun zoomTo(level: Float, callback: ((Boolean, String) -> Unit)?) {
        val clampedLevel = level.coerceIn(ptzRange.zoomMin, ptzRange.zoomMax)
        executeCommand("zoom", clampedLevel, callback)
    }
    
    override fun capturePhoto(callback: ((Boolean, String) -> Unit)?) {
        if (settings == null) {
            callback?.invoke(false, "카메라가 설정되지 않았습니다")
            return
        }
        
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val url = URL("${settings!!.baseUrl}/api/capture/photo?timestamp=$timestamp")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = settings!!.timeout
                connection.readTimeout = settings!!.timeout
                
                if (settings!!.username.isNotEmpty() && settings!!.password.isNotEmpty()) {
                    val credentials = Base64.encodeToString(
                        "${settings!!.username}:${settings!!.password}".toByteArray(),
                        Base64.NO_WRAP
                    )
                    connection.setRequestProperty("Authorization", "Basic $credentials")
                }
                
                val responseCode = connection.responseCode
                val message = if (responseCode == 200) "사진 촬영 성공" else "사진 촬영 실패: $responseCode"
                
                callback?.invoke(responseCode == 200, message)
                Log.d(TAG, "사진 촬영 결과: $message")
                
            } catch (e: Exception) {
                val errorMsg = "사진 촬영 중 오류: ${e.message}"
                callback?.invoke(false, errorMsg)
                Log.e(TAG, errorMsg, e)
            }
        }.start()
    }
    
    override fun startRecording(callback: ((Boolean, String) -> Unit)?) {
        executeRecordCommand("start", callback)
    }
    
    override fun stopRecording(callback: ((Boolean, String) -> Unit)?) {
        executeRecordCommand("stop", callback)
    }
    
    override fun getPTZStatus(callback: ((Boolean, String, Map<String, Any>?) -> Unit)?) {
        if (settings == null) {
            callback?.invoke(false, "카메라가 설정되지 않았습니다", null)
            return
        }
        
        Thread {
            try {
                val url = URL("${settings!!.baseUrl}/api/ptz/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = settings!!.timeout
                connection.readTimeout = settings!!.timeout
                
                if (settings!!.username.isNotEmpty() && settings!!.password.isNotEmpty()) {
                    val credentials = Base64.encodeToString(
                        "${settings!!.username}:${settings!!.password}".toByteArray(),
                        Base64.NO_WRAP
                    )
                    connection.setRequestProperty("Authorization", "Basic $credentials")
                }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    // 간단한 상태 정보 파싱 (실제로는 JSON 파싱 필요)
                    val statusMap = mapOf(
                        "pan" to "0.0",
                        "tilt" to "0.0",
                        "zoom" to "1.0"
                    )
                    callback?.invoke(true, "상태 조회 성공", statusMap)
                } else {
                    callback?.invoke(false, "상태 조회 실패: $responseCode", null)
                }
                
            } catch (e: Exception) {
                val errorMsg = "상태 조회 중 오류: ${e.message}"
                callback?.invoke(false, errorMsg, null)
                Log.e(TAG, errorMsg, e)
            }
        }.start()
    }
    
    override fun moveToPreset(presetId: Int, callback: ((Boolean, String) -> Unit)?) {
        // C12 카메라는 프리셋 기능이 없으므로 기본 구현
        callback?.invoke(false, "C12 카메라는 프리셋 기능을 지원하지 않습니다")
    }
    
    private fun executeCommand(type: String, value: Float, callback: ((Boolean, String) -> Unit)?) {
        if (settings == null) {
            callback?.invoke(false, "카메라가 설정되지 않았습니다")
            return
        }
        
        Thread {
            try {
                val url = URL("${settings!!.baseUrl}/api/ptz/$type?${if(type == "zoom") "level" else "angle"}=$value")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = settings!!.timeout
                connection.readTimeout = settings!!.timeout
                
                if (settings!!.username.isNotEmpty() && settings!!.password.isNotEmpty()) {
                    val credentials = Base64.encodeToString(
                        "${settings!!.username}:${settings!!.password}".toByteArray(),
                        Base64.NO_WRAP
                    )
                    connection.setRequestProperty("Authorization", "Basic $credentials")
                }
                
                val responseCode = connection.responseCode
                val message = if (responseCode == 200) {
                    "$type ${if(type == "zoom") "줌" else "각도"} $value 설정 성공"
                } else {
                    "$type 설정 실패: $responseCode"
                }
                
                callback?.invoke(responseCode == 200, message)
                Log.d(TAG, "$type 명령 결과: $message")
                
            } catch (e: Exception) {
                val errorMsg = "$type 명령 중 오류: ${e.message}"
                callback?.invoke(false, errorMsg)
                Log.e(TAG, errorMsg, e)
            }
        }.start()
    }
    
    private fun executeRecordCommand(action: String, callback: ((Boolean, String) -> Unit)?) {
        if (settings == null) {
            callback?.invoke(false, "카메라가 설정되지 않았습니다")
            return
        }
        
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val url = URL("${settings!!.baseUrl}/api/record/$action?timestamp=$timestamp")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = settings!!.timeout
                connection.readTimeout = settings!!.timeout
                
                if (settings!!.username.isNotEmpty() && settings!!.password.isNotEmpty()) {
                    val credentials = Base64.encodeToString(
                        "${settings!!.username}:${settings!!.password}".toByteArray(),
                        Base64.NO_WRAP
                    )
                    connection.setRequestProperty("Authorization", "Basic $credentials")
                }
                
                val responseCode = connection.responseCode
                val message = if (responseCode == 200) {
                    "녹화 $action 성공"
                } else {
                    "녹화 $action 실패: $responseCode"
                }
                
                callback?.invoke(responseCode == 200, message)
                Log.d(TAG, "녹화 $action 결과: $message")
                
            } catch (e: Exception) {
                val errorMsg = "녹화 $action 중 오류: ${e.message}"
                callback?.invoke(false, errorMsg)
                Log.e(TAG, errorMsg, e)
            }
        }.start()
    }
} 