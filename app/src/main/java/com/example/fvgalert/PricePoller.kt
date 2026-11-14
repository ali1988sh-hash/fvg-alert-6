package com.example.fvgalert

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** قیمت لحظه‌ای طلا (XAU/USD) از xaus.com: رایگان، بدون کلید، بدون سقف روزانه. سرور تا ۳۰ ثانیه کش می‌کند. */
class PricePoller(private val onPrice: (Double) -> Unit) {
    @Volatile var connected = false
    @Volatile private var lastOk = 0L
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        running = true
        thread = Thread { loop() }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        connected = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            try {
                val p = fetch()
                lastOk = System.currentTimeMillis()
                connected = true
                onPrice(p)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                // تا ۶۰ ثانیه خطای کوتاه را تحمل می‌کنیم؛ بعدش سرویس به حالت پشتیبان می‌رود
                if (System.currentTimeMillis() - lastOk > 60000L) connected = false
            }
            try { Thread.sleep(10000) } catch (e: InterruptedException) { break }
        }
    }

    private fun fetch(): Double {
        val conn = URL("https://xaus.com/api/v1/spot").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = try {
            if (conn.responseCode >= 400) throw RuntimeException("HTTP " + conn.responseCode)
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
        val j = JSONObject(body)
        val stale = j.optJSONObject("data_state")?.optBoolean("stale", false) ?: false
        if (stale) throw RuntimeException("stale")
        val p = j.getDouble("spot_usd_oz")
        if (p <= 0.0) throw RuntimeException("bad price")
        return p
    }
}
