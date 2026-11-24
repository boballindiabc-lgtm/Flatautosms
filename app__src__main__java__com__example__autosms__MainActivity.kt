package com.example.autosms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var msg: EditText
    private lateinit var limit: EditText
    private lateinit var hour: EditText

    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Thread {
            val added = try { Db(this).add(ExcelReader.read(this, uri)) } catch (e: Exception) { -1 }
            runOnUiThread {
                toast(if (added < 0) "File padhne me error" else "$added naye number add hue")
                refresh()
            }
        }.start()
    }
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val p = Sender.prefs(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { box.addView(it) }
        fun label(t: String) = TextView(this).apply { text = t }.also { box.addView(it) }

        label("Message (jis number ka alag message Excel ke column B me ho, wo wahi jayega)")
        msg = EditText(this).apply { setText(p.getString("msg", "")); minLines = 3; gravity = android.view.Gravity.TOP }
        box.addView(msg)
        label("Roz ki SMS limit (SIM ke free SMS)")
        limit = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(p.getInt("limit", 100).toString()) }
        box.addView(limit)
        label("Roz kis ghante bheje (0-23)")
        hour = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(p.getInt("hour", 10).toString()) }
        box.addView(hour)

        btn("1. Excel / CSV chuno (Column A = number)") { ensurePerm(); pick.launch("*/*") }
        btn("2. Roz auto SMS shuru karo") {
            if (ensurePerm()) { save(); DailyWorker.schedule(this, hour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 10); toast("Daily SMS shuru") }
        }
        btn("Abhi bhejo (aaj ki limit tak)") {
            if (ensurePerm()) { save(); Thread { val n = Sender.run(this); runOnUiThread { toast("$n SMS bheje"); refresh() } }.start() }
        }
        btn("Daily SMS band karo") { DailyWorker.cancel(this); toast("Band") }
        status = TextView(this).apply { setPadding(0, 30, 0, 0) }
        box.addView(status)

        setContentView(ScrollView(this).apply {
            addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        refresh()
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }

    private fun save() {
        Sender.prefs(this).edit()
            .putString("msg", msg.text.toString())
            .putInt("limit", limit.text.toString().toIntOrNull() ?: 100)
            .putInt("hour", hour.text.toString().toIntOrNull() ?: 10).apply()
    }

    private fun ensurePerm(): Boolean {
        val ok = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!ok) perm.launch(Manifest.permission.SEND_SMS)
        return ok
    }

    private fun refresh() {
        val (total, sent) = Db(this).counts()
        status.text = "Total number: $total\nBheje ja chuke: $sent\nBaaki: ${total - sent}\nAaj use hue SMS: ${Sender.usedToday(this)}"
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
