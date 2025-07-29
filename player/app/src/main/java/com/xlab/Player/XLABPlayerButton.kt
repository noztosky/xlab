package com.xlab.Player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/**
 * XLABPlayer용 프로그래매틱 버튼 클래스
 * 뷰 없이 코드로만 생성하여 재사용 가능
 */
class XLABPlayerButton(
    private val context: Context,
    private val text: String,
    private val buttonType: ButtonType = ButtonType.PRIMARY
) {
    
    enum class ButtonType {
        PRIMARY,    // 기본 버튼 (파란색)
        SUCCESS,    // 성공 버튼 (초록색)
        WARNING,    // 경고 버튼 (주황색)
        DANGER,     // 위험 버튼 (빨간색)
        SECONDARY   // 보조 버튼 (회색)
    }
    
    // 실제 버튼 뷰
    val buttonView: Button
    
    // 버튼 상태
    var isEnabled: Boolean = true
        set(value) {
            field = value
            buttonView.isEnabled = value
            updateButtonAppearance()
        }
    
    // 클릭 리스너
    private var clickListener: (() -> Unit)? = null
    
    init {
        buttonView = createButton()
        setupButton()
    }
    
    /**
     * 버튼 생성
     */
    private fun createButton(): Button {
        return Button(context).apply {
            text = this@XLABPlayerButton.text
            
            // 레이아웃 파라미터 설정
            val params = LinearLayout.LayoutParams(
                dpToPx(40), // 기본 너비 120dp
                dpToPx(40)   // 기본 높이 40dp
            ).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            layoutParams = params
            
            // 텍스트 설정
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            
            // 클릭 리스너 설정
            setOnClickListener {
                clickListener?.invoke()
            }
        }
    }
    
    /**
     * 버튼 스타일 설정
     */
    private fun setupButton() {
        updateButtonAppearance()
    }
    
    /**
     * 버튼 외관 업데이트
     */
    private fun updateButtonAppearance() {
        val (backgroundColor, pressedColor) = getButtonColors()
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(if (isEnabled) backgroundColor else Color.GRAY)
        }
        
        // 눌림 효과를 위한 StateListDrawable
        val stateDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(if (isEnabled) pressedColor else Color.GRAY)
            })
            addState(intArrayOf(), drawable)
        }
        
        buttonView.background = stateDrawable
        buttonView.alpha = if (isEnabled) 1.0f else 0.6f
    }
    
    /**
     * 버튼 타입에 따른 색상 반환
     */
    private fun getButtonColors(): Pair<Int, Int> {
        val (normal, pressed) = BUTTON_COLORS[buttonType] ?: BUTTON_COLORS[ButtonType.PRIMARY]!!
        return Pair(Color.parseColor(normal), Color.parseColor(pressed))
    }
    
    /**
     * 클릭 리스너 설정
     */
    fun setOnClickListener(listener: () -> Unit) {
        this.clickListener = listener
    }
    
    /**
     * 버튼 텍스트 변경
     */
    fun setText(newText: String) {
        buttonView.text = newText
    }
    
    /**
     * 버튼을 아이콘 형태로 설정 (간소화)
     */
    fun setAsIconButton(icon: String, size: Int = 40) {
        buttonView.text = icon
        buttonView.textSize = 20f
        buttonView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        buttonView.background = null
        buttonView.setTextColor(Color.WHITE)
        setSize(size, size)
    }

    /**
     * 투명 배경의 전체화면 버튼 스타일
     */
    fun setAsTransparentIconButton(icon: String) {
        buttonView.text = icon
        buttonView.textSize = 24f
        buttonView.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        
        // 완전 투명 배경
        buttonView.background = null
        buttonView.setTextColor(Color.WHITE)
        
        // 그림자 효과 (가독성을 위해)
        buttonView.setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
        
        // 정사각형으로 만들기
        setSize(36, 36)
    }
    
    /**
     * 공통 스타일 설정 헬퍼 메서드
     */
    private fun applyButtonStyle(
        textSize: Float,
        textColor: Int = Color.WHITE,
        padding: Int = 0,
        normalColor: String,
        pressedColor: String,
        shape: Int = GradientDrawable.OVAL,
        cornerRadius: Float = 0f,
        shadowRadius: Float = 4f
    ) {
        buttonView.textSize = textSize
        buttonView.setPadding(padding, padding, padding, padding)
        buttonView.setTextColor(textColor)
        
        val drawable = GradientDrawable().apply {
            this.shape = shape
            if (shape == GradientDrawable.RECTANGLE && cornerRadius > 0) {
                this.cornerRadius = cornerRadius
            }
            setColor(Color.parseColor(normalColor))
        }
        
        val stateDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
                this.shape = shape
                if (shape == GradientDrawable.RECTANGLE && cornerRadius > 0) {
                    this.cornerRadius = cornerRadius
                }
                setColor(Color.parseColor(pressedColor))
            })
            addState(intArrayOf(), drawable)
        }
        
        buttonView.background = stateDrawable
        buttonView.setShadowLayer(shadowRadius, 2f, 2f, Color.parseColor("#40000000"))
    }

    /**
     * PTZ 컨트롤 버튼 스타일
     */
    fun setAsPtzButton() {
        applyButtonStyle(
            textSize = 20f,
            normalColor = "#80444444",
            pressedColor = "#80666666",
            shape = GradientDrawable.RECTANGLE,
            cornerRadius = 8f,
            shadowRadius = 3f
        )
    }

    /**
     * 전체화면 버튼 스타일 (PTZ와 동일한 스타일)
     */
    fun setAsFullscreenButton(icon: String) {
        buttonView.text = icon
        applyButtonStyle(
            textSize = 14f,
            padding = dpToPx(4),
            normalColor = "#80444444",
            pressedColor = "#80666666",
            shape = GradientDrawable.RECTANGLE,
            cornerRadius = 8f,
            shadowRadius = 3f
        )
    }

    /**
     * 녹화 버튼 스타일 (빨간색 원형)
     */
    fun setAsRecordButton() {
        applyButtonStyle(
            textSize = 16f,
            normalColor = "#FF4444",
            pressedColor = "#CC3333"
        )
    }

    /**
     * 녹화 중 버튼 스타일 (깜빡이는 효과)
     */
    fun setAsRecordButtonRecording() {
        applyButtonStyle(
            textSize = 14f,
            normalColor = "#DD0000",
            pressedColor = "#AA0000",
            shape = GradientDrawable.RECTANGLE,
            cornerRadius = 4f
        )
    }

    /**
     * 사진 촬영 버튼 스타일 (파란색 원형)
     */
    fun setAsCaptureButton() {
        applyButtonStyle(
            textSize = 14f,
            normalColor = "#2196F3",
            pressedColor = "#1976D2"
        )
    }

    /**
     * 버튼 크기 변경
     */
    fun setSize(widthDp: Int, heightDp: Int) {
        val params = LinearLayout.LayoutParams(dpToPx(widthDp), dpToPx(heightDp))
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        buttonView.layoutParams = params
    }
    
    /**
     * FrameLayout용 마진 설정 (크기 고정 제거)
     */
    fun setFrameLayoutMargin(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int, gravity: Int) {
        val params = android.widget.FrameLayout.LayoutParams(
            dpToPx(36), dpToPx(36), gravity
        ).apply {
            setMargins(dpToPx(leftDp), dpToPx(topDp), dpToPx(rightDp), dpToPx(bottomDp))
        }
        buttonView.layoutParams = params
    }

    /**
     * 버튼 마진 설정
     */
    fun setMargin(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int) {
        val params = buttonView.layoutParams as LinearLayout.LayoutParams
        params.setMargins(dpToPx(leftDp), dpToPx(topDp), dpToPx(rightDp), dpToPx(bottomDp))
        buttonView.layoutParams = params
    }
    
    /**
     * dp를 px로 변환
     */
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
    
    companion object {
        // 색상 상수들
        private val BUTTON_COLORS = mapOf(
            ButtonType.PRIMARY to Pair("#2196F3", "#1976D2"),
            ButtonType.SUCCESS to Pair("#4CAF50", "#388E3C"),
            ButtonType.WARNING to Pair("#FF9800", "#F57C00"),
            ButtonType.DANGER to Pair("#F44336", "#D32F2F"),
            ButtonType.SECONDARY to Pair("#9E9E9E", "#757575")
        )
        
        /**
         * 정적 팩토리 메서드
         */
        fun create(
            context: Context,
            text: String,
            type: ButtonType = ButtonType.PRIMARY,
            clickListener: (() -> Unit)? = null
        ): XLABPlayerButton {
            return XLABPlayerButton(context, text, type).apply {
                clickListener?.let { setOnClickListener(it) }
            }
        }
        
        /**
         * 여러 버튼을 한 번에 생성
         */
        fun createButtons(
            context: Context,
            vararg buttonConfigs: Triple<String, ButtonType, (() -> Unit)?>
        ): List<XLABPlayerButton> {
            return buttonConfigs.map { (text, type, listener) ->
                create(context, text, type, listener)
            }
        }
    }
} 