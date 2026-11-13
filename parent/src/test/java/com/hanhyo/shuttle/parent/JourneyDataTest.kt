package com.hanhyo.shuttle.parent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class JourneyDataTest {
    private fun row(status:String="IN_PROGRESS",child:String="BOARDED")=JSONObject().put("tripId","trip-1").put("tripStatus",status).put("childStatus",child)
    @Test fun locationAndEtaKeepEpochMilliseconds() {
        val time="2026-11-12T08:20:00+09:00";val now=time.epochMillis()
        val data=normalizeJourney(row().put("latitude",37.5).put("longitude",127.0).put("locationUpdatedAt",time).put("etaRemainingSec",300).put("etaReceivedAt",time),SchoolDirection.Morning,now)
        assertEquals(now,data.getJSONObject("location").getLong("receivedAt"))
        assertEquals(now,data.getJSONObject("eta").getLong("receivedAt"))
        assertFalse(data.getBoolean("stale"))
    }
    @Test fun tripEndDoesNotClaimChildHandover() {
        assertEquals("TRIP_ENDED",normalizeJourney(row("COMPLETED"),SchoolDirection.Afternoon).getString("journeyStatus"))
        assertEquals("COMPLETED",normalizeJourney(row(child="DROPPED_OFF"),SchoolDirection.Afternoon).getString("journeyStatus"))
        assertEquals("HANDED_OVER",normalizeJourney(row(child="HANDED_OVER"),SchoolDirection.Afternoon).getString("journeyStatus"))
    }
    @Test fun arrivalRequiresServerStopStatus() {
        assertEquals("ARRIVED",normalizeJourney(row().put("stopStatus","ARRIVED"),SchoolDirection.Morning).getString("journeyStatus"))
        assertEquals("IN_PROGRESS",normalizeJourney(row().put("etaRemainingSec",0),SchoolDirection.Morning).getString("journeyStatus"))
    }
    @Test fun activeMorningTripDoesNotSwitchAtNoon() {
        assertEquals(SchoolDirection.Morning,chooseDirection(row(),row("SCHEDULED"),LocalTime.of(13,0)))
        assertEquals(SchoolDirection.Afternoon,chooseDirection(row("COMPLETED"),row("SCHEDULED"),LocalTime.of(10,0)))
    }
    @Test fun timeFallbackDoesNotInventTrips() {
        assertEquals(SchoolDirection.Morning,chooseDirection(null,null,LocalTime.of(8,0)))
        assertEquals(SchoolDirection.Afternoon,chooseDirection(null,null,LocalTime.of(12,0)))
        val data=normalizeJourney(JSONObject(),SchoolDirection.Morning)
        assertFalse(data.getBoolean("available"));assertTrue(data.isNull("eta"))
    }
    @Test fun absentRouteDoesNotCreateProgressOrRemainingStopCount() {
        val data=normalizeJourney(row(),SchoolDirection.Afternoon)
        assertEquals(1,data.getJSONArray("progress").length())
        assertTrue(data.getJSONObject("timeline").getBoolean("paused"))
        assertTrue(data.isNull("remainingStops"))
    }
    @Test fun localServerDateUsesKoreanTimezone() {
        assertEquals("2026-11-12T08:20:00+09:00".epochMillis(),"2026-11-12T08:20:00".epochMillis())
    }
}
