package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aihealth.util.AuthUtils;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
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
    private GoogleSignInClient googleClient;

    // UI
    private View btnGoogle;          // com.google.android.gms.common.SignInButton (캐스팅 X, View로 처리)
    private View btnLogin;           // 일반 로그인 버튼
    private ProgressBar progress;
    private View tvSignUp, tvFindId, tvFindPw;
    private android.widget.EditText etEmail, etPassword;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Google Sign-In 클라이언트
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        // UI binding
        etEmail   = findViewById(R.id.et_email);
        etPassword= findViewById(R.id.et_password);
        btnLogin  = findViewById(R.id.btn_login);
        btnGoogle = findViewById(R.id.btn_google_sign_in);
        progress  = findViewById(R.id.progress) instanceof ProgressBar ? (ProgressBar) findViewById(R.id.progress) : null;
        tvSignUp  = findViewById(R.id.tv_sign_up);
        tvFindId  = findViewById(R.id.tv_find_id);
        tvFindPw  = findViewById(R.id.tv_find_pw);

        if (btnGoogle instanceof SignInButton) {
            ((SignInButton) btnGoogle).setSize(SignInButton.SIZE_WIDE);
        }

        // 일반 로그인
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String emailRaw = safeText(etEmail);
                String pw       = safeText(etPassword);

                if (TextUtils.isEmpty(emailRaw)) { toast("이메일을 입력해 주세요."); return; }
                if (TextUtils.isEmpty(pw))       { toast("비밀번호를 입력해 주세요."); return; }

                showLoading(true);
                // 1차: FirebaseAuth 이메일/비번 로그인
                loginWithFirebaseAuth(emailRaw, pw);
            });
        }

        // 구글 로그인
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                showLoading(true);
                googleLauncher.launch(googleClient.getSignInIntent());
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

    // ===== Google → FirebaseAuth 연동 =====
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            showLoading(false);
                            toast("구글 로그인이 취소되었어요.");
                            return;
                        }
                        try {
                            GoogleSignInAccount acct = GoogleSignIn
                                    .getSignedInAccountFromIntent(result.getData())
                                    .getResult(ApiException.class);
                            if (acct == null) {
                                showLoading(false);
                                toast("구글 로그인에 실패했어요. 다시 시도해 주세요.");
                                return;
                            }
                            firebaseAuthWithGoogle(acct.getIdToken());
                        } catch (ApiException e) {
                            showLoading(false);
                            toast("구글 로그인 오류: " + e.getMessage());
                        }
                    }
            );

    private void firebaseAuthWithGoogle(String idToken) {
        if (TextUtils.isEmpty(idToken)) {
            showLoading(false);
            toast("구글 토큰이 비어 있습니다.");
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null || user.getEmail() == null) {
                        showLoading(false);
                        toast("사용자 이메일을 가져오지 못했습니다.");
                        return;
                    }
                    String email = normalizeEmail(user.getEmail());

                    try { AuthUtils.cacheLastEmail(this, email); } catch (Throwable ignored) {}

                    routeByUsercodeExistenceFlexible(email, /*displayName*/ user.getDisplayName(), /*fromGoogle*/ true);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("로그인 실패: " + e.getMessage());
                });
    }

    // ===== 이메일/비번 로그인: 1차 FirebaseAuth, 실패 시 Firestore 해시 보조검증 =====
    private void loginWithFirebaseAuth(String emailRaw, String plainPassword) {
        final String emailNorm = normalizeEmail(emailRaw);

        auth.signInWithEmailAndPassword(emailNorm, plainPassword)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    String authedEmail = user != null && user.getEmail() != null
                            ? normalizeEmail(user.getEmail()) : emailNorm;

                    // 인증 성공 → usercode 문서 존재 여부로 분기
                    routeByUsercodeExistenceFlexible(authedEmail, /*displayName*/ null, /*fromGoogle*/ false);
                })
                .addOnFailureListener(e -> {
                    // FirebaseAuth 실패 → Firestore password_hash 보조검증 시도 (레거시/커스텀 해시 호환)
                    db.collection("usercode").document(emailNorm)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!snap.exists()) {
                                    // 문서 자체가 없으면 가입 유도
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
                                // Firestore 해시 통과 시 분석 화면
                                showLoading(false);
                                goAnalyze(emailNorm);
                            })
                            .addOnFailureListener(ex -> {
                                showLoading(false);
                                toast("로그인 확인 실패: " + ex.getMessage());
                            });
                });
    }

    // ===== usercode 존재 여부를 소문자 id → 원문 id 순으로 유연하게 확인 =====
    private void routeByUsercodeExistenceFlexible(String emailNormalized, @Nullable String displayName, boolean fromGoogle) {
        final String emailLower = emailNormalized;
        final String emailRaw   = emailNormalized; // 이미 normalizeEmail에서 lower로 변환

        db.collection("usercode").document(emailLower)
                .get()
                .addOnSuccessListener(snapLower -> {
                    if (snapLower.exists()) {
                        showLoading(false);
                        goAnalyze(emailLower);
                        return;
                    }
                    // 혹시 과거에 대소문자 섞인 id로 저장된 경우(안전 가드)
                    db.collection("usercode").document(emailRaw)
                            .get()
                            .addOnSuccessListener(snapRaw -> {
                                showLoading(false);
                                if (snapRaw.exists()) {
                                    goAnalyze(emailRaw);
                                } else {
                                    Intent i = new Intent(this, SignActivity.class);
                                    i.putExtra("email", emailLower);
                                    if (displayName != null) i.putExtra("name", displayName);
                                    i.putExtra("from_google", fromGoogle);
                                    startActivity(i);
                                }
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                toast("회원 정보 확인 실패: " + e.getMessage());
                            });
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
        // 캐시
        try { AuthUtils.cacheLastEmail(this, email); } catch (Throwable ignored) {}
    }

    // ===== 비밀번호 보조 검증 (Firestore) =====
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
                Boolean ok = checkBcryptByReflection(plainPassword, hash);
                if (ok != null) return ok;
                // 리플렉션 실패 시엔 불일치로 간주
                return false;
            }

            // 1-3) 마지막 안전장치: 혹시 평문이 hash 필드에 들어간 사례
            if (hash.equals(plainPassword)) return true;
        }

        // 2) 레거시 평문 필드
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
        return null; // 호출 실패
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
        if (btnGoogle != null) btnGoogle.setEnabled(!show);
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
