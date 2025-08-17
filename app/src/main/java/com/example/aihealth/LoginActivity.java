package com.example.aihealth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvSignUp, tvFindId, tvFindPw;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_login);

        // ✅ 회원가입 이동 복구: tv_sign_up 클릭 → SignActivity
        tvSignUp   = findViewById(R.id.tv_sign_up);
        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignActivity.class);
            startActivity(intent);
        });

        // 선택: 아이디/비번 찾기 뷰가 있다면 바인딩만 유지
        tvFindId = findViewById(R.id.tv_find_id);
        tvFindPw = findViewById(R.id.tv_find_pw);

        // 로그인 버튼 → LoadingActivity로 위임하여 Firestore 검증
        btnLogin.setOnClickListener(v -> {
            String email = String.valueOf(etEmail.getText()).trim();
            String password = String.valueOf(etPassword.getText());

            if (TextUtils.isEmpty(email)) {
                toast("이메일을 입력해 주세요.");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("올바른 이메일 형식이 아닙니다.");
                return;
            }
            if (TextUtils.isEmpty(password)) {
                toast("비밀번호를 입력해 주세요.");
                return;
            }

            Intent intent = new Intent(LoginActivity.this, LoadingActivity.class);
            intent.putExtra("mode", "loginCheck"); // ← 로딩에서 로그인 검증
            intent.putExtra("email", email);
            intent.putExtra("password", password);
            startActivity(intent);
            // 실패 시 LoadingActivity가 finish()되며 로그인 화면으로 자동 복귀
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
