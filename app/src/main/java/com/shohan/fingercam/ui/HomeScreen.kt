package com.shohan.fingercam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shohan.fingercam.R
import com.shohan.fingercam.data.FingerRecord
import com.shohan.fingercam.data.MAX_THRESHOLD
import com.shohan.fingercam.data.MIN_THRESHOLD
import com.shohan.fingercam.data.RESULT_MATCH
import com.shohan.fingercam.data.RESULT_MISMATCH
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val records by viewModel.records.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val threshold by viewModel.threshold.collectAsState()

    val mode = scanMode
    BackHandler(enabled = mode != null) { viewModel.cancelScan() }

    if (mode != null) {
        val title = when (mode) {
            is ScanMode.Register ->
                "আঙুল সংরক্ষণ — ছবি ${mode.templates.size + 1}/$SAMPLES_NEEDED"
            is ScanMode.Verify -> "যাচাই — ${mode.record.name}"
        }
        val hint = when (mode) {
            is ScanMode.Register ->
                "আঙুলের ডগা বৃত্তের ভেতরে রাখুন। প্রতিবার আঙুল একটু আলাদা অবস্থানে রেখে মোট $SAMPLES_NEEDED টি ছবি তুলুন। " +
                    "স্ক্রিনে ট্যাপ করে ফোকাস করুন এবং আঙুলের রেখাগুলো পরিষ্কার দেখা গেলে ছবি তুলুন।"
            is ScanMode.Verify ->
                "যে আঙুল সংরক্ষণ করেছিলেন সেটি বৃত্তের ভেতরে রাখুন। ফোকাস ঠিক না হলে স্ক্রিনে ট্যাপ করুন, তারপর ছবি তুলুন।"
        }
        ScanScreen(
            title = title,
            hint = hint,
            busy = busy,
            banner = banner,
            onDismissBanner = { viewModel.dismissBanner() },
            onCaptureStart = { viewModel.beginCapture() },
            onCaptured = { viewModel.onCaptured(it) },
            onCancel = { viewModel.cancelScan() }
        )
    } else {
        HomeContent(
            records = records,
            banner = banner,
            threshold = threshold,
            onDismissBanner = { viewModel.dismissBanner() },
            onRegister = { viewModel.startRegister(it) },
            onVerify = { viewModel.startVerify(it) },
            onDelete = { viewModel.delete(it) },
            onThreshold = { viewModel.setThreshold(it) }
        )
    }
}

@Composable
private fun HomeContent(
    records: List<FingerRecord>,
    banner: Banner?,
    threshold: Int,
    onDismissBanner: () -> Unit,
    onRegister: (String) -> Unit,
    onVerify: (FingerRecord) -> Unit,
    onDelete: (FingerRecord) -> Unit,
    onThreshold: (Int) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FingerRecord?>(null) }
    var sliderValue by remember(threshold) { mutableStateOf(threshold.toFloat()) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_fingerprint),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "FingerCam",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "ক্যামেরায় আঙুলের ডগার ছবি তুলে সংরক্ষণ করুন, তারপর আবার ছবি তুলে মিলিয়ে দেখুন।",
                style = MaterialTheme.typography.bodySmall
            )

            banner?.let { ResultBanner(banner = it, onDismiss = onDismissBanner) }

            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("নতুন আঙুল সংরক্ষণ")
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "মেলার সীমা: ${sliderValue.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "স্কোর এই সীমার সমান বা বেশি হলে “মিলেছে”। কম করলে সহজে মিলবে, বেশি করলে কড়াকড়ি হবে।",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onThreshold(sliderValue.toInt()) },
                        valueRange = MIN_THRESHOLD.toFloat()..MAX_THRESHOLD.toFloat(),
                        steps = MAX_THRESHOLD - MIN_THRESHOLD - 1,
                        colors = SliderDefaults.colors(inactiveTrackColor = Color(0xFFBDBDBD))
                    )
                }
            }

            if (records.isEmpty()) {
                Text(
                    text = "এখনো কোনো আঙুল সংরক্ষণ করা হয়নি।",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(records, key = { it.id }) { record ->
                        RecordItem(
                            record = record,
                            onVerify = { onVerify(record) },
                            onDelete = { deleteTarget = record }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("আঙুলের নাম") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("যেমন: ডান হাতের বুড়ো আঙুল") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    onRegister(name)
                }) { Text("ক্যামেরা খুলুন") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("বাতিল") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("মুছে ফেলবেন?") },
            text = { Text("“${target.name}” রেকর্ডটি মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("মুছুন") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun RecordItem(record: FingerRecord, onVerify: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.Black),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_fingerprint),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "সংরক্ষিত: " + formatTime(record.createdAt),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "মুছুন"
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(text = lastResultText(record), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(12.dp))
            Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) {
                Text("ক্যামেরায় স্ক্যান করে যাচাই করুন")
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(millis))

private fun lastResultText(record: FingerRecord): String = when (record.lastResult) {
    RESULT_MATCH ->
        "সর্বশেষ যাচাই: মিলেছে, স্কোর ${record.lastScore} (" + formatTime(record.lastCheckedAt) + ")"
    RESULT_MISMATCH ->
        "সর্বশেষ যাচাই: মেলেনি, স্কোর ${record.lastScore} (" + formatTime(record.lastCheckedAt) + ")"
    else -> "এখনো যাচাই করা হয়নি"
}
