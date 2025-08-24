package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aihealth.util.AuthUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

class FavMenuActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var rv: RecyclerView
    private var progress: ProgressBar? = null
    private lateinit var btnMyPage: ImageButton

    private val repo by lazy { FavoritesRepository() }
    private val adapter = FavMenuAdapter { item ->
        startActivity(
            Intent(this, TodayMenuDetailActivity::class.java)
                .putExtra("menuId", item.id)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favmenu)

        rv = findViewById(R.id.rv_saved_menus)
        progress = findViewById(R.id.progress)   // 있으면 사용, 없으면 null 유지
        btnMyPage = findViewById(R.id.btn_mypage)

        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = adapter

        // 상단 탭 동작 (디자인 시안과 동일하게)
        findViewById<View>(R.id.tab_health_state)?.setOnClickListener {
            // "나의 건강 상태" 탭 → 추천 화면으로
            startActivity(Intent(this, TodayMenuActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        findViewById<View>(R.id.tab_saved_menu)?.setOnClickListener {
            // 현재 탭(찜한 식단) → 아무 동작 없음
        }

        // 마이페이지
        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }

        val email = resolveEmailOrRedirect() ?: return
        loadFavorites(email)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun resolveEmailOrRedirect(): String? {
        val email = intent.getStringExtra("user_email")
            ?: FirebaseAuth.getInstance().currentUser?.email
            ?: AuthUtils.readLastEmail(this)
        if (email.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            })
            finish()
            return null
        }
        return email
    }

    private fun loadFavorites(email: String) {
        uiScope.launch {
            showLoading(true)
            try {
                val ids = withContext(Dispatchers.IO) { repo.getFavoriteIds(email) }
                val menus = withContext(Dispatchers.IO) { repo.getMenusByIds(ids) }
                adapter.submitList(menus)
                if (menus.isEmpty()) {
                    android.widget.Toast.makeText(
                        this@FavMenuActivity,
                        "찜한 식단이 없습니다.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    this@FavMenuActivity,
                    "찜한 식단 로드 실패: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                adapter.submitList(emptyList())
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progress?.visibility = if (show) View.VISIBLE else View.GONE
        rv.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }
}
