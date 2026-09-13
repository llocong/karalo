package com.karalo.karalo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.karalo.core.ui.image.buildKaraloImageLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KaraloApplication :
    Application(),
    ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = buildKaraloImageLoader(this)
}
