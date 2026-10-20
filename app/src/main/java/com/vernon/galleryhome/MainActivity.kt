package com.vernon.galleryhome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Gallery Home: a Google TV home screen.
 * The Market Gallery painting fills the screen; press ▼ on the remote for a row of app tiles.
 */
class MainActivity : Activity() {

    companion object {
        const val GALLERY_URL = "https://meek-trifle-ba8141.netlify.app/?app=1"
        const val LONG_PRESS_MS = 600L
        const val DOCK_IDLE_MS = 20_000L
        const val RELOAD_AFTER_MS = 6 * 60 * 60 * 1000L   // refresh the page every 6 hours

        // Apps that go first in the row, in this order, if installed.
        val FAVORITES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvunplugged",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.hulu.livingroomplus",
            "com.wbd.stream",
            "com.peacocktv.peacockandroid",
            "com.spotify.tv.android",
            "de.ozerov.fully",
        )
    }

    private lateinit var web: WebView
    private lateinit var dock: View
    private lateinit var tiles: LinearLayout
    private lateinit var hint: TextView
    private lateinit var offline: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val hideDockLater = Runnable { hideDock() }
    private val retryLoad = Runnable { web.loadUrl(GALLERY_URL) }

    private var centerDownAt = 0L
    private var centerLongFired = false
    private var loadedAt = 0L
    private var pageFailed = false

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#1C222B")) }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.parseColor("#1C222B"))
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!pageFailed) offline.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageFailed = true
                        offline.visibility = View.VISIBLE
                        handler.removeCallbacks(retryLoad)
                        handler.postDelayed(retryLoad, 30_000)
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            }
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        offline = TextView(this).apply {
            text = "Gallery is offline. Checking the connection again in 30 seconds.\nPress ▼ for your apps."
            setTextColor(Color.parseColor("#E9E5DC"))
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1C222B"))
            visibility = View.GONE
        }
        root.addView(offline, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // App dock along the bottom edge.
        tiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48f), dp(18f), dp(48f), dp(30f))
            clipToPadding = false
            clipChildren = false
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            addView(tiles)
        }
        val dockLabel = TextView(this).apply {
            text = "Apps"
            setTextColor(Color.parseColor("#9AA3B0"))
            textSize = 15f
            letterSpacing = 0.12f
            setPadding(dp(52f), dp(14f), 0, 0)
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#001C222B"), Color.parseColor("#E6141920"))
            )
            clipChildren = false
            addView(dockLabel)
            addView(scroller)
            visibility = View.GONE
        }
        root.addView(dock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        hint = TextView(this).apply {
            text = "▼ Apps    ◀ ▶ Paintings    Hold OK: Chart"
            setTextColor(Color.parseColor("#C9CFD8"))
            textSize = 14f
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(Color.parseColor("#B3141920")) }
        }
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(20f) })

        setContentView(root)
        goImmersive()
        loadGallery()
        flashHint()
    }

    private fun goImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun loadGallery() {
        pageFailed = false
        loadedAt = SystemClock.elapsedRealtime()
        web.loadUrl(GALLERY_URL)
        web.requestFocus()
    }

    private fun flashHint() {
        hint.visibility = View.VISIBLE
        hint.alpha = 1f
        hint.animate().alpha(0f).setStartDelay(6000).setDuration(800).withEndAction { hint.visibility = View.GONE }.start()
    }

    private fun js(code: String) = web.evaluateJavascript(code, null)

    // ---------- app dock ----------

    private fun buildTiles() {
        tiles.removeAllViews()
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps: List<ResolveInfo> = pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
        val ordered = apps.sortedWith(compareBy<ResolveInfo>(
            { FAVORITES.indexOf(it.activityInfo.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { it.loadLabel(pm).toString().lowercase() }
        ))
        ordered.forEach { info ->
            val label = info.loadLabel(pm).toString()
            val art = info.activityInfo.loadBanner(pm) ?: info.activityInfo.applicationInfo.loadBanner(pm)
            val icon = art ?: info.loadIcon(pm)
            addTile(label, icon, art != null) {
                val launch = pm.getLeanbackLaunchIntentForPackage(info.activityInfo.packageName)
                    ?: pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                launch?.let { start(it) }
            }
        }
        addTile("Settings", getDrawable(android.R.drawable.ic_menu_preferences), false) { start(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun addTile(label: String, art: android.graphics.drawable.Drawable?, isBanner: Boolean, onClick: () -> Unit) {
        val w = dp(176f); val h = dp(99f)
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(Color.TRANSPARENT) }
        }
        val img = ImageView(this).apply {
            setImageDrawable(art)
            scaleType = if (isBanner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (!isBanner) setPadding(dp(26f), dp(18f), dp(26f), dp(18f))
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(Color.parseColor("#2A323E")) }
            clipToOutline = true
        }
        val text = TextView(this).apply {
            this.text = label
            setTextColor(Color.parseColor("#E9E5DC"))
            textSize = 14f
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
            alpha = 0f
        }
        frame.addView(img, LinearLayout.LayoutParams(w, h))
        frame.addView(text, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.setOnClickListener { onClick() }
        frame.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.12f else 1f).scaleY(if (has) 1.12f else 1f).translationZ(if (has) 12f else 0f).setDuration(150).start()
            (v.background as GradientDrawable).setStroke(if (has) dp(3f) else 0, Color.parseColor("#D9B765"))
            text.animate().alpha(if (has) 1f else 0f).setDuration(150).start()
            if (has) bumpDockTimer()
        }
        tiles.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(18f) })
    }

    private fun start(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideDock()
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    private fun showDock() {
        buildTiles()
        dock.visibility = View.VISIBLE
        dock.translationY = dp(60f).toFloat()
        dock.alpha = 0f
        dock.animate().translationY(0f).alpha(1f).setDuration(220).start()
        hint.visibility = View.GONE
        tiles.getChildAt(0)?.requestFocus()
        bumpDockTimer()
    }

    private fun hideDock() {
        handler.removeCallbacks(hideDockLater)
        if (dock.visibility != View.VISIBLE) return
        dock.animate().translationY(dp(60f).toFloat()).alpha(0f).setDuration(200).withEndAction { dock.visibility = View.GONE }.start()
        web.requestFocus()
    }

    private fun bumpDockTimer() {
        handler.removeCallbacks(hideDockLater)
        handler.postDelayed(hideDockLater, DOCK_IDLE_MS)
    }

    // ---------- remote ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        if (dock.visibility == View.VISIBLE) {
            bumpDockTimer()
            if (down && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_BACK)) { hideDock(); return true }
            if (code == KeyEvent.KEYCODE_BACK) return true
            return super.dispatchKeyEvent(event)
        }

        when (code) {
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) showDock(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down) {
                    if (event.repeatCount == 0) { centerDownAt = event.downTime; centerLongFired = false }
                    else if (!centerLongFired && event.eventTime - centerDownAt >= LONG_PRESS_MS) {
                        centerLongFired = true
                        js("window.mgToggleChart && mgToggleChart()")
                    }
                } else if (!centerLongFired) {
                    js("window.mgOpenSettings && mgOpenSettings()")
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                // This is the home screen: Back only closes the chart or the settings panel.
                if (down) js("window.mgBack && mgBack()")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down) js("window.mgTogglePause && mgTogglePause()")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) js("window.mgStep && mgStep(1)")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) js("window.mgStep && mgStep(-1)")
                return true
            }
        }
        if (!web.hasFocus()) web.requestFocus()
        return super.dispatchKeyEvent(event)
    }

    // Pressing Home while already home: tidy up.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hideDock()
        js("window.mgBack && mgBack()")
    }

    override fun onResume() {
        super.onResume()
        goImmersive()
        web.onResume()
        if (SystemClock.elapsedRealtime() - loadedAt > RELOAD_AFTER_MS) loadGallery()
        web.requestFocus()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }
}
