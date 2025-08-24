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
    private lateinit var btnHealthInfo: MaterialButton   // ← 추가: 건강 정보 버튼

    // 현재 사용자 이메일(의도적으로 nullable)
    private var userEmail: String? = null

    // ── 27 카테고리 멘트 맵 ──
    private val charMent: Map<String, String> by lazy {
        mapOf(
            // wl_*_*
            "char_wl_bl_fl" to "체중과 체지방이 모두 부족해요. 잘 먹고 운동해서 건강하게 체력을 키워 보세요!",
            "char_wl_bl_fn" to "체중은 조금 적지만 체지방은 괜찮아요. 근육량을 늘리면 더 건강한 몸이 될 거예요!",
            "char_wl_bl_fh" to "체중은 적은데 체지방은 많아요. 근육을 키우면서 체력을 보강하는 게 필요해요.",
            "char_wl_bn_fl" to "체중은 적당하지만 체지방이 너무 낮아요. 영양을 잘 챙겨서 체력을 유지하세요!",
            "char_wl_bn_fn" to "체중은 조금 적은 편이지만 전반적으로 균형이 좋아요. 식습관만 잘 챙기면 건강해요!",
            "char_wl_bn_fh" to "체중은 적지만 체지방이 높은 편이에요. 근육량을 늘리고 꾸준히 운동해 주세요!",
            // 안전장치
            "char_wl_bh_fl" to "수치가 일시적으로 튈 수 있어요. 최근 측정 값을 다시 확인해 주세요.",
            "char_wl_bh_fn" to "지표가 상충돼요. 신장·체중·체지방 측정을 재확인해 주세요.",
            "char_wl_bh_fh" to "측정값 재확인이 필요해요. 최근 변화나 기기 오차를 점검해 보세요.",

            // wn_*_*
            "char_wn_bl_fl" to "체중은 정상인데 체지방이 적어요. 건강해 보이지만 에너지가 부족할 수 있으니 잘 챙겨 드세요!",
            "char_wn_bl_fn" to "체중은 정상이고 체지방도 적당해요. 다만 BMI가 낮아 꾸준히 영양을 보충하면 좋아요.",
            "char_wn_bl_fh" to "체중은 정상인데 체지방이 많아요. 규칙적인 운동으로 건강을 유지해 보세요.",
            "char_wn_bn_fl" to "아주 건강한 상태예요! 운동하면서 근육을 키우면 더 탄탄해질 수 있어요.",
            "char_wn_bn_fn" to "전체적으로 균형이 잘 맞아요. 지금처럼만 유지하면 건강하게 지낼 수 있어요!",
            "char_wn_bn_fh" to "체중은 정상인데 체지방이 높은 편이에요. 단 음식 줄이고 운동하면 더 좋아져요.",
            "char_wn_bh_fl" to "BMI는 높지만 체지방은 적어요. 근육이 많은 체형일 수 있어요. 운동을 꾸준히 이어가세요!",
            "char_wn_bh_fn" to "체중은 정상인데 BMI가 높아요. 식습관을 조금 더 관리하면 금방 좋아질 거예요.",
            "char_wn_bh_fh" to "체중은 정상인데 체지방과 BMI가 높아요. 꾸준한 관리로 조절해 보세요.",

            // wh_*_*
            // 안전장치
            "char_wh_bl_fl" to "측정값이 상충돼요. 신장·체중 단위를 재확인해 보세요.",
            "char_wh_bl_fn" to "비정상 조합입니다. 최근 측정/입력 값을 다시 확인해 주세요.",
            "char_wh_bl_fh" to "값이 서로 맞지 않아요. 동일 조건에서 재측정해 주세요.",
            // 일반
            "char_wh_bn_fl" to "체중은 많지만 BMI와 체지방이 안정적이에요. 근육량이 많을 수 있네요! 좋은 상태예요.",
            "char_wh_bn_fn" to "체중은 조금 많지만 건강한 편이에요. 꾸준한 운동으로 지금처럼 유지해 보세요.",
            "char_wh_bn_fh" to "체중은 많고 체지방도 살짝 높아요. 규칙적으로 운동하면 건강하게 조절할 수 있어요.",
            "char_wh_bh_fl" to "체중은 많고 BMI도 높지만 체지방은 적어요. 근육질 체형일 가능성이 있어요!",
            "char_wh_bh_fn" to "체중과 BMI가 높아요. 식습관 관리와 함께 운동을 하면 더 좋아질 거예요.",
            "char_wh_bh_fh" to "체중, 체지방, BMI 모두 높은 편이에요. 생활 습관을 조절해서 건강을 지켜주세요!"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)

        tvToday      = findViewById(R.id.tv_today)
        tvHealthMent = findViewById(R.id.tv_health_ment)
        imgCharacter = findViewById(R.id.img_character)
        btnMyPage    = findViewById(R.id.btn_mypage)
        btnTodayMenu = findViewById(R.id.btn_today_menu)
        btnHealthInfo= findViewById(R.id.btn_health_info) // ← 추가
// MainActivity.kt (onCreate 안에 추가)
        val tabSaved = findViewById<TextView>(R.id.tab_saved_menu)
        tabSaved.setOnClickListener {
            // (선택) 로그인 이메일 전달하고 싶으면 아래 주석 해제
             val email = FirebaseAuth.getInstance().currentUser?.email
            startActivity(
                Intent(this, FavMenuActivity::class.java)
                // .apply { email?.let { putExtra("user_email", it) } }
            )
        }

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

        // ✅ 건강 정보: 바텀시트 팝업 띄우기
        btnHealthInfo.setOnClickListener {
            // 중복 표시 방지: 같은 태그로 이미 떠 있으면 무시
            val tag = "health_analysis_bottom_sheet"
            val fm = supportFragmentManager
            if (fm.findFragmentByTag(tag) == null) {
                HealthAnalysisBottomSheet().show(fm, tag)
            }
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

    // ── Zone → 코드 변환 헬퍼 ──
    private fun wCode(z: HealthAnalyzer.Zone) = when (z) {
        HealthAnalyzer.Zone.LOW    -> "wl"
        HealthAnalyzer.Zone.NORMAL -> "wn"
        HealthAnalyzer.Zone.HIGH   -> "wh"
    }
    private fun bCode(z: HealthAnalyzer.Zone) = when (z) {
        HealthAnalyzer.Zone.LOW    -> "bl"
        HealthAnalyzer.Zone.NORMAL -> "bn"
        HealthAnalyzer.Zone.HIGH   -> "bh"
    }
    private fun fCode(z: HealthAnalyzer.Zone) = when (z) {
        HealthAnalyzer.Zone.LOW    -> "fl"
        HealthAnalyzer.Zone.NORMAL -> "fn"
        HealthAnalyzer.Zone.HIGH   -> "fh"
    }

    /** 결과를 메인 화면에 반영 (멘트/캐릭터 교체: 27 분류) */
    private fun applyResultToMain(r: HealthAnalyzer.AnalysisResult) {
        // (1) 3축(zone) → 코드 조합
        val w = wCode(r.weightGauge.zone) // wl / wn / wh
        val b = bCode(r.bmiGauge.zone)    // bl / bn / bh
        val f = fCode(r.fatGauge.zone)    // fl / fn / fh
        val key = "char_${w}_${b}_${f}"   // 예: char_wn_bn_fn

        // (2) 이미지 리소스 적용 (없으면 기존 3분류 폴백)
        val resId = resources.getIdentifier(key, "drawable", packageName)
        if (resId != 0) {
            imgCharacter.setImageResource(resId)
        } else {
            val hasHigh = (r.bmiGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                    (r.fatGauge.zone == HealthAnalyzer.Zone.HIGH) ||
                    (r.weightGauge.zone == HealthAnalyzer.Zone.HIGH)
            val allNormal = (r.bmiGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                    (r.fatGauge.zone == HealthAnalyzer.Zone.NORMAL) &&
                    (r.weightGauge.zone == HealthAnalyzer.Zone.NORMAL)
            val fallbackRes = when {
                hasHigh   -> R.drawable.char_dumbbell_broccoli
                allNormal -> R.drawable.char_water_broccoli
                else      -> R.drawable.char_sad_leaf
            }
            imgCharacter.setImageResource(fallbackRes)
        }

        // (3) 멘트 적용 (없으면 분석 메시지로 폴백)
        val msg = charMent[key] ?: r.messageBody
        tvHealthMent.text = msg
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
