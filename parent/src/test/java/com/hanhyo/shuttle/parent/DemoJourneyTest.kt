package com.hanhyo.shuttle.parent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DemoJourneyTest {
    private fun data(status:String="IN_PROGRESS",child:String="BOARDED")=normalizeJourney(JSONObject()
        .put("tripId","7").put("tripStatus",status).put("childStatus",child),SchoolDirection.Afternoon)
    @Test fun demoIsExplicitAndCanBeDisabled() {
        assertEquals(6,withDemoEta(data(),true).getJSONObject("eta").getInt("minutes"))
        assertTrue(withDemoEta(data(),false).isNull("eta"))
        assertEquals("DEMO_SYNTHETIC",withDemoEta(data(),true).getJSONObject("eta").getString("kind"))
    }
    @Test fun demoDoesNotOverrideRecordsOrRealEta() {
        for(child in listOf("HANDED_OVER","DROPPED_OFF")) assertTrue(withDemoEta(data(child=child),true).isNull("eta"))
        assertTrue(withDemoEta(data("PREPARING"),true).isNull("eta"))
        assertTrue(withDemoEta(data().put("journeyStatus","ARRIVED"),true).isNull("eta"))
        val real=data().put("eta",JSONObject().put("minutes",2))
        assertEquals(2,withDemoEta(real,true).getJSONObject("eta").getInt("minutes"))
    }
    @Test fun dropAloneDoesNotClaimHandover() {
        assertEquals("하차 기록이 확인됐어요",journeyCompletionTitle(data(child="DROPPED_OFF")))
        assertEquals("오늘도 잘 마중했어요",journeyCompletionTitle(data(child="DROPPED_OFF").put("handedOverAt",123)))
        assertEquals("오늘도 잘 마중했어요",journeyCompletionTitle(data(child="HANDED_OVER")))
        assertNull(journeyCompletionTitle(data()))
    }
}
