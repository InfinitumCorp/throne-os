package com.infinitum.throne.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.home.Edition
import com.infinitum.throne.ui.HydratedTile
import com.infinitum.throne.ui.IconSquircle
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted

private val Brand = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
private val DockFill = Color(0xE8E4E4E9)
private val DockShape = RoundedCornerShape(36.dp)

/** iOS-style layout metrics. */
private val TileSize = 60.dp
private val DockReserve = 108.dp
private val GroupedBackground = Color(0xFFF2F2F7)
private val FieldFill = Color(0x1F767680)
private val Separator = Color(0x1F3C3C43)
private val CardShape = RoundedCornerShape(14.dp)

/** Soft, near-white wallpaper so white tiles read the way they do on a light iPhone home screen. */
private val Wallpaper = Brush.verticalGradient(
    0f to Color(0xFFFAFAFC),
    0.45f to Color(0xFFEFF1F6),
    1f to Color(0xFFE2E5EE),
)

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val state by viewModel.state
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var searchOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            // Leaving for an app returns to a clean home screen, as on iOS.
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onQuery("")
                searchOpen = false
                menuFor = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val query = state.query.trim()
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onContactsGranted() else viewModel.dismissContactsPrompt()
    }
    val closeSearch = {
        viewModel.onQuery("")
        searchOpen = false
        focus.clearFocus()
    }
    val showSearch = searchOpen || query.isNotEmpty()
    BackHandler(enabled = menuFor != null) { menuFor = null }
    BackHandler(enabled = showSearch && menuFor == null && !state.showAbout) { closeSearch() }

    val infinitumGrid = rememberLazyGridState()
    val othersGrid = rememberLazyGridState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val overlay = showSearch || menuFor != null
    val blur by animateDpAsState(if (overlay) 22.dp else 0.dp, tween(220), label = "home-blur")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wallpaper),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(blur)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (state.showAbout) {
                AboutScreen(
                    edition = if (state.edition == Edition.KETER) "^KETERv1" else state.edition.label,
                    onBack = viewModel::closeAbout,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { page ->
                        val apps = if (page == 0) state.infinitum else state.others
                        val empty = if (state.loading) {
                            "Reading apps."
                        } else if (page == 0) {
                            "The product catalog is empty."
                        } else {
                            "No other launchable apps."
                        }
                        AppGrid(
                            apps = apps,
                            state = if (page == 0) infinitumGrid else othersGrid,
                            empty = empty,
                            onOpen = { openApp(context, it) },
                            onMenu = { menuFor = it },
                            onBlankLongPress = viewModel::openAbout,
                        )
                    }
                    SearchPill(
                        page = pagerState.currentPage,
                        pages = 2,
                        swiping = pagerState.isScrollInProgress,
                        onClick = { searchOpen = true },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 14.dp),
                    )
                    Spacer(Modifier.height(DockReserve))
                }
            }
        }

        if (!overlay && !imeVisible) FrostedCallDock(
            messages = state.messages,
            onCall = { openDialer(context) },
            onCamera = { openCamera(context) },
            onMessages = { message -> openMessages(context, message) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )

        AnimatedVisibility(
            visible = showSearch && !state.showAbout,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Spotlight(
                value = state.query,
                query = query,
                hits = state.hits,
                onValueChange = viewModel::onQuery,
                onCancel = closeSearch,
                onSearch = {
                    val exact = PhoneSearch.exactApp(state.hits.apps, query)
                    if (exact != null) openApp(context, exact) else openVeil(context, query)
                    focus.clearFocus()
                },
                onApp = { openApp(context, it) },
                onVeil = { openVeil(context, query) },
                onContact = { openContact(context, it) },
                onCall = { openTel(context, it) },
                onMessage = { openSms(context, it) },
                onSetting = { openSetting(context, it) },
                onFile = { openFile(context, it) },
                onAllowContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
            )
        }

        val menuApp = menuFor
        AnimatedVisibility(
            visible = menuApp != null,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f),
        ) {
            if (menuApp != null) {
                AppMenu(
                    app = menuApp,
                    onOpen = {
                        menuFor = null
                        openApp(context, menuApp)
                    },
                    onInfo = {
                        menuFor = null
                        openInfo(context, menuApp)
                    },
                    onDismiss = { menuFor = null },
                )
            }
        }
    }
}

/* ---------- Home ---------- */

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    state: LazyGridState,
    empty: String,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onBlankLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val blank = Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onBlankLongPress()
        })
    }
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize().then(blank), contentAlignment = Alignment.Center) {
            if (empty.isNotEmpty()) {
                Text(
                    empty,
                    style = Brand.copy(fontSize = 15.sp),
                    color = ThroneMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        modifier = modifier
            .fillMaxSize()
            .then(blank),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            AppTile(app, onOpen, onMenu)
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val scale = remember(app.key) { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                clip = false
            }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        scale.animateTo(0.88f, spring(dampingRatio = 0.7f, stiffness = 700f))
                        tryAwaitRelease()
                        scale.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    },
                    onTap = { onOpen(app) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMenu(app)
                    },
                )
            },
    ) {
        HydratedTile(
            productId = app.productId,
            fallback = app.icon,
            contentDescription = app.label,
            colorIcon = app.colorIcon,
            flat = true,
            modifier = Modifier.size(TileSize),
        )
        Text(
            text = app.label,
            style = Brand.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = ThroneInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(),
        )
    }
}

/** The "Search" capsule above the dock. While pages are swiping it shows the page dots instead. */
@Composable
private fun SearchPill(
    page: Int,
    pages: Int,
    swiping: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(30.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.62f))
            .border(0.6.dp, Color.Black.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Search" }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = swiping, animationSpec = tween(160), label = "pill") { dots ->
            if (dots) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(62.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .background(
                                    if (page == index) ThroneInk else ThroneInk.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(62.dp), horizontalArrangement = Arrangement.Center) {
                    MagnifierGlyph(ThroneInk, 12.dp)
                    Text(
                        "Search",
                        style = Brand.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = ThroneInk,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

/* ---------- Long-press menu ---------- */

@Composable
private fun AppMenu(
    app: AppEntry,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val absorb = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f))
            .clickable(interactionSource = absorb, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HydratedTile(
                productId = app.productId,
                fallback = app.icon,
                contentDescription = app.label,
                colorIcon = app.colorIcon,
                flat = true,
                modifier = Modifier.size(88.dp),
            )
            Text(
                app.label,
                style = Brand.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = ThroneInk,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            )
            Column(
                Modifier
                    .width(250.dp)
                    .shadow(18.dp, CardShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                    .clip(CardShape)
                    .background(Color.White.copy(alpha = 0.94f)),
            ) {
                MenuRow("Open", onOpen)
                if (app.packageName != null) {
                    HairlineDivider(start = 0.dp)
                    MenuRow("App Info", onInfo)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = Brand.copy(fontSize = 17.sp),
        color = ThroneInk,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}

/* ---------- Spotlight search ---------- */

@Composable
private fun Spotlight(
    value: String,
    query: String,
    hits: SearchHits,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSearch: () -> Unit,
    onApp: (AppEntry) -> Unit,
    onVeil: () -> Unit,
    onContact: (ContactHit) -> Unit,
    onCall: (ContactHit) -> Unit,
    onMessage: (ContactHit) -> Unit,
    onSetting: (SettingHit) -> Unit,
    onFile: (FileHit) -> Unit,
    onAllowContacts: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        requester.requestFocus()
        keyboard?.show()
    }
    val absorb = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.55f))
            .clickable(interactionSource = absorb, indication = null, onClick = {})
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
        ) {
            SearchBox(
                value = value,
                onValueChange = onValueChange,
                onSearch = onSearch,
                requester = requester,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Cancel",
                style = Brand.copy(fontSize = 17.sp),
                color = ThroneInk,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (query.isEmpty()) return@Column
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "veil") {
                Card {
                    ResultRow(
                        title = "Search with VEIL",
                        detail = "“$query”",
                        icon = { MarkSquare { MagnifierGlyph(Color.White, 15.dp) } },
                        onClick = onVeil,
                        chevron = true,
                    )
                }
            }
            if (hits.apps.isNotEmpty()) {
                item(key = "apps") {
                    SectionHeader("Apps")
                    Card {
                        hits.apps.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp)) {
                                row.forEach { app ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onApp(app) },
                                    ) {
                                        HydratedTile(
                                            productId = app.productId,
                                            fallback = app.icon,
                                            contentDescription = app.label,
                                            colorIcon = app.colorIcon,
                                            flat = true,
                                            modifier = Modifier.size(52.dp),
                                        )
                                        Text(
                                            app.label,
                                            style = Brand.copy(fontSize = 11.sp),
                                            color = ThroneInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 5.dp, start = 2.dp, end = 2.dp),
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (hits.askContacts || hits.contacts.isNotEmpty()) {
                item(key = "contacts") {
                    SectionHeader("Contacts")
                    Card {
                        if (hits.askContacts) {
                            ResultRow(
                                title = "Allow Contacts",
                                detail = "Search names and numbers",
                                icon = { Avatar("C") },
                                onClick = onAllowContacts,
                                chevron = true,
                            )
                        }
                        hits.contacts.forEachIndexed { index, contact ->
                            if (index > 0 || hits.askContacts) HairlineDivider()
                            ContactRow(contact, onContact, onCall, onMessage)
                        }
                    }
                }
            }
            if (hits.settings.isNotEmpty()) {
                item(key = "settings") {
                    SectionHeader("Settings")
                    Card {
                        hits.settings.forEachIndexed { index, setting ->
                            if (index > 0) HairlineDivider()
                            ResultRow(
                                title = setting.title,
                                icon = { MarkSquare { Letter(setting.title.take(1)) } },
                                onClick = { onSetting(setting) },
                                chevron = true,
                            )
                        }
                    }
                }
            }
            if (hits.files.isNotEmpty()) {
                item(key = "files") {
                    SectionHeader("Files")
                    Card {
                        hits.files.forEachIndexed { index, file ->
                            if (index > 0) HairlineDivider()
                            ResultRow(
                                title = file.name,
                                icon = { MarkSquare { Letter("F") } },
                                onClick = { onFile(file) },
                                chevron = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Brand.copy(fontSize = 17.sp, color = ThroneInk),
        cursorBrush = SolidColor(ThroneInk),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.focusRequester(requester),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldFill)
                    .padding(horizontal = 10.dp),
            ) {
                MagnifierGlyph(ThroneMuted, 15.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 7.dp),
                ) {
                    if (value.isEmpty()) {
                        Text("Search", style = Brand.copy(fontSize = 17.sp), color = ThroneMuted)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    ClearGlyph(onClick = { onValueChange("") })
                }
            }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = Brand.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        color = ThroneInk,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White),
    ) { content() }
}

@Composable
private fun HairlineDivider(start: Dp = 60.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Separator),
    )
}

@Composable
private fun ResultRow(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    detail: String? = null,
    chevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(title, style = Brand.copy(fontSize = 17.sp), color = ThroneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = Brand.copy(fontSize = 13.sp), color = ThroneMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (chevron) ChevronGlyph()
    }
}

@Composable
private fun ContactRow(
    contact: ContactHit,
    onOpen: (ContactHit) -> Unit,
    onCall: (ContactHit) -> Unit,
    onMessage: (ContactHit) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(contact) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name.take(1).uppercase())
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(contact.name, style = Brand.copy(fontSize = 17.sp), color = ThroneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!contact.number.isNullOrBlank()) {
                Text(contact.number, style = Brand.copy(fontSize = 13.sp), color = ThroneMuted, maxLines = 1)
            }
        }
        if (!contact.number.isNullOrBlank()) {
            RoundAction("Message", onClick = { onMessage(contact) }) { drawBubble(ThroneInk) }
            Spacer(Modifier.width(10.dp))
            RoundAction("Call", onClick = { onCall(contact) }) { drawHandset(ThroneInk) }
        }
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit, glyph: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(FieldFill)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) { glyph() }
    }
}

/** Grey circle with an initial, the way a contact without a photo is shown. */
@Composable
private fun Avatar(letter: String) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFA5A8B0), Color(0xFF85888F)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = Brand.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = Color.White)
    }
}

/** Small black squircle for non-app results (settings, files, VEIL). */
@Composable
private fun MarkSquare(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(IconSquircle)
            .background(ThroneInk),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Letter(text: String) {
    Text(text, style = Brand.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Color.White)
}

@Composable
private fun MagnifierGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f
        val r = this.size.minDimension * 0.34f
        val c = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val start = Offset(c.x + r * 0.72f, c.y + r * 0.72f)
        drawLine(color, start, Offset(this.size.width * 0.94f, this.size.height * 0.94f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ClearGlyph(onClick: () -> Unit) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(0xFFAEAEB2))
            .semantics { contentDescription = "Clear" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(7.dp)) {
            val w = 1.4.dp.toPx()
            drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
}

@Composable
private fun ChevronGlyph() {
    Canvas(Modifier.size(width = 8.dp, height = 13.dp)) {
        val w = 1.6.dp.toPx()
        val color = Color(0xFFC4C4C7)
        drawLine(color, Offset(1.dp.toPx(), 1.dp.toPx()), Offset(size.width - 1.dp.toPx(), size.height / 2f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - 1.dp.toPx(), size.height / 2f), Offset(1.dp.toPx(), size.height - 1.dp.toPx()), w, StrokeCap.Round)
    }
}

private fun DrawScope.drawHandset(color: Color) {
    val stroke = Stroke(width = 1.6.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.28f, size.height * 0.06f),
        size = Size(size.width * 0.44f, size.height * 0.88f),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
        style = stroke,
    )
    drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.78f))
}

private fun DrawScope.drawBubble(color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.06f, size.height * 0.08f),
        size = Size(size.width * 0.88f, size.height * 0.64f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
    )
    val tail = androidx.compose.ui.graphics.Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.68f)
        lineTo(size.width * 0.16f, size.height * 0.96f)
        lineTo(size.width * 0.46f, size.height * 0.68f)
        close()
    }
    drawPath(tail, color)
}

/* ---------- About (Settings-style) ---------- */

@Composable
private fun AboutScreen(edition: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = modifier.background(GroupedBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockReserve + 24.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp),
            ) {
                Canvas(Modifier.size(width = 10.dp, height = 17.dp)) {
                    val w = 2.2.dp.toPx()
                    drawLine(ThroneInk, Offset(size.width - 1.dp.toPx(), 1.dp.toPx()), Offset(1.5.dp.toPx(), size.height / 2f), w, StrokeCap.Round)
                    drawLine(ThroneInk, Offset(1.5.dp.toPx(), size.height / 2f), Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), w, StrokeCap.Round)
                }
                Text("Home", style = Brand.copy(fontSize = 17.sp), color = ThroneInk, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                "About",
                style = Brand.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                color = ThroneInk,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp),
            )
            Card {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                ) {
                    Image(
                        painter = painterResource(com.infinitum.throne.ui.R.drawable.infinitum_logo_stacked),
                        contentDescription = "Infinitum Corporation",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                    )
                    Text(
                        "THRONE][OS™",
                        style = Brand.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        color = ThroneInk,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(edition, style = Brand.copy(fontSize = 15.sp), color = ThroneMuted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Card {
                InfoRow("Name", "THRONE][OS™")
                HairlineDivider(start = 16.dp)
                InfoRow("Edition", edition)
                HairlineDivider(start = 16.dp)
                InfoRow("Build", "Debug")
            }
            Text(
                "Debug build. Not a certified release.",
                style = Brand.copy(fontSize = 13.sp),
                color = ThroneMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            Card {
                InfoRow("Network", "None")
                HairlineDivider(start = 16.dp)
                InfoRow("Widgets", "None")
                HairlineDivider(start = 16.dp)
                InfoRow("Ads", "None")
                HairlineDivider(start = 16.dp)
                InfoRow("Analytics", "None")
            }
            Text(
                "No network. No widgets. No ads. No analytics.",
                style = Brand.copy(fontSize = 13.sp),
                color = ThroneMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Brand.copy(fontSize = 17.sp), color = ThroneInk, modifier = Modifier.weight(1f))
        Text(value, style = Brand.copy(fontSize = 17.sp), color = ThroneMuted)
    }
}

/* ---------- Dock (unchanged) ---------- */

@Composable
private fun FrostedCallDock(
    messages: MessageDock?,
    onCall: () -> Unit,
    onCamera: () -> Unit,
    onMessages: (MessageDock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val absorb = remember { MutableInteractionSource() }
    Box(
        modifier
            .padding(horizontal = 22.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(96.dp)
            .drawBehind { drawDockShadow() }
            .background(DockFill, DockShape)
            .border(0.75.dp, Color.Black.copy(alpha = 0.08f), DockShape)
            .clickable(interactionSource = absorb, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        val button = 58.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraButton(onCamera)
            Spacer(Modifier.size(18.dp))
            CallButton(onCall)
            Spacer(Modifier.size(18.dp))
            if (messages != null) {
                MessagesButton(onClick = { onMessages(messages) })
            } else {
                Spacer(Modifier.size(button))
            }
        }
    }
}

private fun DrawScope.drawDockShadow() {
    val corner = 36.dp.toPx()
    val blur = 14.dp.toPx()
    val steps = 8
    for (step in steps downTo 1) {
        val spread = blur * step / steps
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.12f / steps),
            topLeft = Offset(-spread, 2.dp.toPx()),
            size = Size(size.width + spread * 2f, size.height + spread),
            cornerRadius = CornerRadius(corner + spread, corner + spread),
        )
    }
}

@Composable
private fun CameraButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Camera" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = 1.7.dp.toPx())
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.06f, size.height * 0.28f),
                size = Size(size.width * 0.88f, size.height * 0.58f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = stroke,
            )
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.16f,
                center = Offset(size.width / 2f, size.height * 0.58f),
                style = stroke,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.34f, size.height * 0.14f),
                size = Size(size.width * 0.22f, size.height * 0.14f),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun MessagesButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Messages" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                size = Size(size.width * 0.84f, size.height * 0.62f),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
            val tail = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.66f)
                lineTo(size.width * 0.16f, size.height * 0.94f)
                lineTo(size.width * 0.46f, size.height * 0.66f)
                close()
            }
            drawPath(tail, Color.White)
        }
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Call" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = 1.7.dp.toPx())
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.28f, size.height * 0.06f),
                size = Size(size.width * 0.44f, size.height * 0.88f),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = stroke,
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(size.width / 2f, size.height * 0.78f),
            )
        }
    }
}

/* ---------- Launching (unchanged) ---------- */

private fun openCamera(context: android.content.Context) {
    val still = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(still)
        return
    } catch (_: ActivityNotFoundException) {
        // Try the default still-capture handler.
    }
    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivity(capture, PackageManager.MATCH_DEFAULT_ONLY) != null) {
        try {
            context.startActivity(capture)
            return
        } catch (_: ActivityNotFoundException) {
            // Try a known camera package, then any capture handler.
        }
    }
    val pm = context.packageManager
    val packages = LinkedHashSet<String>()
    packages += "com.transsion.camera"
    pm.queryIntentActivities(capture, PackageManager.MATCH_ALL).forEach { info ->
        packages += info.activityInfo.packageName
    }
    pm.queryIntentActivities(still, PackageManager.MATCH_ALL).forEach { info ->
        packages += info.activityInfo.packageName
    }
    for (pkg in packages) {
        val launch = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: continue
        try {
            context.startActivity(launch)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next camera.
        }
    }
}

private fun openMessages(context: android.content.Context, message: MessageDock) {
    val launch = context.packageManager.getLaunchIntentForPackage(message.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to the smsto handler.
        }
    }
    val sendTo = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        .setPackage(message.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(sendTo)
    } catch (_: ActivityNotFoundException) {
        // No messaging app can be opened.
    }
}

private fun openVeil(context: android.content.Context, query: String) {
    if (query.isBlank()) return
    val uri = PhoneSearch.veilUri(query)
    val targeted = Intent(Intent.ACTION_VIEW, uri)
        .setPackage(PhoneSearch.VEIL_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val veilInstalled = context.packageManager.resolveActivity(targeted, PackageManager.MATCH_DEFAULT_ONLY) != null
    val intent = if (veilInstalled) targeted else Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser can take the address.
    }
}

private fun openContact(context: android.content.Context, contact: ContactHit) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id.toString())
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The contacts app is unavailable.
    }
}

private fun openTel(context: android.content.Context, contact: ContactHit) {
    val number = contact.number ?: return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No dialer is installed.
    }
}

private fun openSms(context: android.content.Context, contact: ContactHit) {
    val number = contact.number ?: return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No messaging app is installed.
    }
}

private fun openSetting(context: android.content.Context, setting: SettingHit) {
    val intent = Intent(setting.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // This settings page is not on the device.
    }
}

private fun openFile(context: android.content.Context, file: FileHit) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse(file.uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open this file.
    }
}

private fun openDialer(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No dialer is installed.
    }
}

private fun openApp(context: android.content.Context, app: AppEntry) {
    if (app.productId == "metatron" && app.kind != EntryKind.NATIVE) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://mttrn.app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            // No browser is available.
        }
        return
    }
    if (app.kind == EntryKind.NATIVE && app.packageName != null && app.className != null) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
    }
    val productId = app.productId ?: return
    val intent = Intent()
        .setClassName(ProductCatalog.SUITE_PACKAGE, ProductCatalog.SUITE_ACTIVITY)
        .putExtra(ProductCatalog.EXTRA_PRODUCT_ID, productId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The suite is installed with this layer. Without it, a site cannot open.
    }
}

private fun openInfo(context: android.content.Context, app: AppEntry) {
    val pkg = app.packageName ?: return
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
