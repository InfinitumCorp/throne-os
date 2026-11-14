package com.infinitum.throne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * iOS-style building blocks for THRONE][OS apps: grouped lists, large titles,
 * rounded row groups and capsule buttons. Black ink on white and light grey,
 * no colour accents, matching the launcher.
 */

val IosGroupedBackground = Color(0xFFF2F2F7)
val IosSeparator = Color(0x1F3C3C43)
val IosFieldFill = Color(0x1F767680)
val IosSecondaryLabel = ThroneMuted
private val GroupShape = RoundedCornerShape(12.dp)

/** System sans, the closest match on Android to the iOS system face. */
val IosText = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, color = ThroneInk)

/**
 * A screen with an optional "‹ Back" bar, a 34 sp large title that collapses
 * into a centred inline title on scroll, and a grouped-list body.
 */
@Composable
fun IosScreen(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    background: Color = IosGroupedBackground,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 32.dp,
    content: LazyListScope.() -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
        ) {
            if (onBack != null) {
                IosBackButton(backLabel ?: "Back", onBack, Modifier.align(Alignment.CenterStart))
            }
            Text(
                title,
                style = IosText.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp)
                    .alpha(if (collapsed) 1f else 0f),
            )
            if (trailing != null) {
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailing,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        ) {
            item(key = "ios-large-title") {
                Text(
                    title,
                    style = IosText.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 10.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun IosBackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Canvas(Modifier.size(width = 10.dp, height = 17.dp)) {
            val w = 2.2.dp.toPx()
            drawLine(ThroneInk, Offset(size.width - 1.dp.toPx(), 1.dp.toPx()), Offset(1.5.dp.toPx(), size.height / 2f), w, StrokeCap.Round)
            drawLine(ThroneInk, Offset(1.5.dp.toPx(), size.height / 2f), Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), w, StrokeCap.Round)
        }
        Text(label, style = IosText.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 6.dp))
    }
}

/** Grey caps-style header above a group, as in iOS Settings. */
@Composable
fun IosSectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = IosText.copy(fontSize = 13.sp, letterSpacing = 0.2.sp),
        color = IosSecondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 7.dp),
    )
}

/** Grey explanatory text under a group. */
@Composable
fun IosFooter(text: String) {
    Text(
        text,
        style = IosText.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = IosSecondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
    )
}

/** White rounded group of rows. Separate rows with [IosDivider]. */
@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(Color.White),
        content = content,
    )
}

@Composable
fun IosDivider(inset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(IosSeparator),
    )
}

/**
 * One list row: optional leading icon, title with optional subtitle, optional
 * trailing value, and a chevron when it navigates.
 */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = ThroneInk,
    subtitleLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = IosText.copy(fontSize = 17.sp),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = IosText.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = IosSecondaryLabel,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = IosText.copy(fontSize = 17.sp),
                color = IosSecondaryLabel,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(10.dp))
            IosChevron()
        }
    }
}

@Composable
fun IosChevron() {
    Canvas(Modifier.size(width = 8.dp, height = 13.dp)) {
        val w = 1.6.dp.toPx()
        val color = Color(0xFFC4C4C7)
        drawLine(color, Offset(1.dp.toPx(), 1.dp.toPx()), Offset(size.width - 1.dp.toPx(), size.height / 2f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - 1.dp.toPx(), size.height / 2f), Offset(1.dp.toPx(), size.height - 1.dp.toPx()), w, StrokeCap.Round)
    }
}

/** Full-width black capsule button, the iOS "filled" style in ink. */
@Composable
fun IosFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) ThroneInk else ThroneInk.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

/** Small grey capsule button, like "OPEN" or "GET" in an iOS list. */
@Composable
fun IosPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(IosFieldFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IosText.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) ThroneInk else IosSecondaryLabel,
        )
    }
}

/** Plain ink text button, for nav-bar actions such as "Done" or "Close". */
@Composable
fun IosTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Text(
        text,
        style = IosText.copy(fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Rounded grey search field with a magnifier and clear button. */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = IosText.copy(fontSize = 17.sp),
        cursorBrush = SolidColor(ThroneInk),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosFieldFill)
                    .padding(horizontal = 8.dp),
            ) {
                IosMagnifier(IosSecondaryLabel, 15.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = IosText.copy(fontSize = 17.sp), color = IosSecondaryLabel)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFAEAEB2))
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(7.dp)) {
                            val w = 1.4.dp.toPx()
                            drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
                            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun IosMagnifier(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f
        val r = this.size.minDimension * 0.34f
        val c = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val start = Offset(c.x + r * 0.72f, c.y + r * 0.72f)
        drawLine(color, start, Offset(this.size.width * 0.94f, this.size.height * 0.94f), stroke, StrokeCap.Round)
    }
}

/** Status dot used in place of colour: filled ink for on, hollow for off. */
@Composable
fun IosStatusDot(on: Boolean) {
    Canvas(Modifier.size(10.dp)) {
        if (on) {
            drawCircle(ThroneInk)
        } else {
            drawCircle(ThroneInk.copy(alpha = 0.35f), style = Stroke(1.4.dp.toPx()))
        }
    }
}

/** Centred empty-state text for a grouped list. */
@Composable
fun IosEmpty(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = IosText.copy(fontSize = 15.sp), color = IosSecondaryLabel, textAlign = TextAlign.Center)
    }
}
