# Weather

Sci-fi HUD (Jarvis style) weather app for Android 8.0+. Same design language as Dialer, Scanner, Keyboard, Launcher and Messages.

- Package: `com.mux.android.weather` - Version: `1.0.0`
- Data: [Open-Meteo](https://open-meteo.com) (free, no API key)

## Features
- NOW: temperature, feels like, condition icon, high / low, humidity, wind and gusts, pressure, cloud cover, rain, rain chance, UV, air quality (US AQI, PM2.5, PM10), sunrise / sunset
- FORECAST: next 24 hours with temperature curve, rain chance per hour, and 7-day list with temperature range bars
- PLACES: search any city, saved places, "my location" (last known position), units (C / F, km/h / mph), cyan / amber palette
- Last result is saved, so the app shows an offline copy without internet. Refreshes automatically when older than 15 minutes
- SYS tab: app info, update check, author

## Notes
- "MY LOCATION" uses the phone's last known position (asks location permission once). If the phone has none yet, search a city instead.
- Default place is Dhaka.

## Build
`gradle assembleDebug` (Gradle 8.9, JDK 17) or the GitHub Action. `app/debug.keystore` is shared with the other apps.
Updates are read from GitHub releases of `mahfuz0712/Weather` (edit `AppInfo.kt`).
