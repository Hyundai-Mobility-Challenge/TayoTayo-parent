package com.hanhyo.shuttle.parent

import android.content.Context
import com.hanhyo.shuttle.net.Api
import com.hanhyo.shuttle.net.ApiFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Data-layer boundary. Blocking calls are invoked on Dispatchers.IO by the ViewModel. */
@Singleton
class GuardianRepository @Inject constructor(@ApplicationContext context: Context) {
    val session = Push.prefs(context)

    fun publicRequest(base: String, path: String, data: JSONObject? = null): JSONObject =
        Api.request(base, path, data = data)

    fun request(base: String, path: String, data: JSONObject? = null, method: String? = null): JSONObject {
        val token = session.getString("token", "").orEmpty()
        return try { Api.request(base, path, token, data, method) }
        catch (e: ApiFailure) {
            if (e.status != 401) throw e
            val refresh = session.getString("refreshToken", "").orEmpty()
            if (refresh.isBlank()) throw e
            val renewed = Api.request(base, "/api/v1/auth/refresh", data = JSONObject().put("refreshToken", refresh))
            session.edit().putString("token", renewed.getString("accessToken"))
                .putString("refreshToken", renewed.getString("refreshToken")).commit()
            Api.request(base, path, renewed.getString("accessToken"), data, method)
        }
    }
}
