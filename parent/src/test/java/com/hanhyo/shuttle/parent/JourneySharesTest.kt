package com.hanhyo.shuttle.parent

import com.hanhyo.shuttle.net.ApiFailure
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JourneySharesTest {
    private val saved = mutableMapOf<String, String>()
    private val calls = mutableListOf<String>()
    private var nextId = 0
    private var failure: String? = null
    private var status = 503
    private val manager = JourneyShares({saved[it]}, { key, value ->
        if (value == null) saved.remove(key) else saved[key] = value
    }, { path, _, method ->
        calls += path
        if (path == failure) throw ApiFailure(status, "test failure")
        if (method == "POST") JSONObject() else JSONObject().put("id", ++nextId).put("url", "/share/$nextId")
    })
    private fun active() = JSONObject().put("tripId", "2").put("tripStatus", "IN_PROGRESS").put("childStatus", "BOARDED")
    private fun data(child: String = "BOARDED", phase: String = "IN_PROGRESS") = JSONObject()
        .put("tripId", "2").put("childStatus", child).put("phase", phase)

    @Test fun everyCreatedLinkIsRevokedAfterDropoff() {
        repeat(2) { manager.create("1", "2", active()) }
        val result = manager.reconcile("1", data("DROPPED_OFF"))
        assertEquals(0, result.getJSONArray("shares").length())
        assertTrue(calls.contains("/api/v1/guardian/share-links/1/revoke"))
        assertTrue(calls.contains("/api/v1/guardian/share-links/2/revoke"))
        assertTrue(manager.ids("1", "2").isEmpty())
    }
    @Test fun handoverDoesNotPretendDropoffOrRevokeBeforeRecord() {
        manager.create("1", "2", active())
        assertEquals(1, manager.reconcile("1", data("HANDED_OVER")).getJSONArray("shares").length())
        assertFalse(calls.any { it.endsWith("/revoke") })
        val normalized = normalizeJourney(active().put("childStatus", "HANDED_OVER"), SchoolDirection.Afternoon)
        assertFalse(normalized.getJSONArray("progress").getJSONObject(0).getBoolean("visited"))
    }
    @Test fun failureKeepsOnlyUnconfirmedIdsAndRetries() {
        repeat(2) { manager.create("1", "2", active()) }
        failure = "/api/v1/guardian/share-links/2/revoke"
        val result = manager.reconcile("1", data("DROPPED_OFF"))
        assertTrue(result.getBoolean("shareCleanupError"))
        assertEquals(listOf("2"), manager.ids("1", "2"))
        failure = null
        assertFalse(manager.reconcile("1", data("DROPPED_OFF")).getBoolean("shareCleanupError"))
        assertEquals(1, calls.count { it == "/api/v1/guardian/share-links/1/revoke" })
    }
    @Test fun alreadyRevokedLinkIsIdempotent() {
        saved["share:1:2"] = "9"
        failure = "/api/v1/guardian/share-links/9/revoke"; status = 404
        manager.revoke("1", "2")
        assertTrue(manager.ids("1", "2").isEmpty())
    }
    @Test fun authorizationFailureDoesNotDiscardLink() {
        saved["share:1:2"] = "9"
        failure = "/api/v1/guardian/share-links/9/revoke"; status = 403
        assertTrue(manager.reconcile("1", data("DROPPED_OFF")).getBoolean("shareCleanupError"))
        assertEquals(listOf("9"), manager.ids("1", "2"))
    }
    @Test fun completedTripRevokesEvenWithoutDropoffRecord() {
        saved["share:1:2"] = "9"
        assertEquals(0, manager.reconcile("1", data(phase="COMPLETED")).getJSONArray("shares").length())
    }
    @Test fun endedWrongTripAndHandoverCannotCreateShare() {
        for (row in listOf(active().put("tripStatus", "COMPLETED"), active().put("tripId", "3"),
            active().put("childStatus", "DROPPED_OFF"), active().put("childStatus", "HANDED_OVER"))) {
            try { manager.create("1", "2", row); fail("Must reject invalid state") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(calls.isEmpty())
    }
    @Test fun otherTripsAndChildrenAreUnaffected() {
        saved["share:1:3"] = "8"; saved["share:4:2"] = "9"
        manager.reconcile("1", data("DROPPED_OFF"))
        assertEquals("8", saved["share:1:3"]); assertEquals("9", saved["share:4:2"])
    }
    @Test fun terminalRecordRemovesLiveLocationButNotTeacherRecord() {
        val row = active().put("childStatus", "DROPPED_OFF").put("latitude",37.5).put("longitude",127.0)
            .put("locationUpdatedAt","2026-11-14T12:00:00+09:00")
        val result = normalizeJourney(row, SchoolDirection.Afternoon)
        assertTrue(result.isNull("location")); assertTrue(result.isNull("eta"))
        assertEquals("COMPLETED", result.getString("journeyStatus"))
    }
}
