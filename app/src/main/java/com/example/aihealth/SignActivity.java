package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class SignActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private EditText etPasswordConfirm;
    private TextView tvPwWarning;
    private CheckBox cbTerms;
    private Button btnCheckDup;
    private Button btnSignUp;

    private FirebaseFirestore db;

    // 상태 플래그
    private boolean dupChecked = false; // 이메일 중복검사 "통과" 여부
    private boolean pwMatch    = false; // 비밀번호/확인 일치 여부

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        // View 바인딩
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        tvPwWarning       = findViewById(R.id.tv_pw_warning);  // 기본 GONE 권장
        cbTerms           = findViewById(R.id.cb_terms);
        btnCheckDup       = findViewById(R.id.btn_check_dup);
        btnSignUp         = findViewById(R.id.btn_sign_up);

        // Firebase
        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        // 초기 중복확인 버튼 상태
        setDupBtnState(true, "중복 확인");

        // 이메일이 변경되면: 중복검사 초기화 + 버튼 재활성화 + 가입 버튼 재평가
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                dupChecked = false;
                setDupBtnState(true, "중복 확인"); // 다시 확인 가능
                updateSignUpEnabled();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 비밀번호/확인: 실시간 일치·정책 메시지 & 가입 버튼 재평가
        TextWatcher pwWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderPasswordMessages();
                updateSignUpEnabled();
            }
            @Override public void afterTextChanged(Editable s) {
                renderPasswordMessages();
                updateSignUpEnabled();
            }
        };
        etPassword.addTextChangedListener(pwWatcher);
        etPasswordConfirm.addTextChangedListener(pwWatcher);

        // 약관 체크 변화 시 가입 버튼 재평가
        cbTerms.setOnCheckedChangeListener((buttonView, isChecked) -> updateSignUpEnabled());

        // 중복 확인 버튼
        btnCheckDup.setOnClickListener(v -> checkEmailDuplicate());

        // 가입하기 버튼
        btnSignUp.setOnClickListener(v -> saveUserIfValid());

        // 초기 가입 버튼 상태
        updateSignUpEnabled();
    }

    // ====== UI 상태 헬퍼 ======
    private void setDupBtnState(boolean enabled, String text) {
        btnCheckDup.setEnabled(enabled);
        btnCheckDup.setAlpha(enabled ? 1f : 0.5f);
        btnCheckDup.setText(text);
    }

    private void updateSignUpEnabled() {
        final String email = safeTrim(etEmail);
        final String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        boolean emailOk  = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean policyOk = (getPasswordPolicyError(password) == null);
        boolean termsOk  = cbTerms.isChecked();

        boolean enable = emailOk && policyOk && pwMatch && dupChecked && termsOk;

        btnSignUp.setEnabled(enable);
        btnSignUp.setAlpha(enable ? 1f : 0.5f);
    }

    // ====== 비밀번호 메시지 렌더링 ======
    private void renderPasswordMessages() {
        String p1 = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String p2 = etPasswordConfirm.getText() == null ? "" : etPasswordConfirm.getText().toString();

        // 일치 여부
        pwMatch = !TextUtils.isEmpty(p1) && p1.equals(p2);

        // 정책 위반 상세 메시지
        String policyMsg = getPasswordPolicyError(p1); // null이면 정책 충족

        // 우선순위: 1) 불일치 2) 정책 위반 3) 숨김
        if (!TextUtils.isEmpty(p1) && !TextUtils.isEmpty(p2) && !pwMatch) {
            tvPwWarning.setText("비밀번호가 일치하지 않습니다.");
            tvPwWarning.setVisibility(View.VISIBLE);
        } else if (!TextUtils.isEmpty(p1) && policyMsg != null) {
            tvPwWarning.setText(policyMsg);
            tvPwWarning.setVisibility(View.VISIBLE);
        } else {
            tvPwWarning.setVisibility(View.GONE);
        }
    }

    // 정책 위반 시 구체 메시지, 충족하면 null
    private String getPasswordPolicyError(@NonNull String pwd) {
        if (pwd.length() < 8)  return "비밀번호는 8자 이상이어야 합니다.";
        if (pwd.length() > 64) return "비밀번호는 64자 이하이어야 합니다.";

        boolean hasLetter = false, hasDigit = false, hasSpecial = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (isSpecial(c)) hasSpecial = true;
        }
        if (!hasLetter)  return "영문자를 포함해야 합니다.";
        if (!hasDigit)   return "숫자를 포함해야 합니다.";
        if (!hasSpecial) return "특수문자를 포함해야 합니다.";
        return null;
    }

    private boolean isSpecial(char c) {
        String specials = "!@#$%^&*()_+-=[]{}|;:'\",.<>/?`~\\";
        return specials.indexOf(c) >= 0;
    }

    // ====== 이메일 중복 확인 ======
    private void checkEmailDuplicate() {
        final String email = safeTrim(etEmail);

        // 진행 중 잠금
        setDupBtnState(false, "중복 확인 중...");

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("올바른 이메일을 입력하세요.");
            dupChecked = false;
            setDupBtnState(true, "중복 확인"); // 재시도 가능
            updateSignUpEnabled();
            return;
        }

        db.collection("usercode")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        // 중복 존재 → 통과 실패, 재확인 가능해야 하므로 활성화
                        dupChecked = false;
                        toast("이미 사용 중인 이메일입니다.");
                        setDupBtnState(true, "중복 확인");
                    } else {
                        // 중복 없음 → 통과, 실수 클릭 방지 위해 비활성화 + 텍스트 변경
                        dupChecked = true;
                        toast("사용 가능한 이메일입니다.");
                        setDupBtnState(false, "중복 확인 완료");
                    }
                    updateSignUpEnabled();
                })
                .addOnFailureListener(e -> {
                    dupChecked = false;
                    toast("중복 확인 실패: " + e.getMessage());
                    setDupBtnState(true, "중복 확인"); // 실패 시 재시도 허용
                    updateSignUpEnabled();
                });
    }

    // ====== 가입 처리 ======
    private void saveUserIfValid() {
        final String email = safeTrim(etEmail);
        final String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        String policyMsg = getPasswordPolicyError(password);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("올바른 이메일을 입력하세요."); return;
        }
        if (policyMsg != null) {
            toast("비밀번호 조건 미충족: " + policyMsg); return;
        }
        if (!pwMatch) {
            toast("비밀번호가 서로 일치하지 않습니다."); return;
        }
        if (!dupChecked) {
            toast("이메일 중복 확인을 먼저 해주세요."); return;
        }
        if (!cbTerms.isChecked()) {
            toast("이용약관 및 개인정보에 동의해 주세요."); return;
        }

        // 대한민국(KST) 날짜
        TimeZone tz = TimeZone.getTimeZone("Asia/Seoul");
        Calendar cal = Calendar.getInstance(tz);
        int yyyy = cal.get(Calendar.YEAR);
        int mm   = cal.get(Calendar.MONTH) + 1; // 0~11 → +1
        int dd   = cal.get(Calendar.DAY_OF_MONTH);

        // BCrypt 해싱
        String passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("password_hash", passwordHash);
        data.put("join_yyyy", yyyy);
        data.put("join_mm", mm);
        data.put("join_dd", dd);

        db.collection("usercode")
                .document(email) // 문서ID = 이메일
                .set(data)
                .addOnSuccessListener(unused -> {
                    toast("가입 정보가 저장되었습니다.");
                    // ▶ 프로필 화면으로 이동: 동일 문서에 이어서 저장할 수 있도록 이메일 전달
                    Intent it = new Intent(SignActivity.this, ProfileActivity.class);
                    it.putExtra("user_email", email);
                    startActivity(it);
                    finish();
                })
                .addOnFailureListener(e -> toast("저장 실패: " + e.getMessage()));
    }

    // ====== 공용 ======
    private String safeTrim(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
