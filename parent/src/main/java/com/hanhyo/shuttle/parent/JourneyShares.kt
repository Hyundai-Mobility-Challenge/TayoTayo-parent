package com.hanhyo.shuttle.parent

import com.hanhyo.shuttle.net.ApiFailure
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.sharingEnded() = optString("childStatus") == "DROPPED_OFF" ||
    optString("tripStatus", optString("phase")) in listOf("COMPLETED", "CANCELLED")

/** Retains every locally created link until the server confirms revocation. */
internal class JourneyShares(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
    private val request: (String, JSONObject?, String?) -> JSONObject
) {
    private fun key(child: String, trip: String) = "share-links:$child:$trip"
    fun ids(child: String, trip: String): List<String> =
        (read(key(child, trip))?.let { JSONArray(it).objects().map { row -> row.getString("id") } }.orEmpty() +
            listOfNotNull(read("share:$child:$trip"))).distinct()

    private fun save(child: String, trip: String, ids: List<String>) {
        write(key(child, trip), if (ids.isEmpty()) null else JSONArray().apply {
            ids.forEach { put(JSONObject().put("id", it)) }
        }.toString())
        write("share:$child:$trip", null)
    }

    fun create(child: String, trip: String, latest: JSONObject): JSONObject {
        require(latest.optString("tripId") == trip && latest.optString("tripStatus") == "IN_PROGRESS" &&
            latest.optString("childStatus") !in listOf("HANDED_OVER", "DROPPED_OFF") && !latest.sharingEnded()) {
            "운행 중인 아이만 공유할 수 있어요. 최신 운행 상태를 확인해 주세요."
        }
        val result = request("/api/v1/guardian/children/$child/trips/$trip/share-links", JSONObject(), null)
        val id = result.optString("id")
        check(id.toLongOrNull()?.let { it > 0 } == true) { "공유 링크 응답을 확인할 수 없어요." }
        save(child, trip, (ids(child, trip) + id).distinct())
        return result
    }

    fun revoke(child: String, trip: String) {
        var remaining = ids(child, trip)
        for (id in remaining.toList()) {
            try { request("/api/v1/guardian/share-links/$id/revoke", null, "POST") }
            catch (e: ApiFailure) { if (e.status != 404) throw e }
            remaining = remaining - id
            save(child, trip, remaining)
        }
    }

    fun reconcile(child: String, data: JSONObject): JSONObject {
        val trip = data.optString("tripId")
        var failed = false
        if (trip.isNotBlank() && data.sharingEnded()) {
            try { revoke(child, trip) } catch (_: Exception) { failed = true }
        }
        return JSONObject(data.toString()).put("shares", JSONArray().apply {
            ids(child, trip).forEach { put(JSONObject().put("id", it)) }
        }).put("shareCleanupError", failed)
    }
}
