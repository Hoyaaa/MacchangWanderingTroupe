package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // UI binding
        etEmail   = findViewById(R.id.et_email);
        etPassword= findViewById(R.id.et_password);
        btnLogin  = findViewById(R.id.btn_login);
        progress  = findViewById(R.id.progress) instanceof ProgressBar ? (ProgressBar) findViewById(R.id.progress) : null;
        tvSignUp  = findViewById(R.id.tv_sign_up);
        tvFindId  = findViewById(R.id.tv_find_id);
        tvFindPw  = findViewById(R.id.tv_find_pw);

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
                i.putExtra("from_google", false);
                startActivity(i);
            });
        }

        // (옵션) 아이디/비번 찾기
        if (tvFindId != null) tvFindId.setOnClickListener(v -> toast("아이디 찾기 준비 중입니다."));
        if (tvFindPw != null) tvFindPw.setOnClickListener(v -> toast("비밀번호 찾기 준비 중입니다."));
    }

    // ===== 이메일/비번 로그인 =====
    private void loginWithFirebaseAuth(String emailRaw, String plainPassword) {
        final String emailNorm = normalizeEmail(emailRaw);

        auth.signInWithEmailAndPassword(emailNorm, plainPassword)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    String authedEmail = user != null && user.getEmail() != null
                            ? normalizeEmail(user.getEmail()) : emailNorm;

                    routeByUsercodeExistenceFlexible(authedEmail, /*displayName*/ null);
                })
                .addOnFailureListener(e -> {
                    // FirebaseAuth 실패 → Firestore password_hash 보조검증
                    db.collection("usercode").document(emailNorm)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!snap.exists()) {
                                    showLoading(false);
                                    toast("가입 이력이 없습니다. 회원가입 화면으로 이동합니다.");
                                    Intent i = new Intent(this, SignActivity.class);
                                    i.putExtra("email", emailNorm);
                                    i.putExtra("from_google", false);
                                    startActivity(i);
                                    return;
                                }
                                if (!verifyPasswordWithSnapshot(snap, plainPassword)) {
                                    showLoading(false);
                                    toast("이메일 또는 비밀번호가 올바르지 않습니다.");
                                    return;
                                }
                                showLoading(false);
                                goAnalyze(emailNorm);
                            })
                            .addOnFailureListener(ex -> {
                                showLoading(false);
                                toast("로그인 확인 실패: " + ex.getMessage());
                            });
                });
    }

    // ===== usercode 존재 여부 확인 =====
    private void routeByUsercodeExistenceFlexible(String emailNormalized, String displayName) {
        final String emailLower = emailNormalized;

        db.collection("usercode").document(emailLower)
                .get()
                .addOnSuccessListener(snapLower -> {
                    showLoading(false);
                    if (snapLower.exists()) {
                        goAnalyze(emailLower);
                    } else {
                        Intent i = new Intent(this, SignActivity.class);
                        i.putExtra("email", emailLower);
                        if (displayName != null) i.putExtra("name", displayName);
                        i.putExtra("from_google", false);
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
        try { AuthUtils.cacheLastEmail(this, email); } catch (Throwable ignored) {}
    }

    // ===== 비밀번호 보조 검증 (Firestore) =====
    private boolean verifyPasswordWithSnapshot(DocumentSnapshot snap, String plainPassword) {
        String hash = getAsStringSafe(snap.get("password_hash"));
        if (!TextUtils.isEmpty(hash)) {
            hash = hash.trim();
            if (isSha256Hex(hash)) {
                String input = sha256Hex(plainPassword);
                return input != null && hash.equalsIgnoreCase(input);
            }
            if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
                Boolean ok = checkBcryptByReflection(plainPassword, hash);
                if (ok != null) return ok;
                return false;
            }
            if (hash.equals(plainPassword)) return true;
        }
        String legacyPlain = getAsStringSafe(snap.get("password"));
        return !TextUtils.isEmpty(legacyPlain) && legacyPlain.equals(plainPassword);
    }

    private static Boolean checkBcryptByReflection(String plain, String hash) {
        try {
            Class<?> c = Class.forName("org.mindrot.jbcrypt.BCrypt");
            Method m = c.getMethod("checkpw", String.class, String.class);
            Object r = m.invoke(null, plain, hash);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isSha256Hex(String s) {
        return s != null && s.length() == 64 && s.matches("[0-9a-fA-F]{64}");
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

    // ===== 공통 유틸 =====
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void showLoading(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (btnLogin  != null) btnLogin.setEnabled(!show);
    }

    private static String safeText(android.widget.EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static String getAsStringSafe(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        return String.valueOf(obj);
    }
}
