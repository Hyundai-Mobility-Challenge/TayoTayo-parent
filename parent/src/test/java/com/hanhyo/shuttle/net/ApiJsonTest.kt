package com.hanhyo.shuttle.net

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiJsonTest {
    @Test fun preservesServerIdsNullsAndNestedPayloads() {
        val payload = """{"tripId":9007199254740993,"etaRemainingSec":null,"children":[{"name":"가상 아이","active":true}],"distance":12.5}"""
        val decoded = ApiJson.decodeObject(payload)
        assertEquals(9007199254740993L, decoded.getLong("tripId"))
        assertTrue(decoded.isNull("etaRemainingSec"))
        assertTrue(decoded.getJSONArray("children").getJSONObject(0).getBoolean("active"))
        val roundTrip = ApiJson.decodeObject(ApiJson.encodeObject(decoded))
        assertEquals("가상 아이", roundTrip.getJSONArray("children").getJSONObject(0).getString("name"))
        assertEquals(12.5, roundTrip.getDouble("distance"), 0.0)
    }

    @Test fun serializesEscapesWithoutChangingRequestValues() {
        val request = JSONObject().put("reasonText", "첫 줄\n\"두 번째\"\\끝").put("id", "0012")
        val decoded = ApiJson.decodeObject(ApiJson.encodeObject(request))
        assertEquals(request.getString("reasonText"), decoded.getString("reasonText"))
        assertEquals("0012", decoded.getString("id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonObjectApiResponse() { ApiJson.decodeObject("[1,2,3]") }
}
