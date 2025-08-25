package com.example.aihealth

import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.toMenuItemCompat(): MenuItem {
    val id = id
    val name = getString("name") ?: getString("title") ?: id
    val kcal = when (val v = get("kcal")) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
    val imageUrl = getString("imageUrl") ?: getString("image_url")
    val ingredients = when (val any = get("ingredients")) {
        is List<*> -> any.mapNotNull { it?.toString() }
        is String -> listOf(any)
        else -> emptyList()
    }
    val steps = when (val any = get("steps")) {
        is List<*> -> any.mapNotNull { it?.toString() }
        is String -> listOf(any)
        else -> emptyList()
    }
    val tags = when (val any = get("tags")) {
        is List<*> -> any.mapNotNull { it?.toString() }
        is String -> listOf(any)
        else -> emptyList()
    }
    val allergyFlags = when (val any = get("allergy_flags") ?: get("allergyFlags")) {
        is List<*> -> any.mapNotNull { it?.toString() }
        is String -> listOf(any)
        else -> emptyList()
    }
    return MenuItem(
        id = id,
        name = name,
        kcal = kcal,
        imageUrl = imageUrl,
        ingredients = ingredients,
        steps = steps,
        tags = tags,
        allergyFlags = allergyFlags
    )
}
