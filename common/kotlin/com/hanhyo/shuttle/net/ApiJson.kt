package com.hanhyo.shuttle.net

import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject

/** Kotlinx.serialization owns the wire format; org.json is only the legacy UI adapter. */
internal object ApiJson {
    private val codec = Json { isLenient = false }

    fun decodeObject(text: String): JSONObject {
        val element = codec.parseToJsonElement(text)
        require(element is JsonObject) { "서버 응답 형식이 올바르지 않습니다." }
        return element.legacyValue() as JSONObject
    }

    fun encodeObject(value: JSONObject): String = codec.encodeToString(JsonElement.serializer(), value.jsonElement())

    private fun JsonElement.legacyValue(): Any = when (this) {
        JsonNull -> JSONObject.NULL
        is JsonObject -> JSONObject().also { out -> forEach { (key, value) -> out.put(key, value.legacyValue()) } }
        is JsonArray -> JSONArray().also { out -> forEach { out.put(it.legacyValue()) } }
        is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }

    private fun Any?.jsonElement(): JsonElement = when {
        this == null || this === JSONObject.NULL -> JsonNull
        this is JSONObject -> JsonObject(keys().asSequence().associateWith { get(it).jsonElement() })
        this is JSONArray -> JsonArray((0 until length()).map { get(it).jsonElement() })
        this is Boolean -> JsonPrimitive(this)
        this is Number -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
}
