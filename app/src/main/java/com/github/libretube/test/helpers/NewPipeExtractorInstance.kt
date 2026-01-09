package com.github.libretube.test.helpers

import com.github.libretube.test.util.NewPipeDownloaderImpl
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.constants.PreferenceKeys
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.localization.ContentCountry

import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

object NewPipeExtractorInstance {
    val extractor: StreamingService by lazy {
        NewPipe.getService(ServiceList.YouTube.serviceId)
    }

    fun init() {
        val region = PreferenceHelper.getString(PreferenceKeys.REGION, "GB")
        val language = Locale.getDefault().toLanguageTag()
        NewPipe.init(
            NewPipeDownloaderImpl(),
            Localization(region, language)
        )
    }
}
