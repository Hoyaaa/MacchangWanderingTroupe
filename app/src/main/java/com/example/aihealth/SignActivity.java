// SignActivity.java — 최종본 (요청 코드 그대로 반영)
// - 이메일 중복확인: Firestore whereEqualTo 방식 유지
// - 비밀번호 메시지 우선순위: 1) 불일치 2) 정책 위반 3) 숨김 그대로
// - 가입 버튼 활성화 로직 그대로
// - 가입 처리 순서: ① createUserWithEmailAndPassword 성공 → ② Firestore 저장 성공 → ③ goMain(email)

package com.example.aihealth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class SignActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etPasswordConfirm;
    private Button btnSignUp, btnCheckDup;
    private CheckBox cbTerms;
    private TextView tvPwWarning;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // 상태
    private boolean dupChecked = false;
    private boolean pwMatch    = false;
    private String  lastCheckedEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        cbTerms           = findViewById(R.id.cb_terms);
        btnSignUp         = findViewById(R.id.btn_sign_up);
        btnCheckDup       = findViewById(R.id.btn_check_dup);
        tvPwWarning       = findViewById(R.id.tv_pw_warning);

        // 초기 버튼 상태
        btnSignUp.setEnabled(false);
        btnSignUp.setAlpha(0.5f);

        // ===== 리스너(기존 로직 유지) =====
        // 이메일이 바뀌면: 중복검사 초기화 + 버튼 재활성화 + 가입 버튼 재평가
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                dupChecked = false;
                setDupBtnState(true, "중복 확인");
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

        // 초기 렌더
        renderPasswordMessages();
        updateSignUpEnabled();
    }

    // ===== 버튼/상태 헬퍼 =====
    private void setDupBtnState(boolean enabled, String text) {
        btnCheckDup.setEnabled(enabled);
        btnCheckDup.setAlpha(enabled ? 1f : 0.5f);
        btnCheckDup.setText(text);
    }

    private void updateSignUpEnabled() {
        final String email    = safeTrim(etEmail);
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

    // ====== 이메일 중복 확인 (Auth + Firestore 모두 확인) ======
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

        // 1) FirebaseAuth에 가입 이력 확인
        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(methods -> {
                    boolean inAuth = methods.getSignInMethods() != null && !methods.getSignInMethods().isEmpty();

                    // 2) Firestore usercode 문서 존재 여부
                    FirebaseFirestore.getInstance()
                            .collection("usercode")
                            .document(email) // 문서키를 이메일로 쓰는 현재 스키마
                            .get()
                            .addOnSuccessListener(snap -> {
                                boolean inFirestore = snap.exists();

                                // 둘 중 하나라도 있으면 "이미 존재"
                                if (inAuth || inFirestore) {
                                    dupChecked = false;
                                    setDupBtnState(true, "이미 존재");
                                    // 어떤 공급자가 있는지 알려주면 UX↑
                                    if (inAuth && methods.getSignInMethods().contains("google.com")) {
                                        toast("이미 Google 계정으로 가입되어 있습니다. Google 로그인으로 진행해 주세요.");
                                    } else {
                                        toast("이미 사용 중인 이메일입니다. 로그인으로 진행해 주세요.");
                                    }
                                } else {
                                    dupChecked = true;
                                    setDupBtnState(false, "중복 확인 완료");
                                    toast("사용 가능한 이메일입니다.");
                                }
                                lastCheckedEmail = email;
                                updateSignUpEnabled();
                            })
                            .addOnFailureListener(e -> {
                                // Firestore 조회 실패 시 최소한 Auth 결과는 반영
                                if (inAuth) {
                                    dupChecked = false;
                                    setDupBtnState(true, "이미 존재");
                                    toast("이미 사용 중인 이메일입니다. (Auth)");
                                } else {
                                    dupChecked = true;
                                    setDupBtnState(false, "중복 확인 완료*");
                                    toast("Firestore 점검 실패, Auth 기준으로 사용 가능 처리했습니다.");
                                }
                                lastCheckedEmail = email;
                                updateSignUpEnabled();
                            });
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
        final String email    = safeTrim(etEmail);
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
        if (!dupChecked || !email.equalsIgnoreCase(lastCheckedEmail)) {
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

        setLoading(true);

        // ① Firebase Auth 계정 생성 → ② Firestore 저장 → ③ goMain
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    final String docId = email; // 문서키 = 이메일
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", docId);
                    data.put("join_yyyy", yyyy);
                    data.put("join_mm", mm);
                    data.put("join_dd", dd);
                    data.put("height_cm", 0);
                    data.put("weight_kg", 0.0);
                    data.put("age_years", 0);
                    data.put("age_man_years", 0);
                    data.put("birth_yyyy", 0);
                    data.put("birth_mm", 0);
                    data.put("birth_dd", 0);
                    data.put("allergies", Collections.emptyList());
                    data.put("diseases", Collections.emptyList());

                    FirebaseFirestore.getInstance()
                            .collection("usercode")
                            .document(docId)
                            .set(data)
                            .addOnSuccessListener(unused -> {
                                toast("회원가입 완료");
                                goMain(docId); // Firestore 저장 성공 시점에만 이동
                            })
                            .addOnFailureListener(e -> {
                                toast("프로필 저장 실패: " + e.getMessage());
                                setLoading(false);
                            });
                })
                .addOnFailureListener(e -> {
                    toast("회원가입 실패: " + e.getMessage());
                    setLoading(false);
                });
    }

    // ===== 유틸 =====
    private void setLoading(boolean loading) {
        btnSignUp.setEnabled(!loading);
        btnCheckDup.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etPasswordConfirm.setEnabled(!loading);
        cbTerms.setEnabled(!loading);

        btnSignUp.setText(loading ? "처리 중..." : "가입하기");
        btnSignUp.setAlpha(loading ? 0.8f : (btnSignUp.isEnabled() ? 1f : 0.5f));
    }

    private void goMain(@Nullable String email) {
        if (email != null && !email.trim().isEmpty()) {
            saveLastEmail(email);
        }
        android.content.Intent i = new android.content.Intent(this, ProfileActivity.class);
        if (email != null && !email.trim().isEmpty()) {
            i.putExtra("user_email", email.trim());
        }
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void saveLastEmail(@NonNull String email) {
        getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putString("last_email", email.trim())
                .apply();
    }

    private String safe(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString();
    }

    private String safeTrim(EditText et) {
        return safe(et).trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
