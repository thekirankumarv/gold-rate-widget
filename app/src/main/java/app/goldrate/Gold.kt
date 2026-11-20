package app.goldrate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

object Gold {
    private const val XAU_URL = "https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAU/USD"
    private const val FX_URL = "https://query1.finance.yahoo.com/v8/finance/chart/USDINR=X"
    private const val IBJA_URL = "https://ibjarates.com/"
    private const val GRAMS_PER_OZ = 31.1034768
    private const val GST = 1.03

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gold", Context.MODE_PRIVATE)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun internationalPerGram(): Double {
        val q = JSONArray(get(XAU_URL)).getJSONObject(0)
            .getJSONArray("spreadProfilePrices").getJSONObject(0)
        val xau = (q.getDouble("bid") + q.getDouble("ask")) / 2
        val inr = JSONObject(get(FX_URL)).getJSONObject("chart").getJSONArray("result")
            .getJSONObject(0).getJSONObject("meta").getDouble("regularMarketPrice")
        return xau * inr / GRAMS_PER_OZ
    }

    private fun ibja(): Map<String, Float>? = runCatching {
        val html = get(IBJA_URL)
        listOf("999", "916", "750").associateWith { p ->
            Regex("""id="GoldRatesCompare$p">([0-9.]+)""").find(html)!!.groupValues[1].toFloat()
        }
    }.getOrNull()

    fun refresh(ctx: Context) {
        val p = prefs(ctx)
        val intl = internationalPerGram()
        val e = p.edit()
        val rates = ibja()
        if (rates != null && rates["999"] != p.getFloat("i999", 0f)) {
            e.putFloat("i999", rates["999"]!!)
                .putFloat("i916", rates["916"]!!)
                .putFloat("i750", rates["750"]!!)
                .putFloat("factor", (rates["999"]!! / intl).toFloat())
        }
        e.apply()
        val factor = p.getFloat("factor", 0f)
        check(factor > 0f) { "IBJA unavailable" }
        val live24 = intl * factor
        p.edit()
            .putFloat("p24", live24.toFloat())
            .putFloat("p22", (live24 * p.getFloat("i916", 0f) / p.getFloat("i999", 1f)).toFloat())
            .putFloat("p18", (live24 * p.getFloat("i750", 0f) / p.getFloat("i999", 1f)).toFloat())
            .putLong("time", System.currentTimeMillis())
            .apply()
    }

    fun render(ctx: Context, status: String? = null) {
        val p = prefs(ctx)
        val rupees = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
        fun price(key: String, mul: Double = 1.0) = p.getFloat(key, 0f).let { if (it > 0f) rupees.format(it * mul) else "—" }
        val time = p.getLong("time", 0L)
        val v = RemoteViews(ctx.packageName, R.layout.widget)
        v.setTextViewText(R.id.k24, price("p24"))
        v.setTextViewText(R.id.k22, price("p22"))
        v.setTextViewText(R.id.k18, price("p18"))
        v.setTextViewText(R.id.g24, price("p24", GST))
        v.setTextViewText(R.id.g22, price("p22", GST))
        v.setTextViewText(R.id.g18, price("p18", GST))
        v.setTextViewText(
            R.id.status,
            status ?: if (time > 0) "● Live · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) else "Tap ⟳ to load"
        )
        v.setTextColor(R.id.status, if (status == null && time > 0) 0xFF7FB77E.toInt() else 0xFF7A705E.toInt())
        val tap = Intent(ctx, GoldWidget::class.java).setAction(GoldWidget.REFRESH)
        v.setOnClickPendingIntent(
            R.id.refresh,
            PendingIntent.getBroadcast(ctx, 0, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
        AppWidgetManager.getInstance(ctx).updateAppWidget(ComponentName(ctx, GoldWidget::class.java), v)
    }
}
