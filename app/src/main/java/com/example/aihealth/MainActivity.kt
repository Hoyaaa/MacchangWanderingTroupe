// MainActivity.kt
package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 기능 개요
 * - activity_main.xml과 연결되는 메인 액티비티.
 * - 오늘 날짜(대한민국/Asia-Seoul) 표시.
 * - 사용자 건강 분석 결과(인텐트 "analysis_result" 또는 Firestore 조회)를 이용해
 *   - 말풍선 텍스트(tv_health_ment) 갱신
 *   - 캐릭터 이미지(img_character) 상태별 교체
 *
 * 인텐트 입력
 * - "user_email": String (Firestore에서 가져올 때 필요)
 * - "analysis_result": HealthAnalyzer.AnalysisResult (이미 계산된 결과가 있을 때)
 *
 * 참고
 * - HealthAnalyzer.analyze(...)가 반환하는 messageBody를 말풍선에 사용합니다.
 * - AnalysisResult 필드(bmiGauge/weightGauge/fatGauge/messageBody 등)를 그대로 활용합니다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvToday: TextView
    private lateinit var tvHealthMent: TextView
    private lateinit var imgCharacter: ImageView
    private lateinit var btnMyPage: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) 뷰 바인딩
        tvToday       = findViewById(R.id.tv_today)
        tvHealthMent  = findViewById(R.id.tv_health_ment)   // 말풍선 텍스트(가운데 정렬/자동 줄바꿈 XML 설정됨)
        imgCharacter  = findViewById(R.id.img_character)    // 상황별 PNG 교체 대상
        btnMyPage     = findViewById(R.id.btn_mypage)

        FirebaseApp.initializeApp(this)

        // 2) 오늘 날짜(대한민국 표준시)
        val seoul = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(seoul)
        tvToday.text = today.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))


        btnMyPage.setOnClickListener {
            // MyPageActivity로 이동
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
        }


        // 4) 분석 결과 적용
        //    - 인텐트로 AnalysisResult가 오면 그대로 사용
        //    - 없으면 Firestore에서 읽어 HealthAnalyzer.analyze(...) 수행
        val passed = intent.getSerializableExtra("analysis_result") as? HealthAnalyzer.AnalysisResult
        if (passed != null) {
            applyResultToMain(passed)
        } else {
            val email = intent.getStringExtra("user_email")
            if (email.isNullOrBlank()) {
                toast("사용자 정보가 없습니다.")
                return
            }
            fetchAndAnalyzeFromFirestore(email)
        }
    }

    /** Firestore에서 사용자 프로필을 읽어 분석 후 반영 */
    private fun fetchAndAnalyzeFromFirestore(email: String) {
        FirebaseFirestore.getInstance()
            .collection("usercode").document(email)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    toast("프로필이 없습니다.")
                    return@addOnSuccessListener
                }

                val h = snap.getLong("height_cm")?.toInt()
                val w = snap.getDouble("weight_kg") ?: snap.getLong("weight_kg")?.toDouble()
                val age = (snap.getLong("age_man_years") ?: snap.getLong("age_years"))?.toInt()

                val sexStr = snap.getString("sex")?.lowercase()
                val isMale = when (sexStr) {
                    "male", "m", "남", "남자" -> true
                    "female", "f", "여", "여자" -> false
                    else -> null
                }

                if (h == null || w == null || age == null) {
                    toast("키/몸무게/나이 정보가 부족합니다.")
                    return@addOnSuccessListener
                }

                val result = HealthAnalyzer.analyze(
                    HealthAnalyzer.AnalysisInput(
                        heightCm = h,
                        weightKg = w,
                        ageYears = age,
                        isMale   = isMale
                    )
                )
                applyResultToMain(result)
            }
            .addOnFailureListener { e ->
                toast("데이터 로드 실패: ${e.message}")
            }
    }

    /** 결과를 메인 화면에 반영 (멘트/캐릭터 교체) */
    private fun applyResultToMain(r: HealthAnalyzer.AnalysisResult) {
        // 말풍선 텍스트: HealthAnalyzer가 생성한 본문 사용
        tvHealthMent.text = r.messageBody   //

        // 캐릭터 이미지 매핑 규칙
        // - HIGH가 하나라도 있으면: 관리 필요(운동 자극) → dumbbell 캐릭터
        // - 모두 NORMAL이면: 균형 양호 → 물+브로콜리 캐릭터
        // - 그 외(LOW 포함/혼재): 영양 보충 필요 → 눈물/속상 캐릭터
        val hasHigh   = (r.bmiGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                (r.fatGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                (r.weightGauge.zone == HealthAnalyzer.Zone.HIGH)
        val allNormal = (r.bmiGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                (r.fatGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                (r.weightGauge.zone == HealthAnalyzer.Zone.NORMAL)

        val drawableRes = when {
            hasHigh   -> R.drawable.char_dumbbell_broccoli   // 이미지1 (운동+브로콜리)
            allNormal -> R.drawable.char_water_broccoli      // 이미지4 (물병+브로콜리)
            else      -> R.drawable.char_sad_leaf            // 이미지3 (눈물)
        }
        imgCharacter.setImageResource(drawableRes)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
