package com.hanhyo.shuttle.parent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JourneyRouteTest {
    private fun journey(arrived:Boolean=false)=normalizeJourney(JSONObject().put("tripId","7").put("tripStatus","IN_PROGRESS")
        .put("stopStatus",if(arrived)"ARRIVED" else "PENDING"),SchoolDirection.Morning)
    private fun trip()=JSONObject("""{"id":7,"direction":"PICK_UP","ownStopId":20,"stops":[{"id":20,"seq":2,"stopName":"B","status":"PENDING"},{"id":10,"seq":1,"stopName":"A","status":"PENDING"},{"id":30,"seq":3,"stopName":"C","status":"PENDING"}]}""")
    @Test fun routeUsesSequenceAndChildStopId() {
        val result=attachJourneyRoute(journey(),trip(),"2")
        assertEquals(listOf("A","B","C"),result.getJSONArray("progress").objects().map { it.getString("name") })
        assertTrue(result.getJSONArray("progress").getJSONObject(1).getBoolean("own"))
        assertEquals(-1,result.getInt("confirmedStopIndex"))
        assertFalse(result.toString().contains("childName"))
    }
    @Test fun ownArrivalDoesNotInventEarlierVisits() {
        val result=attachJourneyRoute(journey(true),trip(),"2")
        assertEquals(1,result.getInt("confirmedStopIndex"))
        assertFalse(result.getJSONArray("progress").getJSONObject(0).getBoolean("visited"))
        assertTrue(result.getJSONObject("timeline").getBoolean("paused"))
    }
    @Test fun mismatchedTripOrChildKeepsSingleStopFallback() {
        assertFalse(attachJourneyRoute(journey(),trip().put("id",8),"2").getBoolean("routeAvailable"))
        assertFalse(attachJourneyRoute(journey(),trip().apply { remove("ownStopId") },"2").getBoolean("routeAvailable"))
    }
}
