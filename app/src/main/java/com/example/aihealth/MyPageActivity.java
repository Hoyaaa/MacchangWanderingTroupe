package com.example.aihealth;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyPageActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userEmail;

    // --- Views (스펙의 ID 전부) ---
    private ImageView btnBack, ivProvider, btnAccountSettings;
    private ImageView btnEditBody, btnEditDisease;
    private TextView tvEmail, tvHeightVal, tvWeightVal, tvAgeVal, tvDiseaseList;
    private ImageView ivWeightMarker, ivFatMarker, ivBmiMarker;
    private View barWeight, barFat, barBmi;

    private final List<View> editableIcons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);

        // [3-3][3-4] Firebase
        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        // [3-5] View 초기화
        btnBack            = findViewById(R.id.btn_back);
        ivProvider         = findViewById(R.id.iv_provider);
        tvEmail            = findViewById(R.id.tv_email);
        btnAccountSettings = findViewById(R.id.btn_account_settings);
        btnEditBody        = findViewById(R.id.btn_edit_body);
        btnEditDisease     = findViewById(R.id.btn_edit_disease);
        tvHeightVal        = findViewById(R.id.tv_height_val);
        tvWeightVal        = findViewById(R.id.tv_weight_val);
        tvAgeVal           = findViewById(R.id.tv_age_val);
        tvDiseaseList      = findViewById(R.id.tv_disease_list);
        ivWeightMarker     = findViewById(R.id.iv_weight_marker);
        ivFatMarker        = findViewById(R.id.iv_fat_marker);
        ivBmiMarker        = findViewById(R.id.iv_bmi_marker);
        barWeight          = findViewById(R.id.bar_weight);
        barFat             = findViewById(R.id.bar_fat);
        barBmi             = findViewById(R.id.bar_bmi);

        editableIcons.add(btnEditBody);
        editableIcons.add(btnEditDisease);

        // [3-6] 사용자 이메일 판단: Intent → FirebaseAuth
        userEmail = getIntent() != null ? getIntent().getStringExtra("user_email") : null;
        if (TextUtils.isEmpty(userEmail) && FirebaseAuth.getInstance().getCurrentUser() != null) {
            userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }

        // [3-7] 헤더
        tvEmail.setText(TextUtils.isEmpty(userEmail) ? "-" : userEmail);

        // [3-8] 클릭 리스너
        btnBack.setOnClickListener(v -> finish());
        btnAccountSettings.setOnClickListener(v -> toast("계정 설정 화면은 준비 중입니다."));
        btnEditBody.setOnClickListener(v -> showEditBodyDialog());
        btnEditDisease.setOnClickListener(v -> {
            // 질병 편집은 ProfileActivity로 위임(스펙 6-3 A안)
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("user_email", userEmail);
            startActivity(i);
        });

        // [3-9] 초기 로드
        if (TextUtils.isEmpty(userEmail)) {
            // 종료하지 않음: 화면 유지 + 편집 비활성화
            setEditorsEnabled(false);
            toast("사용자 정보가 없습니다. 로그인 후 다시 시도하세요.");
        } else {
            loadAllData();
        }
    }

    // [4] 복귀 시 최신화
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(userEmail)) loadAllData();
    }

    // [5] Firestore → UI 바인딩 → 분석 호출
    private void loadAllData() {
        if (TextUtils.isEmpty(userEmail)) return;

        setEditorsEnabled(false);
        db.collection("usercode").document(userEmail).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) {
                        toast("프로필이 없습니다.");
                        setEditorsEnabled(true);
                        return;
                    }
                    bindAndAnalyze(snap);
                    setEditorsEnabled(true);
                })
                .addOnFailureListener(e -> {
                    toast("데이터 불러오기 실패: " + e.getMessage());
                    setEditorsEnabled(true);
                });
    }

    private void bindAndAnalyze(DocumentSnapshot snap) {
        // [5-3] 필드 파싱 (null-safe)
        Integer heightCm = snap.getLong("height_cm") != null
                ? snap.getLong("height_cm").intValue() : null;

        Double weightKg = snap.getDouble("weight_kg");
        if (weightKg == null && snap.getLong("weight_kg") != null) {
            weightKg = snap.getLong("weight_kg").doubleValue();
        }

        Integer age = snap.getLong("age_man_years") != null
                ? snap.getLong("age_man_years").intValue()
                : (snap.getLong("age_years") != null ? snap.getLong("age_years").intValue() : null);

        Boolean isMale = snap.getBoolean("is_male");
        if (isMale == null) isMale = true; // 기본값

        // [5-6] 질병 리스트
        String diseaseText = "없음";
        try {
            Object obj = snap.get("diseases");
            if (obj instanceof List && !((List<?>) obj).isEmpty()) {
                diseaseText = android.text.TextUtils.join(", ", (List<?>) obj);
            }
        } catch (Exception ignore) {}

        // [5-4] 필수값 검증
        if (heightCm == null || weightKg == null || age == null) {
            tvHeightVal.setText("-");
            tvWeightVal.setText("-");
            tvAgeVal.setText("-");
            tvDiseaseList.setText(diseaseText);
            toast("키/몸무게/나이 정보가 부족합니다.");
            return;
        }

        // [5-5] 텍스트 표기
        tvHeightVal.setText(String.format(Locale.KOREA, "%dcm", heightCm));
        tvWeightVal.setText(String.format(Locale.KOREA, "%.1fkg", weightKg));
        tvAgeVal.setText(String.format(Locale.KOREA, "%d(만)세", age));
        tvDiseaseList.setText(diseaseText);

        // [5-7] HealthAnalyzer 호출 (Java → Kotlin object)
        try {
            HealthAnalyzer.AnalysisInput input =
                    new HealthAnalyzer.AnalysisInput(heightCm, weightKg.floatValue(), age, isMale);
            HealthAnalyzer.AnalysisResult result =
                    HealthAnalyzer.INSTANCE.analyze(input);

            // [5-8][5-9] 마커 위치 반영 (instanceof 방어)
            setMarkerBias(ivWeightMarker, result.getWeightGauge().getBias());
            setMarkerBias(ivFatMarker,    result.getFatGauge().getBias());
            setMarkerBias(ivBmiMarker,    result.getBmiGauge().getBias());
        } catch (Throwable t) {
            // 분석 중 예외가 나도 액티비티 종료하지 않음
            toast("분석 처리 오류: " + t.getMessage());
        }
    }

    // [7-1] 마커 bias 적용 (ConstraintLayout인 경우에만)
    private void setMarkerBias(ImageView marker, float bias) {
        if (marker == null || marker.getLayoutParams() == null) return;

        float clamped = Math.max(0f, Math.min(1f, bias));
        ViewGroup.LayoutParams p = marker.getLayoutParams();
        if (p instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) p;
            lp.horizontalBias = clamped;
            marker.setLayoutParams(lp);
        } // 부모가 ConstraintLayout이 아니면 조용히 스킵(크래시 방지)
    }

    // [6-1] 신체 정보 편집 메인 다이얼로그
    private void showEditBodyDialog() {
        String[] items = {"키 변경", "몸무게 변경", "생년월일 변경"};
        new AlertDialog.Builder(this)
                .setTitle("신체 정보 수정")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showHeightPicker(); break;
                        case 1: showWeightPicker(); break;
                        case 2: showBirthPicker();  break;
                    }
                })
                .show();
    }

    // [6-1] 키 변경
    private void showHeightPicker() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null);
        NumberPicker picker = view.findViewById(R.id.number_picker);
        picker.setMinValue(100);
        picker.setMaxValue(220);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        int cur = parseIntOrZero(tvHeightVal.getText().toString().replace("cm", ""));
        if (cur >= 100 && cur <= 220) picker.setValue(cur);

        new AlertDialog.Builder(this)
                .setTitle("키 변경")
                .setView(view)
                .setPositiveButton("확인", (dlg, w) -> {
                    int newVal = picker.getValue();
                    db.collection("usercode").document(userEmail)
                            .update("height_cm", newVal)
                            .addOnSuccessListener(x -> { toast("저장되었습니다."); loadAllData(); })
                            .addOnFailureListener(e -> toast("저장 실패: " + e.getMessage()));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // [6-1] 몸무게 변경 (30.0~200.0, 0.5 step)
    private void showWeightPicker() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null);
        NumberPicker picker = view.findViewById(R.id.number_picker);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        String[] values = new String[341]; // 30.0 ~ 200.0
        for (int i = 0; i < values.length; i++) {
            values[i] = String.format(Locale.KOREA, "%.1f", 30.0 + i * 0.5);
        }
        picker.setMinValue(0);
        picker.setMaxValue(values.length - 1);
        picker.setDisplayedValues(values);

        double cur = parseDoubleOrZero(tvWeightVal.getText().toString().replace("kg", ""));
        int idx = (int) Math.round((cur - 30.0) / 0.5);
        if (idx >= 0 && idx < values.length) picker.setValue(idx);

        new AlertDialog.Builder(this)
                .setTitle("몸무게 변경")
                .setView(view)
                .setPositiveButton("확인", (dlg, w) -> {
                    double newVal = Double.parseDouble(values[picker.getValue()]);
                    db.collection("usercode").document(userEmail)
                            .update("weight_kg", newVal)
                            .addOnSuccessListener(x -> { toast("저장되었습니다."); loadAllData(); })
                            .addOnFailureListener(e -> toast("저장 실패: " + e.getMessage()));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // [6-1] 생년월일 변경 → 만나이 재계산 후 merge 저장
    private void showBirthPicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(
                this,
                (DatePicker v, int y, int m, int d) -> {
                    int manAge = computeManAge(y, m + 1, d);
                    Map<String, Object> map = new HashMap<>();
                    map.put("birth_yyyy", y);
                    map.put("birth_mm", m + 1);
                    map.put("birth_dd", d);
                    map.put("age_years", cal.get(Calendar.YEAR) - y);
                    map.put("age_man_years", manAge);

                    db.collection("usercode").document(userEmail)
                            .set(map, SetOptions.merge())
                            .addOnSuccessListener(x -> { toast("저장되었습니다."); loadAllData(); })
                            .addOnFailureListener(e -> toast("저장 실패: " + e.getMessage()));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dpd.show();
    }

    // [7-4] 만나이 계산
    private int computeManAge(int yyyy, int mm, int dd) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - yyyy;
        int tm = today.get(Calendar.MONTH) + 1;
        int td = today.get(Calendar.DAY_OF_MONTH);
        if (tm < mm || (tm == mm && td < dd)) age--;
        return Math.max(age, 0);
    }

    // 유틸
    private int parseIntOrZero(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }

    private double parseDoubleOrZero(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0.0; }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void setEditorsEnabled(boolean enabled) {
        for (View v : editableIcons) if (v != null) v.setEnabled(enabled);
    }
}
