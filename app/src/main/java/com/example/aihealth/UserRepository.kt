package com.example.aihealth

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getUserProfile(email: String): UserProfile {
        val snap = db.collection("usercode").document(email).get().await()

        val allergies = (snap.get("allergies") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val diseases  = (snap.get("diseases")  as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        return UserProfile(
            email       = email,
            heightCm    = snap.get("height_cm").toIntSafe(),
            weightKg    = snap.get("weight_kg").toDoubleSafe()
                ?: snap.get("weight_kg")?.toString()?.toDoubleOrNull(),
            fatPercent  = snap.get("fat_percent").toDoubleSafe(),
            ageYears    = snap.get("age_years").toIntSafe(),
            ageManYears = snap.get("age_man_years").toIntSafe(),
            allergies   = allergies,
            diseases    = diseases
        )
    }
}
