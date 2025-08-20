package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import org.mindrot.jbcrypt.BCrypt

/**
 * LoadingActivity.kt (최종본)
 *
 * - mode = "loginCheck" : 로그인 검증 수행
 * - mode = "analyze"    : Firestore 사용자 프로필을 불러 분석 후 결과 화면으로 이동
 * - 그 외/없음           : 기본(회원가입 등) → 5초 뒤 메인으로
 */
class LoadingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val DELAY_SIGNUP_MS    = 5_000L  // 회원가입/일반 진입 딜레이
    private val DELAY_LOGIN_OK_MS  = 3_000L  // 로그인 성공 후 딜레이
    private val DELAY_ANALYZE_MS   = 1_800L  // 분석 완료 후 결과 화면으로 넘길 때 살짝 보여줄 시간

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        // 로딩 애니메이션 시작
        findViewById<View>(R.id.progress_bar_gauge)?.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.loading_gauge)
        )

        FirebaseApp.initializeApp(this)

        when (intent.getStringExtra("mode")) {
            "loginCheck" -> doLoginCheck()
            "analyze"    -> doAnalyze()
            null         -> startMainAfterDelay(DELAY_SIGNUP_MS, null)  // null 분기 처리
            else         -> startMainAfterDelay(DELAY_SIGNUP_MS, null)  // 나머지
        }

    }

    /** 로그인 검증: Firestore의 usercode/{email} 문서 확인 */
    private fun doLoginCheck() {
        val email = intent.getStringExtra("email")?.trim().orEmpty()
        val password = intent.getStringExtra("password").orEmpty()

        if (email.isEmpty() || password.isEmpty()) {
            toast("이메일/비밀번호를 입력해 주세요.")
            finish()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("usercode")
            .document(email)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    toast("등록된 회원이 아닙니다.")
                    finish()
                    return@addOnSuccessListener
                }

                val hash = snap.getString("password_hash")
                if (hash.isNullOrBlank()) {
                    toast("비밀번호 정보가 없습니다. 비밀번호를 재설정해 주세요.")
                    finish()
                    return@addOnSuccessListener
                }

                val ok = try { BCrypt.checkpw(password, hash) } catch (_: Exception) { false }
                if (ok) {
                    startMainAfterDelay(DELAY_LOGIN_OK_MS, email)
                } else {
                    toast("올바른 비밀번호가 아닙니다.")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                toast("로그인 오류: ${e.message}")
                finish()
            }
    }

    /** ✅ Firestore에서 프로필을 읽고 HealthAnalyzer로 분석 → 결과 화면으로 이동 */
    private fun doAnalyze() {
        val email = intent.getStringExtra("user_email") ?: run {
            toast("사용자 정보가 없습니다.")
            finish()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("usercode").document(email)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    toast("프로필이 없습니다.")
                    finish()
                    return@addOnSuccessListener
                }

                val h   = snap.getLong("height_cm")?.toInt()
                val w   = snap.getDouble("weight_kg") ?: snap.getLong("weight_kg")?.toDouble()
                val age = (snap.getLong("age_man_years") ?: snap.getLong("age_years"))?.toInt()

                // 성별 필드가 있다면 사용 (없으면 null)
                val sexStr = snap.getString("sex")?.lowercase()
                val isMale = when (sexStr) {
                    "male", "m", "남", "남자"   -> true
                    "female", "f", "여", "여자" -> false
                    else -> null
                }

                if (h == null || w == null || age == null) {
                    toast("키/몸무게/나이 정보가 부족합니다.")
                    finish()
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

                // 로딩 애니메이션이 보이도록 약간의 지연 후 결과 화면으로
                handler.postDelayed({
                    startActivity(Intent(this, HealthAnalysisActivity::class.java).apply {
                        putExtra("user_email", email)
                        putExtra("analysis_result", result)
                    })
                    finish()
                }, DELAY_ANALYZE_MS)
            }
            .addOnFailureListener { e ->
                toast("분석 실패: ${e.message}")
                finish()
            }
    }

    /** delay 후 MainActivity로 전환 (email 전달은 선택) */
    private fun startMainAfterDelay(delayMs: Long, email: String?) {
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java).apply {
                email?.let { putExtra("user_email", it) }
            })
            finish()
        }, delayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        findViewById<View>(R.id.progress_bar_gauge)?.clearAnimation()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
