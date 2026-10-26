package com.example.enjoydock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val allApps = ArrayList<AppInfo>()
    private val selectedPackages = HashSet<String>()
    private lateinit var listView: ListView

    data class AppInfo(val name: String, val pkg: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dock_settings", Context.MODE_PRIVATE)
        selectedPackages.addAll(prefs.getStringSet("selected_apps", HashSet()) ?: HashSet())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "Настройки Виджета"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        })

        layout.addView(Button(this).apply {
            text = "1. Разрешить поверх других окон"
            textSize = 16f
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        })

        layout.addView(Button(this).apply {
            text = "2. ЗАПУСТИТЬ / ОБНОВИТЬ ВИДЖЕТ"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Сначала выдай разрешение!", Toast.LENGTH_SHORT).show()
                } else {
                    startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                    Toast.makeText(this@MainActivity, "Виджет обновлён!", Toast.LENGTH_SHORT).show()
                }
            }
        })

        layout.addView(TextView(this).apply {
            text = "\nПрозрачность виджета (0 = невидимый, 100 = сплошной):"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        val transparencyLabel = TextView(this).apply {
            val current = prefs.getInt("dock_transparency", 80)
            text = "$current %"
            textSize = 18f
            setPadding(0, 0, 0, 10)
        }
        layout.addView(transparencyLabel)

        val seekBarTransparency = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("dock_transparency", 80)
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    transparencyLabel.text = "$progress %"
                    prefs.edit().putInt("dock_transparency", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                }
            })
        }
        layout.addView(seekBarTransparency)

        layout.addView(TextView(this).apply {
            text = "\nРазмер виджета (50% - компактный, 200% - крупный):"
            textSize = 16f
            setPadding(0, 20, 0, 10)
        })

        val sizeLabel = TextView(this).apply {
            val currentSize = prefs.getInt("dock_size", 100)
            text = "$currentSize %"
            textSize = 18f
            setPadding(0, 0, 0, 10)
        }
        layout.addView(sizeLabel)

        val seekBarSize = SeekBar(this).apply {
            max = 150
            progress = prefs.getInt("dock_size", 100) - 50
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val realSize = progress + 50
                    sizeLabel.text = "$realSize %"
                    prefs.edit().putInt("dock_size", realSize).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                }
            })
        }
        layout.addView(seekBarSize)

        layout.addView(TextView(this).apply {
            text = "\nВыберите приложения для Виджета:"
            textSize = 18f
            setPadding(0, 30, 0, 20)
        })

        listView = ListView(this)
        layout.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)

        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != this.packageName) {
                allApps.add(AppInfo(info.loadLabel(pm).toString(), pkg))
            }
        }
        allApps.sortBy { it.name.lowercase() }

        val names = allApps.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, names)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        for (i in allApps.indices) {
            if (selectedPackages.contains(allApps[i].pkg)) listView.setItemChecked(i, true)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = allApps[position].pkg
            if (selectedPackages.contains(pkg)) selectedPackages.remove(pkg)
            else selectedPackages.add(pkg)
            
            prefs.edit().putStringSet("selected_apps", selectedPackages).apply()
            startForegroundService(Intent(this, FloatingDockService::class.java))
        }
    }
}
