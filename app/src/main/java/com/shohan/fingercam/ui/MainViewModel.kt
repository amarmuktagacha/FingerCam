package com.shohan.fingercam.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.fingercam.data.AppDatabase
import com.shohan.fingercam.data.DEFAULT_THRESHOLD
import com.shohan.fingercam.data.FingerRecord
import com.shohan.fingercam.data.RESULT_MATCH
import com.shohan.fingercam.data.RESULT_MISMATCH
import com.shohan.fingercam.data.SettingsStore
import com.shohan.fingercam.engine.FingerprintEngine
import com.shohan.fingercam.engine.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SAMPLES_NEEDED = 3

data class Banner(val title: String, val detail: String, val positive: Boolean)

sealed interface ScanMode {
    data class Register(val name: String, val templates: List<String>) : ScanMode
    data class Verify(val record: FingerRecord) : ScanMode
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).fingerDao()
    private val settings = SettingsStore(application)

    val records: StateFlow<List<FingerRecord>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val threshold: StateFlow<Int> = settings.threshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_THRESHOLD)

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _scanMode = MutableStateFlow<ScanMode?>(null)
    val scanMode: StateFlow<ScanMode?> = _scanMode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun dismissBanner() {
        _banner.value = null
    }

    private fun show(title: String, detail: String, positive: Boolean) {
        _banner.value = Banner(title, detail, positive)
    }

    private fun showError(detail: String) = show("সমস্যা হয়েছে", detail, false)

    fun setThreshold(value: Int) {
        viewModelScope.launch { settings.setThreshold(value) }
    }

    // ------------------------------------------------------------ scan control

    fun startRegister(rawName: String) {
        val name = rawName.trim().ifEmpty { "আঙুল ${records.value.size + 1}" }
        _banner.value = null
        _scanMode.value = ScanMode.Register(name, emptyList())
    }

    fun startVerify(record: FingerRecord) {
        _banner.value = null
        _scanMode.value = ScanMode.Verify(record)
    }

    fun cancelScan() {
        _scanMode.value = null
        _busy.value = false
    }

    fun beginCapture() {
        _busy.value = true
    }

    // --------------------------------------------------------------- captured

    /** Called with the cropped fingertip photo (or null if the capture failed). */
    fun onCaptured(bitmap: Bitmap?) {
        val mode = _scanMode.value
        if (mode == null) {
            bitmap?.recycle()
            _busy.value = false
            return
        }
        if (bitmap == null) {
            _busy.value = false
            showError("ছবি তোলা যায়নি। আবার চেষ্টা করুন।")
            return
        }
        viewModelScope.launch {
            val template = withContext(Dispatchers.Default) {
                try {
                    FingerprintEngine.extract(bitmap)
                } catch (e: Throwable) {
                    null
                } finally {
                    bitmap.recycle()
                }
            }
            when (mode) {
                is ScanMode.Register -> handleRegister(mode, template)
                is ScanMode.Verify -> handleVerify(mode, template)
            }
            _busy.value = false
        }
    }

    private suspend fun handleRegister(mode: ScanMode.Register, template: Template?) {
        if (template == null) {
            showError("আঙুলের রেখা পড়া যায়নি। ফোকাস ঠিক করে, আলো বাড়িয়ে আবার তুলুন।")
            return
        }
        val collected = mode.templates + template.encode()
        if (collected.size < SAMPLES_NEEDED) {
            _scanMode.value = ScanMode.Register(mode.name, collected)
            show(
                "ছবি ${collected.size}/$SAMPLES_NEEDED নেওয়া হয়েছে",
                "আঙুল সামান্য সরিয়ে পরের ছবি তুলুন।",
                true
            )
            return
        }
        dao.insert(
            FingerRecord(
                name = mode.name,
                createdAt = System.currentTimeMillis(),
                templates = collected.joinToString("|")
            )
        )
        _scanMode.value = null
        show("সংরক্ষিত হয়েছে", "“${mode.name}” সংরক্ষণ করা হয়েছে। এখন যাচাই করে দেখুন।", true)
    }

    private suspend fun handleVerify(mode: ScanMode.Verify, template: Template?) {
        if (template == null) {
            showError("আঙুলের রেখা পড়া যায়নি। ফোকাস ঠিক করে, আলো বাড়িয়ে আবার তুলুন।")
            return
        }
        val limit = threshold.value
        val best = withContext(Dispatchers.Default) {
            mode.record.templates.split("|").maxOfOrNull { encoded ->
                FingerprintEngine.score(template, Template.decode(encoded))
            } ?: 0
        }
        val matched = best >= limit
        dao.updateResult(
            mode.record.id,
            if (matched) RESULT_MATCH else RESULT_MISMATCH,
            best,
            System.currentTimeMillis()
        )
        _scanMode.value = null
        if (matched) {
            show("মিলেছে", "“${mode.record.name}” — স্কোর $best (সীমা $limit)", true)
        } else {
            show("মেলেনি", "“${mode.record.name}” — স্কোর $best (সীমা $limit)", false)
        }
    }

    // ------------------------------------------------------------------ delete

    fun delete(record: FingerRecord) {
        viewModelScope.launch { dao.deleteById(record.id) }
    }
}
