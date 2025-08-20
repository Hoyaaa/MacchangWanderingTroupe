package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firestore에서 사용자 프로필(키/몸무게/나이 등)을 읽어 HealthAnalyzer로 분석,
 * activity_health_analysis.xml에 표시.
 *
 * 인텐트:
 *  - "user_email" : String (필수)
 *  - 또는 "analysis_result" : HealthAnalyzer.AnalysisResult (직접 계산된 결과 전달 시)
 */
class HealthAnalysisActivity : AppCompatActivity() {

    private lateinit var tvNoticeTitle: TextView
    private lateinit var tvNoticeBody: TextView

    private lateinit var ivWeightMarker: ImageView
    private lateinit var ivFatMarker: ImageView
    private lateinit var ivBmiMarker: ImageView

    private lateinit var tvLow: TextView
    private lateinit var tvNormal: TextView
    private lateinit var tvHigh: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_analysis)
        FirebaseApp.initializeApp(this)

        // 바/지시자 & 알림카드
        ivWeightMarker = findViewById(R.id.iv_weight_marker)
        ivFatMarker    = findViewById(R.id.iv_fat_marker)
        ivBmiMarker    = findViewById(R.id.iv_bmi_marker)

        tvNoticeTitle  = findViewById(R.id.tv_notice_title)
        tvNoticeBody   = findViewById(R.id.tv_notice_desc)

        tvLow    = findViewById(R.id.tv_low)
        tvNormal = findViewById(R.id.tv_normal)
        tvHigh   = findViewById(R.id.tv_high)

        // ✅ finish 버튼 → MainActivity 이동
        val btnFinish = findViewById<Button>(R.id.btn_finish)
        btnFinish.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            // 뒤로가기 시 분석화면 안 나오게 스택 정리
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // 미리 계산된 결과가 들어오면 그대로 사용
        val passed = intent.getSerializableExtra("analysis_result") as? HealthAnalyzer.AnalysisResult
        if (passed != null) {
            applyResult(passed)
            return
        }

        // 아니면 Firestore에서 읽어서 분석
        val email = intent.getStringExtra("user_email")
        if (email.isNullOrBlank()) {
            toast("사용자 정보가 없습니다."); finish(); return
        }

        FirebaseFirestore.getInstance()
            .collection("usercode").document(email)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { toast("프로필이 없습니다."); finish(); return@addOnSuccessListener }

                val h = snap.getLong("height_cm")?.toInt()
                val w = snap.getDouble("weight_kg") ?: snap.getLong("weight_kg")?.toDouble()
                val age = (snap.getLong("age_man_years") ?: snap.getLong("age_years"))?.toInt()

                // 성별 필드가 있다면 불러오기
                val sexStr = snap.getString("sex")?.lowercase()
                val isMale = when (sexStr) {
                    "male", "m", "남", "남자" -> true
                    "female", "f", "여", "여자" -> false
                    else -> null
                }

                if (h == null || w == null || age == null) {
                    toast("키/몸무게/나이 정보가 부족합니다."); finish(); return@addOnSuccessListener
                }

                val result = HealthAnalyzer.analyze(
                    HealthAnalyzer.AnalysisInput(
                        heightCm = h,
                        weightKg = w,
                        ageYears = age,
                        isMale = isMale
                    )
                )
                applyResult(result)
            }
            .addOnFailureListener { e ->
                toast("데이터 로드 실패: ${e.message}")
                finish()
            }
    }

    private fun applyResult(r: HealthAnalyzer.AnalysisResult) {
        // 지시자 위치 바꾸기 (bias)
        setMarkerBias(ivWeightMarker, r.weightGauge.bias)
        setMarkerBias(ivFatMarker,    r.fatGauge.bias)
        setMarkerBias(ivBmiMarker,    r.bmiGauge.bias)

        // 알림 카드
        tvNoticeTitle.text = r.messageTitle
        tvNoticeBody.text  = r.messageBody
    }

    private fun setMarkerBias(marker: ImageView, bias: Float) {
        val lp = marker.layoutParams as ConstraintLayout.LayoutParams
        lp.horizontalBias = bias.coerceIn(0f, 1f)
        marker.layoutParams = lp
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
