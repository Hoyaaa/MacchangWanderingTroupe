package com.example.aihealth

/*
 * AllergActivity.kt (최종본)
 *
 * 기능
 * 1) 아이콘 다중 선택 토글
 * 2) "20. 없음" 추가: 선택 시 다른 항목 모두 해제되고 '없음'만 선택됨. 일반 항목을 선택하면 '없음'은 자동 해제됨.
 * 3) 저장 시 usercode/{email}.allergies 배열로 MERGE 저장 후 finish()로 ProfileActivity 복귀
 * 4) 재진입 시 Firestore 값 복원
 *
 * 주의
 * - activity_userallergy.xml 에 id=btn_none(20. 없음) 가 있으며, android:src="@android:drawable/ic_menu_close_clear_cancel" 이어야 함.
 * - AndroidManifest.xml 에 <activity android:name=".AllergActivity" /> 등록 필요.
 */

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AllergActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var email: String? = null

    // 현재 선택된 키(중복X, 순서유지)
    private val selectedAllergies = linkedSetOf<String>()

    // view id ↔ 데이터 키 ↔ 아이콘 리소스 매핑
    private val items by lazy {
        listOf(
            Item(R.id.btn_egg,        "egg",        R.drawable.ic_egg),
            Item(R.id.btn_milk,       "milk",       R.drawable.ic_milk),
            Item(R.id.btn_buckwheat,  "buckwheat",  R.drawable.ic_buckwheat),
            Item(R.id.btn_peanut,     "peanut",     R.drawable.ic_peanut),
            Item(R.id.btn_soybean,    "soybean",    R.drawable.ic_soybean),
            Item(R.id.btn_wheat,      "wheat",      R.drawable.ic_wheat),
            Item(R.id.btn_mackerel,   "mackerel",   R.drawable.ic_fish),
            Item(R.id.btn_crab,       "crab",       R.drawable.ic_crab),
            Item(R.id.btn_shrimp,     "shrimp",     R.drawable.ic_shrimp),
            Item(R.id.btn_pork,       "pork",       R.drawable.ic_pork),
            Item(R.id.btn_peach,      "peach",      R.drawable.ic_peach),
            Item(R.id.btn_tomato,     "tomato",     R.drawable.ic_tomato),
            Item(R.id.btn_sulfites,   "sulfites",   R.drawable.ic_sulfurousacid),
            Item(R.id.btn_walnut,     "walnut",     R.drawable.ic_walnut),
            Item(R.id.btn_chicken,    "chicken",    R.drawable.ic_chickin), // 리소스명 주의
            Item(R.id.btn_beef,       "beef",       R.drawable.ic_beef),
            Item(R.id.btn_squid,      "squid",      R.drawable.ic_squid),
            Item(R.id.btn_shellfish,  "shellfish",  R.drawable.ic_clam),
            Item(R.id.btn_pine_nut,   "pine_nut",   R.drawable.ic_pinenut),
            // 20. 없음 (시스템 내장 아이콘 사용)
            Item(R.id.btn_none,       "none",       android.R.drawable.ic_menu_close_clear_cancel)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userallergy)

        // 사용자 식별(이메일) 필수
        email = intent.getStringExtra("user_email")
        if (email.isNullOrBlank()) {
            toast("사용자 정보가 없습니다. 이전 화면에서 다시 시도해 주세요.")
            finish()
            return
        }

        // Firebase
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // 버튼 초기화 및 리스너
        items.forEach { item ->
            val btn = findViewById<ImageButton>(item.viewId)
            btn.setImageResource(item.iconRes)
            applySelectedVisual(btn, false)
            btn.setOnClickListener { toggleItem(item, btn) }
        }

        // 복원
        restoreSelectionFromFirestore()

        // 저장
        findViewById<Button>(R.id.save_button).setOnClickListener { onClickSave() }
    }

    /** 토글 로직 ('none' 특수 처리 포함) */
    private fun toggleItem(item: Item, btn: ImageButton) {
        if (item.key == "none") {
            // '없음' 토글
            val wasSelected = selectedAllergies.contains("none")
            if (wasSelected) {
                selectedAllergies.remove("none")
                applySelectedVisual(btn, false)
            } else {
                // 다른 모든 항목 해제 + '없음'만 선택
                selectedAllergies.clear()
                items.forEach { it2 ->
                    val b = findViewById<ImageButton>(it2.viewId)
                    applySelectedVisual(b, it2.key == "none")
                }
                selectedAllergies.add("none")
            }
            return
        }

        // 일반 항목 토글
        if (selectedAllergies.remove(item.key)) {
            applySelectedVisual(btn, false)
        } else {
            selectedAllergies.add(item.key)
            applySelectedVisual(btn, true)
        }

        // 일반 항목이 하나라도 선택되면 '없음'은 자동 해제
        if (selectedAllergies.remove("none")) {
            val noneBtn = findViewById<ImageButton>(R.id.btn_none)
            applySelectedVisual(noneBtn, false)
        }
    }

    /** 선택/비선택 시각 효과 */
    private fun applySelectedVisual(btn: ImageButton, selected: Boolean) {
        val corner = dp(16f)
        val stroke = dp(2f).toInt()
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setStroke(stroke, Color.parseColor("#D2B0B0"))
            setColor(if (selected) Color.parseColor("#FFF6F6") else Color.TRANSPARENT)
        }
        btn.background = drawable
        btn.alpha = if (selected) 1.0f else 0.92f
    }

    /** 저장: MERGE 후 프로필로 복귀 */
    private fun onClickSave() {
        val em = email ?: return
        val data = mapOf("allergies" to selectedAllergies.toList())
        db.collection("usercode")
            .document(em)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                toast("알레르기 정보가 저장되었습니다.")
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                toast("저장 실패: ${e.message}")
            }
    }

    /** Firestore 값 복원 (none 단독 처리) */
    private fun restoreSelectionFromFirestore() {
        val em = email ?: return
        db.collection("usercode")
            .document(em)
            .get()
            .addOnSuccessListener { snap ->
                val saved = (snap.get("allergies") as? List<*>)?.filterIsInstance<String>().orEmpty()
                selectedAllergies.clear()

                val onlyNone = saved.contains("none") && saved.size == 1
                if (onlyNone) {
                    selectedAllergies.add("none")
                } else {
                    selectedAllergies.addAll(saved.filter { it != "none" })
                }

                items.forEach { item ->
                    val btn = findViewById<ImageButton>(item.viewId)
                    val sel = if (onlyNone) item.key == "none" else selectedAllergies.contains(item.key)
                    applySelectedVisual(btn, sel)
                }
            }
            .addOnFailureListener {
                // 필요시 실패 처리 추가 가능
            }
    }

    /** dp → px */
    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private data class Item(
        val viewId: Int,
        val key: String,
        val iconRes: Int
    )
}
