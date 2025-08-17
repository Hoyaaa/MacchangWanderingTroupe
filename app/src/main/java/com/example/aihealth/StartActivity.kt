/**
 * StartActivity
 * 기능: 앱 실행 시 start.xml을 표시하고 5초 후 LoginActivity로 자동 이동하는 스플래시 화면.
 * 동작 흐름:
 *  1. setContentView(start.xml)로 UI 표시
 *  2. lifecycleScope를 이용해 5초 대기
 *  3. Intent로 LoginActivity로 전환
 *  4. finish()로 종료해 뒤로가기 시 다시 안 보이게 함
 * 핵심 의존성:
 *  - androidx.appcompat.app.AppCompatActivity
 *  - androidx.lifecycle.lifecycleScope
 *  - kotlinx.coroutines.delay
 * 주의 사항:
 *  - AndroidManifest.xml에서 StartActivity를 MAIN/LAUNCHER로 설정해야 함
 *  - 너무 긴 대기시간은 UX에 부정적일 수 있으므로 주의
 */
package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.start) // start.xml 레이아웃 표시

        // 5초 후 LoginActivity로 이동
        lifecycleScope.launch {
            delay(5000) // 5초 대기
            startActivity(Intent(this@StartActivity, LoginActivity::class.java))
            finish() // 스플래시 종료
        }
    }
}
