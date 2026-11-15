package com.hanhyo.shuttle.parent

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.hanhyo.shuttle.net.Api
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TayoTayoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) return
        val session = Push.prefs(this)
        val saved = session.getString("base", null)
        val invalidOrigin = saved != null && runCatching { Api.base(saved, false) }.isFailure
        val tokenWithoutOrigin = saved == null && session.contains("token")
        if (invalidOrigin || tokenWithoutOrigin) {
            // A session issued by the old HTTP server cannot be moved to an HTTPS server.
            session.edit().clear().apply()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }.build()
}
