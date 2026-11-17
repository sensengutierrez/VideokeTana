# Videoke Android app

Buong-screen na app para sa phone, tablet at Smart TV (Android TV / Mi TV). Binubuksan nito ang parehong app na nasa `frontend/`, kaya sabay ang updates ng app at ng website.

Ginagawa ng GitHub Actions (`.github/workflows/android.yml`) ang APK tuwing may binago sa `android/`, at inilalagay sa Releases:
https://github.com/sensengutierrez/VideokeTana/releases/latest/download/Videoke.apk

Pansarili ang `videoke-personal.keystore` (para iisa ang pirma ng bawat build at ma-update ang app nang hindi binubura). Para sa Play Store, gumamit ng pribadong susi sa GitHub Secrets.
