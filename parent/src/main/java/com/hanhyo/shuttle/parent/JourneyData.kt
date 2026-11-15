package com.hanhyo.shuttle.parent

import org.json.JSONArray
import org.json.JSONObject
import java.time.*

enum class SchoolDirection(val api: String,val label: String) {
    Morning("PICK_UP","등원"), Afternoon("DROP_OFF","하원")
}

internal fun String.epochMillis(): Long = runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
    .recoverCatching { LocalDateTime.parse(this).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli() }.getOrDefault(0L)

/** JDBC DATE values can arrive as a UTC instant for midnight in the service timezone. */
internal fun String.serviceDateText(): String = runCatching { LocalDate.parse(this).toString() }
    .recoverCatching { OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate().toString() }
    .recoverCatching { LocalDateTime.parse(this).toLocalDate().toString() }.getOrDefault("")

/** Active trips take precedence. Without schedule metadata, noon is the display-only fallback. */
internal fun chooseDirection(morning: JSONObject?, afternoon: JSONObject?, now: LocalTime): SchoolDirection {
    fun active(row: JSONObject?) = row?.optString("tripStatus",row.optString("status")) in listOf("IN_PROGRESS","RETURNING")
    if(active(morning)) return SchoolDirection.Morning
    if(active(afternoon)) return SchoolDirection.Afternoon
    if(morning?.optString("tripStatus")=="COMPLETED") return SchoolDirection.Afternoon
    return if(now.isBefore(LocalTime.NOON)) SchoolDirection.Morning else SchoolDirection.Afternoon
}

internal fun normalizeJourney(row: JSONObject, direction: SchoolDirection, now: Long=System.currentTimeMillis()): JSONObject {
    val status=row.optString("tripStatus",row.optString("status","NO_TRIP_TODAY"))
    val tripId=row.optString("tripId")
    val childStatus=row.optString("childStatus","EXPECTED")
    val handed=childStatus=="HANDED_OVER"
    val dropped=childStatus=="DROPPED_OFF"
    val arrived=row.optString("stopStatus")=="ARRIVED"
    val active=tripId.isNotBlank() && status in listOf("IN_PROGRESS","RETURNING","COMPLETED")
    val locationAt=row.optString("locationUpdatedAt").epochMillis()
    val lat=row.optDouble("latitude",Double.NaN); val lon=row.optDouble("longitude",Double.NaN)
    val location=if(!dropped&&status !in listOf("COMPLETED","CANCELLED")&&lat.isFinite()&&lon.isFinite()&&locationAt>0) JSONObject().put("latitude",lat).put("longitude",lon).put("receivedAt",locationAt) else JSONObject.NULL
    val seconds=row.optInt("etaRemainingSec",-1)
    // Never refresh ETA age using an unrelated GPS update. The current API has no TTL/validity fields.
    val etaAt=row.optString("etaReceivedAt").epochMillis()
    // Local server and device clocks can differ by a few seconds. Clamp only that bounded
    // future skew to receipt time; never extend stale ETA using GPS or accept distant futures.
    val eta=if(seconds>=0&&etaAt>0&&now-etaAt in -ETA_CLOCK_SKEW_MS..JOURNEY_FRESHNESS_MS&&status=="IN_PROGRESS"&&!handed&&!dropped&&!arrived) JSONObject().put("seconds",seconds).put("minutes",kotlin.math.ceil(seconds/60.0).toInt())
        .put("receivedAt",minOf(etaAt,now)).put("kind","SERVER_REMAINING") else JSONObject.NULL
    val stopName=row.optString("stopName").ifBlank { "등록된 ${direction.label} 지점" }
    // Do not invent passed stops or a moving vehicle when the API only supplies one destination.
    val progress=JSONArray().put(JSONObject().put("name",stopName).put("visited",dropped).put("own",true))
    return JSONObject().put("tripId",tripId).put("direction",direction.api).put("childStatus",childStatus)
        .put("available",active).put("journeyStatus",when { handed->"HANDED_OVER";dropped->"COMPLETED";arrived->"ARRIVED";status=="COMPLETED"->"TRIP_ENDED";else->"IN_PROGRESS" })
        .put("phase",status).put("serviceDate",row.optString("serviceDate").serviceDateText()).put("serverTime",now/1000.0).put("clientReceivedAt",now)
        .put("location",location).put("source","SERVER").put("stale",location==JSONObject.NULL||now-locationAt>30_000)
        .put("startedAt",row.optString("startedAt").epochMillis()).put("completedAt",row.optString("droppedOffAt").epochMillis())
        .put("handedOverAt",row.optString("handedOverAt").epochMillis())
        .put("pickup",JSONObject().put("name",stopName)).put("progress",progress)
        .put("timeline",JSONObject().put("current",0).put("fraction",0).put("paused",true).put("ownEta",eta))
        .put("eta",eta).put("remainingStops",JSONObject.NULL).put("shares",JSONArray())
        .put("routeAvailable",false).put("connectionError",false)
        .put("etaReason",when {
            status !in listOf("IN_PROGRESS","RETURNING","COMPLETED") -> "차량 출발 전이에요."
            handed||dropped -> "인계·하차가 기록되었어요."
            arrived -> "도착 후 인계를 기다리고 있어요."
            status=="COMPLETED" -> "종료된 운행이에요."
            etaAt<=0||seconds<0 -> "우리 아이 정류장의 예상 시간이 아직 제공되지 않았어요."
            eta==JSONObject.NULL -> "예상 시간 갱신이 지연되고 있어요."
            else -> ""
        })
        .put("reason",if(tripId.isBlank()) "오늘 예정된 ${direction.label} 운행을 확인해 주세요." else "차량에서 출발하면 소식을 알려드려요.")
}

/** Retain only route fields, never the other children's records from trip detail. */
internal fun attachJourneyRoute(journey: JSONObject, trip: JSONObject, childId: String): JSONObject {
    if(trip.optString("id") != journey.optString("tripId") || trip.optString("direction") != journey.optString("direction")) return journey
    val ownStopId=trip.optJSONArray("children")?.objects()
        ?.singleOrNull { it.optString("childId")==childId }?.optString("tripStopId") ?: return journey
    val stops=trip.optJSONArray("stops")?.objects()?.sortedBy { it.optInt("seq",Int.MAX_VALUE) }.orEmpty()
    val ownIndex=stops.indexOfFirst { it.optString("id")==ownStopId }
    if(ownIndex<0 || stops.any { it.optString("stopName").isBlank() }) return journey
    val ownArrived=journey.optString("journeyStatus") in listOf("ARRIVED","HANDED_OVER","COMPLETED")
    val progress=JSONArray()
    stops.forEachIndexed { index, stop ->
        val own=index==ownIndex
        progress.put(JSONObject().put("name",stop.optString("stopName")).put("own",own)
            .put("visited",stop.optString("status")=="COMPLETED" || (own && journey.optString("childStatus")=="DROPPED_OFF"))
            .put("label",when {
                own && ownArrived -> if(journey.optString("childStatus")=="DROPPED_OFF") "우리 아이 하차 완료" else "우리 아이 정류장 도착"
                stop.optString("status")=="COMPLETED" -> "방문 완료"
                stop.optString("status")=="ARRIVED" -> "도착 확인"
                stop.optString("status")=="SKIPPED" -> "방문 제외"
                own -> "우리 아이 정류장"
                else -> "예정 정류장"
            }))
    }
    val confirmedIndex=if(ownArrived)ownIndex else stops.indexOfFirst { it.optString("status")=="ARRIVED" }
    return journey.put("progress",progress).put("routeAvailable",true)
        .put("confirmedStopIndex",confirmedIndex)
        .put("timeline",JSONObject().put("current",confirmedIndex.coerceAtLeast(0)).put("fraction",0).put("paused",true))
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Demo fallback only; never infer arrival or override a real ETA or terminal record. */
internal fun withDemoEta(data: JSONObject, enabled: Boolean, now: Long=System.currentTimeMillis()): JSONObject {
    if(!enabled || data.optBoolean("connectionError") || data.optString("phase")!="IN_PROGRESS" ||
        data.optString("journeyStatus")!="IN_PROGRESS" || data.optJSONObject("eta")!=null) return data
    val minutes=if(data.optString("direction")=="PICK_UP")15 else 6
    return JSONObject(data.toString()).put("eta",JSONObject().put("minutes",minutes).put("seconds",minutes*60)
        .put("receivedAt",now).put("kind","DEMO_SYNTHETIC")).put("etaReason","")
}

internal fun journeyCompletionTitle(data: JSONObject): String? = when(data.optString("journeyStatus")) {
    "HANDED_OVER" -> "오늘도 잘 마중했어요"
    "COMPLETED" -> if(data.optLong("handedOverAt")>0) "오늘도 잘 마중했어요" else "하차 기록이 확인됐어요"
    else -> null
}
internal fun requestTypeLabel(type: String)=when(type) { "NOT_RIDING"->"차량 이용 안 함";"ABSENT"->"결석";"SELF_PICKUP"->"직접 데리러 가요";"STOP_CHANGE"->"정류장 변경";else->type }
internal fun requestStatusLabel(status: String)=when(status) { "PENDING"->"확인 대기";"APPROVED"->"승인됨";"REJECTED"->"반려";"CANCELLED"->"취소됨";else->status }

// Conservative app fallback until the server supplies an explicit validity period.
internal const val JOURNEY_FRESHNESS_MS = 30_000L
internal const val ETA_CLOCK_SKEW_MS = 5_000L

internal fun journeyConnectionFailure(previous: JSONObject?): JSONObject =
    JSONObject(previous?.toString() ?: "{}").put("connectionError", true).put("stale", true)
        .put("eta", JSONObject.NULL).put("etaReason", "휴대폰에서 서버에 연결하지 못했어요. 다시 확인해 주세요.")
        .put("reason", "서버 연결에 실패했어요. 연결 후 다시 확인해 주세요.")
        .apply { optJSONObject("timeline")?.put("paused", true)?.put("ownEta", JSONObject.NULL) }
