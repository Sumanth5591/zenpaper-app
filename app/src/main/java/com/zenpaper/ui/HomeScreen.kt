package com.zenpaper.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenpaper.WallpaperController
import com.zenpaper.worker.WallpaperUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isBatteryWhitelisted: Boolean,
    onRequestBatteryWhitelist: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zenpaper_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    
    // States
    var selectedCategory by remember { mutableStateOf(prefs.getString("pref_category", "all") ?: "all") }
    var selectedColor by remember { mutableStateOf(prefs.getString("pref_color", "all") ?: "all") }
    var selectedTarget by remember { mutableStateOf(prefs.getString("pref_target", "both") ?: "both") }
    var recentsList by remember { mutableStateOf(getRecents(prefs)) }

    // API Source State
    var selectedApiSource by remember { 
        mutableStateOf(prefs.getString("pref_api_source", "zenpaper") ?: "zenpaper") 
    }

    // Custom Sync Interval State
    var syncInterval by remember { 
        mutableStateOf(prefs.getInt("pref_sync_interval", 15)) 
    }

    // Background Service state
    var isServiceEnabled by remember { 
        mutableStateOf(prefs.getBoolean("pref_background_service_enabled", true)) 
    }
    
    // Last Sync status states
    var lastSyncTime by remember { mutableStateOf(prefs.getLong("pref_last_sync_time", 0L)) }
    var lastSyncStatus by remember { mutableStateOf(prefs.getString("pref_last_sync_status", "Never Run") ?: "Never Run") }
    var lastSyncWallpaper by remember { mutableStateOf(prefs.getString("pref_last_sync_wallpaper", "None") ?: "None") }

    // Instant local coroutine Force-Updating loading state
    var isForceUpdating by remember { mutableStateOf(false) }

    // Brand Colors
    val darkBg = Color(0xFF0A0A0A)
    val cardBg = Color(0xFF161616)
    val accentColor = Color(0xFFF7F06D) // Gold / Zen yellow
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFFA0A0A0)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ZENPAPER",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        letterSpacing = 5.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = darkBg
                )
            )
        },
        containerColor = darkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Info & Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E2E2E))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Periodic Wallpaper Changer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = textWhite,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cropped & center-fitted to S25 Ultra hardware resolution automatically.",
                            fontSize = 12.sp,
                            color = textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF222222))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Background Service Toggle (Explicit Acceptance)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background Auto-Updates",
                                    color = textWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isServiceEnabled) "Active (Syncs every ${syncInterval}m)" else "Inactive",
                                    color = if (isServiceEnabled) Color(0xFF4CAF50) else textMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = { enabled ->
                                    isServiceEnabled = enabled
                                    prefs.edit().putBoolean("pref_background_service_enabled", enabled).apply()
                                    if (enabled) {
                                        WallpaperUpdateWorker.rescheduleNextWork(context)
                                        Toast.makeText(context, "Background Service scheduled!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("ZenPaperBackgroundWork")
                                        Toast.makeText(context, "Background Service stopped.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = textMuted,
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        // Last Sync Logger Card
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F0F))
                                .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Last Sync Status", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = lastSyncStatus, 
                                        color = if (lastSyncStatus == "Success") Color(0xFF4CAF50) else Color(0xFFF44336), 
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Sync Time", color = textMuted, fontSize = 11.sp)
                                    Text(
                                        text = if (lastSyncTime == 0L) "Never" else SimpleDateFormat("hh:mm a (dd MMM)", Locale.getDefault()).format(Date(lastSyncTime)),
                                        color = textWhite,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Last Set", color = textMuted, fontSize = 11.sp)
                                    Text(
                                        text = lastSyncWallpaper, 
                                        color = accentColor,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Ignore Battery Optimizations Prompter (S25 Whitelister)
                        if (!isBatteryWhitelisted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onRequestBatteryWhitelist() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Bypass Samsung Battery Optimizations",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // API Source Selector
            item {
                Column {
                    Text(
                        text = "Wallpaper Library Source",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val sources = listOf("zenpaper" to "ZenPaper Curated", "wallhaven" to "Wallhaven 4K HD")
                        sources.forEach { pair ->
                            val isSelected = selectedApiSource == pair.first
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2E2E2E) else Color.Transparent)
                                    .clickable {
                                        selectedApiSource = pair.first
                                        prefs.edit().putString("pref_api_source", pair.first).apply()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pair.second,
                                    color = if (isSelected) accentColor else textMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Custom Sync Interval Selector Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222222))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Update Frequency",
                                color = textWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${syncInterval}m",
                                color = accentColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                        
                        Slider(
                            value = syncInterval.toFloat(),
                            onValueChange = { valRounded ->
                                syncInterval = valRounded.roundToInt()
                            },
                            valueRange = 1f..300f,
                            onValueChangeFinished = {
                                prefs.edit().putInt("pref_sync_interval", syncInterval).apply()
                                // Re-schedule background thread immediately with new interval
                                if (isServiceEnabled) {
                                    WallpaperUpdateWorker.rescheduleNextWork(context)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color(0xFF222222)
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "1 min", color = textMuted, fontSize = 10.sp)
                            Text(text = "5 Hours (300 min)", color = textMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Category Selector
            item {
                Column {
                    Text(
                        text = "Category Filter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val categories = listOf("all", "spiritual", "minimal", "nature", "tech", "abstract", "art", "cars", "anime", "architecture")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 12.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) accentColor else Color(0xFF202020))
                                    .clickable {
                                        selectedCategory = cat
                                        prefs.edit().putString("pref_category", cat).apply()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (cat == "spiritual") "Gods & Spiritual" else cat.replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) Color.Black else textWhite,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Color Selector
            item {
                Column {
                    Text(
                        text = "Color Tone",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val colors = listOf(
                        "all" to Color.DarkGray,
                        "blue" to Color(0xFF2196F3),
                        "red" to Color(0xFFE91E63),
                        "green" to Color(0xFF4CAF50),
                        "purple" to Color(0xFF9C27B0),
                        "pink" to Color(0xFFF48FB1),
                        "black" to Color(0xFF000000),
                        "white" to Color(0xFFFFFFFF)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 12.dp)
                    ) {
                        items(colors) { pair ->
                            val isSelected = selectedColor == pair.first
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(pair.second)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) accentColor else Color(0xFF404040),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColor = pair.first
                                        prefs.edit().putString("pref_color", pair.first).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (pair.first == "white") Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (pair.first == "all") {
                                    Text(
                                        text = "All",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Target Selector
            item {
                Column {
                    Text(
                        text = "Wallpaper Apply Target",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val targets = listOf("home" to "Home Screen", "lock" to "Lock Screen", "both" to "Both")
                        targets.forEach { pair ->
                            val isSelected = selectedTarget == pair.first
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2E2E2E) else Color.Transparent)
                                    .clickable {
                                        selectedTarget = pair.first
                                        prefs.edit().putString("pref_target", pair.first).apply()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pair.second,
                                    color = if (isSelected) accentColor else textMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons (Force Update Now - direct, robust local coroutine!)
            item {
                Button(
                    onClick = {
                        isForceUpdating = true
                        Toast.makeText(context, "Connecting to High-Res CDN...", Toast.LENGTH_SHORT).show()
                        
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val url = WallpaperController.executeWallpaperUpdate(
                                    context, selectedCategory, selectedColor, selectedTarget
                                )
                                
                                // Update Sync States instantly
                                val time = System.currentTimeMillis()
                                prefs.edit()
                                    .putLong("pref_last_sync_time", time)
                                    .putString("pref_last_sync_status", "Success")
                                    .putString("pref_last_sync_wallpaper", url.substringAfterLast("/"))
                                    .apply()

                                withContext(Dispatchers.Main) {
                                    lastSyncTime = time
                                    lastSyncStatus = "Success"
                                    lastSyncWallpaper = url.substringAfterLast("/")
                                    recentsList = getRecents(prefs)
                                    Toast.makeText(context, "Wallpaper updated successfully!", Toast.LENGTH_SHORT).show()
                                    isForceUpdating = false
                                }
                            } catch (e: Exception) {
                                val time = System.currentTimeMillis()
                                prefs.edit()
                                    .putLong("pref_last_sync_time", time)
                                    .putString("pref_last_sync_status", "Failed: ${e.message}")
                                    .apply()

                                withContext(Dispatchers.Main) {
                                    lastSyncTime = time
                                    lastSyncStatus = "Failed"
                                    Toast.makeText(context, "Sync Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    isForceUpdating = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isForceUpdating
                ) {
                    if (isForceUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Force Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Recents List
            item {
                Text(
                    text = "Recent Wallpaper History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textWhite
                )
            }

            if (recentsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No wallpaper updates yet.\nClick Force Update to fetch your first image!",
                            color = textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(recentsList) { url ->
                    val path = url.substringAfterLast("/")
                    val cat = url.substringBeforeLast("/").substringAfterLast("/")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🖼️",
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = path,
                                    color = textWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Category: $cat",
                                    color = textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Applied",
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private fun getRecents(prefs: android.content.SharedPreferences): List<String> {
    val recents = prefs.getString("pref_recents", "") ?: ""
    return recents.split(",").filter { it.isNotEmpty() }
}
