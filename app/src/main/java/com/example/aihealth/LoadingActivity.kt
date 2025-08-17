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
 * LoadingActivity.kt (최종본, Kotlin)
 *
 * - mode = "loginCheck" : 로그인 검증 수행
 *     · 문서 없음  → "등록된 회원이 아닙니다." 토스트 후 finish() (로그인 화면으로 복귀)
 *     · 비번 불일치 → "올바른 비밀번호가 아닙니다." 토스트 후 finish()
 *     · 성공       → 3초 뒤 MainActivity 로 이동
 *
 * - mode 가 없거나 그 외 : 회원가입/일반 진입 → 5초 뒤 MainActivity 로 이동
 */
class LoadingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val DELAY_SIGNUP_MS = 5_000L      // 회원가입 완료 후 5초
    private val DELAY_LOGIN_OK_MS = 3_000L    // 로그인 성공 시 3초

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        // 게이지 애니메이션 시작
        findViewById<View>(R.id.progress_bar_gauge)?.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.loading_gauge)
        )

        FirebaseApp.initializeApp(this)

        when (intent.getStringExtra("mode")) {
            "loginCheck" -> doLoginCheck()
            else -> startMainAfterDelay(DELAY_SIGNUP_MS, null) // 기본 5초 딜레이
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
                    // 로그인 성공 → 3초 후 메인으로
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
