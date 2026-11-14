package com.hanhyo.shuttle.parent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** State-level rehearsal using API-shaped inputs, not a live vehicle or Pleos simulation. */
class ParentDemoScenarioTest {
    private val start = "2026-11-14T15:00:00+09:00".epochMillis()
    private fun row(trip: String = "2", status: String = "IN_PROGRESS", child: String = "BOARDED",
        stop: String = "PENDING", seconds: Int = 120, time: String = "2026-11-14T15:00:00+09:00") =
        JSONObject().put("tripId",trip).put("tripStatus",status).put("childStatus",child)
            .put("stopStatus",stop).put("stopName","내 아이 하차 지점").put("startedAt",time)
            .put("latitude",37.5).put("longitude",127.0).put("locationUpdatedAt",time)
            .put("etaRemainingSec",seconds).put("etaReceivedAt",time)
    private fun normalize(row: JSONObject, now: Long = start) = normalizeJourney(row, SchoolDirection.Afternoon, now)

    @Test fun departureEtaArrivalHandoverDropoffAndEndStayDistinct() {
        val preparing = normalize(row(status="PREPARING",child="EXPECTED"))
        assertFalse(preparing.getBoolean("available")); assertTrue(preparing.isNull("eta"))
        val departed = normalize(row())
        assertTrue(departed.getBoolean("available")); assertEquals(2,departed.getJSONObject("eta").getInt("minutes"))
        assertEquals(start,departed.getLong("startedAt"))
        val next = normalize(row(seconds=60,time="2026-11-14T15:00:05+09:00"),start+5_000)
        assertEquals(1,next.getJSONObject("eta").getInt("minutes"))
        val arrived = normalize(row(stop="ARRIVED"))
        assertEquals("ARRIVED",arrived.getString("journeyStatus")); assertTrue(arrived.isNull("eta"))
        val handed = normalize(row(child="HANDED_OVER",stop="ARRIVED"))
        assertEquals("HANDED_OVER",handed.getString("journeyStatus"))
        assertFalse(handed.getJSONArray("progress").getJSONObject(0).getBoolean("visited"))
        val dropped = normalize(row(child="DROPPED_OFF",stop="ARRIVED"))
        assertEquals("COMPLETED",dropped.getString("journeyStatus"))
        assertTrue(dropped.getJSONArray("progress").getJSONObject(0).getBoolean("visited"))
        assertTrue(dropped.isNull("location")); assertTrue(dropped.isNull("eta"))
        assertEquals("COMPLETED",normalize(row(status="COMPLETED",child="DROPPED_OFF")).getString("journeyStatus"))
        assertEquals("TRIP_ENDED",normalize(row(status="COMPLETED")).getString("journeyStatus"))
    }
    @Test fun zeroEtaAndNearbyCoordinatesNeverReplaceTeacherRecords() {
        val data = normalize(row(seconds=0))
        assertEquals("IN_PROGRESS",data.getString("journeyStatus"))
        assertFalse(data.getJSONArray("progress").getJSONObject(0).getBoolean("visited"))
        assertFalse(data.getBoolean("routeAvailable")); assertTrue(data.isNull("remainingStops"))
        assertTrue(data.getJSONObject("timeline").getBoolean("paused"))
    }
    @Test fun connectionLossHidesEtaAndRecoveryUsesNewServerSample() {
        val before = normalize(row())
        val failed = journeyConnectionFailure(before)
        assertTrue(failed.isNull("eta")); assertTrue(failed.getBoolean("connectionError"))
        assertEquals("IN_PROGRESS",failed.getString("journeyStatus"))
        val recovered = normalize(row(seconds=45,time="2026-11-14T15:00:10+09:00"),start+10_000)
        assertFalse(recovered.getBoolean("connectionError")); assertEquals(45,recovered.getJSONObject("eta").getInt("seconds"))
    }
    @Test fun liveShareEndsOnlyAfterDropoffIsObservedAndNewTripHasNoOldShare() {
        val saved = mutableMapOf<String,String>()
        val revoked = mutableListOf<String>()
        val shares = JourneyShares({saved[it]}, {key,value->if(value==null)saved.remove(key)else saved[key]=value},
            {path,_,method->if(method=="POST") {revoked+=path;JSONObject()} else JSONObject().put("id",9).put("url","/share/test")})
        shares.create("1","2",row())
        assertEquals(1,shares.reconcile("1",normalize(row(stop="ARRIVED"))).getJSONArray("shares").length())
        assertEquals(1,shares.reconcile("1",normalize(row(child="HANDED_OVER"))).getJSONArray("shares").length())
        assertTrue(revoked.isEmpty())
        val dropped=shares.reconcile("1",normalize(row(child="DROPPED_OFF")))
        assertEquals(0,dropped.getJSONArray("shares").length())
        assertEquals(listOf("/api/v1/guardian/share-links/9/revoke"),revoked)
        val next=shares.reconcile("1",normalize(row(trip="3")))
        assertEquals("IN_PROGRESS",next.getString("journeyStatus"));assertEquals(0,next.getJSONArray("shares").length())
    }
}
