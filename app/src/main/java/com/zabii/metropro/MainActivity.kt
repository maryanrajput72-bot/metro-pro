package com.zabii.metropro

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

private const val CURRENT_VERSION_CODE = 5
private const val CURRENT_VERSION_NAME = "1.4"
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
    val backStack = remember { mutableStateListOf<Screen>() }
    var drawerOpen by remember { mutableStateOf(false) }
    var lastHomeBack by remember { mutableStateOf(0L) }
    val updateState = remember { UpdateUiState() }

    fun goTo(target: Screen) {
        if (target == screen) return
        if (target == Screen.Home) backStack.clear() else backStack.add(screen)
        screen = target
    }
    fun navBack() {
        if (backStack.isNotEmpty()) screen = backStack.removeAt(backStack.lastIndex)
        else if (screen != Screen.Home) screen = Screen.Home
    }

    // ANDROID BACK RULE: har screen se pichli screen; Home pe double-press exit
    BackHandler {
        when {
            drawerOpen -> drawerOpen = false
            screen != Screen.Home -> navBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastHomeBack < 2000) (context as? Activity)?.finish()
                else {
                    lastHomeBack = now
                    Toast.makeText(context, "App band karne ke liye dobara back dabain", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

    Box(
        Modifier.fillMaxSize().background(ForestBrush)
            .pointerInput(drawerOpen) {
                var total = 0f
                var startX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { off -> total = 0f; startX = off.x },
                    onHorizontalDrag = { _, amt -> total += amt },
                    onDragEnd = {
                        // Left kenare se right swipe = drawer; drawer khula ho to left swipe = band
                        if (!drawerOpen && startX < 190f && total > 70f) drawerOpen = true
                        else if (drawerOpen && total < -70f) drawerOpen = false
                    }
                )
            }
    ) {
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
                        (fadeIn(tween(360)) + slideInHorizontally(tween(360)) { it / 6 }) togetherWith
                            (fadeOut(tween(240)) + slideOutHorizontally(tween(240)) { -it / 6 })
                    },
                    label = "screen"
                ) { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(go = { goTo(it) }, openDrawer = { drawerOpen = true })
                        Screen.Tom -> TomScreen(back = { navBack() })
                        Screen.Camera -> CameraScreen(back = { navBack() })
                        Screen.Calc -> CalcScreen(back = { navBack() })
                        Screen.Reports -> ReportsScreen(back = { navBack() })
                        Screen.Profile -> ProfileScreen(back = { navBack() })
                        Screen.Settings -> SettingsScreen(back = { navBack() }, updateState = updateState, onCheck = { checkUpdate(auto = false) })
                        Screen.Portal -> PortalScreen(back = { navBack() })
                        Screen.Attendance -> AttendanceScreen(back = { navBack() })
                        Screen.Complaint -> ComplaintScreen(back = { navBack() })
                    }
                }
            }
            BottomNav(current = screen, go = { goTo(it) })
        }

        // ===== Profile Drawer (left slider) =====
        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { drawerOpen = false })
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(tween(300)) { -it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(180))
        ) {
            DrawerContent(
                go = { target -> drawerOpen = false; goTo(target) },
                close = { drawerOpen = false }
            )
        }
    }
}

/* ---------------- Shared components ---------------- */

/* ===== Profile store + smooth press + drawer ===== */

object ProfileStore {
    private const val PREFS = "metropro_profile"
    fun get(ctx: android.content.Context, key: String, def: String): String =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(key, def) ?: def
    fun put(ctx: android.content.Context, key: String, value: String) {
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }
    fun putAll(ctx: android.content.Context, values: Map<String, String>) {
        val e = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
        values.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }
}

// Card dabane pe halki si smooth scale animation (smoothness)
@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null) { onClick() }
}

@Composable
fun DrawerContent(go: (Screen) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val name = remember { ProfileStore.get(context, "name", "Muhammad Aryan") }
    val station = remember { ProfileStore.get(context, "station", "Shaheed-e-Millat") }
    val shift = remember { ProfileStore.get(context, "shift", "Morning Shift") }
    val items = listOf(
        Triple(Screen.Home, "Home", Icons.Filled.Home),
        Triple(Screen.Tom, "TOM Data", Icons.Filled.Create),
        Triple(Screen.Camera, "GPS Camera", Icons.Filled.LocationOn),
        Triple(Screen.Calc, "Money Calculator", Icons.Filled.ShoppingCart),
        Triple(Screen.Reports, "Reports", Icons.Filled.List),
        Triple(Screen.Attendance, "Attendance", Icons.Filled.CheckCircle),
        Triple(Screen.Complaint, "Complaint", Icons.Filled.Warning),
        Triple(Screen.Portal, "GCS Portal", Icons.Filled.Place)
    )
    Column(
        Modifier.width(302.dp).fillMaxHeight().background(Forest2)
    ) {
        // Header — profile + settings icon (Settings sirf drawer ke andar)
        Column(Modifier.fillMaxWidth().background(ForestBrush).padding(start = 18.dp, end = 12.dp, top = 20.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Image(
                    painterResource(R.drawable.logo), contentDescription = null,
                    Modifier.size(58.dp).clip(CircleShape).border(2.dp, Gold1, CircleShape), contentScale = ContentScale.Crop
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Forest1).border(1.dp, GoldBorder, CircleShape)
                        .clickable { go(Screen.Settings) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Gold1, modifier = Modifier.size(19.dp)) }
            }
            Text(name, color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
            Text("$station — $shift", color = White70, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
            items.forEach { (scr, label, icon) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressScale { go(scr) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoldIconBox(icon, size = 38, iconSize = 19, radius = 12)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xCCFFD700), modifier = Modifier.size(19.dp))
                }
            }
        }
        HorizontalDivider(color = Color(0x14FFFFFF))
        Row(
            Modifier.fillMaxWidth().pressScale { go(Screen.Settings) }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Gold1, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "MetroPro v$CURRENT_VERSION_NAME — by Muneeb",
            color = White60, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp)
        )
    }
}

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
fun HomeScreen(go: (Screen) -> Unit, openDrawer: () -> Unit) {
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date()) }
    val pname = remember { ProfileStore.get(context, "name", "Muhammad Aryan") }
    val pstation = remember { ProfileStore.get(context, "station", "Shaheed-e-Millat") }
    val pshift = remember { ProfileStore.get(context, "shift", "Morning Shift") }

    // Today's Summary — aaj ke saved TOM data se (save na ho to sab 0)
    var opMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var clMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var opAt by remember { mutableStateOf<String?>(null) }
    var clAt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        opMap = TomStore.load(context, "opening")
        clMap = TomStore.load(context, "closing")
        opAt = TomStore.savedAt(context, "opening")
        clAt = TomStore.savedAt(context, "closing")
    }
    val merged = remember(opMap, clMap) {
        val m = opMap.toMutableMap()
        clMap.forEach { (k, v) -> if (k.endsWith("_cls") || k == "bop_slips") m[k] = v }
        m
    }
    fun n(key: String) = tomNum(merged, key)
    val sumBopOp = n("bop_op")
    val sumTomTotal = (1..5).sumOf { n("tom${it}_op") + n("tom${it}_rchg") }
    val sumBopSlips = n("bop_slips")
    fun rollUsed(pre: String): Double =
        if ((merged[pre + "_cls"] ?: "").isNotBlank())
            maxOf(0.0, (n(pre + "_op") + n(pre + "_recv") - n(pre + "_dist")) - n(pre + "_cls")) else 0.0
    val sumUsed = rollUsed("bop") + rollUsed("roll") + rollUsed("tkm")
    var sumSale = sumBopSlips * 30
    var sumSlips = sumBopSlips
    for (i in 1..5) {
        val k = "tom$i"
        if ((merged[k + "_cls"] ?: "").isNotBlank()) {
            val sale = maxOf(0.0, (n(k + "_op") + n(k + "_rchg")) - n(k + "_cls"))
            sumSale += sale
            if (sale > 0) sumSlips += Math.round((sale / 30) * 10) / 10.0
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header — greeting + profile photo (tap = drawer) + bell
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo), contentDescription = "Profile — drawer kholo",
                Modifier.size(48.dp).clip(CircleShape).border(2.dp, Gold1, CircleShape).clickable { openDrawer() },
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Assalam-o-Alaikum", color = White70, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(pname, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Forest2).border(1.dp, Color(0x73FFD700), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = Gold1, modifier = Modifier.size(21.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(GoldBrush).align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp))
            }
        }

        // Hero banner — 3D metro train (reference style, Metro rang)
        Box(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).clip(RoundedCornerShape(26.dp))
                .background(GoldBrush)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.05f)) {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("METRO STATION", color = Gold1, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(pstation, color = Color.Black, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("$pshift — Opening, Closing, Reports sab ek jagah", color = Color.Black.copy(alpha = 0.72f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    Box(
                        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(999.dp)).background(Color.Black)
                            .clickable { go(Screen.Tom) }.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Open TOM Data  →", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Image(
                    painterResource(R.drawable.hero_bus), contentDescription = "Metro bus",
                    Modifier.weight(1f).height(132.dp).padding(end = 10.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(today, color = White60, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 22.dp, top = 8.dp))

        // Category circles — 3D pictures
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CatCircle(R.drawable.icon3d_tom, "TOM Data") { go(Screen.Tom) }
            CatCircle(R.drawable.icon3d_calc, "Calculator") { go(Screen.Calc) }
            CatCircle(R.drawable.icon3d_camera, "GPS Camera") { go(Screen.Camera) }
            CatCircle(R.drawable.icon3d_portal, "GCS Portal") { go(Screen.Portal) }
            CatCircle(R.drawable.icon3d_report, "Reports") { go(Screen.Reports) }
            CatCircle(R.drawable.icon3d_complaint, "Complaint") { go(Screen.Complaint) }
            CatCircle(R.drawable.icon3d_attendance, "Attendance") { go(Screen.Attendance) }
        }

        // Quick Services — bare cards, 3D pictures ke sath
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(GoldBrush))
            Spacer(Modifier.width(10.dp))
            Text("Quick Services", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("View All →", fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(brush = GoldBrush), modifier = Modifier.clickable { openDrawer() })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceCard(R.drawable.icon3d_tom, "TOM Data", "Opening • Closing register", Modifier.weight(1f)) { go(Screen.Tom) }
            ServiceCard(R.drawable.icon3d_calc, "Money Calculator", "Notes gin ke total", Modifier.weight(1f)) { go(Screen.Calc) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceCard(R.drawable.icon3d_camera, "GPS Camera", "Stamp wali photos", Modifier.weight(1f)) { go(Screen.Camera) }
            ServiceCard(R.drawable.icon3d_portal, "GCS Portal", "Portal yahan khulega", Modifier.weight(1f)) { go(Screen.Portal) }
        }

        // Today's Summary — Opening upar, Closing neeche (uski spec)
        SectionTitle("Today's Summary")
        MpCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Station Overview", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(pstation, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = TextStyle(brush = GoldBrush))
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("OPENING", color = Gold1, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (opAt != null) "Saved — $opAt" else "Not saved", color = if (opAt != null) White70 else White60, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SummaryStat(tomFmt(sumBopOp), "BOP Roll", Modifier.weight(1f))
                SummaryStat(tomFmt(sumTomTotal), "TOM Opening", Modifier.weight(1f))
                SummaryStat(tomFmt(sumBopSlips), "BOP Slips", Modifier.weight(1f))
            }
            HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CLOSING", color = Gold1, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (clAt != null) "Saved — $clAt" else "Not saved", color = if (clAt != null) White70 else White60, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SummaryStat(tomFmt(sumUsed), "Used", Modifier.weight(1f))
                SummaryStat("Rs ${tomFmt(sumSale)}", "Sale", Modifier.weight(1f))
                SummaryStat(tomFmt(sumSlips), "Slips", Modifier.weight(1f))
            }
        }

        // Bottom banner — Reports (gold)
        Box(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).clip(RoundedCornerShape(24.dp))
                .background(GoldBrush).pressScale { go(Screen.Reports) }
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.icon3d_report), contentDescription = null,
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily Reports", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Closing report WhatsApp pe bhejo", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black).padding(horizontal = 13.dp, vertical = 8.dp)) {
                    Text("Open →", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // More options
        SectionTitle("More Options")
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceCard(R.drawable.icon3d_attendance, "Attendance", "Staff record", Modifier.weight(1f)) { go(Screen.Attendance) }
            ServiceCard(R.drawable.icon3d_complaint, "Complaint", "Send to TL", Modifier.weight(1f)) { go(Screen.Complaint) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridCard("My Profile", "Account detail", Icons.Filled.Person, Modifier.weight(1f)) { go(Screen.Profile) }
            GridCard("Reports", "Daily & closing", Icons.Filled.List, Modifier.weight(1f)) { go(Screen.Reports) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CatCircle(img: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(img), contentDescription = label,
            Modifier.size(62.dp).clip(CircleShape).border(1.5.dp, Gold1, CircleShape).pressScale(onClick),
            contentScale = ContentScale.Crop
        )
        Text(label, color = White70, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ServiceCard(img: Int, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(20.dp))
            .pressScale(onClick).padding(10.dp)
    ) {
        Image(
            painterResource(img), contentDescription = title,
            Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 16.sp)
                Text(sub, color = White60, fontSize = 10.5.sp, lineHeight = 13.sp)
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(GoldBrush),
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun QuickCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(18.dp))
            .pressScale(onClick).padding(vertical = 14.dp, horizontal = 4.dp),
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
            .pressScale(onClick).padding(14.dp),
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
        Text(num, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, style = TextStyle(brush = GoldBrush))
        Text(cap, color = White70, fontSize = 11.sp)
    }
}

/* ---------------- TOM Data (Metro+ wala concept) ---------------- */

object TomStore {
    private const val PREFS = "metropro_tom"
    fun dateKey(): String = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
    fun load(ctx: android.content.Context, which: String): Map<String, String> {
        val prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("d_${dateKey()}_$which", null) ?: return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) { val k = keys.next(); map[k] = obj.optString(k, "") }
            map
        } catch (e: Exception) { emptyMap() }
    }
    fun savedAt(ctx: android.content.Context, which: String): String? {
        val prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return prefs.getString("d_${dateKey()}_${which}_at", null)
    }
    fun save(ctx: android.content.Context, which: String, data: Map<String, String>) {
        val obj = org.json.JSONObject()
        data.forEach { (k, v) -> obj.put(k, v) }
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("d_${dateKey()}_$which", obj.toString())
            .putString("d_${dateKey()}_${which}_at", time)
            .apply()
    }
}

fun tomNum(map: Map<String, String>, key: String): Double = map[key]?.replace(",", "")?.toDoubleOrNull() ?: 0.0
fun tomFmt(v: Double): String = if (v % 1.0 == 0.0) "%,d".format(v.toInt()) else "%,.1f".format(v)

/* ---------------- TOM Data — compact, ek screen pe sab (scroll nahi) ---------------- */

@Composable
fun TomMiniField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = White60, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold),
            cursorBrush = SolidColor(Gold1),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Forest1)
                        .border(1.dp, Color(0x4DFFD700), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 8.dp)
                ) { inner() }
            }
        )
    }
}

@Composable
fun TomMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = White60, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFD700))
                .border(1.dp, Color(0x33FFD700), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 8.dp)
        ) { Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, style = TextStyle(brush = GoldBrush)) }
    }
}

@Composable
fun TomCompactCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        content = content
    )
}

@Composable
fun TomScreen(back: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("opening") }
    val fields = remember { mutableStateMapOf<String, String>() }
    var openingSavedAt by remember { mutableStateOf<String?>(null) }
    var closingSavedAt by remember { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fields.putAll(TomStore.load(context, "opening"))
        val cls = TomStore.load(context, "closing")
        cls.forEach { (k, v) -> if (k.endsWith("_cls") || k == "bop_slips") fields[k] = v }
        openingSavedAt = TomStore.savedAt(context, "opening")
        closingSavedAt = TomStore.savedAt(context, "closing")
    }

    fun f(key: String) = fields[key] ?: ""
    fun set(key: String, v: String) { fields[key] = v }

    fun prefillClosing() {
        if (prefilled) return
        prefilled = true
        listOf("bop", "roll", "tkm").forEach { p ->
            if (f(p + "_cls").isBlank()) {
                val op = f(p + "_op"); if (op.isNotBlank()) fields[p + "_cls"] = op
            }
        }
        for (i in 1..5) {
            val k = "tom$i"
            if (f(k + "_cls").isBlank()) {
                val sum = tomNum(fields, k + "_op") + tomNum(fields, k + "_rchg")
                if (sum > 0) fields[k + "_cls"] = tomFmt(sum)
            }
        }
    }

    fun rollUsed(pre: String): Double =
        if (f(pre + "_cls").isNotBlank())
            maxOf(0.0, (tomNum(fields, pre + "_op") + tomNum(fields, pre + "_recv") - tomNum(fields, pre + "_dist")) - tomNum(fields, pre + "_cls")) else 0.0
    fun tomSale(k: String): Double =
        if (f(k + "_cls").isNotBlank()) maxOf(0.0, (tomNum(fields, k + "_op") + tomNum(fields, k + "_rchg")) - tomNum(fields, k + "_cls")) else 0.0
    fun tomSlips(k: String): Double { val sl = tomSale(k); return if (sl > 0) Math.round((sl / 30) * 10) / 10.0 else 0.0 }

    val today = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date()) }
    val savedAt = if (mode == "opening") openingSavedAt else closingSavedAt

    Column(Modifier.fillMaxSize()) {
        // Compact header
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(GoldBrush).clickable { back() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TOM Data", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(today, color = White60, fontSize = 11.sp)
            }
            Text(
                if (savedAt != null) "✓ Saved $savedAt" else "Not saved",
                color = if (savedAt != null) Gold1 else White60, fontSize = 11.5.sp, fontWeight = FontWeight.Bold
            )
        }

        // Opening / Closing switch
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp).clip(RoundedCornerShape(999.dp))
                .background(Forest2).border(1.dp, GoldBorder, RoundedCornerShape(999.dp)).padding(3.dp)
        ) {
            listOf("opening" to "Opening", "closing" to "Closing").forEach { (m, label) ->
                val active = mode == m
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                        .then(if (active) Modifier.background(GoldBrush) else Modifier)
                        .clickable { mode = m; if (m == "closing") prefillClosing() }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (active) Color.Black else White70, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (mode == "opening") {
            TomCompactCard("BOP Roll") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TomMiniField("Opening", f("bop_op"), { set("bop_op", it) }, Modifier.weight(1f))
                    TomMiniField("Receive", f("bop_recv"), { set("bop_recv", it) }, Modifier.weight(1f))
                    TomMiniField("Distribute", f("bop_dist"), { set("bop_dist", it) }, Modifier.weight(1f))
                    TomMiniField("Sale Slips", f("bop_slips"), { set("bop_slips", it) }, Modifier.weight(1f))
                }
            }
            TomCompactCard("TOM Machines") {
                for (i in 1..5) {
                    val k = "tom$i"
                    Row(Modifier.padding(top = if (i > 1) 6.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                        Text("TOM $i", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(46.dp).padding(bottom = 9.dp))
                        TomMiniField("Opening", f(k + "_op"), { set(k + "_op", it) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniField("Recharge", f(k + "_rchg"), { set(k + "_rchg", it) }, Modifier.weight(1f))
                    }
                }
            }
            TomCompactCard("Rolls") {
                listOf("roll" to "TOM Roll", "tkm" to "TKM Roll").forEachIndexed { idx, (pre, label) ->
                    Row(Modifier.padding(top = if (idx > 0) 8.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                        Text(label, color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(62.dp).padding(bottom = 9.dp))
                        TomMiniField("Opening", f(pre + "_op"), { set(pre + "_op", it) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniField("Receive", f(pre + "_recv"), { set(pre + "_recv", it) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniField("Distribute", f(pre + "_dist"), { set(pre + "_dist", it) }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GoldButton("Save Opening") {
                val data = fields.filterKeys { it.endsWith("_op") || it.endsWith("_recv") || it.endsWith("_dist") || it.endsWith("_rchg") || it == "bop_slips" }
                TomStore.save(context, "opening", data)
                openingSavedAt = TomStore.savedAt(context, "opening")
                Toast.makeText(context, "Opening saved", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(10.dp))
        } else {
            TomCompactCard("BOP Roll") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TomMiniField("Closing", f("bop_cls"), { set("bop_cls", it) }, Modifier.weight(1f))
                    TomMiniStat("Used", tomFmt(rollUsed("bop")), Modifier.weight(1f))
                    TomMiniStat("Sale Amount", "Rs ${tomFmt(tomNum(fields, "bop_slips") * 30)}", Modifier.weight(1.2f))
                }
            }
            TomCompactCard("TOM Machines") {
                for (i in 1..5) {
                    val k = "tom$i"
                    Row(Modifier.padding(top = if (i > 1) 6.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                        Text("TOM $i", color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(46.dp).padding(bottom = 9.dp))
                        TomMiniField("Closing", f(k + "_cls"), { set(k + "_cls", it) }, Modifier.weight(1.1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniStat("Sale", "Rs ${tomFmt(tomSale(k))}", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniStat("Slips", tomFmt(tomSlips(k)), Modifier.weight(0.7f))
                    }
                }
            }
            TomCompactCard("Rolls") {
                listOf("roll" to "TOM Roll", "tkm" to "TKM Roll").forEachIndexed { idx, (pre, label) ->
                    Row(Modifier.padding(top = if (idx > 0) 8.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                        Text(label, color = Gold1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(62.dp).padding(bottom = 9.dp))
                        TomMiniField("Closing", f(pre + "_cls"), { set(pre + "_cls", it) }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TomMiniStat("Used", tomFmt(rollUsed(pre)), Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GoldButton("Save & Lock Closing") {
                val data = fields.filterKeys { it.endsWith("_cls") || it == "bop_slips" }
                TomStore.save(context, "closing", data)
                closingSavedAt = TomStore.savedAt(context, "closing")
                Toast.makeText(context, "Closing saved", Toast.LENGTH_SHORT).show()
            }
            Text(
                "WhatsApp report agli bari me ayega",
                color = White60, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 2.dp)
            )
        }
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

/* ===== My Profile ===== */

@Composable
fun ProfileScreen(back: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(ProfileStore.get(context, "name", "Muhammad Aryan")) }
    var mobile by remember { mutableStateOf(ProfileStore.get(context, "mobile", "")) }
    var empId by remember { mutableStateOf(ProfileStore.get(context, "empId", "")) }
    var station by remember { mutableStateOf(ProfileStore.get(context, "station", "Shaheed-e-Millat")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("My Profile", "Your account detail", back)
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).clip(RoundedCornerShape(24.dp))
                .background(GoldBrush).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.logo), contentDescription = null, Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Text(name.ifBlank { "Muhammad Aryan" }, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            Text("${station.ifBlank { "Shaheed-e-Millat" }} Station", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        MpCard {
            DarkField("Full Name", name, { name = it })
            DarkField("Mobile Number", mobile, { mobile = it })
            DarkField("Employee ID", empId, { empId = it })
            DarkField("Station", station, { station = it })
        }
        Spacer(Modifier.height(10.dp))
        GoldButton("Save Profile") {
            ProfileStore.putAll(context, mapOf("name" to name, "mobile" to mobile, "empId" to empId, "station" to station))
            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(20.dp))
    }
}

/* ===== Settings — full proper, har cheez ka alag section ===== */

@Composable
fun SettingsScreen(back: () -> Unit, updateState: UpdateUiState, onCheck: () -> Unit) {
    val context = LocalContext.current
    // Profile section
    var name by remember { mutableStateOf(ProfileStore.get(context, "name", "Muhammad Aryan")) }
    var station by remember { mutableStateOf(ProfileStore.get(context, "station", "Shaheed-e-Millat")) }
    var shift by remember { mutableStateOf(ProfileStore.get(context, "shift", "Morning Shift")) }
    var mobile by remember { mutableStateOf(ProfileStore.get(context, "mobile", "")) }
    var empId by remember { mutableStateOf(ProfileStore.get(context, "empId", "")) }
    // TL / RP section
    var tlName by remember { mutableStateOf(ProfileStore.get(context, "tlName", "")) }
    var rpName by remember { mutableStateOf(ProfileStore.get(context, "rpName", "")) }
    // App section
    var notifs by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Settings", "Har cheez ki setting apne section me", back)

        SectionTitle("Profile")
        MpCard {
            DarkField("Full Name", name, { name = it })
            DarkField("Station", station, { station = it })
            DarkField("Mobile Number", mobile, { mobile = it })
            DarkField("Employee ID", empId, { empId = it })
            Text("Shift", color = White70, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Morning Shift", "Evening Shift").forEach { sh ->
                    val active = shift == sh
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                            .then(if (active) Modifier.background(GoldBrush) else Modifier.background(Forest1).border(1.dp, Color(0x4DFFD700), RoundedCornerShape(999.dp)))
                            .clickable { shift = sh }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(sh.replace(" Shift", ""), color = if (active) Color.Black else White70, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        GoldButton("Save Profile") {
            ProfileStore.putAll(context, mapOf("name" to name, "station" to station, "shift" to shift, "mobile" to mobile, "empId" to empId))
            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
        }

        SectionTitle("TL / RP")
        MpCard {
            DarkField("TL Name", tlName, { tlName = it })
            DarkField("RP Name", rpName, { rpName = it })
            Text("TL/RP access aur login ka full system agli bari me is section se chalega.", color = White60, fontSize = 11.5.sp)
        }
        GoldButton("Save TL / RP") {
            ProfileStore.putAll(context, mapOf("tlName" to tlName, "rpName" to rpName))
            Toast.makeText(context, "TL / RP saved", Toast.LENGTH_SHORT).show()
        }

        SectionTitle("App & Updates")
        MpCard {
            ListRow(Icons.Filled.Refresh, "Auto Update", "Har app open pe 5 second baad check — hamesha ON", {}) {
                Text("ON", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, style = TextStyle(brush = GoldBrush))
            }
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Notifications, "Notifications", "Shift reminders", {}) {
                Switch(checked = notifs, onCheckedChange = { notifs = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Gold1, uncheckedThumbColor = Gold1, uncheckedTrackColor = Forest1))
            }
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Info, "App Language", "English", {})
        }
        GoldButton(
            when (updateState.phase) {
                UpdatePhase.CHECKING -> "Checking..."
                UpdatePhase.DOWNLOADING -> "Downloading ${updateState.progress}%"
                else -> "Check for Update"
            }
        ) { onCheck() }
        MpCard {
            Text(updateState.statusText, color = White70, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Updates GitHub se automatic aati hain", color = White60, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        SectionTitle("About")
        MpCard {
            ListRow(Icons.Filled.AccountCircle, "MetroPro", "Daily Work App — Version $CURRENT_VERSION_NAME", {})
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Person, "Developer", "Muneeb", {})
            HorizontalDivider(color = Color(0x14FFFFFF))
            ListRow(Icons.Filled.Info, "Package", "com.zabii.metropro", {})
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
