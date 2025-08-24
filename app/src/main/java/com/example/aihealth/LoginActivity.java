package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aihealth.util.AuthUtils;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * LoginActivity (Google 로그인 제거 버전)
 * - 구글 로그인 관련 코드/의존성/런처 제거
 * - 일반 이메일/비밀번호 로그인만 활성화
 * - FirebaseAuth 1차, 실패 시 Firestore(usercode.password_hash) 보조 검증
 * - 가입 필요 시 SignActivity로 이동
 * - 성공 시 LoadingActivity(mode=analyze) → MainActivity
 */
public class LoginActivity extends AppCompatActivity {

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // UI
    private View btnLogin;           // 일반 로그인 버튼
    private ProgressBar progress;
    private View tvSignUp, tvFindId, tvFindPw;
    private android.widget.EditText etEmail, etPassword;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 레이아웃에 남아 있을 수 있는 구글 버튼은 가려 둔다 (id: btn_google_sign_in)
        try {
            View g = findViewById(R.id.btn_google_sign_in);
            if (g != null) g.setVisibility(View.GONE);
        } catch (Throwable ignored) {}

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // UI binding
        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_login);
        progress   = findViewById(R.id.progress);
        tvSignUp   = findViewById(R.id.tv_sign_up);
        tvFindId   = findViewById(R.id.tv_find_id);
        tvFindPw   = findViewById(R.id.tv_find_pw);

        // 일반 로그인
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String emailRaw = safeText(etEmail);
                String pw       = safeText(etPassword);

                if (TextUtils.isEmpty(emailRaw)) { toast("이메일을 입력해 주세요."); return; }
                if (TextUtils.isEmpty(pw))       { toast("비밀번호를 입력해 주세요."); return; }

                showLoading(true);
                loginWithFirebaseAuth(emailRaw, pw);
            });
        }

        // 회원가입 이동
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> {
                String emailRaw = safeText(etEmail);
                Intent i = new Intent(this, SignActivity.class);
                if (!TextUtils.isEmpty(emailRaw)) i.putExtra("email", emailRaw.trim());
                startActivity(i);
            });
        }

        // (옵션) 아이디/비번 찾기
        if (tvFindId != null) tvFindId.setOnClickListener(v -> toast("아이디 찾기 준비 중입니다."));
        if (tvFindPw != null) tvFindPw.setOnClickListener(v -> toast("비밀번호 찾기 준비 중입니다."));
    }

    // ===== 이메일/비번 로그인: 1차 FirebaseAuth, 실패 시 Firestore 해시 보조검증 =====
    private void loginWithFirebaseAuth(String emailRaw, String plainPassword) {
        final String emailNorm = normalizeEmail(emailRaw);

        auth.signInWithEmailAndPassword(emailNorm, plainPassword)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    String authedEmail = user != null && user.getEmail() != null
                            ? normalizeEmail(user.getEmail()) : emailNorm;

                    try { AuthUtils.cacheLastEmail(this, authedEmail); } catch (Throwable ignored) {}

                    // 인증 성공 → usercode 문서 존재 여부로 분기
                    routeByUsercodeExistenceFlexible(authedEmail);
                })
                .addOnFailureListener(e -> {
                    // FirebaseAuth 실패 → Firestore password_hash 보조검증 시도 (레거시/커스텀 해시 호환)
                    db.collection("usercode").document(emailNorm)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!snap.exists()) {
                                    // 아예 회원이 아님 → 가입 화면
                                    showLoading(false);
                                    Intent i = new Intent(this, SignActivity.class);
                                    i.putExtra("email", emailNorm);
                                    startActivity(i);
                                    return;
                                }
                                if (!verifyPasswordWithSnapshot(snap, plainPassword)) {
                                    showLoading(false);
                                    toast("이메일 또는 비밀번호가 올바르지 않습니다.");
                                    return;
                                }
                                // Firestore 해시 통과 시 분석 화면
                                showLoading(false);
                                try { AuthUtils.cacheLastEmail(this, emailNorm); } catch (Throwable ignored2) {}
                                goAnalyze(emailNorm);
                            })
                            .addOnFailureListener(ex -> {
                                showLoading(false);
                                toast("로그인 확인 실패: " + ex.getMessage());
                            });
                });
    }

    // ===== usercode 존재 여부를 소문자 id 기준으로 확인 =====
    private void routeByUsercodeExistenceFlexible(String emailNormalized) {
        final String emailLower = emailNormalized;

        db.collection("usercode").document(emailLower)
                .get()
                .addOnSuccessListener(snapLower -> {
                    if (snapLower.exists()) {
                        showLoading(false);
                        goAnalyze(emailLower);
                    } else {
                        showLoading(false);
                        Intent i = new Intent(this, SignActivity.class);
                        i.putExtra("email", emailLower);
                        startActivity(i);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("회원 정보 확인 실패: " + e.getMessage());
                });
    }

    // ===== 분석 화면 이동 =====
    private void goAnalyze(String email) {
        Intent i = new Intent(this, LoadingActivity.class);
        i.putExtra("mode", "analyze");
        i.putExtra("user_email", email);
        startActivity(i);
        finish();
    }

    // ===== Firestore password_hash 보조 검증 =====
    private boolean verifyPasswordWithSnapshot(DocumentSnapshot snap, String plainPassword) {
        // 1) password_hash 우선 (SHA-256 hex / BCrypt / 기타)
        String hash = getAsStringSafe(snap.get("password_hash"));
        if (!TextUtils.isEmpty(hash)) {
            hash = hash.trim();

            // 1-1) SHA-256(hex) → 대소문자 무시 비교
            if (isSha256Hex(hash)) {
                String input = sha256Hex(plainPassword);
                return input != null && hash.equalsIgnoreCase(input);
            }

            // 1-2) BCrypt → 리플렉션으로 org.mindrot.jbcrypt.BCrypt.checkpw 호출 (의존성 없어도 시도)
            if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
                try {
                    Class<?> bcrypt = Class.forName("org.mindrot.jbcrypt.BCrypt");
                    Method method = bcrypt.getMethod("checkpw", String.class, String.class);
                    Object ok = method.invoke(null, plainPassword, hash);
                    if (ok instanceof Boolean) return (Boolean) ok;
                } catch (Throwable ignored) {}
            }
        }

        // 2) 폴백: password_plain 필드가 있는 경우(개발 중 임시) 직접 비교
        String plain = getAsStringSafe(snap.get("password_plain"));
        if (!TextUtils.isEmpty(plain)) {
            return plain.equals(plainPassword);
        }

        return false;
    }

    // ===== 로딩 표시 =====
    private void showLoading(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!show);
    }

    // ===== 공통 유틸 =====
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSha256Hex(String s) {
        if (s == null) return false;
        int n = s.length();
        if (!(n == 64)) return false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') ||
                    (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeText(@Nullable android.widget.EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // Nullable 어노테이션 로컬 정의 (빌드 환경에 따라 필요)
    public @interface Nullable {}
    private static String getAsStringSafe(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        return String.valueOf(obj);
    }
}
