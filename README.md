# Gallery Home

A Google TV home screen for a Hisense CanvasTV. The Market Gallery painting fills the screen, and a row of app tiles slides up from the bottom.

## Remote

| Button | What it does |
|---|---|
| ◀ ▶ | Previous / next painting |
| ▼ | Show your apps; ▲ or Back hides them |
| OK | Gallery settings (tickers, seconds per painting) |
| Hold OK | Full price chart for the current stock; hold again to go back |
| Play/Pause | Pause or resume the rotation |
| Fast-forward / Rewind | Next / previous painting |

## Install

Every push to `main` builds the app on GitHub and publishes it to the `latest` release:
`https://github.com/vd-spec123/gallery-home/releases/download/latest/GalleryHome.apk`

On the TV, install the free **Downloader** app, allow it to install unknown apps, and enter that address.

## Make it the home screen

From a computer on the same Wi-Fi, with ADB and the TV's Developer options → USB debugging turned on:

```
adb connect <TV IP address>
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

## Undo

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```
