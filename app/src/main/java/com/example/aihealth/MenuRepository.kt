package com.example.aihealth

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MenuRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getTodayRecommendations(email: String, force: Boolean): List<MenuItem> {
        val userSnap = db.collection("usercode").document(email).get().await()
        if (!userSnap.exists()) return emptyList()
        val profile = userSnap.toUserProfile()

        val bmi = bmiOf(profile.heightCm, profile.weightKg)
        val cat = HealthCat(catWeightFromBmi(bmi), catBmi(bmi), catFat(profile.fatPercent))

        val wantedTags = profileToDietTags(cat, profile.diseases).take(10)
        val kcalRange = calorieWindow(cat)
        val baseQuery = if (wantedTags.isNotEmpty())
            db.collection("menu").whereArrayContainsAny("tags", wantedTags).limit(30)
        else db.collection("menu").limit(30)

        val docs = baseQuery.get().await().documents
        val userAllergies = profile.allergies.map { it.lowercase() }.toSet()

        return docs.mapNotNull { it.toMenuItem() }.filter { m ->
            val okAllergy = userAllergies.intersect(m.allergyFlags.map { it.lowercase() }.toSet()).isEmpty()
            val okKcal = m.kcal?.let { it in kcalRange } ?: true
            okAllergy && okKcal
        }
    }

    /** ✅ 상세에서 사용: 메뉴 단건 조회 */
    suspend fun getMenu(id: String): MenuItem? =
        db.collection("menu").document(id).get().await().toMenuItem()

    suspend fun getFavoritesSet(email: String): Set<String> {
        val qs = db.collection("users").document(email).collection("favorites").get().await()
        return qs.documents.filter { it.getBoolean("enabled") == true }.map { it.id }.toSet()
    }

    suspend fun isFav(email: String, menuId: String): Boolean {
        val snap = db.collection("users").document(email)
            .collection("favorites").document(menuId).get().await()
        return snap.getBoolean("enabled") == true
    }

    suspend fun setFav(email: String, menuId: String, enabled: Boolean) {
        val ref = db.collection("users").document(email).collection("favorites").document(menuId)
        if (enabled) ref.set(mapOf("enabled" to true)).await() else ref.delete().await()
    }
}

/* ---------- Mappers & helpers ---------- */
private fun DocumentSnapshot.toUserProfile(): UserProfile {
    val email = id
    val height = get("height_cm").toIntSafe()
    val weight = (get("weight_kg") ?: get("weightKg")).toDoubleSafe()
    val fatPct = (get("fat_percent") ?: get("fatPercent")).toDoubleSafe()
    val ageYears = get("age_years").toIntSafe()
    val ageManYears = get("age_man_years").toIntSafe()
    val allergies = anyToStringList(get("allergies"))
    val diseases = anyToStringList(get("diseases"))
    return UserProfile(email, height, weight, fatPct, ageYears, ageManYears, allergies, diseases)
}

private fun DocumentSnapshot.toMenuItem(): MenuItem? {
    val id = this.id
    val name = getString("name") ?: return null

    val kcalAny = get("kcal")
    val kcal = when (kcalAny) {
        is Number -> kcalAny.toInt()
        is String -> kcalAny.toIntOrNull()
        else -> null
    }

    val imgCandidate = getString("imageUrl") ?: getString("image")
    val imageUrl = when {
        !imgCandidate.isNullOrBlank() -> imgCandidate
        kcalAny is String && kcalAny.startsWith("http", true) -> kcalAny
        else -> null
    }

    val ingredients = anyToStringList(get("ingredients"))
    val steps = anyToStringList(get("steps"))
    val tags = anyToStringList(get("tags"))
    val allergyFlags = anyToStringList(get("allergy_flags"))

    return MenuItem(id, name, kcal, imageUrl, ingredients, steps, tags, allergyFlags)
}

private fun anyToStringList(any: Any?): List<String> = when (any) {
    is List<*> -> any.mapNotNull { it?.toString() }
    is String -> listOf(any)
    else -> emptyList()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { if (c.isActive) c.resume(it) }
    addOnFailureListener { e -> if (c.isActive) c.resumeWithException(e) }
    addOnCanceledListener { if (c.isActive) c.cancel() }
}
