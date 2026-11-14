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
    @Test fun freshGpsCannotMakeMissingOrExpiredEtaFresh() {
        val now="2026-11-12T08:20:00+09:00".epochMillis()
        val base=row().put("latitude",37.5).put("longitude",127.0)
            .put("locationUpdatedAt","2026-11-12T08:20:00+09:00").put("etaRemainingSec",120)
        assertTrue(normalizeJourney(base,SchoolDirection.Morning,now).isNull("eta"))
        base.put("etaReceivedAt","2026-11-12T08:19:00+09:00")
        val result=normalizeJourney(base,SchoolDirection.Morning,now)
        assertTrue(result.isNull("eta"))
        assertTrue(result.getString("etaReason").contains("지연"))
    }
    @Test fun futureEtaIsRejectedAndCompletionIsNotArrival() {
        val now="2026-11-12T08:20:00+09:00".epochMillis()
        val data=normalizeJourney(row().put("etaRemainingSec",0).put("etaReceivedAt","2026-11-12T08:21:00+09:00")
            .put("stopStatus","COMPLETED"),SchoolDirection.Afternoon,now)
        assertTrue(data.isNull("eta"))
        assertEquals("IN_PROGRESS",data.getString("journeyStatus"))
    }
    @Test fun connectionFailureRemovesEtaWithoutInventingCompletion() {
        val now="2026-11-12T08:20:00+09:00".epochMillis()
        val data=normalizeJourney(row().put("etaRemainingSec",30).put("etaReceivedAt","2026-11-12T08:20:00+09:00"),SchoolDirection.Afternoon,now)
        val failed=journeyConnectionFailure(data)
        assertTrue(failed.isNull("eta"))
        assertTrue(failed.getJSONObject("timeline").isNull("ownEta"))
        assertTrue(failed.getBoolean("connectionError"))
        assertEquals("IN_PROGRESS",failed.getString("journeyStatus"))
        assertFalse(data.isNull("eta")) // Keep immutable previous snapshots.
    }
    @Test fun notificationIdsMustBePositiveServerIds() {
        assertEquals(NotificationTarget("12","34"),NotificationTarget.from("12","34"))
        assertNull(NotificationTarget.from(null,"34"))
        assertNull(NotificationTarget.from("0","34"))
        assertNull(NotificationTarget.from("12","../34"))
        assertNull(NotificationTarget.from("-1","34"))
        assertNull(NotificationTarget.from("12","999999999999999999999"))
    }
}
