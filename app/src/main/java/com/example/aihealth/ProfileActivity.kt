package com.example.aihealth

/*
 * ProfileActivity.kt (최종본: 저장 성공 시 LoadingActivity 이동 포함)
 *
 * 1) 키/몸무게/나이 선택 및 Firestore MERGE 저장
 * 2) 알레르기 미리보기 (tv_allergy_desc ↔ layout_allergy_preview 전환)
 * 3) 질병/질환 팝업(선택사항) - 팝업 입력 시 즉시 목록 반영(Optimistic UI)
 * 4) 저장 버튼 활성화/안내:
 *    - 키, 몸무게, 생년월일(나이), 알레르기 정보가 모두 설정되어야 버튼이 진하게 보임
 *    - 미완료 상태에서 클릭 시 부족 항목을 토스트로 안내
 * 5) ✅ 저장 성공 시 LoadingActivity로 이동(5초 후 MainActivity로 전환)
 */

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.TimeZone

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvHeight: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvAge: TextView
    private lateinit var btnConfirm: Button

    // 알레르기 UI
    private lateinit var tvAllergyDesc: TextView
    private lateinit var layoutAllergyPreview: LinearLayout
    private lateinit var ivA1: ImageView
    private lateinit var ivA2: ImageView
    private lateinit var ivA3: ImageView
    private lateinit var ivPlus: ImageView

    // 질병/질환 카드
    private lateinit var tvDiseaseHint: TextView
    private lateinit var btnDiseaseEdit: ImageView

    private lateinit var db: FirebaseFirestore
    private var email: String? = null

    // 생년월일
    private var birthY: Int? = null
    private var birthM: Int? = null
    private var birthD: Int? = null

    // 알레르기 설정 여부 (Firestore 값 기준, "none" 단독도 설정으로 인정)
    private var hasAllergySelected: Boolean = false

    // 알레르기 키 → 아이콘 리소스 매핑 (AllergActivity와 동일)
    private val allergyIconMap = mapOf(
        "egg" to R.drawable.ic_egg,
        "milk" to R.drawable.ic_milk,
        "buckwheat" to R.drawable.ic_buckwheat,
        "peanut" to R.drawable.ic_peanut,
        "soybean" to R.drawable.ic_soybean,
        "wheat" to R.drawable.ic_wheat,
        "mackerel" to R.drawable.ic_fish,
        "crab" to R.drawable.ic_crab,
        "shrimp" to R.drawable.ic_shrimp,
        "pork" to R.drawable.ic_pork,
        "peach" to R.drawable.ic_peach,
        "tomato" to R.drawable.ic_tomato,
        "sulfites" to R.drawable.ic_sulfurousacid,
        "walnut" to R.drawable.ic_walnut,
        "chicken" to R.drawable.ic_chickin, // 리소스명 주의
        "beef" to R.drawable.ic_beef,
        "squid" to R.drawable.ic_squid,
        "shellfish" to R.drawable.ic_clam,
        "pine_nut" to R.drawable.ic_pinenut,
        "none" to android.R.drawable.ic_menu_close_clear_cancel
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 이메일
        email = intent.getStringExtra("user_email")
        if (email.isNullOrBlank()) {
            toast("사용자 식별 정보가 없습니다.")
            finish(); return
        }

        // 바인딩
        tvHeight = findViewById(R.id.tv_height_value)
        tvWeight = findViewById(R.id.tv_weight_value)
        tvAge    = findViewById(R.id.tv_age_value)
        btnConfirm = findViewById(R.id.btn_profile_confirm)

        tvAllergyDesc = findViewById(R.id.tv_allergy_desc)
        layoutAllergyPreview = findViewById(R.id.layout_allergy_preview)
        ivA1 = findViewById(R.id.iv_allergy_1)
        ivA2 = findViewById(R.id.iv_allergy_2)
        ivA3 = findViewById(R.id.iv_allergy_3)
        ivPlus = findViewById(R.id.iv_allergy_plus)

        tvDiseaseHint = findViewById(R.id.tv_disease_hint)
        btnDiseaseEdit = findViewById(R.id.btn_disease_edit)

        // 클릭 리스너
        tvAllergyDesc.setOnClickListener { openAllergyPicker() }
        ivPlus.setOnClickListener { openAllergyPicker() }
        btnDiseaseEdit.setOnClickListener { showDiseaseDialog() }

        // Firebase
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // 키 선택
        tvHeight.setOnClickListener {
            showIntPickerDialog(
                title = "키 선택 (cm)",
                min = 100, max = 220,
                current = tvHeight.text.toString().toIntOrNull() ?: 170
            ) { picked ->
                tvHeight.text = picked.toString()
                refreshConfirmButtonStyle()
            }
        }

        // 몸무게 선택
        tvWeight.setOnClickListener {
            showWeightPicker { picked ->
                tvWeight.text = picked
                refreshConfirmButtonStyle()
            }
        }

        // 만나이 표시
        tvAge.setOnClickListener { showBirthDatePicker() }

        // 저장(프로필)
        btnConfirm.setOnClickListener {
            if (!isFormValid()) {
                showMissingFieldsToast()
                return@setOnClickListener
            }

            val hText = tvHeight.text?.toString()?.trim().orEmpty()
            val wText = tvWeight.text?.toString()?.trim().orEmpty()

            val height = hText.toIntOrNull()
            val weight = wText.toDoubleOrNull()
            if (height == null || height !in 50..260) { toast("키 값이 올바르지 않습니다."); return@setOnClickListener }
            if (weight == null || weight <= 0.0)      { toast("몸무게 값이 올바르지 않습니다."); return@setOnClickListener }

            val (ageMan, ageKor) = computeAges(birthY!!, birthM!!, birthD!!)
            if (ageMan !in 1..120) { toast("나이(만)가 올바르지 않습니다."); return@setOnClickListener }

            val map = hashMapOf(
                "height_cm" to height,
                "weight_kg" to weight,
                "birth_yyyy" to birthY,
                "birth_mm"  to birthM,
                "birth_dd"  to birthD,
                "age_years" to ageKor,
                "age_man_years" to ageMan
            )

            db.collection("usercode").document(email!!)
                .set(map, SetOptions.merge())
                .addOnSuccessListener {
                    toast("프로필이 저장되었습니다.")
                    // ✅ 저장 성공 후 로딩 화면으로 이동 (로딩에서 5초 후 MainActivity)
                    startActivity(
                        Intent(this, LoadingActivity::class.java).apply {
                            putExtra("user_email", email)
                        }
                    )
                    finish()
                }
                .addOnFailureListener { e ->
                    toast("프로필 저장 실패: ${e.message}")
                }
        }

        // 초기 버튼 스타일
        refreshConfirmButtonStyle()
    }

    override fun onResume() {
        super.onResume()
        loadAllergiesPreview()
        loadDiseaseNotesIntoCard()
    }

    // ─────────── 알레르기 미리보기 ───────────
    private fun openAllergyPicker() {
        startActivity(Intent(this, AllergActivity::class.java).putExtra("user_email", email))
    }

    private fun loadAllergiesPreview() {
        val em = email ?: return
        db.collection("usercode").document(em)
            .get()
            .addOnSuccessListener { snap ->
                val list = (snap.get("allergies") as? List<*>)?.filterIsInstance<String>().orEmpty()

                hasAllergySelected = list.isNotEmpty() // "none" 단독 선택도 설정으로 인정
                refreshConfirmButtonStyle()

                if (!hasAllergySelected) {
                    tvAllergyDesc.visibility = View.VISIBLE
                    layoutAllergyPreview.visibility = View.GONE
                } else {
                    tvAllergyDesc.visibility = View.GONE
                    layoutAllergyPreview.visibility = View.VISIBLE

                    val targets = listOf(ivA1, ivA2, ivA3)
                    targets.forEach { it.setImageResource(android.R.color.transparent) }
                    list.take(3).forEachIndexed { idx, key ->
                        targets[idx].setImageResource(allergyIconMap[key] ?: android.R.color.transparent)
                    }
                    ivPlus.alpha = if (list.size > 3) 1.0f else 0.95f
                }
            }
            .addOnFailureListener {
                hasAllergySelected = false
                refreshConfirmButtonStyle()
                tvAllergyDesc.visibility = View.VISIBLE
                layoutAllergyPreview.visibility = View.GONE
            }
    }

    // ─────────── 질병/질환 카드 렌더링 (선택사항) ───────────
    private fun loadDiseaseNotesIntoCard() {
        val em = email ?: return
        db.collection("usercode").document(em)
            .get()
            .addOnSuccessListener { snap ->
                val notes = (snap.get("diseases") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (notes.isEmpty()) {
                    tvDiseaseHint.text = "가지고 계신 질병에 대해 작성해주세요."
                } else {
                    val built = buildString {
                        notes.forEachIndexed { i, s ->
                            if (i > 0) append("\n\n")
                            append("✓  ").append(s)
                        }
                    }
                    tvDiseaseHint.text = built
                }
            }
    }

    // ─────────── 질병/질환 팝업 ───────────
    private fun showDiseaseDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_disease, null)
        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        val btnClose = view.findViewById<ImageView>(R.id.btn_close)
        val scroll   = view.findViewById<ScrollView>(R.id.scroll_notes)
        val container = view.findViewById<LinearLayout>(R.id.container_notes)
        val et = view.findViewById<EditText>(R.id.et_note)
        val btnSave = view.findViewById<ImageButton>(R.id.btn_save).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dpInt(6f), dpInt(6f), dpInt(6f), dpInt(6f))
        }

        fun addNoteView(text: String) {
            val tv = TextView(this).apply {
                this.text = "✓  $text"
                setTextColor(getColorCompat(R.color.loginTextDark))
                textSize = 16f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpInt(8f) }
            tv.layoutParams = lp
            container.addView(tv)
        }

        fun refreshList() {
            val em = email ?: return
            db.collection("usercode").document(em)
                .get()
                .addOnSuccessListener { snap ->
                    val notes = (snap.get("diseases") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    container.removeAllViews()
                    if (notes.isEmpty()) {
                        container.addView(TextView(this).apply {
                            text = "아직 작성된 내용이 없습니다."
                            setTextColor(getColorCompat(android.R.color.darker_gray))
                            textSize = 14f
                        })
                    } else {
                        notes.forEach { s -> addNoteView(s) }
                    }
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
        }

        btnSave.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                toast("내용을 입력해 주세요.")
                return@setOnClickListener
            }
            val em = email ?: return@setOnClickListener

            // Optimistic UI
            addNoteView(text)
            et.setText("")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

            db.collection("usercode").document(em)
                .update("diseases", FieldValue.arrayUnion(text))
                .addOnSuccessListener {
                    toast("저장되었습니다.")
                    refreshList()
                    loadDiseaseNotesIntoCard()
                }
                .addOnFailureListener {
                    db.collection("usercode").document(em)
                        .set(mapOf("diseases" to listOf(text)), SetOptions.merge())
                        .addOnSuccessListener {
                            toast("저장되었습니다.")
                            refreshList()
                            loadDiseaseNotesIntoCard()
                        }
                        .addOnFailureListener { e ->
                            toast("저장 실패: ${e.message}")
                        }
                }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        refreshList()
        dialog.show()
    }

    // ─────────── 날짜 선택 & 나이 계산 ───────────
    private fun showBirthDatePicker() {
        val defY = birthY ?: 2000
        val defM0 = ((birthM ?: 1) - 1).coerceIn(0, 11)
        val defD = birthD ?: 1

        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val today = Calendar.getInstance(tz)
        val minCal = Calendar.getInstance(tz).apply { set(1900, Calendar.JANUARY, 1) }
        val maxCal = (today.clone() as Calendar)

        val dlg = DatePickerDialog(
            this,
            { _, y, m0, d ->
                birthY = y; birthM = m0 + 1; birthD = d
                val (ageMan, _) = computeAges(y, m0 + 1, d)
                tvAge.text = "만 ${ageMan}세"
                refreshConfirmButtonStyle()
            },
            defY, defM0, defD
        )
        dlg.datePicker.minDate = minCal.timeInMillis
        dlg.datePicker.maxDate = maxCal.timeInMillis
        dlg.show()
    }

    private fun computeAges(y: Int, m: Int, d: Int): Pair<Int, Int> {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        val now = Calendar.getInstance(tz)
        val ny = now.get(Calendar.YEAR)
        val nm = now.get(Calendar.MONTH) + 1
        val nd = now.get(Calendar.DAY_OF_MONTH)

        var man = ny - y
        if (nm < m || (nm == m && nd < d)) man -= 1
        if (man < 0) man = 0

        val korean = (ny - y) + 1
        return man to korean
    }

    // ─────────── 저장 버튼 활성/안내 유틸 ───────────
    private fun isFormValid(): Boolean {
        val hOk = !tvHeight.text.isNullOrBlank()
        val wOk = !tvWeight.text.isNullOrBlank()
        val ageOk = (birthY != null && birthM != null && birthD != null)
        val allergyOk = hasAllergySelected
        return hOk && wOk && ageOk && allergyOk
    }

    private fun refreshConfirmButtonStyle() {
        val valid = isFormValid()
        // 시각적으로만 활성/비활성처럼 표현
        btnConfirm.alpha = if (valid) 1.0f else 0.5f

        // 정말 물리적으로 비활성화하려면 아래 주석 해제
        // btnConfirm.isEnabled = valid
    }

    private fun showMissingFieldsToast() {
        val missing = mutableListOf<String>()
        if (tvHeight.text.isNullOrBlank()) missing += "키"
        if (tvWeight.text.isNullOrBlank()) missing += "몸무게"
        if (birthY == null || birthM == null || birthD == null) missing += "생년월일/나이"
        if (!hasAllergySelected) missing += "알레르기 정보"

        if (missing.isEmpty()) return
        val msg = "다음 항목을 완료해 주세요: " + missing.joinToString(", ")
        toast(msg)
    }

    // ─────────── 숫자 선택 다이얼로그 유틸 ───────────
    private fun showIntPickerDialog(
        title: String,
        min: Int,
        max: Int,
        current: Int,
        onPicked: (Int) -> Unit
    ) {
        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_number_picker, null)
        val picker = view.findViewById<android.widget.NumberPicker>(R.id.number_picker)
        val titleTv = view.findViewById<TextView>(R.id.tv_picker_title)
        titleTv.text = title

        picker.minValue = min
        picker.maxValue = max
        picker.wrapSelectorWheel = false
        picker.descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
        picker.value = current.coerceIn(min, max)

        dialog.setView(view)
            .setPositiveButton("확인") { d, _ -> onPicked(picker.value); d.dismiss() }
            .setNegativeButton("취소") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showWeightPicker(onPicked: (String) -> Unit) {
        val values = buildList {
            var v = 30.0
            while (v <= 200.0 + 1e-9) { add(String.format("%.1f", v)); v += 0.5 }
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_number_picker, null)
        val picker = view.findViewById<android.widget.NumberPicker>(R.id.number_picker)
        val titleTv = view.findViewById<TextView>(R.id.tv_picker_title)
        titleTv.text = "몸무게 선택 (kg)"

        picker.minValue = 0
        picker.maxValue = values.size - 1
        picker.displayedValues = values
        picker.wrapSelectorWheel = false
        picker.descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS

        val now = tvWeight.text.toString().toDoubleOrNull()
        val idx = if (now != null) (((now - 30.0) / 0.5).toInt()).coerceIn(0, values.lastIndex) else 20
        picker.value = idx

        dialog.setView(view)
            .setPositiveButton("확인") { d, _ -> onPicked(values[picker.value]); d.dismiss() }
            .setNegativeButton("취소") { d, _ -> d.dismiss() }
            .show()
    }

    // ─────────── 유틸 ───────────
    private fun dpInt(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun getColorCompat(resId: Int): Int =
        if (Build.VERSION.SDK_INT >= 23) getColor(resId) else @Suppress("DEPRECATION") resources.getColor(resId)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
