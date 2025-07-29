package com.xlab.Player

import kotlinx.coroutines.*
import java.net.*
import java.io.IOException
import android.util.Base64
import kotlin.coroutines.suspendCoroutine

/**
 * C12 카메라 PTZ 제어 전용 클래스
 * 팬, 틸트, 줌 등의 PTZ 기능을 담당
 */
class C12PTZController {
    
    companion object {
        // PTZ 각도 범위
        const val PAN_MIN = -180.0f
        const val PAN_MAX = 180.0f
        const val TILT_MIN = -90.0f
        const val TILT_MAX = 90.0f
        const val ZOOM_MIN = 1.0f
        const val ZOOM_MAX = 10.0f
        
        // 기본 설정
        const val DEFAULT_TIMEOUT = 5000
        const val DEFAULT_USERNAME = "admin"
        const val DEFAULT_PASSWORD = ""
    }
    
    // 카메라 연결 설정
    private var cameraHost: String? = null
    private var cameraInetAddress: InetAddress? = null // IP 주소 캐싱
    private var ptzPort: Int = 5000
    private var username: String = DEFAULT_USERNAME
    private var password: String = DEFAULT_PASSWORD
    private var timeout: Int = DEFAULT_TIMEOUT
    private var isConnected: Boolean = false
    private var udpSocket: DatagramSocket? = null
    
    // 현재 PTZ 상태
    private var currentPan: Float = 0.0f
    private var currentTilt: Float = 0.0f
    private var currentZoom: Float = 1.0f
    
    // PTZ 명령 순차 처리를 위한 변수
    private var isProcessingCommand = false
    private val commandQueue = mutableListOf<() -> Unit>()
    
    // 콜백 인터페이스들
    interface PTZMoveCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
    }
    
    interface PTZStatusCallback {
        fun onSuccess(pan: Float, tilt: Float, zoom: Float)
        fun onError(error: String)
    }
    
    interface PresetCallback {
        fun onSuccess(presetId: Int, message: String)
        fun onError(error: String)
    }
    
    interface PatrolCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
    }
    
    interface ConnectionCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
    }
    

    
    /**
     * C12 카메라 연결 설정
     * @param host 카메라 IP 주소 (예: "192.168.144.108")
     * @param ptzPort PTZ 제어 포트 (기본값: 5000)
     * @param username 사용자명 (기본값: "admin")
     * @param password 비밀번호 (기본값: "")
     * @param timeout 타임아웃 (기본값: 5000ms)
     */
    fun configure(
        host: String,
        ptzPort: Int = 5000,
        username: String = DEFAULT_USERNAME,
        password: String = DEFAULT_PASSWORD,
        timeout: Int = DEFAULT_TIMEOUT
    ): C12PTZController {
        this.cameraHost = host
        this.ptzPort = ptzPort
        this.username = username
        this.password = password
        this.timeout = timeout
        this.isConnected = false
        
        return this
    }
    
    /**
     * C12 카메라 연결 설정 (URL 방식 - 호환성)
     * @param baseUrl 카메라 기본 URL (예: "http://192.168.144.108:5000")
     * @param username 사용자명 (기본값: "admin")
     * @param password 비밀번호 (기본값: "")
     * @param timeout 타임아웃 (기본값: 5000ms)
     */
    fun configureWithUrl(
        baseUrl: String,
        username: String = DEFAULT_USERNAME,
        password: String = DEFAULT_PASSWORD,
        timeout: Int = DEFAULT_TIMEOUT
    ): C12PTZController {
        try {
            val uri = URI(baseUrl)
            this.cameraHost = uri.host
            this.ptzPort = if (uri.port != -1) uri.port else 5000
            
            // IP 주소 미리 캐싱 (성능 최적화)
            this.cameraInetAddress = InetAddress.getByName(uri.host)
            
        } catch (e: Exception) {
            
        }
        
        this.username = username
        this.password = password
        this.timeout = timeout
        this.isConnected = false
        
        return this
    }
    
    /**
     * 카메라 UDP 연결 테스트 및 연결 상태 확인
     */
    fun connect(callback: ConnectionCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라 호스트가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // UDP 소켓 생성
                udpSocket = DatagramSocket()
                udpSocket?.soTimeout = 2000  // 2초 타임아웃
                
                // C12 카메라는 응답하지 않을 수 있으므로 바로 연결 성공으로 간주
                withContext(Dispatchers.Main) {
                    isConnected = true
                    callback?.onSuccess("C12 카메라 UDP 연결 준비 완료")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnected = false
                    val errorMsg = "UDP 소켓 생성 실패: ${e.message}"
                    callback?.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * 카메라 연결 상태 확인
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * 연결 해제
     */
    fun disconnect() {
        try {
            // 카메라에 정상 종료 신호 전송 (선택사항)
            if (isConnected && cameraHost != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 정상 종료 명령 전송 (카메라가 지원하는 경우)
                        val shutdownCommand = "#TPUG6wSHUTDOWN"
                        val crc = calculateCrc(shutdownCommand)
                        val finalCommand = "$shutdownCommand${String.format("%02X", crc)}"
                        
                        sendUDPCommand(finalCommand)
                    } catch (e: Exception) {
                        
                    }
                }
            }
            
            // 연결 상태 해제
            isConnected = false
            
            // UDP 소켓 안전하게 정리
            udpSocket?.let { socket ->
                try {
                    if (!socket.isClosed) {
                        socket.close()
                    } else {
                        
                    }
                } catch (e: Exception) {
                    
                }
            }
            udpSocket = null
            
            // 카메라 호스트 정보 초기화
            cameraHost = null
            ptzPort = 0
            
        } catch (e: Exception) {
            
            // 오류가 발생해도 강제로 정리
            isConnected = false
            udpSocket = null
            cameraHost = null
            ptzPort = 0
        }
    }
    
    /**
     * 팬(Pan) 각도 설정
     * @param angle 팬 각도 (-180 ~ 180도)
     * @param callback 결과 콜백
     */
    fun setPan(angle: Float, callback: PTZMoveCallback? = null) {
        val clampedAngle = angle.coerceIn(PAN_MIN, PAN_MAX)
        
        if (clampedAngle != angle) {
            
        }
        
        executePTZCommand("pan", clampedAngle) { success, message ->
            if (success) {
                callback?.onSuccess("팬 각도 $clampedAngle° 설정 완료")
            } else {
                callback?.onError("팬 설정 실패: $message")
            }
        }
    }
    
    /**
     * 틸트(Tilt) 각도 설정
     * @param angle 틸트 각도 (-90 ~ 90도)
     * @param callback 결과 콜백
     */
    fun setTilt(angle: Float, callback: PTZMoveCallback? = null) {
        val clampedAngle = angle.coerceIn(TILT_MIN, TILT_MAX)
        
        if (clampedAngle != angle) {
            
        }
        
        executePTZCommand("tilt", clampedAngle) { success, message ->
            if (success) {
                callback?.onSuccess("틸트 각도 $clampedAngle° 설정 완료")
            } else {
                callback?.onError("틸트 설정 실패: $message")
            }
        }
    }
    
    /**
     * 줌(Zoom) 레벨 설정
     * @param level 줌 레벨 (1.0 ~ 10.0배)
     * @param callback 결과 콜백
     */
    fun setZoom(level: Float, callback: PTZMoveCallback? = null) {
        val clampedLevel = level.coerceIn(ZOOM_MIN, ZOOM_MAX)
        
        if (clampedLevel != level) {
            
        }
        
        executePTZCommand("zoom", clampedLevel) { success, message ->
            if (success) {
                currentZoom = clampedLevel
                callback?.onSuccess("줌 레벨 ${clampedLevel}배 설정 완료")
            } else {
                callback?.onError("줌 설정 실패: $message")
            }
        }
    }
    
    /**
     * 팬, 틸트 동시 설정
     * @param pan 팬 각도
     * @param tilt 틸트 각도
     * @param callback 결과 콜백
     */
    fun setPanTilt(pan: Float, tilt: Float, callback: PTZMoveCallback? = null) {
        val clampedPan = pan.coerceIn(PAN_MIN, PAN_MAX)
        val clampedTilt = tilt.coerceIn(TILT_MIN, TILT_MAX)
        
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        if (!isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 팬 명령 먼저 전송
                val panCommand = generateC12AxisCommand("GAY", clampedPan, 1.0f)
                val panSuccess = sendUDPCommand(panCommand)
                
                // 짧은 지연 후 틸트 명령 전송  
                kotlinx.coroutines.delay(100)
                val tiltCommand = generateC12AxisCommand("GAP", clampedTilt, 1.0f)
                val tiltSuccess = sendUDPCommand(tiltCommand)
                
                withContext(Dispatchers.Main) {
                    if (panSuccess && tiltSuccess) {
                        synchronized(this@C12PTZController) {
                            currentPan = clampedPan
                            currentTilt = clampedTilt
                        }
                        callback?.onSuccess("팬 ${clampedPan}°, 틸트 ${clampedTilt}° 설정 완료")
                    } else {
                        callback?.onError("팬/틸트 동시 설정 실패")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = "팬/틸트 설정 중 오류: ${e.message}"
                    callback?.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * PTZ 명령 순차 처리
     */
    private fun executeCommandSequentially(command: () -> Unit) {
        synchronized(this) {
            commandQueue.add(command)
            if (!isProcessingCommand) {
                processNextCommand()
            }
        }
    }
    
    private fun processNextCommand() {
        synchronized(this) {
            if (commandQueue.isEmpty()) {
                isProcessingCommand = false
                return
            }
            
            isProcessingCommand = true
            val command = commandQueue.removeAt(0)
            command()
        }
    }
    
    private fun onCommandCompleted() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(10) // 명령 간 최소 지연 (50ms → 10ms)
            processNextCommand()
        }
    }

    /**
     * 상대적 이동 (현재 위치에서 증감) - 순차 처리
     * @param deltaPan 팬 증감값
     * @param deltaTilt 틸트 증감값
     * @param callback 결과 콜백
     */
    fun moveRelative(deltaPan: Float, deltaTilt: Float, callback: PTZMoveCallback? = null) {
        executeCommandSequentially {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 현재 위치 기준 계산
                    val newPan = currentPan - deltaPan
                    val newTilt = currentTilt + deltaTilt

                    // 성공 시 위치 업데이트하는 콜백
                    val updateCallback = object : PTZMoveCallback {
                        override fun onSuccess(message: String) {
                            synchronized(this@C12PTZController) {
                                currentPan = newPan
                                currentTilt = newTilt
                            }
                            CoroutineScope(Dispatchers.Main).launch {
                                callback?.onSuccess(message)
                            }
                            onCommandCompleted() // 다음 명령 처리
                        }
                        
                        override fun onError(error: String) {
                            CoroutineScope(Dispatchers.Main).launch {
                                callback?.onError(error)
                            }
                            onCommandCompleted() // 다음 명령 처리
                        }
                    }

                    // 축별 개별 제어
                                            when {
                            deltaPan == 0f && deltaTilt != 0f -> {
                                setTilt(newTilt, updateCallback)
                            }
                            deltaTilt == 0f && deltaPan != 0f -> {
                                setPan(newPan, updateCallback)
                            }
                            deltaPan != 0f && deltaTilt != 0f -> {
                                setPanTilt(newPan, newTilt, updateCallback)
                            }
                            else -> {
                                CoroutineScope(Dispatchers.Main).launch {
                                    callback?.onSuccess("이동 없음")
                                }
                                onCommandCompleted()
                            }
                        }
                    
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = "상대 이동 중 오류: ${e.message}"
                        callback?.onError(errorMsg)
                    }
                    onCommandCompleted()
                }
            }
        }
    }
    
    /**
     * 홈 포지션으로 이동 (0, 0, 1배줌) - 비동기 병렬 처리
     * @param callback 결과 콜백
     */
    fun moveToHome(callback: PTZMoveCallback? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 팬/틸트와 줌을 병렬로 실행
                val panTiltDeferred = async {
                    suspendCoroutine<Boolean> { continuation ->
                        setPanTilt(0.0f, 0.0f, object : PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                continuation.resumeWith(Result.success(true))
                            }
                            override fun onError(error: String) {
                                continuation.resumeWith(Result.success(false))
                            }
                        })
                    }
                }
                
                val zoomDeferred = async {
                    suspendCoroutine<Boolean> { continuation ->
                        setZoom(1.0f, object : PTZMoveCallback {
                            override fun onSuccess(message: String) {
                                continuation.resumeWith(Result.success(true))
                            }
                            override fun onError(error: String) {
                                continuation.resumeWith(Result.success(false))
                            }
                        })
                    }
                }
                
                // 두 작업 모두 완료될 때까지 대기
                val panTiltResult = panTiltDeferred.await()
                val zoomResult = zoomDeferred.await()
                
                withContext(Dispatchers.Main) {
                    if (panTiltResult && zoomResult) {
                        callback?.onSuccess("홈 포지션 이동 완료")
                    } else if (!panTiltResult) {
                        callback?.onError("팬/틸트 초기화 실패")
                    } else {
                        callback?.onError("줌 초기화 실패")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("홈 포지션 이동 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 현재 PTZ 상태 조회
     * @param callback 결과 콜백
     */
    fun getCurrentStatus(callback: PTZStatusCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        if (!isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "STATUS"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        // UDP에서는 상태 응답 파싱이 필요하지만 현재는 로컬 값 반환
                        callback?.onSuccess(currentPan, currentTilt, currentZoom)
                    } else {
                        callback?.onError("상태 조회 실패")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = "상태 조회 중 오류: ${e.message}"
                    callback?.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * 현재 로컬에 저장된 PTZ 값 반환 (네트워크 요청 없음)
     */
    fun getLocalStatus(): Triple<Float, Float, Float> {
        return Triple(currentPan, currentTilt, currentZoom)
    }
    
    /**
     * PTZ 이동 중인지 확인
     */
    fun isMoving(): Boolean {
        // 실제 구현에서는 카메라 API로 이동 상태를 확인
        // 여기서는 간단히 false 반환
        return false
    }
    
    /**
     * PTZ 이동 정지
     */
    fun stopMovement(callback: PTZMoveCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        if (!isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "STOP"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("PTZ 이동 정지")
                    } else {
                        callback?.onError("PTZ 정지 실패")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("PTZ 정지 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * PTZ 명령 실행 (내부 메서드)
     */
    private fun executePTZCommand(
        type: String, 
        value: Float, 
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        if (cameraHost == null) {
            callback?.invoke(false, "카메라가 설정되지 않았습니다")
            return
        }
        
        if (!isConnected) {
            callback?.invoke(false, "카메라가 연결되지 않았습니다. 먼저 connect()를 호출하세요")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // C12 카메라 실제 UDP 프로토콜 명령 생성
                val command = when (type) {
                    "pan" -> generateC12AxisCommand("GAY", value, 1.0f)  // Yaw (좌우)
                    "tilt" -> generateC12AxisCommand("GAP", value, 1.0f) // Pitch (상하)
                    else -> null
                }
                
                if (command != null) {
                    val success = sendUDPCommand(command)
                    val message = if (success) {
                        "$type $value° 설정 성공"
                    } else {
                        "$type 설정 실패"
                    }
                    
                    withContext(Dispatchers.Main) {
                        callback?.invoke(success, message)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback?.invoke(false, "$type 명령은 지원되지 않습니다")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = "$type 명령 중 오류: ${e.message}"
                    callback?.invoke(false, errorMsg)
                }
            }
        }
    }
    
    /**
     * C12 카메라 실제 UDP 명령 생성
     * 예: #TPUG6wGAY000A0A63
     */
    private fun generateC12AxisCommand(axis: String, angle: Float, speed: Float): String {
        // 각도를 100배하여 정수로 변환 (예: 10.0° -> 1000)
        val angleInt = (angle * 100).toInt()
        
        // 2바이트 signed 값으로 변환 (big-endian)
        val angleHex = String.format("%04X", angleInt and 0xFFFF)
        
        // 속도를 10배하여 정수로 변환 (예: 1.0 -> 10)
        val speedInt = (speed * 10).toInt()
        val speedHex = String.format("%02X", speedInt)
        
        // C12 프로토콜 명령 생성
        val command = "#TPUG6w$axis$angleHex$speedHex"
        
        // CRC 계산
        val crc = calculateCrc(command)
        val crcHex = String.format("%02X", crc)
        
        val finalCommand = "$command$crcHex"
        
        return finalCommand
    }
    
    /**
     * CRC 계산 (Python 코드와 동일)
     */
    private fun calculateCrc(command: String): Int {
        var crc = 0
        for (char in command) {
            crc += char.code
        }
        return crc and 0xFF  // 8-bit 값으로 제한
    }
    
    /**
     * UDP 명령 전송 (내부 메서드) - 최적화 버전
     */
    private fun sendUDPCommand(command: String): Boolean {
        return try {
            if (udpSocket == null || cameraInetAddress == null) {
                
                return false
            }
            
            // UDP 패킷 생성 및 전송 (캐싱된 IP 주소 사용)
            val commandBytes = command.toByteArray()
            val packet = DatagramPacket(
                commandBytes,
                commandBytes.size,
                cameraInetAddress,
                ptzPort
            )
            
            udpSocket?.send(packet)
            
            // C12 카메라는 응답하지 않으므로 송신 후 즉시 성공 반환
            return true
            
        } catch (e: Exception) {
            
            false
        }
    }
    
    /**
     * PTZ 상태 응답 파싱 (내부 메서드)
     */
    private fun parsePTZStatus(response: String) {
        try {
            // 간단한 파싱 (실제로는 JSON 라이브러리 사용 권장)
            // 예상 응답: {"pan": 45.0, "tilt": -30.0, "zoom": 2.5}
            
            // 임시로 기본값 사용 (실제 구현에서는 JSON 파싱 필요)
            // val json = JSONObject(response)
            // currentPan = json.getDouble("pan").toFloat()
            // currentTilt = json.getDouble("tilt").toFloat()
            // currentZoom = json.getDouble("zoom").toFloat()
            
            
        } catch (e: Exception) {
            
        }
    }
    
    // ===========================================
    // 추가 PTZ 제어 기능들 (UDP 미지원 - 기본 기능만 사용)
    // ===========================================
    
    /**
     * 프리셋 위치 저장 (1-8번) - UDP 버전
     */
    fun savePreset(presetId: Int, callback: PresetCallback? = null) {
        if (presetId !in 1..8) {
            callback?.onError("프리셋 번호는 1-8 사이여야 합니다")
            return
        }
        
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "PRESET_SAVE=$presetId"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(presetId, "프리셋 $presetId 저장 완료")
                    } else {
                        callback?.onError("프리셋 $presetId 저장 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("프리셋 저장 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 프리셋 위치로 이동
     */
    fun gotoPreset(presetId: Int, callback: PresetCallback? = null) {
        if (presetId !in 1..8) {
            callback?.onError("프리셋 번호는 1-8 사이여야 합니다")
            return
        }
        
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "PRESET_GOTO=$presetId"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(presetId, "프리셋 $presetId 이동 완료")
                    } else {
                        callback?.onError("프리셋 $presetId 이동 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("프리셋 이동 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 프리셋 삭제
     */
    fun deletePreset(presetId: Int, callback: PresetCallback? = null) {
        if (presetId !in 1..8) {
            callback?.onError("프리셋 번호는 1-8 사이여야 합니다")
            return
        }
        
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "PRESET_DELETE=$presetId"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(presetId, "프리셋 $presetId 삭제 완료")
                    } else {
                        callback?.onError("프리셋 $presetId 삭제 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("프리셋 삭제 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 연속 이동 시작 (방향키 제어)
     */
    enum class PTZDirection {
        UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT, ZOOM_IN, ZOOM_OUT
    }
    
    fun startContinuousMove(direction: PTZDirection, speed: Int = 50, callback: PTZMoveCallback? = null) {
        val clampedSpeed = speed.coerceIn(1, 100)
        
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val directionParam = when (direction) {
                    PTZDirection.UP -> "up"
                    PTZDirection.DOWN -> "down"  
                    PTZDirection.LEFT -> "left"
                    PTZDirection.RIGHT -> "right"
                    PTZDirection.UP_LEFT -> "up_left"
                    PTZDirection.UP_RIGHT -> "up_right"
                    PTZDirection.DOWN_LEFT -> "down_left"
                    PTZDirection.DOWN_RIGHT -> "down_right"
                    PTZDirection.ZOOM_IN -> "zoom_in"
                    PTZDirection.ZOOM_OUT -> "zoom_out"
                }
                
                val command = "MOVE_START=$directionParam;$clampedSpeed"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("연속 이동 시작: $direction")
                    } else {
                        callback?.onError("연속 이동 시작 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("연속 이동 시작 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 연속 이동 정지
     */
    fun stopContinuousMove(callback: PTZMoveCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "MOVE_STOP"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("연속 이동 정지")
                    } else {
                        callback?.onError("연속 이동 정지 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("연속 이동 정지 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 순찰 시작 (프리셋 1-4 순서대로)
     */
    fun startPatrol(presetList: List<Int> = listOf(1, 2, 3, 4), intervalSeconds: Int = 5, callback: PatrolCallback? = null) {
        if (presetList.isEmpty() || presetList.any { it !in 1..8 }) {
            callback?.onError("유효하지 않은 프리셋 목록입니다")
            return
        }
        
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val presetParams = presetList.joinToString(",")
                val command = "PATROL_START=$presetParams;$intervalSeconds"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("순찰 시작: 프리셋 $presetList")
                    } else {
                        callback?.onError("순찰 시작 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("순찰 시작 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 순찰 정지
     */
    fun stopPatrol(callback: PatrolCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "PATROL_STOP"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("순찰 정지 완료")
                    } else {
                        callback?.onError("순찰 정지 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("순찰 정지 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 자동 추적 시작/정지
     */
    fun setAutoTracking(enabled: Boolean, callback: PTZMoveCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val action = if (enabled) "start" else "stop"
                val command = "TRACKING_$action"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        val msg = if (enabled) "자동 추적 시작" else "자동 추적 정지"
                        callback?.onSuccess(msg)
                    } else {
                        callback?.onError("자동 추적 설정 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("자동 추적 설정 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 이동 속도 설정 (1-100)
     */
    fun setPTZSpeed(speed: Int, callback: PTZMoveCallback? = null) {
        val clampedSpeed = speed.coerceIn(1, 100)
        
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "SPEED=$clampedSpeed"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("PTZ 속도 설정: $clampedSpeed")
                    } else {
                        callback?.onError("PTZ 속도 설정 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("PTZ 속도 설정 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 줌 포커스 자동 조절
     */
    fun autoFocus(callback: PTZMoveCallback? = null) {
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "FOCUS_AUTO"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("자동 포커스 완료")
                    } else {
                        callback?.onError("자동 포커스 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("자동 포커스 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 수동 포커스 조절 (near/far)
     */
    fun manualFocus(direction: String, callback: PTZMoveCallback? = null) {
        if (direction !in listOf("near", "far")) {
            callback?.onError("포커스 방향은 'near' 또는 'far'여야 합니다")
            return
        }
        
        if (cameraHost == null) {
            callback?.onError("카메라가 설정되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "FOCUS_MANUAL=$direction"
                val success = sendUDPCommand(command)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("수동 포커스 $direction 완료")
                    } else {
                        callback?.onError("수동 포커스 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("수동 포커스 중 오류: ${e.message}")
                }
            }
        }
    }
    
    // ===== D류 녹화 관련 콜백 인터페이스 =====
    
    interface RecordingCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
    }
    
    interface RecordingStatusCallback {
        fun onSuccess(isRecording: Boolean, message: String)
        fun onError(error: String)
    }
    
    interface ResolutionCallback {
        fun onSuccess(resolution: Int, message: String)
        fun onError(error: String)
    }
    
    interface SDCardCallback {
        fun onSuccess(capacity: String, message: String)
        fun onError(error: String)
    }
    
    // ===== 1. 녹화 (录像) =====
    
    /**
     * 녹화 시작 명령
     * 제어위: w, 표식위: REC, 데이터위: 01
     */
    fun startRecording(callback: RecordingCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2wREC01"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("녹화 시작 명령 전송됨")
                    } else {
                        callback?.onError("녹화 시작 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("녹화 시작 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 녹화 정지 명령
     * 제어위: w, 표식위: REC, 데이터위: 00
     */
    fun stopRecording(callback: RecordingCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2wREC00"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("녹화 정지 명령 전송됨")
                    } else {
                        callback?.onError("녹화 정지 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("녹화 정지 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 녹화 상태 확인 명령
     * 제어위: w, 표식위: REC, 데이터위: 0A
     */
    fun checkRecordingStatus(callback: RecordingStatusCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2wREC0A"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(false, "녹화 상태 확인 명령 전송됨")
                    } else {
                        callback?.onError("녹화 상태 확인 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("녹화 상태 확인 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 녹화 상태 조회 명령
     * 제어위: r, 표식위: REC, 데이터위: 00
     */
    fun getRecordingStatus(callback: RecordingStatusCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2rREC00"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(false, "녹화 상태 조회 명령 전송됨")
                    } else {
                        callback?.onError("녹화 상태 조회 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("녹화 상태 조회 중 오류: ${e.message}")
                }
            }
        }
    }
        

    
    // ===== 2. 사진촬영 (拍照) =====
    
    /**
     * 사진 촬영 명령
     * 제어위: w, 표식위: CAP, 데이터위: 01
     */
    fun capturePhoto(callback: RecordingCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2wCAP01"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("사진 촬영 명령 전송됨")
                    } else {
                        callback?.onError("사진 촬영 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("사진 촬영 중 오류: ${e.message}")
                }
            }
        }
    }
    
    // ===== 3. 녹화 분해능 설정 (录像分辨率) =====
    
    /**
     * 녹화 해상도 설정 명령
     * 제어위: w, 표식위: VID, 데이터위: XoXi
     * @param resolution 0: 720p, 1: 1080p, 2: 2k, 3: 4k
     */
    fun setVideoResolution(resolution: Int, callback: ResolutionCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        val resolutionCode = when (resolution) {
            0 -> "00"  // 720p
            1 -> "01"  // 1080p
            2 -> "02"  // 2k
            3 -> "03"  // 4k
            else -> {
                callback?.onError("유효하지 않은 해상도입니다 (0: 720p, 1: 1080p, 2: 2k, 3: 4k)")
                return
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2wVID$resolutionCode"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        val resolutionName = when (resolution) {
                            0 -> "720p"
                            1 -> "1080p"
                            2 -> "2K"
                            3 -> "4K"
                            else -> "Unknown"
                        }
                        callback?.onSuccess(resolution, "해상도 $resolutionName 설정 명령 전송됨")
                    } else {
                        callback?.onError("해상도 설정 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("해상도 설정 중 오류: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 녹화 해상도 조회 명령
     * 제어위: r, 표식위: VID, 데이터위: 00
     */
    fun getVideoResolution(callback: ResolutionCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2rVID00"
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess(-1, "해상도 조회 명령 전송됨")
                    } else {
                        callback?.onError("해상도 조회 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("해상도 조회 중 오류: ${e.message}")
                }
            }
        }
    }
    
    // ===== 4. 내장 메모리 용량 (内存卡容量) =====
    
    /**
     * 메모리 카드 용량 조회 명령
     * 제어위: r, 표식위: SDC, 데이터위: x1x2
     */
    fun getSDCardCapacity(callback: SDCardCallback? = null) {
        if (cameraHost == null || !isConnected) {
            callback?.onError("카메라가 연결되지 않았습니다")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val command = "#TPUD2rSDC00"  // x1x2 값은 문서에서 명시되지 않음
                val crc = calculateCrc(command)
                val finalCommand = "$command${String.format("%02X", crc)}"
                val success = sendUDPCommand(finalCommand)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback?.onSuccess("", "SD카드 용량 조회 명령 전송됨")
                    } else {
                        callback?.onError("SD카드 용량 조회 실패")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("SD카드 용량 조회 중 오류: ${e.message}")
                }
            }
        }
    }
    
    // ===== 응답 해석 함수들 =====
    
    /**
     * 녹화 상태 응답 해석
     * @param response 카메라 응답 (예: #TPUD2rREC001E)
     * @return true: 녹화 중(1), false: 녹화 안함(0)
     */
    fun parseRecordingStatusResponse(response: String): Boolean {
        return if (response.length >= 12) {
            val statusChar = response[11]  // X2 위치
            statusChar == '1'
        } else {
            false
        }
    }

    /**
     * 리소스 정리
     */
    fun release() {
        disconnect()
        cameraHost = null
        currentPan = 0.0f
        currentTilt = 0.0f
        currentZoom = 1.0f
    }
} 