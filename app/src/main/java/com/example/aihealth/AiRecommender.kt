package com.example.aihealth

interface AiRecommender {
    /** 기본 단건 호출(오늘 추천 4개) */
    suspend fun recommendToday(email: String): AiRecommendationResult

    /** 새로고침/페이징 등: 제외 목록과 배치 크기를 함께 전달 */
    suspend fun recommendBatch(
        email: String,
        exclude: List<String> = emptyList(),
        batchSize: Int = 4
    ): AiRecommendationResult
}
