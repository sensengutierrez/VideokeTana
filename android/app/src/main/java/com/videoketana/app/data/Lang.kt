package com.videoketana.app.data

/** Isinasalin ang text ayon sa napiling wika (Settings → Wika). Tagalog ang default. */
fun tr(fil: String, en: String): String = if (Prefs.lang == "en") en else fil
