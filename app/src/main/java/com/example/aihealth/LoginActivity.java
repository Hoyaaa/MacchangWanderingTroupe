// LoginActivity.java
package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private View btnGoogle;       // MaterialButton or SignInButton 등 어떤 위젯이든 OK
    private TextView tvSignUp;    // "회원가입" 이동 텍스트/버튼
    private ProgressBar progress;

    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::onGoogleResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        etEmail   = findViewById(R.id.et_email);
        etPassword= findViewById(R.id.et_password);
        btnLogin  = findViewById(R.id.btn_login);
        btnGoogle = findViewById(R.id.btn_google_sign_in); // 없으면 null일 수 있음
        tvSignUp  = findViewById(R.id.tv_sign_up);
        progress  = findViewById(R.id.progress);

        btnLogin.setOnClickListener(v -> doEmailLogin());
        if (btnGoogle != null) btnGoogle.setOnClickListener(v -> doGoogleLogin());
        if (tvSignUp != null)  tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignActivity.class)));

        // Google 로그인 클라이언트 (Firebase 사용 시 Web client ID 필요)
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            googleClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception ignore) {
            googleClient = null; // strings.xml 미설정 시 null 허용
        }
    }

    private void doEmailLogin() {
        String email = safe(etEmail);
        String pw    = safe(etPassword);

        if (email.isEmpty() || pw.isEmpty()) {
            toast("이메일/비밀번호를 입력하세요.");
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener(result -> {
                    String finalEmail = email;
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && user.getEmail() != null) finalEmail = user.getEmail();
                    goMain(finalEmail);
                })
                .addOnFailureListener(e -> {
                    toast("로그인 실패: " + e.getMessage());
                    setLoading(false);
                });
    }

    private void doGoogleLogin() {
        if (googleClient == null) {
            toast("Google 로그인 설정이 완성되지 않았습니다.");
            return;
        }
        googleLauncher.launch(googleClient.getSignInIntent());
    }

    private void onGoogleResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            toast("구글 로그인 취소");
            return;
        }
        setLoading(true);
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                    .getResult(ApiException.class);

            if (account == null) {
                setLoading(false);
                toast("구글 계정 정보를 불러오지 못했습니다.");
                return;
            }

            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential)
                    .addOnSuccessListener(cred -> {
                        String email = account.getEmail();
                        if (email == null && auth.getCurrentUser() != null) {
                            email = auth.getCurrentUser().getEmail();
                        }
                        goMain(email);
                    })
                    .addOnFailureListener(e -> {
                        toast("구글 로그인 실패: " + e.getMessage());
                        setLoading(false);
                    });

        } catch (ApiException e) {
            setLoading(false);
            toast("구글 로그인 실패: " + e.getMessage());
        }
    }

    // ===== 공통 유틸 =====

    private void goMain(@Nullable String email) {
        if (email != null && !email.trim().isEmpty()) {
            saveLastEmail(email);
        }
        Intent i = new Intent(this, MainActivity.class);
        if (email != null && !email.trim().isEmpty()) {
            i.putExtra("user_email", email.trim()); // ✅ MainActivity로 이메일 전달
        }
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void saveLastEmail(@NonNull String email) {
        getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putString("last_email", email.trim())  // ✅ 로컬 저장
                .apply();
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnLogin != null)  btnLogin.setEnabled(!loading);
        if (btnGoogle != null) btnGoogle.setEnabled(!loading);
        if (tvSignUp != null)  tvSignUp.setEnabled(!loading);
    }

    private String safe(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
