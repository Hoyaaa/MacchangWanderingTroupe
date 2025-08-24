// MainActivity.kt
package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var tvToday: TextView
    private lateinit var tvHealthMent: TextView
    private lateinit var imgCharacter: ImageView
    private lateinit var btnMyPage: ImageButton
    private lateinit var btnTodayMenu: MaterialButton

    // 현재 사용자 이메일(의도적으로 nullable)
    private var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)

        tvToday      = findViewById(R.id.tv_today)
        tvHealthMent = findViewById(R.id.tv_health_ment)
        imgCharacter = findViewById(R.id.img_character)
        btnMyPage    = findViewById(R.id.btn_mypage)
        btnTodayMenu = findViewById(R.id.btn_today_menu)

        // 오늘 날짜
        val seoul = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(seoul)
        tvToday.text = today.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

        // 마이페이지
        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }

        // ✅ 이메일 해석: Intent → FirebaseAuth → SharedPreferences
        userEmail = resolveUserEmail()
        // 마지막으로 알아낸 이메일은 저장(다음 앱 재실행 대비)
        userEmail?.let { saveLastEmail(it) }

        // 오늘의 식단 페이지로 이동할 때도 항상 이메일을 싣는다
        btnTodayMenu.setOnClickListener {
            startActivity(Intent(this, TodayMenuActivity::class.java).apply {
                userEmail?.let { putExtra("user_email", it) }
            })
        }

        // 분석 결과를 넘겨받았으면 그대로 표시, 아니면 Firestore에서 읽어 분석
        val passed = intent.getSerializableExtra("analysis_result") as? HealthAnalyzer.AnalysisResult
        if (passed != null) {
            applyResultToMain(passed)
        } else {
            val email = userEmail
            if (email.isNullOrBlank()) {
                toast("로그인 정보가 없어 로그인 화면으로 이동합니다.")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            fetchAndAnalyzeFromFirestore(email)
        }
    }

    /** Intent, FirebaseAuth, SharedPreferences 순으로 사용자 이메일을 찾아 반환 */
    private fun resolveUserEmail(): String? {
        // 1) Intent
        intent.getStringExtra("user_email")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 2) FirebaseAuth
        FirebaseAuth.getInstance().currentUser?.email
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 3) SharedPreferences
        getSharedPreferences("auth", MODE_PRIVATE)
            .getString("last_email", null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    private fun saveLastEmail(email: String) {
        getSharedPreferences("auth", MODE_PRIVATE)
            .edit()
            .putString("last_email", email.trim())
            .apply()
    }

    /** Firestore에서 사용자 프로필을 읽어 분석 후 반영 */
    private fun fetchAndAnalyzeFromFirestore(email: String) {
        FirebaseFirestore.getInstance()
            .collection("usercode")
            .document(email.trim()) // ✅ 공백·대소문자 안전
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    toast("프로필이 없습니다.")
                    return@addOnSuccessListener
                }

                val h   = snap.getLong("height_cm")?.toInt()
                val w   = snap.getDouble("weight_kg") ?: snap.getLong("weight_kg")?.toDouble()
                val age = (snap.getLong("age_man_years") ?: snap.getLong("age_years"))?.toInt()

                val sexStr = snap.getString("sex")?.lowercase()
                val isMale = when (sexStr) {
                    "male","m","남","남자"   -> true
                    "female","f","여","여자" -> false
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
        tvHealthMent.text = r.messageBody

        val hasHigh   = (r.bmiGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                (r.fatGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                (r.weightGauge.zone == HealthAnalyzer.Zone.HIGH)
        val allNormal = (r.bmiGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                (r.fatGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                (r.weightGauge.zone == HealthAnalyzer.Zone.NORMAL)

        val drawableRes = when {
            hasHigh   -> R.drawable.char_dumbbell_broccoli
            allNormal -> R.drawable.char_water_broccoli
            else      -> R.drawable.char_sad_leaf
        }
        imgCharacter.setImageResource(drawableRes)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
