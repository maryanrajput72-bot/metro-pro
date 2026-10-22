package com.zabii.metropro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val CURRENT_VERSION_CODE = 1
private const val CURRENT_VERSION_NAME = "1.0"
private const val VERSION_URL = "https://maryanrajput72-bot.github.io/metro-pro/version.json"

private val Forest1 = Color(0xFF0D2818)
private val Forest2 = Color(0xFF1B4332)
private val Gold1 = Color(0xFFFFD700)
private val Gold2 = Color(0xFFFF9800)
private val White70 = Color(0xB3FFFFFF)
private val White60 = Color(0x99FFFFFF)
private val GoldBorder = Color(0x59FFD700)

private val GoldBrush = Brush.linearGradient(listOf(Gold1, Gold2))
private val ForestBrush = Brush.verticalGradient(listOf(Forest1, Forest2))

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NEED_PERMISSION, DOWNLOADING, INSTALLING, FAILED }

class UpdateUiState {
    var phase by mutableStateOf(UpdatePhase.IDLE)
    var latest by mutableStateOf<LatestInfo?>(null)
    var progress by mutableStateOf(0)
    var statusText by mutableStateOf("Not checked yet")
}

enum class Screen { Home, Tom, Camera, Calc, Reports, Profile, Settings, Portal, Attendance, Complaint }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold1, background = Forest1, surface = Forest2)) {
                AppShell()
            }
        }
    }
}

@Composable
fun AppShell() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Home) }
    val updateState = remember { UpdateUiState() }

    fun startUpdate() {
        val info = updateState.latest ?: return
        if (!UpdateManager.canInstall(context)) {
            updateState.phase = UpdatePhase.NEED_PERMISSION
            updateState.statusText = "Allow install permission once — then updates are automatic"
            UpdateManager.openInstallPermissionSettings(context)
            return
        }
        updateState.phase = UpdatePhase.DOWNLOADING
        updateState.progress = 0
        updateState.statusText = "Downloading update..."
        scope.launch {
            val file = UpdateManager.downloadApk(context, info.apkUrl) { pct -> updateState.progress = pct }
            if (file == null) {
                updateState.phase = UpdatePhase.FAILED
                updateState.statusText = "Download failed — check internet, tap to retry"
                return@launch
            }
            updateState.phase = UpdatePhase.INSTALLING
            updateState.statusText = "Installing update..."
            val ok = UpdateManager.installWithPackageInstaller(context, file)
            if (ok) {
                Toast.makeText(context, "Installing update", Toast.LENGTH_SHORT).show()
            } else {
                updateState.phase = UpdatePhase.FAILED
                updateState.statusText = "Could not start install — tap to retry"
            }
        }
    }

    fun checkUpdate(auto: Boolean) {
        if (updateState.phase == UpdatePhase.CHECKING || updateState.phase == UpdatePhase.DOWNLOADING) return
        updateState.phase = UpdatePhase.CHECKING
        updateState.statusText = "Checking for update..."
        scope.launch {
            val latest = UpdateManager.fetchLatest(VERSION_URL)
            when {
                latest == null -> {
                    updateState.phase = UpdatePhase.FAILED
                    updateState.statusText = if (auto) "Not checked yet" else "Could not check — check your internet"
                    if (auto) updateState.phase = UpdatePhase.IDLE
                }
                latest.versionCode > CURRENT_VERSION_CODE && latest.apkUrl.isNotEmpty() -> {
                    updateState.latest = latest
                    updateState.phase = UpdatePhase.AVAILABLE
                    updateState.statusText = "New version ${latest.versionName} available"
                    // Zero-touch: update mile to khud download + install shuru
                    startUpdate()
                }
                else -> {
                    updateState.phase = UpdatePhase.UP_TO_DATE
                    updateState.statusText = "Your app is up to date (v$CURRENT_VERSION_NAME)"
                }
            }
        }
    }

    // STICKY RULE: app open ke 5 second baad update check LAZMI
    LaunchedEffect(Unit) {
        delay(5000)
        checkUpdate(auto = true)
    }

    Box(Modifier.fillMaxSize().background(ForestBrush)) {
        Column(Modifier.fillMaxSize()) {
            // Update banner (sirf jab kuch ho raha ho)
            if (updateState.phase == UpdatePhase.DOWNLOADING || updateState.phase == UpdatePhase.INSTALLING ||
                updateState.phase == UpdatePhase.NEED_PERMISSION || updateState.phase == UpdatePhase.FAILED
            ) {
                Box(
                    Modifier.fillMaxWidth().background(GoldBrush)
                        .clickable {
                            if (updateState.phase == UpdatePhase.NEED_PERMISSION) startUpdate()
                            else if (updateState.phase == UpdatePhase.FAILED) checkUpdate(auto = false)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val txt = when (updateState.phase) {
                        UpdatePhase.DOWNLOADING -> "Downloading update... ${updateState.progress}%"
                        UpdatePhase.INSTALLING -> "Installing update..."
                        UpdatePhase.NEED_PERMISSION -> "Tap here and Allow install — one time only"
                        else -> updateState.statusText
                    }
                    Text(txt, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInHorizontally(tween(320)) { it / 6 }) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -it / 6 })
                    },
                    label = "screen"
                ) { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(go = { screen = it })
                        Screen.Tom -> TomScreen(back = { screen = Screen.Home })
                        Screen.Camera -> CameraScreen(back = { screen = Screen.Home })
                        Screen.Calc -> CalcScreen(back = { screen = Screen.Home })
                        Screen.Reports -> ReportsScreen(back = { screen = Screen.Home })
                        Screen.Profile -> ProfileScreen(back = { screen = Screen.Home }, go = { screen = it })
                        Screen.Settings -> SettingsScreen(back = { screen = Screen.Home }, updateState = updateState, onCheck = { checkUpdate(auto = false) })
                        Screen.Portal -> PortalScreen(back = { screen = Screen.Home })
                        Screen.Attendance -> AttendanceScreen(back = { screen = Screen.Home })
                        Screen.Complaint -> ComplaintScreen(back = { screen = Screen.Home })
                    }
                }
            }
            BottomNav(current = screen, go = { screen = it })
        }
    }
}

/* ---------------- Shared components ---------------- */

@Composable
fun GoldIconBox(icon: ImageVector, size: Int = 44, iconSize: Int = 22, radius: Int = 14) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(radius.dp)).background(GoldBrush),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(iconSize.dp)) }
}

@Composable
fun SubHeader(title: String, sub: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(GoldBrush).clickable { back() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = White70, fontSize = 12.sp)
        }
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(GoldBrush).clickable { onClick() }.padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.Black, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .border(1.6.dp, Color(0x8CFFD700), RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Gold1, fontSize = 14.5.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun MpCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(20.dp)).padding(16.dp),
        content = content
    )
}

@Composable
fun ListRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoldIconBox(icon, size = 40, iconSize = 20, radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = White60, fontSize = 11.5.sp)
        }
        if (trailing != null) trailing() else Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xCCFFD700), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DarkField(label: String, value: String, onChange: (String) -> Unit, placeholder: Boolean = false, numeric: Boolean = false) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label, color = White70, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = if (placeholder) White60 else Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(Gold1),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Forest1)
                        .border(1.dp, Color(0x4DFFD700), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
                ) { inner() }
            }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(GoldBrush))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Home, "Home", Icons.Filled.Home),
        Triple(Screen.Tom, "TOM", Icons.Filled.Create),
        Triple(Screen.Camera, "Camera", Icons.Filled.LocationOn),
        Triple(Screen.Reports, "Reports", Icons.Filled.List),
        Triple(Screen.Profile, "Profile", Icons.Filled.Person)
    )
    val activeKey = when (current) {
        Screen.Calc, Screen.Portal, Screen.Attendance, Screen.Complaint -> Screen.Home
        Screen.Settings -> Screen.Profile
        else -> current
    }
    Row(
        Modifier.fillMaxWidth().background(Forest1).border(0.dp, Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (scr, label, icon) ->
            val active = scr == activeKey
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (active) {
                    Column(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(GoldBrush).clickable { go(scr) }
                            .padding(horizontal = 18.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(21.dp))
                        Text(label, color = Color.Black, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(
                        Modifier.clickable { go(scr) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = label, tint = Gold1, modifier = Modifier.size(22.dp))
                        Text(label, color = White60, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ---------------- Home ---------------- */

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val today = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo), contentDescription = "MetroPro logo",
                Modifier.size(50.dp).clip(CircleShape), contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "MetroPro", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = GoldBrush)
                )
                Text("Daily Work App", color = White70, fontSize = 12.sp)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Forest2).border(1.dp, Color(0x73FFD700), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = Gold1, modifier = Modifier.size(20.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(GoldBrush).align(Alignment.TopEnd).padding(0.dp).offset(x = (-9).dp, y = 9.dp))
            }
        }
        // Hero (Golden — black text)
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).clip(RoundedCornerShape(24.dp))
                .background(GoldBrush).padding(20.dp)
        ) {
            Text("Assalam-o-Alaikum", color = Color.Black.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Muhammad Aryan", color = Color.Black, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(top = 12.dp)) {
                Box(Modifier.clip(RoundedCornerShape(999.dp)).border(1.6.dp, Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Shaheed-e-Millat", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Morning Shift", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TODAY", color = Color.Black.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(today, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", color = Color.Black.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("On Duty ●", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        // Quick access
        SectionTitle("Quick Access")
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard("Money Calculator", Icons.Filled.ShoppingCart, Modifier.weight(1f)) { go(Screen.Calc) }
            QuickCard("TOM Data", Icons.Filled.Create, Modifier.weight(1f)) { go(Screen.Tom) }
            QuickCard("GPS Camera", Icons.Filled.LocationOn, Modifier.weight(1f)) { go(Screen.Camera) }
            QuickCard("GCS Portal", Icons.Filled.Place, Modifier.weight(1f)) { go(Screen.Portal) }
        }
        // Today summary
        SectionTitle("Today's Summary")
        MpCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Station Overview", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("View All", fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(brush = GoldBrush))
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                SummaryStat("5", "TOMs", Modifier.weight(1f))
                SummaryStat("128", "Cards Sold", Modifier.weight(1f))
                SummaryStat("42", "Tokens", Modifier.weight(1f))
            }
        }
        // More options
        SectionTitle("More Options")
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard("Attendance", "Staff record", Icons.Filled.CheckCircle, Modifier.weight(1f)) { go(Screen.Attendance) }
            GridCard("Reports", "Daily & closing", Icons.Filled.List, Modifier.weight(1f)) { go(Screen.Reports) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard("Complaint", "Send to TL", Icons.Filled.Warning, Modifier.weight(1f)) { go(Screen.Complaint) }
            GridCard("Settings", "App options", Icons.Filled.Settings, Modifier.weight(1f)) { go(Screen.Settings) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun QuickCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GoldIconBox(icon)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
fun GridCard(title: String, sub: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoldIconBox(icon, size = 42, iconSize = 21, radius = 13)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = White60, fontSize = 11.sp)
        }
    }
}

@Composable
fun SummaryStat(num: String, cap: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, style = TextStyle(brush = GoldBrush))
        Text(cap, color = White70, fontSize = 11.sp)
    }
}

/* ---------------- TOM Data ---------------- */

@Composable
fun TomScreen(back: () -> Unit) {
    val context = LocalContext.current
    var opening by remember { mutableStateOf("12,450") }
    var cards by remember { mutableStateOf("38") }
    var notes by remember { mutableStateOf("") }
    val today = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("TOM Data", "$today — Morning Shift", back)
        MpCard {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                listOf("TOM" to 1.1f, "OPENING" to 1.2f, "CARDS" to 0.8f, "CLOSING" to 1.1f).forEach { (t, w) ->
                    Text(t, color = White60, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w))
                }
            }
            val rows = listOf(
                listOf("TOM 1", "12,450", "38", "18,940"),
                listOf("TOM 2", "9,870", "31", "15,220"),
                listOf("TOM 3", "11,300", "27", "16,610"),
                listOf("TOM 4", "8,540", "19", "12,480"),
                listOf("TOM 5", "10,120", "13", "13,940")
            )
            rows.forEach { r ->
                HorizontalDivider(color = Color(0x14FFFFFF))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    listOf(1.1f, 1.2f, 0.8f, 1.1f).forEachIndexed { i, w ->
                        Text(r[i], color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(w))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MpCard {
            DarkField("Opening Balance — TOM 1", opening, { opening = it }, numeric = true)
            DarkField("Cards Sold", cards, { cards = it }, numeric = true)
            DarkField("Notes (optional)", if (notes.isEmpty()) "Add a note" else notes, { notes = it }, placeholder = notes.isEmpty())
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Save TOM Data") { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
        GhostButton("Share on WhatsApp") { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------- GPS Camera ---------------- */

@Composable
fun CameraScreen(back: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("GPS Camera", "Photo with location stamp", back)
        Box(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).height(340.dp)
                .clip(RoundedCornerShape(22.dp)).background(Forest1).border(1.dp, Color(0x4DFFD700), RoundedCornerShape(22.dp))
        ) {
            // grid lines
            Row(Modifier.fillMaxSize()) { repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0x12FFD700))) } }
            Text("● LIVE GPS", color = Gold1, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
            Column(
                Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(10.dp))
                    .background(Forest1.copy(alpha = 0.85f)).border(1.dp, Color(0x59FFD700), RoundedCornerShape(10.dp)).padding(10.dp)
            ) {
                Text("Shaheed-e-Millat Station", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Islamabad, Pakistan", color = Color.White, fontSize = 11.5.sp)
                Text("Lat 33.7294, Lng 73.0931", color = Color.White, fontSize = 11.5.sp)
            }
        }
        Box(
            Modifier.padding(top = 18.dp).size(66.dp).clip(CircleShape).background(GoldBrush)
                .border(5.dp, Color(0xD9FFFFFF), CircleShape).clickable {
                    Toast.makeText(context, "Camera feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show()
                }.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        GhostButton("Capture Video") { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------- Money Calculator (asal kaam karta hai) ---------------- */

@Composable
fun CalcScreen(back: () -> Unit) {
    val denoms = listOf(5000, 1000, 500, 100, 50, 10)
    val counts = remember { mutableStateMapOf(5000 to 2, 1000 to 5, 500 to 4, 100 to 12, 50 to 8, 10 to 25) }
    val total = denoms.sumOf { it * (counts[it] ?: 0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Money Calculator", "Count notes & coins", back)
        Spacer(Modifier.height(8.dp))
        for (row in denoms.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    val c = counts[d] ?: 0
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Forest2)
                            .border(1.dp, GoldBorder, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$d", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (d == 10) "Coin" else "Note", color = White60, fontSize = 11.sp)
                        }
                        QtyBtn("−") { if (c > 0) counts[d] = c - 1 }
                        Text("$c", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp))
                        QtyBtn("+") { counts[d] = c + 1 }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).clip(RoundedCornerShape(18.dp))
                .background(GoldBrush).padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TOTAL AMOUNT", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Rs %,d".format(total), color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("PKR", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(8.dp))
        GoldButton("Clear All") { denoms.forEach { counts[it] = 0 } }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun QtyBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(GoldBrush).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

/* ---------------- Reports ---------------- */

@Composable
fun ReportsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val soon = { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Reports", "Daily & closing reports", back)
        MpCard {
            ListRow(Icons.Filled.List, "Closing Report", "RMBS format — WhatsApp ready", soon)
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Star, "Card Claim Report", "Daily card claim detail", soon)
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.DateRange, "Shift Summary", "Morning / Evening totals", soon)
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Send, "Export History", "Previous days record", soon)
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Send Report on WhatsApp", soon)
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------- Profile ---------------- */

@Composable
fun ProfileScreen(back: () -> Unit, go: (Screen) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("My Profile", "Your account detail", back)
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).clip(RoundedCornerShape(24.dp))
                .background(GoldBrush).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.logo), contentDescription = null, Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Text("Muhammad Aryan", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            Text("Shaheed-e-Millat Station — Operator", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        MpCard {
            var name by remember { mutableStateOf("Muhammad Aryan") }
            var mobile by remember { mutableStateOf("03xx-xxxxxxx") }
            var empId by remember { mutableStateOf("EMP-0000") }
            var station by remember { mutableStateOf("Shaheed-e-Millat") }
            DarkField("Full Name", name, { name = it })
            DarkField("Mobile Number", mobile, { mobile = it })
            DarkField("Employee ID", empId, { empId = it })
            DarkField("Station", station, { station = it })
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Save Profile") { Toast.makeText(context, "Profile saving comes with the login update", Toast.LENGTH_SHORT).show() }
        GhostButton("Open Settings") { go(Screen.Settings) }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------- Settings ---------------- */

@Composable
fun SettingsScreen(back: () -> Unit, updateState: UpdateUiState, onCheck: () -> Unit) {
    var autoUpd by remember { mutableStateOf(true) }
    var notifs by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Settings", "App options", back)
        MpCard {
            ListRow(Icons.Filled.Refresh, "Auto Update", "Check on every app open", {}) {
                Switch(checked = autoUpd, onCheckedChange = { autoUpd = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Gold1, uncheckedThumbColor = Gold1, uncheckedTrackColor = Forest1))
            }
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Notifications, "Notifications", "Shift reminders", {}) {
                Switch(checked = notifs, onCheckedChange = { notifs = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Gold1, uncheckedThumbColor = Gold1, uncheckedTrackColor = Forest1))
            }
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Info, "App Language", "English", {})
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.AccountCircle, "About MetroPro", "Version $CURRENT_VERSION_NAME", {})
        }
        Spacer(Modifier.height(10.dp))
        GoldButton(
            when (updateState.phase) {
                UpdatePhase.CHECKING -> "Checking..."
                UpdatePhase.DOWNLOADING -> "Downloading ${updateState.progress}%"
                else -> "Check for Update"
            }
        ) { onCheck() }
        MpCard {
            Text(updateState.statusText, color = White70, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("MetroPro v$CURRENT_VERSION_NAME — updates come automatically from GitHub", color = White60, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------------- Portal / Attendance / Complaint ---------------- */

@Composable
fun PortalScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("GCS Portal", "Portal will open here", back)
        MpCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GoldIconBox(Icons.Filled.Place, size = 58, iconSize = 28, radius = 18)
                Text("GCS Portal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Text("Ye screen agli update me detail se banegi", color = White60, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun AttendanceScreen(back: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Attendance", "Staff record — aaj ki hazri", back)
        MpCard {
            val staff = listOf(
                Triple("Staff Member 1", "Morning — Present", "PRESENT"),
                Triple("Staff Member 2", "Morning — Present", "PRESENT"),
                Triple("Staff Member 3", "Evening — Pending", "PENDING")
            )
            staff.forEachIndexed { i, (n, s, st) ->
                if (i > 0) HorizontalDivider(color = Color(0x14FFFFFF))
                ListRow(Icons.Filled.Person, n, s, {}) {
                    Text(st, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = if (st == "PRESENT") TextStyle(brush = GoldBrush) else TextStyle(color = White60))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Mark Attendance") { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ComplaintScreen(back: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("Machine Issue") }
    var detail by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Complaint", "Send to TL", back)
        MpCard {
            DarkField("Complaint Type", type, { type = it })
            DarkField("Detail", if (detail.isEmpty()) "Write your complaint here..." else detail, { detail = it }, placeholder = detail.isEmpty())
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Send Complaint") { Toast.makeText(context, "Feature coming in next update — GUI test build", Toast.LENGTH_SHORT).show() }
        Spacer(Modifier.height(20.dp))
    }
}
