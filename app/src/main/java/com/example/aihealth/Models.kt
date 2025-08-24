package com.example.aihealth

/* ---------- 데이터 모델 ---------- */

data class MenuItem(
    val id: String,
    val name: String,
    val kcal: Int? = null,
    val imageUrl: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val allergyFlags: List<String> = emptyList()
)

data class UserProfile(
    val email: String,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val fatPercent: Double? = null,
    val ageYears: Int? = null,
    val ageManYears: Int? = null,
    val allergies: List<String> = emptyList(),
    val diseases: List<String> = emptyList()
)

/* ---------- AI 응답 ---------- */

data class AiMenuScore(val menuId: String, val score: Double)
data class AiRecommendationResult(
    val analysisMessage: String,
    val items: List<MenuItem>,
    val scores: List<AiMenuScore>
)

/* ---------- 안전 변환 유틸 ---------- */

fun Any?.toIntSafe(): Int? = when (this) {
    is Number -> this.toInt()
    is String -> this.toIntOrNull()
    else -> null
}
fun Any?.toDoubleSafe(): Double? = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}

/* ---------- 건강 카테고리/권장 ---------- */

data class HealthCat(val w: Zone, val b: Zone, val f: Zone) { enum class Zone { L, N, H } }

fun bmiOf(heightCm: Int?, weightKg: Double?): Double? {
    if (heightCm == null || weightKg == null || heightCm <= 0) return null
    val m = heightCm / 100.0
    return weightKg / (m * m)
}
fun catBmi(bmi: Double?): HealthCat.Zone = when {
    bmi == null -> HealthCat.Zone.N
    bmi < 18.5 -> HealthCat.Zone.L
    bmi < 25.0 -> HealthCat.Zone.N
    else       -> HealthCat.Zone.H
}
fun catWeightFromBmi(bmi: Double?): HealthCat.Zone = catBmi(bmi)
fun catFat(fatPct: Double?): HealthCat.Zone = when {
    fatPct == null -> HealthCat.Zone.N
    fatPct < 18.0  -> HealthCat.Zone.L
    fatPct <= 28.0 -> HealthCat.Zone.N
    else           -> HealthCat.Zone.H
}
fun profileToDietTags(cat: HealthCat, diseases: List<String>): List<String> {
    val tags = mutableSetOf<String>()
    when (cat.b) { HealthCat.Zone.H -> tags += listOf("calorie_deficit","balanced")
        HealthCat.Zone.L -> tags += listOf("high_protein","balanced")
        else             -> tags += listOf("balanced") }
    val d = diseases.joinToString(" ").lowercase()
    if ("당뇨" in d || "diabetes" in d) tags += "low_sugar"
    if ("고혈압" in d || "hypertension" in d) tags += "low_sodium"
    return tags.toList()
}
fun calorieWindow(cat: HealthCat): IntRange = when (cat.b) {
    HealthCat.Zone.H -> 200..500
    HealthCat.Zone.N -> 300..700
    HealthCat.Zone.L -> 400..900
}
