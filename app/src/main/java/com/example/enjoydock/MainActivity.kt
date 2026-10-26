package com.example.enjoydock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val allApps = ArrayList<AppInfo>()
    private lateinit var listShowApps: ListView
    private lateinit var listHideApps: ListView
    
    private val selectedShowPackages = HashSet<String>()
    private val selectedHidePackages = HashSet<String>()

    data class AppInfo(val name: String, val pkg: String)

    // Лончер для выбора картинки из галереи
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                // Сохраняем разрешение на доступ к файлу, чтобы после перезагрузки фон не пропал
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                
                prefs.edit().putString("bg_image_uri", uri.toString()).apply()
                startForegroundService(Intent(this, FloatingDockService::class.java))
                Toast.makeText(this, "Фон обновлён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dock_settings", Context.MODE_PRIVATE)
        selectedShowPackages.addAll(prefs.getStringSet("selected_apps", HashSet()) ?: HashSet())
        selectedHidePackages.addAll(prefs.getStringSet("hidden_apps", HashSet()) ?: HashSet())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "Настройки Виджета"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        layout.addView(Button(this).apply {
            text = "1. Разрешить поверх других окон"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        })

        layout.addView(Button(this).apply {
            text = "2. Разрешить статистику использования"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this@MainActivity, "Найди Виджет и разреши доступ", Toast.LENGTH_LONG).show()
            }
        })

        layout.addView(Button(this).apply {
            text = "3. ЗАПУСТИТЬ / ОБНОВИТЬ ВИДЖЕТ"
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

        // --- НАСТРОЙКА ФОНА ---
        layout.addView(TextView(this).apply {
            text = "\nФон виджета:"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        })

        val btnPickImage = Button(this).apply {
            text = "Выбрать картинку (обои)"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                pickImageLauncher.launch(intent)
            }
        }
        layout.addView(btnPickImage)

        layout.addView(Button(this).apply {
            text = "Сбросить фон (вернуть цвет)"
            setOnClickListener {
                prefs.edit().remove("bg_image_uri").apply()
                startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                Toast.makeText(this, "Фон сброшен", Toast.LENGTH_SHORT).show()
            }
        })

        // --- ГАЛОЧКА БЛОКИРОВКИ ---
        layout.addView(CheckBox(this).apply {
            text = "Заблокировать перемещение виджета"
            textSize = 16f
            isChecked = prefs.getBoolean("is_locked", false)
            setPadding(0, 20, 0, 10)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("is_locked", isChecked).apply()
                startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
            }
        })

        // --- ПРОЗРАЧНОСТЬ ---
        val transparencyLabel = TextView(this).apply {
            text = "Прозрачность: ${prefs.getInt("dock_transparency", 80)} %"
            textSize = 16f
            setPadding(0, 20, 0, 5)
        }
        layout.addView(transparencyLabel)

        layout.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("dock_transparency", 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    transparencyLabel.text = "Прозрачность: $progress %"
                    prefs.edit().putInt("dock_transparency", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                }
            })
        })

        // --- РАЗМЕР ---
        val sizeLabel = TextView(this).apply {
            text = "Размер: ${prefs.getInt("dock_size", 100)} %"
            textSize = 16f
            setPadding(0, 20, 0, 5)
        }
        layout.addView(sizeLabel)

        layout.addView(SeekBar(this).apply {
            max = 150
            progress = prefs.getInt("dock_size", 100) - 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val realSize = progress + 50
                    sizeLabel.text = "Размер: $realSize %"
                    prefs.edit().putInt("dock_size", realSize).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    startForegroundService(Intent(this@MainActivity, FloatingDockService::class.java))
                }
            })
        })

        // --- СПОЙЛЕР 1: Выбор приложений для показа ---
        val toggleShowBtn = TextView(this).apply {
            text = "▼ Выбрать приложения для Виджета"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 40, 0, 10)
        }
        layout.addView(toggleShowBtn)

        listShowApps = ListView(this)
        val showParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600)
        listShowApps.layoutParams = showParams
        listShowApps.visibility = View.GONE
        layout.addView(listShowApps)

        toggleShowBtn.setOnClickListener {
            if (listShowApps.visibility == View.GONE) {
                listShowApps.visibility = View.VISIBLE
                toggleShowBtn.text = "▲ Скрыть список"
            } else {
                listShowApps.visibility = View.GONE
                toggleShowBtn.text = "▼ Выбрать приложения для Виджета"
            }
        }

        // --- СПОЙЛЕР 2: Выбор приложений для скрытия ---
        val toggleHideBtn = TextView(this).apply {
            text = "▼ Скрывать виджет на этих приложениях"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 40, 0, 10)
        }
        layout.addView(toggleHideBtn)

        listHideApps = ListView(this)
        listHideApps.layoutParams = showParams
        listHideApps.visibility = View.GONE
        layout.addView(listHideApps)

        toggleHideBtn.setOnClickListener {
            if (listHideApps.visibility == View.GONE) {
                listHideApps.visibility = View.VISIBLE
                toggleHideBtn.text = "▲ Скрыть список"
            } else {
                listHideApps.visibility = View.GONE
                toggleHideBtn.text = "▼ Скрывать виджет на этих приложениях"
            }
        }

        setContentView(ScrollView(this).apply { addView(layout) })
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

        // Настройка списка ДЛЯ ПОКАЗА
        val adapterShow = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, names)
        listShowApps.adapter = adapterShow
        listShowApps.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in allApps.indices) {
            if (selectedShowPackages.contains(allApps[i].pkg)) listShowApps.setItemChecked(i, true)
        }
        listShowApps.setOnItemClickListener { _, _, position, _ ->
            val pkg = allApps[position].pkg
            if (selectedShowPackages.contains(pkg)) selectedShowPackages.remove(pkg)
            else selectedShowPackages.add(pkg)
            prefs.edit().putStringSet("selected_apps", selectedShowPackages).apply()
            startForegroundService(Intent(this, FloatingDockService::class.java))
        }

        // Настройка списка ДЛЯ СКРЫТИЯ
        val adapterHide = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, names)
        listHideApps.adapter = adapterHide
        listHideApps.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in allApps.indices) {
            if (selectedHidePackages.contains(allApps[i].pkg)) listHideApps.setItemChecked(i, true)
        }
        listHideApps.setOnItemClickListener { _, _, position, _ ->
            val pkg = allApps[position].pkg
            if (selectedHidePackages.contains(pkg)) selectedHidePackages.remove(pkg)
            else selectedHidePackages.add(pkg)
            prefs.edit().putStringSet("hidden_apps", selectedHidePackages).apply()
            startForegroundService(Intent(this, FloatingDockService::class.java))
        }
    }
}
