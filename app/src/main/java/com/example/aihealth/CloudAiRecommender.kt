package com.example.aihealth

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

class CloudAiRecommender(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) : AiRecommender {

    private companion object {
        const val TAG = "CloudAiRecommender"
        const val FN  = "recommendTodayMenuBatch"
    }

    // 단건(기본 4개)도 내부적으로 배치 API 재사용
    override suspend fun recommendToday(email: String): AiRecommendationResult =
        recommendBatch(email = email, exclude = emptyList(), batchSize = 4)

    // 🔧 배치 호출 (exclude, batchSize 반영)
    // CloudAiRecommender.kt
    override suspend fun recommendBatch(
        email: String,
        exclude: List<String>,
        batchSize: Int
    ): AiRecommendationResult {
        val payload = hashMapOf<String, Any>(
            "email" to email,
            "exclude" to exclude,
            "batchSize" to batchSize
        )
        try {
            val data = functions
                .getHttpsCallable(FN)
                .call(payload)
                .await()
                .data as? Map<*, *> ?: emptyMap<Any, Any>()
            return parseResult(data)
        } catch (e: FirebaseFunctionsException) {
            val msg = buildString {
                append("Functions error [${e.code}] ${e.message ?: ""}")
                e.details?.let { append(" details=" + it.toString()) }
            }
            Log.e(TAG, msg, e)
            throw Exception(msg, e)
        } catch (e: Exception) {
            Log.e(TAG, "Functions call failed: ${e.message}", e)
            throw e
        }
    }



    // 응답 파서(서버의 items, analysisMessage 스키마에 맞춤)
    private fun parseResult(result: Map<*, *>): AiRecommendationResult {
        val analysis = (result["analysisMessage"] as? String).orEmpty()

        val itemsRaw  = result["items"]  as? List<*> ?: emptyList<Any?>()
        val scoresRaw = result["scores"] as? List<*> ?: emptyList<Any?>()

        val items = itemsRaw.mapNotNull { any ->
            (any as? Map<*, *>)?.let { map ->
                val id   = map["id"]?.toString() ?: return@let null
                val name = map["name"]?.toString() ?: return@let null
                val kcal: Int? = when (val v = map["kcal"]) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull()
                    else -> null
                }
                val imageUrl    = map["imageUrl"]?.toString()
                val ingredients = (map["ingredients"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val steps       = (map["steps"]       as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val tags        = (map["tags"]        as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val allergy     = (map["allergyFlags"]as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                MenuItem(id, name, kcal, imageUrl, ingredients, steps, tags, allergy)
            }
        }

        val scores = scoresRaw.mapNotNull { any ->
            (any as? Map<*, *>)?.let { map ->
                val id = map["menuId"]?.toString() ?: return@let null
                val score: Double = when (val v = map["score"]) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull() ?: return@let null
                    else -> return@let null
                }
                AiMenuScore(menuId = id, score = score)
            }
        }

        return AiRecommendationResult(analysisMessage = analysis, items = items, scores = scores)
    }
}
