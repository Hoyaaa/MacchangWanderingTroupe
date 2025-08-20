/*
 * 기능 요약:
 * - 신장/체중/나이/성별(선택)에 기반하여 BMI, 추정 체지방률(Deurenberg), 게이지 위치(bias), 구간 라벨(LOW/NORMAL/HIGH),
 *   그리고 종합 문구(제목/내용)를 계산해 반환합니다.
 * - 게이지 bias는 3등분(저/정상/고) 막대에서 0.0~1.0 구간으로 매핑되어 ConstraintLayout의 horizontalBias에 바로 사용 가능합니다.
 *
 * 주요 변경점:
 * - Kotlin when 브랜치를 한 줄에 콤마(,)로 나열하던 부분을 모두 개행 형태로 수정하여
 *   "Expecting a when-condition" 오류를 제거했습니다.
 * - String.format 기본 로케일 경고를 없애기 위해 Locale.KOREA를 명시했습니다.
 */

package com.example.aihealth

import java.io.Serializable
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object HealthAnalyzer {

    data class AnalysisInput(
        val heightCm: Int,
        val weightKg: Double,
        val ageYears: Int,
        val isMale: Boolean? = null        // null이면 성별 미상
    ) : Serializable

    enum class Zone { LOW, NORMAL, HIGH }

    data class Gauge(
        val zone: Zone,
        val bias: Float   // 0.0 ~ 1.0, activity_health_analysis.xml의 horizontalBias에 그대로 사용
    ) : Serializable

    data class AnalysisResult(
        val bmi: Double,
        val bmiGauge: Gauge,
        val weightGauge: Gauge,
        val fatPct: Double?,              // % (성별 미상이면 null)
        val fatGauge: Gauge,
        val messageTitle: String,         // 알립니다!
        val messageBody: String           // 통합 요약 설명
    ) : Serializable

    /** BMI 계산 */
    private fun bmi(heightCm: Int, weightKg: Double): Double {
        val h = heightCm / 100.0
        return if (h > 0) weightKg / (h * h) else 0.0
    }

    /**
     * 3구간 가로 바에서 비율로 변환.
     * lowEnd .. normalStart .. normalEnd .. highEnd 범위를 넣으면 0.0~1.0로 매핑.
     */
    private fun toBias(
        value: Double,
        lowEnd: Double,
        normalStart: Double,
        normalEnd: Double,
        highEnd: Double
    ): Float {
        val lowR = 0.0
        val normalL = 1.0 / 3.0
        val normalR = 2.0 / 3.0
        val highL = 1.0

        val v = value.coerceIn(lowEnd, highEnd)

        return when {
            v <= normalStart -> { // 미만 구간: [lowEnd..normalStart] → [0.0..1/3]
                val t = if (normalStart == lowEnd) 0.0 else (v - lowEnd) / (normalStart - lowEnd)
                (lowR + (normalL - lowR) * t).toFloat()
            }
            v <= normalEnd -> {   // 정상 구간: [normalStart..normalEnd] → [1/3..2/3]
                val t = if (normalEnd == normalStart) 0.0 else (v - normalStart) / (normalEnd - normalStart)
                (normalL + (normalR - normalL) * t).toFloat()
            }
            else -> {             // 초과 구간: [normalEnd..highEnd] → [2/3..1.0]
                val t = if (highEnd == normalEnd) 0.0 else (v - normalEnd) / (highEnd - normalEnd)
                (normalR + (highL - normalR) * t).toFloat()
            }
        }
    }

    /** 구간 라벨 */
    private fun zoneOf(value: Double, lowMax: Double, normalMax: Double): Zone =
        when {
            value <= lowMax -> Zone.LOW
            value <= normalMax -> Zone.NORMAL
            else -> Zone.HIGH
        }

    /** 체지방률 추정 (Deurenberg 공식). 성별 미상이면 null 반환. */
    private fun estimateBodyFatPct(bmi: Double, age: Int, isMale: Boolean?): Double? {
        if (isMale == null) return null
        val sexTerm = if (isMale) 1 else 0
        val pct = 1.20 * bmi + 0.23 * age - 10.8 * sexTerm - 5.4
        // 현실적인 3~60% 범위로 클램핑
        return max(3.0, min(60.0, pct))
    }

    /**
     * 체지방률 카테고리 경계 (간단화):
     *  - 남성: 저체지방 < 8, 정상 8~20, 과다 > 20
     *  - 여성: 저체지방 < 18, 정상 18~30, 과다 > 30
     *  성별 미상은 BMI 기반으로 대체(저<18.5, 정상18.5~24.9, 과>24.9).
     */
    private fun fatGauging(pct: Double?, bmi: Double, isMale: Boolean?): Gauge {
        return if (pct == null || isMale == null) {
            val zone = zoneOf(bmi, 18.5, 24.9)
            val bias = toBias(bmi, 14.0, 18.5, 24.9, 35.0) // 대체 스케일
            Gauge(zone, bias)
        } else {
            if (isMale) {
                val zone = zoneOf(pct, 8.0, 20.0)
                val bias = toBias(pct, 4.0, 8.0, 20.0, 35.0)
                Gauge(zone, bias)
            } else {
                val zone = zoneOf(pct, 18.0, 30.0)
                val bias = toBias(pct, 12.0, 18.0, 30.0, 45.0)
                Gauge(zone, bias)
            }
        }
    }

    /** 체중 게이지: BMI 구간을 체중으로 환산하여 동일 스케일 사용 */
    private fun weightGauge(heightCm: Int, weightKg: Double): Gauge {
        val h = heightCm / 100.0
        val bmiVal = bmi(heightCm, weightKg)
        val zone = zoneOf(bmiVal, 18.5, 24.9)
        // 체중으로 스케일링: BMI 15~35를 체중으로 변환
        val wLowEnd      = 15.0 * h * h
        val wNormalStart = 18.5 * h * h
        val wNormalEnd   = 24.9 * h * h
        val wHighEnd     = 35.0 * h * h
        val bias = toBias(weightKg, wLowEnd, wNormalStart, wNormalEnd, wHighEnd)
        return Gauge(zone, bias)
    }

    /** 결과 메시지 생성 */
    private fun buildMessage(
        bmiZone: Zone,
        fatZone: Zone,
        weightZone: Zone,
        bmi: Double,
        fatPct: Double?,
        age: Int
    ): Pair<String, String> {
        val title = "알립니다!"

        val bmiWord = when (bmiZone) {
            Zone.LOW -> "저체중"
            Zone.NORMAL -> "정상"
            Zone.HIGH -> "과체중/비만"
        }

        val fatWord = when (fatZone) {
            Zone.LOW -> "낮음"
            Zone.NORMAL -> "정상"
            Zone.HIGH -> "높음"
        }

        val wtWord = when (weightZone) {
            Zone.LOW -> "미만"
            Zone.NORMAL -> "정상"
            Zone.HIGH -> "초과"
        }

        val sb = StringBuilder()
        sb.append("BMI ")
            .append(String.format(Locale.KOREA, "%.1f", bmi))
            .append(" (").append(bmiWord).append(")")

        fatPct?.let {
            sb.append(" · 체지방 ")
                .append(String.format(Locale.KOREA, "%.1f", it))
                .append("% (").append(fatWord).append(")")
        }

        sb.append(" · 체중 ").append(wtWord).append(".\n")

        // ▼▼▼ 21가지 상세 문구 매핑 ▼▼▼
        val caseMessage: String = when (weightZone) {

            // 1) 체중 = 미만 (6가지) — BMI=HIGH는 이론상 불가라 안전문구 배치
            Zone.LOW -> when (bmiZone) {
                Zone.LOW -> when (fatZone) {
                    Zone.LOW    -> "체중과 체지방이 모두 부족해요. 잘 먹고 운동해서 건강하게 체력을 키워 보세요!"
                    Zone.NORMAL -> "체중은 조금 적지만 체지방은 괜찮아요. 근육량을 늘리면 더 건강한 몸이 될 거예요!"
                    Zone.HIGH   -> "체중은 적은데 체지방은 많아요. 근육을 키우면서 체력을 보강하는 게 필요해요."
                }
                Zone.NORMAL -> when (fatZone) {
                    Zone.LOW    -> "체중은 적당하지만 체지방이 너무 낮아요. 영양을 잘 챙겨서 체력을 유지하세요!"
                    Zone.NORMAL -> "체중은 조금 적은 편이지만 전반적으로 균형이 좋아요. 식습관만 잘 챙기면 건강해요."
                    Zone.HIGH   -> "체중은 적지만 체지방이 높은 편이에요. 근육량을 늘리고 꾸준히 운동해 주세요!"
                }
                Zone.HIGH -> {
                    "수치 간 불일치가 있어 보여요. 측정값을 다시 확인하고, 균형 잡힌 식사와 규칙적인 운동을 병행해 주세요."
                }
            }

            // 2) 체중 = 정상 (9가지)
            Zone.NORMAL -> when (bmiZone) {
                Zone.LOW -> when (fatZone) {
                    Zone.LOW    -> "체중은 정상인데 체지방이 적어요. 건강해 보이지만 에너지가 부족할 수 있으니 잘 챙겨 드세요!"
                    Zone.NORMAL -> "체중은 정상이고 체지방도 적당해요. 다만 BMI가 낮아 꾸준히 영양을 보충하면 좋아요."
                    Zone.HIGH   -> "체중은 정상인데 체지방이 많아요. 규칙적인 운동으로 건강을 유지해 보세요."
                }
                Zone.NORMAL -> when (fatZone) {
                    Zone.LOW    -> "아주 건강한 상태예요! 운동하면서 근육을 키우면 더 탄탄해질 수 있어요."
                    Zone.NORMAL -> "전체적으로 균형이 잘 맞아요. 지금처럼만 유지하면 건강하게 지낼 수 있어요!"
                    Zone.HIGH   -> "체중은 정상인데 체지방이 높은 편이에요. 단 음식 줄이고 운동하면 더 좋아져요."
                }
                Zone.HIGH -> when (fatZone) {
                    Zone.LOW    -> "BMI는 높지만 체지방은 적어요. 근육이 많은 체형일 수 있어요. 운동을 꾸준히 이어가세요!"
                    Zone.NORMAL -> "체중은 정상인데 BMI가 높아요. 식습관을 조금 더 관리하면 금방 좋아질 거예요."
                    Zone.HIGH   -> "체중은 정상인데 체지방과 BMI가 높아요. 꾸준한 관리로 조절해 보세요."
                }
            }

            // 3) 체중 = 초과 (6가지) — BMI=LOW는 이론상 불가라 안전문구 배치
            Zone.HIGH -> when (bmiZone) {
                Zone.LOW -> {
                    "수치 간 불일치가 있어 보여요. 측정값을 다시 확인해 주세요."
                }
                Zone.NORMAL -> when (fatZone) {
                    Zone.LOW    -> "체중은 많지만 BMI와 체지방이 안정적이에요. 근육량이 많을 수 있네요! 좋은 상태예요."
                    Zone.NORMAL -> "체중은 조금 많지만 건강한 편이에요. 꾸준한 운동으로 지금처럼 유지해 보세요."
                    Zone.HIGH   -> "체중은 많고 체지방도 살짝 높아요. 규칙적으로 운동하면 건강하게 조절할 수 있어요."
                }
                Zone.HIGH -> when (fatZone) {
                    Zone.LOW    -> "체중은 많고 BMI도 높지만 체지방은 적어요. 근육질 체형일 가능성이 있어요!"
                    Zone.NORMAL -> "체중과 BMI가 높아요. 식습관 관리와 함께 운동을 하면 더 좋아질 거예요."
                    Zone.HIGH   -> "체중, 체지방, BMI 모두 높은 편이에요. 생활 습관을 조절해서 건강을 지켜주세요!"
                }
            }
        }

        sb.append(caseMessage)
        // ▲▲▲ 21가지 상세 문구 매핑 끝 ▲▲▲

        return title to sb.toString()
    }

    /** 통합 분석 */
    fun analyze(input: AnalysisInput): AnalysisResult {
        val bmiVal = bmi(input.heightCm, input.weightKg)
        val bmiZone = zoneOf(bmiVal, 18.5, 24.9)
        val bmiBias = toBias(bmiVal, 14.0, 18.5, 24.9, 35.0)

        val weightG = weightGauge(input.heightCm, input.weightKg)

        val fatPct = estimateBodyFatPct(bmiVal, input.ageYears, input.isMale)
        val fatG = fatGauging(fatPct, bmiVal, input.isMale)

        val (title, body) = buildMessage(
            bmiZone,
            fatG.zone,
            weightG.zone,
            bmiVal,
            fatPct,
            input.ageYears
        )

        return AnalysisResult(
            bmi = round1(bmiVal),
            bmiGauge = Gauge(bmiZone, bmiBias),
            weightGauge = weightG,
            fatPct = fatPct?.let { round1(it) },
            fatGauge = fatG,
            messageTitle = title,
            messageBody = body
        )
    }

    private fun round1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
}
