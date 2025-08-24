package com.example.aihealth

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.example.aihealth.util.AuthUtils

class HealthAnalysisBottomSheet : BottomSheetDialogFragment() {

    private lateinit var tvNoticeTitle: TextView
    private lateinit var tvNoticeDesc: TextView
    private lateinit var ivWeightMarker: ImageView
    private lateinit var ivFatMarker: ImageView
    private lateinit var ivBmiMarker: ImageView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setOnShowListener { d ->
            val bsDialog = d as BottomSheetDialog
            val bottomSheet =
                bsDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let { sheet ->
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                val behavior = BottomSheetBehavior.from(sheet).apply {
                    isFitToContents = false
                    expandedOffset = 0
                    skipCollapsed = true
                    isDraggable = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                    peekHeight = 0
                }
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottom: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                    override fun onSlide(bottom: View, slideOffset: Float) = Unit
                })
                sheet.requestLayout()
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.popup_health_analysis, container, false)

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_finish)?.setOnClickListener { dismiss() }

        tvNoticeTitle   = view.findViewById(R.id.tv_notice_title)
        tvNoticeDesc    = view.findViewById(R.id.tv_notice_desc)
        ivWeightMarker  = view.findViewById(R.id.iv_weight_marker)
        ivFatMarker     = view.findViewById(R.id.iv_fat_marker)
        ivBmiMarker     = view.findViewById(R.id.iv_bmi_marker)

        // 1) 호출 측에서 결과를 넘겨준 경우 그대로 사용
        val passed = arguments?.getSerializable("analysis_result") as? HealthAnalyzer.AnalysisResult
        if (passed != null) {
            applyResult(passed)
            return
        }

        // 2) 없으면 Firestore에서 읽어 분석
        FirebaseApp.initializeApp(requireContext())
        val email = AuthUtils.currentEmail(requireContext(), activity?.intent, true)
        if (email.isNullOrBlank()) {
            tvNoticeTitle.text = "알림"
            tvNoticeDesc.text = "사용자 정보를 찾을 수 없습니다."
            return
        }

        FirebaseFirestore.getInstance()
            .collection("usercode").document(email)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    tvNoticeTitle.text = "알림"
                    tvNoticeDesc.text = "프로필이 없습니다."
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
                    tvNoticeTitle.text = "알림"
                    tvNoticeDesc.text = "키/몸무게/나이 정보가 부족합니다."
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
                applyResult(result)
            }
            .addOnFailureListener { e ->
                tvNoticeTitle.text = "알림"
                tvNoticeDesc.text = "데이터 로드 실패: ${e.message}"
            }
    }

    private fun applyResult(r: HealthAnalyzer.AnalysisResult) {
        // 알림 카드 텍스트
        tvNoticeTitle.text = r.messageTitle
        tvNoticeDesc.text  = r.messageBody

        // 지시자 위치 반영 (0f..1f)
        setMarkerBias(ivWeightMarker, r.weightGauge.bias)
        setMarkerBias(ivFatMarker,    r.fatGauge.bias)
        setMarkerBias(ivBmiMarker,    r.bmiGauge.bias)
    }

    private fun setMarkerBias(marker: ImageView, bias: Float) {
        val lp = marker.layoutParams as ConstraintLayout.LayoutParams
        lp.horizontalBias = bias.coerceIn(0f, 1f)
        marker.layoutParams = lp
    }
}
