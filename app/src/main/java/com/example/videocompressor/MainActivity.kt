package com.example.videocompressor

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.button.MaterialButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.widget.TextViewCompat
import android.graphics.Color
import android.widget.GridLayout
import android.content.res.ColorStateList
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.card.MaterialCardView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import java.io.File
import java.util.*

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectVideo: Button
    private lateinit var btnCompress: Button
    private lateinit var btnSave: View
    private lateinit var btnShare: View
    private lateinit var btnBack: Button
    private lateinit var tvSelectedVideo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rgQuality: RadioGroup
    private lateinit var etTargetSize: TextInputEditText
    private lateinit var tlTargetSize: TextInputLayout
    private lateinit var ivThumbnail: ImageView
    private lateinit var flThumbnail: FrameLayout
    private lateinit var tvDuration: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var cbTrim: CheckBox
    private lateinit var layoutTrim: View
    private lateinit var rangeSliderTrim: RangeSlider
    private lateinit var layoutFilmstrip: LinearLayout
    private lateinit var viewTrimLeft: View
    private lateinit var viewTrimRight: View
    private lateinit var tvTrimDuration: TextView
    private lateinit var playerView: PlayerView
    private lateinit var rbLow: RadioButton
    private lateinit var rbMedium: RadioButton
    private lateinit var rbHigh: RadioButton
    private lateinit var rbCustom: RadioButton
    private lateinit var cardLow: MaterialCardView
    private lateinit var cardMedium: MaterialCardView
    private lateinit var cardHigh: MaterialCardView
    private lateinit var cardCustom: MaterialCardView
    private lateinit var mainLayout: View

    private var exoPlayer: ExoPlayer? = null
    // Result view elements
    private lateinit var layoutSelection: View
    private lateinit var layoutResult: View
    private lateinit var ivThumbnailResult: ImageView
    private lateinit var tvDurationResult: TextView
    private lateinit var tvResultInfo: TextView

    private var selectedVideoUri: Uri? = null
    private var compressedVideoFile: File? = null
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var openDialogTag: String? = null

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            displayVideoInfo(uri)
        }
    }

    private fun displayVideoInfo(uri: Uri) {
        val fileSizeMB = getFileSize(uri)
        val durationMs = getVideoDuration(uri)
        val (_, originalHeight) = getVideoResolution(uri)
        
        initializePlayer(uri)
        tvDuration.text = formatDuration(durationMs)
        flThumbnail.visibility = View.VISIBLE
        
        tvSelectedVideo.text = getString(R.string.video_selected, fileSizeMB)
        btnCompress.visibility = View.VISIBLE
        btnCompress.isEnabled = true
        cbTrim.visibility = View.VISIBLE
        tvStatus.text = ""
        layoutResult.visibility = View.GONE
        layoutSelection.visibility = View.VISIBLE

        updateQualityLabels(originalHeight)
        setupTrimSlider(durationMs)
        generateFilmstrip(uri, durationMs)
        updateQualitySelectionUI()
    }

    private fun updateQualityLabels(sourceHeight: Int) {
        // Reset labels
        rbLow.text = getString(R.string.quality_low)
        rbMedium.text = getString(R.string.quality_medium)
        rbHigh.text = getString(R.string.quality_high)
        
        val tag = getString(R.string.recommended_tag)
        
        when {
            sourceHeight > 720 -> {
                rbMedium.text = getString(R.string.quality_recommended_format, getString(R.string.quality_medium), tag)
                rbMedium.isChecked = true
            }
            sourceHeight > 480 -> {
                rbLow.text = getString(R.string.quality_recommended_format, getString(R.string.quality_low), tag)
                rbLow.isChecked = true
            }
            else -> {
                rbLow.isChecked = true
            }
        }
        updateQualitySelectionUI()
    }

    private fun initializePlayer(uri: Uri) {
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false // Pause initially
        }
        playerView.player = exoPlayer
    }

    private fun setupTrimSlider(durationMs: Long) {
        val durationSec = durationMs / 1000.0
        rangeSliderTrim.valueFrom = 0f
        rangeSliderTrim.valueTo = durationSec.toFloat()
        rangeSliderTrim.values = listOf(0f, durationSec.toFloat())
        tvTrimDuration.text = getString(R.string.trim_duration_format, "00:00", formatDuration(durationMs))
        updateTrimOverlays()
    }

    private fun generateFilmstrip(uri: Uri, durationMs: Long) {
        layoutFilmstrip.removeAllViews()
        val numThumbnails = 12 // Tăng lên 12 hình để thấy nhiều chi tiết hơn
        val intervalMs = durationMs / numThumbnails
        
        Thread {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                for (i in 0 until numThumbnails) {
                    val timeUs = (i * intervalMs * 1000L)
                    // Lấy khung hình chính xác hơn
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        runOnUiThread {
                            val imageView = ImageView(this)
                            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                            imageView.layoutParams = params
                            imageView.setImageBitmap(bitmap)
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutFilmstrip.addView(imageView)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }
        }.start()
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun getFileSize(uri: Uri): Double {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length / (1024.0 * 1024.0)
            } ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getVideoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = newBase.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val languageCode = sharedPreferences.getString("Language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getInt("Theme_Mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(currentTheme)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        mainLayout = findViewById(R.id.main)
        
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Selection Layout Views
        layoutSelection = findViewById(R.id.layoutSelection)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnCompress = findViewById(R.id.btnCompress)
        tvSelectedVideo = findViewById(R.id.tvSelectedVideo)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        rgQuality = findViewById(R.id.rgQuality)
        etTargetSize = findViewById(R.id.etTargetSize)
        tlTargetSize = findViewById(R.id.tlTargetSize)
        ivThumbnail = findViewById(R.id.ivThumbnail)
        flThumbnail = findViewById(R.id.flThumbnail)
        tvDuration = findViewById(R.id.tvDuration)
        btnSettings = findViewById(R.id.btnSettings)
        cbTrim = findViewById(R.id.cbTrim)
        layoutTrim = findViewById(R.id.layoutTrim)
        rangeSliderTrim = findViewById(R.id.rangeSliderTrim)
        layoutFilmstrip = findViewById(R.id.layoutFilmstrip)
        viewTrimLeft = findViewById(R.id.viewTrimLeft)
        viewTrimRight = findViewById(R.id.viewTrimRight)
        tvTrimDuration = findViewById(R.id.tvTrimDuration)
        playerView = findViewById(R.id.playerView)
        rbLow = findViewById(R.id.rbLow)
        rbMedium = findViewById(R.id.rbMedium)
        rbHigh = findViewById(R.id.rbHigh)
        rbCustom = findViewById(R.id.rbCustom)
        cardLow = findViewById(R.id.cardLow)
        cardMedium = findViewById(R.id.cardMedium)
        cardHigh = findViewById(R.id.cardHigh)
        cardCustom = findViewById(R.id.cardCustom)

        cbTrim.visibility = View.GONE

        cbTrim.setOnCheckedChangeListener { _, isChecked ->
            layoutTrim.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        rangeSliderTrim.addOnChangeListener { slider, _, fromUser ->
            val values = slider.values
            tvTrimDuration.text = getString(R.string.trim_duration_format, 
                formatDuration((values[0] * 1000).toLong()), 
                formatDuration((values[1] * 1000).toLong()))
            
            updateTrimOverlays()
            
            if (fromUser) {
                // Seek player to start or end depending on which thumb is moved
                // Simple logic: seek to the one being moved or just values[0]
                exoPlayer?.seekTo((values[0] * 1000).toLong())
            }
        }
        
        rangeSliderTrim.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) {}
            override fun onStopTrackingTouch(slider: RangeSlider) {
                // When stop dragging, seek to start point for preview
                exoPlayer?.seekTo((slider.values[0] * 1000).toLong())
            }
        })

        // Result Layout Views
        layoutResult = findViewById(R.id.layoutResult)
        ivThumbnailResult = findViewById(R.id.ivThumbnailResult)
        tvDurationResult = findViewById(R.id.tvDurationResult)
        tvResultInfo = findViewById(R.id.tvResultInfo)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnBack = findViewById(R.id.btnBack)

        val qualityButtons = listOf(rbLow, rbMedium, rbHigh, rbCustom)
        val qualityCards = listOf(cardLow, cardMedium, cardHigh, cardCustom)

        qualityButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                qualityButtons.forEach { other -> other.isChecked = (other == rb) }
                tlTargetSize.visibility = if (rb.id == R.id.rbCustom) View.VISIBLE else View.GONE
                updateQualitySelectionUI()
            }
        }
        
        tlTargetSize.visibility = if (rbCustom.isChecked) View.VISIBLE else View.GONE

        applyCustomColor()

        btnSettings.setOnClickListener {
            showSettingsMenu()
        }

        if (savedInstanceState != null) {
            savedInstanceState.getString("selected_video_uri")?.let {
                selectedVideoUri = Uri.parse(it)
                displayVideoInfo(selectedVideoUri!!)
            }
            savedInstanceState.getString("compressed_video_path")?.let {
                val file = File(it)
                if (file.exists()) {
                    compressedVideoFile = file
                    showResultPage()
                }
            }
            openDialogTag = savedInstanceState.getString("open_dialog_tag")
            when (openDialogTag) {
                "settings" -> showSettingsMenu()
                "theme" -> showThemeDialog()
                "color" -> showColorDialog()
                "language" -> showLanguageDialog()
                "about" -> showAboutDialog()
            }
        }

        btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        btnCompress.setOnClickListener {
            if (transformer != null && progressRunnable != null) {
                cancelCompression()
            } else {
                startCompression()
            }
        }

        btnSave.setOnClickListener {
            saveVideoToGallery()
        }

        btnShare.setOnClickListener {
            shareVideo()
        }

        btnBack.setOnClickListener {
            layoutResult.visibility = View.GONE
            layoutSelection.visibility = View.VISIBLE
            tvStatus.text = ""
            progressBar.visibility = View.GONE
            btnCompress.text = getString(R.string.compress_video)
        }
    }

    private fun showResultPage() {
        val file = compressedVideoFile ?: return
        val fileUri = Uri.fromFile(file)
        
        // Lấy thông tin từ file đã nén/trim để hiển thị kết quả chính xác
        val thumbnail = getVideoThumbnail(fileUri)
        val durationMs = getVideoDuration(fileUri)
        val newSizeMB = file.length() / (1024.0 * 1024.0)

        ivThumbnailResult.setImageBitmap(thumbnail)
        tvDurationResult.text = formatDuration(durationMs)
        tvResultInfo.text = getString(R.string.compression_success, newSizeMB)

        layoutSelection.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
    }

    private fun shareVideo() {
        val file = compressedVideoFile ?: return
        if (!file.exists()) return

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun showSettingsMenu() {
        openDialogTag = "settings"
        val items = arrayOf(
            SettingItem(getString(R.string.theme), R.drawable.ic_theme),
            SettingItem(getString(R.string.bg_color), R.drawable.ic_color),
            SettingItem(getString(R.string.language), R.drawable.ic_language),
            SettingItem(getString(R.string.about), R.drawable.ic_info)
        )

        val adapter = object : ArrayAdapter<SettingItem>(this, R.layout.list_item_settings, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.list_item_settings, parent, false)
                val item = getItem(position)
                val ivIcon = view.findViewById<ImageView>(R.id.ivIcon)
                val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
                tvTitle.text = item?.title
                ivIcon.setImageResource(item?.iconRes ?: 0)
                
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
                tvTitle.setTextColor(textColor)

                val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
                val hue = sharedPreferences.getFloat("Bg_Hue", -1f)
                val accentColor = if (hue >= 0) {
                    Color.HSVToColor(floatArrayOf(hue, 0.8f, if (isDarkMode) 1.0f else 0.6f))
                } else {
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                    typedValue.data
                }
                ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)

                return view
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> showColorDialog()
                    2 -> showLanguageDialog()
                    3 -> showAboutDialog()
                }
            }
            .setPositiveButton(R.string.done, null)
            .setOnDismissListener { 
                if (openDialogTag == "settings") {
                    openDialogTag = null 
                }
            }
            .show()
    }

    data class SettingItem(val title: String, val iconRes: Int)

    private fun showAboutDialog() {
        openDialogTag = "about"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.done, null)
            .setNegativeButton(R.string.back) { _, _ ->
                showSettingsMenu()
            }
            .setOnDismissListener { 
                if (openDialogTag == "about") {
                    openDialogTag = null 
                }
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system))
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getInt("Theme_Mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        val checkedItem = when (currentTheme) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> 0
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }

        openDialogTag = "theme"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(themes, checkedItem) { _, which ->
                val mode = when (which) {
                    0 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (mode != currentTheme) {
                    sharedPreferences.edit().putInt("Theme_Mode", mode).apply()
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
            .setPositiveButton(R.string.done, null)
            .setNegativeButton(R.string.back) { _, _ ->
                showSettingsMenu()
            }
            .setOnDismissListener { 
                if (openDialogTag == "theme") {
                    openDialogTag = null 
                }
            }
            .show()
    }

    private fun showColorDialog() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val initialHue = sharedPreferences.getFloat("Bg_Hue", -1f)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val gridColors = dialogView.findViewById<GridLayout>(R.id.gridColors)
        
        // 8 Predefined base hues: Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink
        val hues = floatArrayOf(0f, 30f, 60f, 120f, 180f, 210f, 270f, 320f)
        var selectedHue = initialHue
        var isConfirmed = false

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        hues.forEach { hue ->
            val colorView = View(this)
            val size = (48 * resources.displayMetrics.density).toInt()
            val margin = (8 * resources.displayMetrics.density).toInt()
            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(margin, margin, margin, margin)
            colorView.layoutParams = params
            
            // Preview color (saturated for selection)
            val displayColor = Color.HSVToColor(floatArrayOf(hue, 0.6f, 1.0f))
            
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(displayColor)
            
            if (hue == selectedHue) {
                drawable.setStroke(6, if (isDarkMode) Color.WHITE else Color.BLACK)
            }
            
            colorView.background = drawable
            
            colorView.setOnClickListener {
                selectedHue = hue
                // Refresh all children to update stroke
                for (i in 0 until gridColors.childCount) {
                    val child = gridColors.getChildAt(i)
                    val childHue = hues[i]
                    val childDrawable = child.background as android.graphics.drawable.GradientDrawable
                    if (childHue == selectedHue) {
                        childDrawable.setStroke(6, if (isDarkMode) Color.WHITE else Color.BLACK)
                    } else {
                        childDrawable.setStroke(0, Color.TRANSPARENT)
                    }
                }
                // Update background immediately for preview
                applyCustomColor(selectedHue)
            }
            gridColors.addView(colorView)
        }

        openDialogTag = "color"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.bg_color)
            .setView(dialogView)
            .setPositiveButton(R.string.done) { _, _ ->
                isConfirmed = true
                sharedPreferences.edit()
                    .putFloat("Bg_Hue", selectedHue)
                    .putFloat("Bg_Sat", 0.15f) // Fixed saturation for subtle backgrounds
                    .apply()
                applyCustomColor()
            }
            .setNeutralButton(R.string.color_default) { _, _ ->
                isConfirmed = true
                sharedPreferences.edit().putFloat("Bg_Hue", -1f).apply()
                applyCustomColor()
            }
            .setNegativeButton(R.string.back) { _, _ ->
                applyCustomColor(initialHue)
                showSettingsMenu()
            }
            .setOnDismissListener { 
                if (!isConfirmed) applyCustomColor(initialHue)
                if (openDialogTag == "color") openDialogTag = null 
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Tiếng Việt")
        val codes = arrayOf("en", "vi")
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentLang = sharedPreferences.getString("Language", "en")
        val checkedItem = codes.indexOf(currentLang)

        openDialogTag = "language"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.language))
            .setSingleChoiceItems(languages, checkedItem) { _, which ->
                val code = codes[which]
                if (code != currentLang) {
                    setLocale(code)
                    recreate()
                }
            }
            .setPositiveButton(R.string.done, null)
            .setNegativeButton(R.string.back) { _, _ ->
                showSettingsMenu()
            }
            .setOnDismissListener { 
                if (openDialogTag == "language") {
                    openDialogTag = null 
                }
            }
            .show()
    }

    private fun applyCustomColor(previewHue: Float? = null) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val hue = previewHue ?: sharedPreferences.getFloat("Bg_Hue", -1f)
        
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        if (hue >= 0) {
            // Apply custom background color to the main layout
            // Tăng mạnh Saturation (0.8f) để màu cực kỳ rõ nét ở Dark Mode
            // Nâng nhẹ Brightness (0.18f) để màu không bị quá đen, giúp nhận diện màu tốt hơn
            val saturation = if (isDarkMode) 0.8f else 0.25f
            val brightness = if (isDarkMode) 0.18f else 0.95f
            val bgColor = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
            mainLayout.setBackgroundColor(bgColor)
        } else {
            // Restore default background color
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            mainLayout.setBackgroundColor(typedValue.data)
        }
        
        // Sử dụng màu nhấn (Accent) dựa trên màu nền tùy chỉnh hoặc màu mặc định của theme
        val accentColor = if (hue >= 0) {
            // Nếu có màu nền tùy chỉnh, dùng tông màu đó nhưng đậm hơn làm màu nhấn
            Color.HSVToColor(floatArrayOf(hue, 0.8f, if (isDarkMode) 1.0f else 0.6f))
        } else {
            // Nếu không, lấy màu Primary mặc định từ Theme hệ thống
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }

        val accentStateList = android.content.res.ColorStateList.valueOf(accentColor)
        
        // Tính toán màu chữ cho nút dựa trên độ sáng của màu nền nút (accentColor)
        // Công thức tính Luminance tiêu chuẩn (ITU-R BT.601)
        val red = Color.red(accentColor)
        val green = Color.green(accentColor)
        val blue = Color.blue(accentColor)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        
        // Ngưỡng 0.6 để các màu sáng như Cyan (hơn 0.7) và Yellow (hơn 0.8) hiện chữ đen rõ rệt
        val buttonTextColor = if (luminance > 0.6) Color.BLACK else Color.WHITE

        val primaryTextColor = if (isDarkMode) Color.WHITE else Color.BLACK

        // Cập nhật màu cho các nút chính (Upload và Nén) theo màu chủ đề (Accent)
        if (::btnSelectVideo.isInitialized) {
            val btn = btnSelectVideo
            btn.backgroundTintList = accentStateList
            btn.setTextColor(buttonTextColor)
            // Fix for MaterialButton: use iconTint instead of compoundDrawableTintList
            if (btn is MaterialButton) {
                btn.iconTint = android.content.res.ColorStateList.valueOf(buttonTextColor)
            } else {
                TextViewCompat.setCompoundDrawableTintList(btn, android.content.res.ColorStateList.valueOf(buttonTextColor))
            }
        }
        if (::btnCompress.isInitialized) {
            btnCompress.backgroundTintList = accentStateList
            btnCompress.setTextColor(buttonTextColor)
        }
        
        // Cập nhật màu cho checkbox và settings
        if (::cbTrim.isInitialized) {
            cbTrim.buttonTintList = accentStateList
            // Icon cây kéo bây giờ cũng đi theo màu chữ chính (Trắng/Đen) để chuyên nghiệp
            TextViewCompat.setCompoundDrawableTintList(cbTrim, android.content.res.ColorStateList.valueOf(primaryTextColor))
            cbTrim.setTextColor(primaryTextColor)
        }
        if (::btnSettings.isInitialized) {
            btnSettings.imageTintList = accentStateList
        }

        // Cập nhật màu cho các RadioButtons chất lượng
        if (::rbLow.isInitialized) rbLow.buttonTintList = accentStateList
        if (::rbMedium.isInitialized) rbMedium.buttonTintList = accentStateList
        if (::rbHigh.isInitialized) rbHigh.buttonTintList = accentStateList
        if (::rbCustom.isInitialized) rbCustom.buttonTintList = accentStateList

        if (::btnBack.isInitialized) btnBack.setTextColor(accentColor)
        
        updateQualitySelectionUI(hue)

        // Update Save/Share buttons with accent color
        findViewById<View>(R.id.btnSave)?.let { layout ->
            val iv = layout.findViewById<ImageView>(R.id.ivIcon) ?: (if (layout is ViewGroup && layout.childCount > 0) layout.getChildAt(0) as? ImageView else null)
            val tv = layout.findViewById<TextView>(R.id.tvTitle) ?: (if (layout is ViewGroup && layout.childCount > 1) layout.getChildAt(1) as? TextView else null)
            iv?.imageTintList = accentStateList
            tv?.setTextColor(accentColor)
        }
        findViewById<View>(R.id.btnShare)?.let { layout ->
            val iv = layout.findViewById<ImageView>(R.id.ivIcon) ?: (if (layout is ViewGroup && layout.childCount > 0) layout.getChildAt(0) as? ImageView else null)
            val tv = layout.findViewById<TextView>(R.id.tvTitle) ?: (if (layout is ViewGroup && layout.childCount > 1) layout.getChildAt(1) as? TextView else null)
            iv?.imageTintList = accentStateList
            tv?.setTextColor(accentColor)
        }
    }

    private fun setLocale(languageCode: String) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("Language", languageCode).apply()
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
    }

    private fun updateQualitySelectionUI(previewHue: Float? = null) {
        if (!::cardLow.isInitialized) return
        
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val hue = previewHue ?: sharedPreferences.getFloat("Bg_Hue", -1f)
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        val accentColor = if (hue >= 0) {
            Color.HSVToColor(floatArrayOf(hue, 0.8f, if (isDarkMode) 1.0f else 0.6f))
        } else {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }

        val cardStrokeColor = getColor(R.color.quality_card_stroke)

        val cards = listOf(cardLow, cardMedium, cardHigh, cardCustom)
        val buttons = listOf(rbLow, rbMedium, rbHigh, rbCustom)

        cards.forEachIndexed { index, card ->
            if (buttons[index].isChecked) {
                card.strokeColor = accentColor
                card.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            } else {
                card.strokeColor = cardStrokeColor
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    private fun updateTrimOverlays() {
        if (!::viewTrimLeft.isInitialized || !::viewTrimRight.isInitialized || !::rangeSliderTrim.isInitialized) return
        
        val values = rangeSliderTrim.values
        val totalRange = rangeSliderTrim.valueTo - rangeSliderTrim.valueFrom
        if (totalRange <= 0) return
        
        val filmstrip = findViewById<View>(R.id.layoutFilmstrip)
        
        // Wait for layout if width is 0
        if (filmstrip.width == 0) {
            filmstrip.post { updateTrimOverlays() }
            return
        }
        
        val filmstripWidth = filmstrip.width.toFloat()
        
        val leftWidth = ((values[0] - rangeSliderTrim.valueFrom) / totalRange) * filmstripWidth
        val rightWidth = ((rangeSliderTrim.valueTo - values[1]) / totalRange) * filmstripWidth
        
        viewTrimLeft.layoutParams.width = leftWidth.toInt()
        viewTrimLeft.requestLayout()
        
        viewTrimRight.layoutParams.width = rightWidth.toInt()
        viewTrimRight.requestLayout()
    }

    private fun startCompression() {
        val uri = selectedVideoUri ?: return
        
        val isCustom = rgQuality.checkedRadioButtonId == R.id.rbCustom
        val targetSizeInput = etTargetSize.text.toString().toDoubleOrNull()
        
        if (isCustom && (targetSizeInput == null || targetSizeInput <= 0)) {
            Toast.makeText(this, getString(R.string.invalid_size), Toast.LENGTH_SHORT).show()
            return
        }

        val originalDurationMs = getVideoDuration(uri)
        if (originalDurationMs <= 0) {
            Toast.makeText(this, "Could not determine video duration", Toast.LENGTH_SHORT).show()
            return
        }

        var effectiveDurationMs = originalDurationMs
        if (cbTrim.isChecked) {
            val values = rangeSliderTrim.values
            val startMs = (values[0] * 1000).toLong()
            val endMs = (values[1] * 1000).toLong()
            if (endMs > startMs) {
                effectiveDurationMs = endMs - startMs
            }
        }

        val durationSec = effectiveDurationMs / 1000.0
        val originalSizeMB = getFileSize(uri)
        val (_, originalHeight) = getVideoResolution(uri)

        // Khi trim, chúng ta bỏ qua kiểm tra dung lượng gốc vì dung lượng đoạn trim sẽ khác
        if (!cbTrim.isChecked && isCustom && targetSizeInput != null && targetSizeInput >= originalSizeMB) {
            Toast.makeText(this, "Target size must be smaller than original size", Toast.LENGTH_SHORT).show()
            return
        }

        var targetHeight = when (rgQuality.checkedRadioButtonId) {
            R.id.rbLow -> 480
            R.id.rbMedium -> 720
            R.id.rbHigh -> 1080
            else -> if (originalHeight > 0) originalHeight else 720
        }

        val encoderFactory: Codec.EncoderFactory = if (isCustom && targetSizeInput != null) {
            // Sử dụng hệ số 0.70 và dùng đơn vị decimal (1000) để tính bitrate.
            val totalTargetBits = targetSizeInput * 1000 * 1000 * 8 * 0.70
            
            var audioBitrate = 96_000
            if (audioBitrate * durationSec > totalTargetBits * 0.15) audioBitrate = 64_000
            if (audioBitrate * durationSec > totalTargetBits * 0.15) audioBitrate = 32_000
            
            val audioBits = audioBitrate * durationSec
            var videoBitrate = ((totalTargetBits - audioBits) / durationSec).toInt()
            if (videoBitrate < 30_000) videoBitrate = 30_000
            
            targetHeight = when {
                videoBitrate < 500_000 -> 240
                videoBitrate < 1_000_000 -> 360
                videoBitrate < 1_800_000 -> 480
                videoBitrate < 3_500_000 -> 720
                else -> if (originalHeight > 0) originalHeight else 1080
            }

            DefaultEncoderFactory.Builder(this)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(videoBitrate)
                        .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                        .build()
                )
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(audioBitrate)
                        .build()
                )
                .build()
        } else {
            val targetBitrate = when (rgQuality.checkedRadioButtonId) {
                R.id.rbLow -> 800_000
                R.id.rbMedium -> 2_200_000 // Tăng nhẹ cho option Recommended
                R.id.rbHigh -> 4_000_000
                else -> 2_200_000
            }
            
            DefaultEncoderFactory.Builder(this)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(targetBitrate)
                        .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                        .build()
                ).build()
        }

        // Đảm bảo không bao giờ tăng độ phân giải so với video gốc
        if (originalHeight in 1 until targetHeight) {
            targetHeight = originalHeight
        }

        val outputFile = File(externalCacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        compressedVideoFile = outputFile
        
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        btnCompress.isEnabled = true
        btnCompress.text = getString(R.string.cancel_compress)
        tvStatus.text = getString(R.string.compressing_to, targetHeight)

        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        
        if (cbTrim.isChecked) {
            val values = rangeSliderTrim.values
            val startMs = (values[0] * 1000).toLong()
            val endMs = (values[1] * 1000).toLong()
            
            if (startMs >= 0 && endMs > startMs) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
            }
        }
        
        val mediaItem = mediaItemBuilder.build()
        val videoEffect: Effect = Presentation.createForHeight(targetHeight)
        
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(), listOf(videoEffect)))
            .build()

        transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .build()

        transformer?.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                runOnUiThread {
                    stopProgressPolling()
                    transformer = null
                    showResultPage()
                    Toast.makeText(this@MainActivity, getString(R.string.compression_finished), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                runOnUiThread {
                    tvStatus.text = getString(R.string.error_message, exportException.message ?: "Unknown error")
                    progressBar.visibility = View.GONE
                    btnCompress.text = getString(R.string.compress_video)
                    btnCompress.isEnabled = true
                    stopProgressPolling()
                    transformer = null
                }
            }
        })

        try {
            transformer?.start(editedMediaItem, outputFile.absolutePath)
            startProgressPolling()
        } catch (e: Exception) {
            tvStatus.text = getString(R.string.init_error, e.message ?: "Initialization failed")
            btnCompress.isEnabled = true
        }
    }

    private fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun getVideoResolution(uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            Pair(width, height)
        } catch (e: Exception) {
            Pair(0, 0)
        } finally {
            retriever.release()
        }
    }

    private fun cancelCompression() {
        transformer?.cancel()
        transformer = null
        stopProgressPolling()
        progressBar.visibility = View.GONE
        tvStatus.text = getString(R.string.compression_cancelled)
        btnCompress.text = getString(R.string.compress_video)
    }

    private fun saveVideoToGallery() {
        val file = compressedVideoFile ?: return
        if (!file.exists()) return

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Compressed_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoCompressor")
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.save_error, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startProgressPolling() {
        progressRunnable = object : Runnable {
            override fun run() {
                val progressHolder = ProgressHolder()
                val state = transformer?.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progressBar.progress = progressHolder.progress
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(progressRunnable!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_video_uri", selectedVideoUri?.toString())
        outState.putString("compressed_video_path", compressedVideoFile?.absolutePath)
        outState.putString("open_dialog_tag", openDialogTag)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressPolling()
        exoPlayer?.release()
    }
}
