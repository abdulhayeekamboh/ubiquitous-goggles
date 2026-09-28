package com.neo.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val number = StringBuilder()
    private lateinit var numberView: TextView
    private lateinit var keypad: View
    private lateinit var recents: ListView
    private lateinit var contacts: ListView
    private val recentNumbers = ArrayList<String>()
    private val contactNumbers = ArrayList<String>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun circle(text: String, color: Int, size: Int = 76, textSize: Float = 28f): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            this.textSize = textSize
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(size); height = dp(size); setMargins(dp(8), dp(8), dp(8), dp(8))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        keypad = buildKeypad()
        recents = ListView(this)
        contacts = ListView(this)
        val frame = FrameLayout(this).apply {
            addView(keypad); addView(recents); addView(contacts)
        }
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply {
            setBackgroundColor(0xFF1A1A1A.toInt())
            weightSum = 3f
        }
        listOf("Keypad", "Recents", "Contacts").forEachIndexed { i, name ->
            bar.addView(TextView(this).apply {
                text = name; setTextColor(Color.WHITE); textSize = 15f
                gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16))
                setOnClickListener { showTab(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        recents.setOnItemClickListener { _, _, p, _ -> dial(recentNumbers[p]) }
        contacts.setOnItemClickListener { _, _, p, _ -> dial(contactNumbers[p]) }

        // Opened from another app via tel: link
        intent?.data?.schemeSpecificPart?.let { number.append(it); refresh() }

        showTab(0)
        val perms = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            requestPermissions(perms, 1) else loadLists()
    }

    private fun buildKeypad(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        numberView = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 36f; gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(16)); maxLines = 1
        }
        col.addView(numberView, LinearLayout.LayoutParams(-1, -2))

        val grid = GridLayout(this).apply { columnCount = 3 }
        "123456789*0#".forEach { k ->
            grid.addView(circle(k.toString(), 0xFF2C2C2E.toInt()).apply {
                setOnClickListener { number.append(k); refresh() }
            })
        }
        col.addView(grid, LinearLayout.LayoutParams(-2, -2))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(View(this), LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        row.addView(circle("\u260E", 0xFF34C759.toInt()).apply {
            setOnClickListener { dial(number.toString()) }
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        row.addView(TextView(this).apply {
            text = "\u232B"; setTextColor(Color.WHITE); textSize = 28f; gravity = Gravity.CENTER
            setOnClickListener { if (number.isNotEmpty()) number.deleteCharAt(number.length - 1); refresh() }
            setOnLongClickListener { number.setLength(0); refresh(); true }
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        col.addView(row)
        return col
    }

    private fun refresh() { numberView.text = number.toString() }

    private fun showTab(i: Int) {
        keypad.visibility = if (i == 0) View.VISIBLE else View.GONE
        recents.visibility = if (i == 1) View.VISIBLE else View.GONE
        contacts.visibility = if (i == 2) View.VISIBLE else View.GONE
    }

    private fun dial(num: String) {
        if (num.isBlank()) return
        val uri = Uri.parse("tel:" + Uri.encode(num))
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
            startActivity(Intent(Intent.ACTION_CALL, uri))
        else startActivity(Intent(Intent.ACTION_DIAL, uri))
    }

    private fun loadLists() {
        try {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                val labels = ArrayList<String>(); recentNumbers.clear()
                contentResolver.query(CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE),
                    null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                    while (c.moveToNext() && labels.size < 100) {
                        val n = c.getString(0) ?: continue
                        val name = c.getString(1)
                        val arrow = when (c.getInt(2)) {
                            CallLog.Calls.INCOMING_TYPE -> "\u2199 "
                            CallLog.Calls.OUTGOING_TYPE -> "\u2197 "
                            else -> "\u2716 "
                        }
                        labels.add(arrow + (if (name.isNullOrEmpty()) n else "$name\n    $n"))
                        recentNumbers.add(n)
                    }
                }
                recents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
            }
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                val labels = ArrayList<String>(); contactNumbers.clear()
                contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
                    while (c.moveToNext()) {
                        labels.add(c.getString(0) + "\n    " + c.getString(1))
                        contactNumbers.add(c.getString(1))
                    }
                }
                contacts.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
            }
        } catch (e: Exception) { Toast.makeText(this, "Could not load lists", Toast.LENGTH_SHORT).show() }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r); loadLists()
    }
}
