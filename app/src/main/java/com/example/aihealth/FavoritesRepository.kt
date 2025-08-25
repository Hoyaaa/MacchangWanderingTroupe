package com.example.aihealth

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FavoritesRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** users/{email}/favorites 하위 문서 id(=menuId)들 */
    suspend fun getFavoriteIds(email: String): Set<String> {
        val snap = db.collection("users").document(email)
            .collection("favorites")
            .get().await()
        return snap.documents.map { it.id }.toSet()
    }

    /** 메뉴 상세: 우선 menus, 없으면 menu 컬렉션에서 id로 조회 (in 쿼리 10개 제한 처리) */
    suspend fun getMenusByIds(ids: Collection<String>): List<MenuItem> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<MenuItem>()
        val list = ids.toList()
        val chunks = list.chunked(10)

        // 1) menus
        for (chunk in chunks) {
            val qs = db.collection("menus")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            for (d in qs.documents) out += d.toMenuItemCompat()
        }

        // 2) menus에서 못 찾은 나머지 → menu
        val found = out.map { it.id }.toSet()
        val remain = list.filterNot { found.contains(it) }
        if (remain.isNotEmpty()) {
            for (chunk in remain.chunked(10)) {
                val qs = db.collection("menu")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get().await()
                for (d in qs.documents) out += d.toMenuItemCompat()
            }
        }

        // 원래 ids 순서 유지
        val index = list.withIndex().associate { it.value to it.index }
        return out.sortedBy { index[it.id] ?: Int.MAX_VALUE }
    }
}
