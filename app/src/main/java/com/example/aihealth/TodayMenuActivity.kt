package com.example.aihealth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

class TodayMenuActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvAnalysis: TextView

    private val uiScope = MainScope()
    private var currentEmail: String? = null

    private lateinit var adapter: TodayMenuAdapter
    private val menuRepository by lazy { MenuRepository() }
    private val ai: AiRecommender by lazy { CloudAiRecommender() }

    // 지금까지 보여준 메뉴 ID들 (한 세션 동안 누적)
    private val shownIds = LinkedHashSet<String>()
    private val batchSize = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today_menu)

        rv = findViewById(R.id.rv_today_recommend)
        progress = findViewById(R.id.progress)
        btnBack = findViewById(R.id.btn_back)
        btnRefresh = findViewById(R.id.btn_refresh)
        tvAnalysis = findViewById(R.id.tv_analysis)

        rv.layoutManager = GridLayoutManager(this, 2)

        adapter = TodayMenuAdapter(
            onClick = { item ->
                startActivity(
                    Intent(this, TodayMenuDetailActivity::class.java)
                        .putExtra("menuId", item.id)
                )
            },
            onToggleFav = { item ->
                val email = currentEmail ?: return@TodayMenuAdapter
                uiScope.launch(Dispatchers.IO) {
                    try {
                        val now = adapter.favoriteIds.contains(item.id)
                        menuRepository.setFav(email, item.id, !now)
                        val newSet = adapter.favoriteIds.toMutableSet()
                        if (now) newSet.remove(item.id) else newSet.add(item.id)
                        withContext(Dispatchers.Main) { adapter.favoriteIds = newSet }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@TodayMenuActivity,
                                "즐겨찾기 실패: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
        rv.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnRefresh.setOnClickListener {
            requestBatch(exclude = shownIds.toList(), resetIfEmpty = true)
        }
        btnRefresh.setOnLongClickListener {
            // 길게 누르면 완전 초기화 후 새 탐색
            shownIds.clear()
            requestBatch(exclude = emptyList(), resetIfEmpty = false)
            true
        }

        currentEmail = resolveEmailOrRedirect() ?: return
        // 진입할 때 즉석 분석 → 4개
        requestBatch(exclude = emptyList(), resetIfEmpty = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun resolveEmailOrRedirect(): String? {
        val email = intent.getStringExtra("user_email")
            ?: FirebaseAuth.getInstance().currentUser?.email
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

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * AI에게 즉석 배치 추천 요청
     * @param exclude 현재까지 노출된 ID들
     * @param resetIfEmpty 새로 뽑을 게 없을 때 exclude 초기화 후 다시 시도할지 여부
     */
    private fun requestBatch(exclude: List<String>, resetIfEmpty: Boolean) {
        val email = currentEmail ?: return
        uiScope.launch {
            showLoading(true)
            try {
                // 1) 클라우드 AI 배치 호출
                val aiRes = withContext(Dispatchers.IO) {
                    ai.recommendBatch(email = email, exclude = exclude, batchSize = batchSize)
                }

                val list = aiRes.items
                if (list.isEmpty()) {
                    if (resetIfEmpty) {
                        Toast.makeText(this@TodayMenuActivity, "새로 보여줄 추천이 더 이상 없습니다. 다시 탐색합니다.", Toast.LENGTH_SHORT).show()
                        shownIds.clear()
                        // exclude 초기화 후 한 번 더
                        val retry = withContext(Dispatchers.IO) {
                            ai.recommendBatch(email = email, exclude = emptyList(), batchSize = batchSize)
                        }
                        applyBatch(retry)
                    } else {
                        Toast.makeText(this@TodayMenuActivity, "추천 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                        adapter.submitList(emptyList())
                    }
                } else {
                    applyBatch(aiRes)
                }

                // 즐겨찾기 상태 갱신
                val favs = withContext(Dispatchers.IO) { menuRepository.getFavoritesSet(email) }
                adapter.favoriteIds = favs

            } catch (e: Exception) {
                // 완전 실패 시, 기본 후보에서 랜덤 4개 (알고리즘 실패 대비)
                try {
                    val base = withContext(Dispatchers.IO) {
                        menuRepository.getTodayRecommendations(email, force = false)
                    }.shuffled().take(batchSize)

                    adapter.submitList(base)
                    shownIds.addAll(base.map { it.id })
                    tvAnalysis.text = "AI 호출 실패 — 기본 후보에서 랜덤 추천을 표시합니다."
                } catch (e2: Exception) {
                    Toast.makeText(this@TodayMenuActivity, "식단 로드 실패: ${e2.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun applyBatch(res: AiRecommendationResult) {
        adapter.submitList(res.items)
        tvAnalysis.text = res.analysisMessage.ifBlank { "맞춤 추천을 적용했어요." }
        shownIds.addAll(res.items.map { it.id })
    }
}
