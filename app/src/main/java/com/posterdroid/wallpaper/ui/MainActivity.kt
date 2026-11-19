package com.posterdroid.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Display
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.posterdroid.caml.parser.TendiesExtractor
import com.posterdroid.core.PosterConstants
import com.posterdroid.hook.status.HookStatus
import com.posterdroid.wallpaper.PosterWallpaperService
import com.posterdroid.wallpaper.R
import com.posterdroid.wallpaper.settings.PosterPreferences
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var preferences: PosterPreferences
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private lateinit var tvHookIndicator: TextView
    private lateinit var tvHookTitle: TextView
    private lateinit var tvHookSubtitle: TextView
    private lateinit var tvActiveName: TextView
    private lateinit var tvActiveDetails: TextView
    private lateinit var btnSetWallpaper: MaterialButton
    private lateinit var btnImportFile: MaterialButton
    private lateinit var tvFpsLabel: TextView
    private lateinit var sliderFPS: Slider

    private val openTendiesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importTendiesFromUri(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = PosterPreferences(this)
        // раньше это было в настройках, но типо смысла нет
        // держать то, что всегда будет включено
        preferences.isEffectEnabled = true
        preferences.isSmoothingEnabled = true
        preferences.sensitivity = 1.0f

        val sliderFPS = findViewById<Slider>(R.id.sliderFPS)
        val maxHz = (display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f)
            .let { kotlin.math.round(it) }

        sliderFPS.valueFrom = 30f
        sliderFPS.valueTo = maxHz
        sliderFPS.stepSize = 1f
        sliderFPS.value = preferences.targetFps.toFloat().coerceIn(30f, maxHz)

        val funnyText = findViewById<TextView>(R.id.randomfunnytext)
        funnyText.text = preferences.randomFunnyString

        bindViews()
        setupListeners()
        updateUiState()
    }

    private fun bindViews() {
        tvHookIndicator = findViewById(R.id.tvHookIndicator)
        tvHookTitle = findViewById(R.id.tvHookTitle)
        tvHookSubtitle = findViewById(R.id.tvHookSubtitle)
        tvActiveName = findViewById(R.id.tvActiveName)
        tvActiveDetails = findViewById(R.id.tvActiveDetails)
        btnSetWallpaper = findViewById(R.id.btnSetWallpaper)
        btnImportFile = findViewById(R.id.btnImportFile)
        tvFpsLabel = findViewById(R.id.tvFpsLabel)
        sliderFPS = findViewById(R.id.sliderFPS)
    }

    private fun setupListeners() {
        btnSetWallpaper.setOnClickListener {
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, PosterWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Выбери PosterDroid в списке обоев", Toast.LENGTH_SHORT).show()
                val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                startActivity(fallbackIntent)
            }
        }

        btnImportFile.setOnClickListener {
            Toast.makeText(this, "Не забудь заново применить обои", Toast.LENGTH_SHORT).show()
            openTendiesLauncher.launch(arrayOf("*/*"))
        }

        sliderFPS.addOnChangeListener { _, value, fromUser ->
            val fps = value.toInt()
            tvFpsLabel.text = "Частота кадров: $fps FPS"
            if (fromUser) {
                preferences.targetFps = fps
            }
        }
    }

    private fun updateUiState() {
        val isHookActive = HookStatus.isModuleActive()
        if (isHookActive) {
            tvHookIndicator.text = "●"
            tvHookIndicator.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            tvHookTitle.text = "LSPosed модуль: Активен!"
            tvHookSubtitle.text = "Все хорошо! ☆~"
        } else {
            tvHookIndicator.text = "●"
            tvHookIndicator.setTextColor(android.graphics.Color.parseColor("#FFA000"))
            tvHookTitle.text = "LSPosed модуль: Не активен.."
            tvHookSubtitle.text = "Включи PosterDroid в LSPosed и перезагрузи устройство"
        }

        tvActiveName.text = preferences.activeTendiesName
        val path = preferences.activeTendiesPath
        if (path != null && File(path).exists()) {
            val file = File(path)
            val sizeMb = file.length() / (1024f * 1024f)
            tvActiveDetails.text = "${"%.1f".format(sizeMb)} МБ"
        } else {
            tvActiveDetails.text = "Тема по умолчанию"
        }

        val fps = preferences.targetFps.coerceIn(30, 144)
        sliderFPS.value = fps.toFloat()
        tvFpsLabel.text = "Частота кадров: $fps FPS"
    }

    private fun importTendiesFromUri(uri: Uri) {
        val displayName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.tendies"
        Toast.makeText(this, "Загрузка и проверка $displayName...", Toast.LENGTH_SHORT).show()

        bgExecutor.execute {
            val tempFile = File(filesDir, "temp_import.tendies")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Не удалось открыть файл")

                val extractor = TendiesExtractor()
                val pkg = tempFile.inputStream().use { stream ->
                    extractor.extract(stream, displayName)
                }

                if (pkg.totalLayers == 0) {
                    throw IllegalArgumentException("В пакете не найдено слоев CAML")
                }

                val targetFile = File(filesDir, "active.tendies")
                replaceActiveTendies(tempFile, targetFile)

                runOnUiThread {
                    preferences.activeTendiesPath = targetFile.absolutePath
                    preferences.activeTendiesName = displayName
                    updateUiState()
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Успешно импортировано: $displayName (${pkg.totalLayers} слоёв)",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (t: Throwable) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                Log.e(PosterConstants.TAG_WALLPAPER, "Import failed: ${t.message}", t)
                runOnUiThread {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Ошибка импорта: ${t.message ?: "Некорректный .tendies архив"}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }



    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    private fun replaceActiveTendies(tempFile: File, targetFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                return
            } catch (e: Exception) {
                Log.w(PosterConstants.TAG_WALLPAPER, "Atomic move failed, falling back to copy: ${e.message}")
            }
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdown()
    }
}
