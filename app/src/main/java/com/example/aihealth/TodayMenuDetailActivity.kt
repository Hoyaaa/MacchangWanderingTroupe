package com.example.aihealth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.aihealth.databinding.ActivityTodayMenuDetailBinding
import com.example.aihealth.util.AuthUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*

class TodayMenuDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MENU_ID = "menuId"
        fun newIntent(ctx: Context, menuId: String): Intent =
            Intent(ctx, TodayMenuDetailActivity::class.java).putExtra(EXTRA_MENU_ID, menuId)
    }

    private lateinit var b: ActivityTodayMenuDetailBinding

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private val menuRepo by lazy { MenuRepository() }

    private var menuId: String? = null
    private var currentEmail: String? = null
    private var isFav: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTodayMenuDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        menuId = intent.getStringExtra(EXTRA_MENU_ID)
        if (menuId.isNullOrBlank()) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUi()
        loadDetail()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun setupUi() = with(b) {
        btnBack.setOnClickListener { finish() }
        btnMypage.setOnClickListener {
            Toast.makeText(this@TodayMenuDetailActivity, "마이페이지로 이동 (추후 연결)", Toast.LENGTH_SHORT).show()
        }
        btnFav.setOnClickListener {
            val email = currentEmail ?: return@setOnClickListener
            val id = menuId ?: return@setOnClickListener
            uiScope.launch {
                try {
                    isFav = !isFav
                    applyFavIcon()
                    menuRepo.setFav(email, id, isFav)
                } catch (e: Exception) {
                    isFav = !isFav
                    applyFavIcon()
                    Snackbar.make(b.root, "즐겨찾기 변경 실패: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyFavIcon() {
        // ✅ 채움/테두리 분기 제대로
        b.btnFav.setImageResource(
            if (isFav) R.drawable.ic_star_border_24 else R.drawable.ic_star_border_24
        )
        b.btnFav.contentDescription = if (isFav) "즐겨찾기 해제" else "즐겨찾기"
    }

    private fun loadDetail() {
        uiScope.launch {
            showLoading(true)
            try {
                currentEmail = AuthUtils.currentEmail(this@TodayMenuDetailActivity, intent)
                val id = menuId ?: return@launch

                isFav = currentEmail?.let { menuRepo.isFav(it, id) } ?: false
                applyFavIcon()

                // ✅ 반환 타입 명시로 제네릭 추론 고정
                val menu: MenuItem? = withContext(Dispatchers.IO) { menuRepo.getMenu(id) }
                if (menu == null) {
                    Snackbar.make(b.root, "해당 메뉴를 찾을 수 없습니다.", Snackbar.LENGTH_LONG).show()
                    finish(); return@launch
                }
                bindMenu(menu)
            } catch (e: Exception) {
                Snackbar.make(b.root, "상세 로드 실패: ${e.message}", Snackbar.LENGTH_LONG).show()
                finish()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun bindMenu(menu: MenuItem) = with(b) {
        val hasImage = !menu.imageUrl.isNullOrBlank()
        tvImagePlaceholder.visibility = if (hasImage) View.GONE else View.VISIBLE
        ivFood.visibility = if (hasImage) View.VISIBLE else View.INVISIBLE

        if (hasImage) {
            Glide.with(this@TodayMenuDetailActivity)
                .load(menu.imageUrl)
                .thumbnail(0.25f)
                .into(ivFood)
        }

        tvFoodName.text = menu.name
        tvFoodCalorie.text = menu.kcal?.let { "$it kcal" } ?: "- kcal"

        if (menu.ingredients.isNotEmpty()) {
            tvIngredients.text = menu.ingredients.joinToString("\n") { "• $it" }
            cardIngredients.visibility = View.VISIBLE
            tvSectionIngredients.visibility = View.VISIBLE
        } else {
            cardIngredients.visibility = View.GONE
            tvSectionIngredients.visibility = View.GONE
        }

        renderSteps(menu.steps)
    }

    private fun renderSteps(steps: List<String>) = with(b) {
        layoutSteps.removeAllViews()
        if (steps.isEmpty()) {
            tvSectionSteps.visibility = View.GONE
            return
        } else {
            tvSectionSteps.visibility = View.VISIBLE
        }

        steps.forEachIndexed { idx, s ->
            val tv = TextView(this@TodayMenuDetailActivity).apply {
                text = "${idx + 1}. $s"
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutSteps.addView(tv, lp)
        }
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun showLoading(loading: Boolean) {
        b.scrollContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        b.btnFav.isEnabled = !loading
    }
}
