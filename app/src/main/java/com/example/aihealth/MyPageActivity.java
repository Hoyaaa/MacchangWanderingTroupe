package com.example.aihealth;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MyPageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 마이페이지 레이아웃 연결
        setContentView(R.layout.activity_mypage);
    }
}
