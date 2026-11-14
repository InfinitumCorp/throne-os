package com.infinitum.throne.guard

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.core.audit.Finding
import com.infinitum.throne.core.audit.FindingStatus
import com.infinitum.throne.core.audit.InstalledApp
import com.infinitum.throne.core.net.PrivateDnsPolicy
import com.infinitum.throne.ui.IosDivider
import com.infinitum.throne.ui.IosEmpty
import com.infinitum.throne.ui.IosFooter
import com.infinitum.throne.ui.IosGroup
import com.infinitum.throne.ui.IosRow
import com.infinitum.throne.ui.IosScreen
import com.infinitum.throne.ui.IosSearchField
import com.infinitum.throne.ui.IosSectionHeader
import com.infinitum.throne.ui.IosStatusDot
import com.infinitum.throne.ui.IosText
import com.infinitum.throne.ui.IosTextButton
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted

@Composable
fun GuardScreen(viewModel: GuardViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (viewModel.screen == GuardScreen.DnsGuide) {
        DnsGuide(viewModel)
    } else {
        Dashboard(viewModel)
    }
}

private fun statusLabel(status: FindingStatus): String = when (status) {
    FindingStatus.CLEAR -> "Clear"
    FindingStatus.REVIEW -> "Review"
    FindingStatus.UNKNOWN -> "Unknown"
    FindingStatus.NOTE -> "Note"
}

@Composable
private fun Dashboard(viewModel: GuardViewModel) {
    val context = LocalContext.current
    val report = viewModel.report
    val messaging = report?.findings?.filter { it.id.startsWith("msg_") }.orEmpty()
    val device = report?.findings?.filter { !it.id.startsWith("msg_") }.orEmpty()
    val query = viewModel.query.trim()
    val apps = report?.permissionApps.orEmpty().filter { app ->
        query.isEmpty() ||
            app.label.contains(query, true) ||
            app.packageName.contains(query, true) ||
            app.grantedDangerous.any { it.contains(query, true) }
    }
    val review = report?.findings?.count { it.status == FindingStatus.REVIEW } ?: 0
    val open: (String) -> Unit = { destination ->
        if (destination == "private_dns_guide") viewModel.showGuide() else GuardIntents.open(context, destination)
    }
    IosScreen(
        title = "Guard",
        trailing = { IosTextButton("Refresh", onClick = viewModel::refresh) },
    ) {
        item(key = "summary") {
            IosGroup {
                IosRow(
                    title = when {
                        report == null -> "Reading this device"
                        review == 0 -> "Nothing to review"
                        review == 1 -> "1 item to review"
                        else -> "$review items to review"
                    },
                    subtitle = "Local audit, ${viewModel.edition.label}. Nothing here is enforced. Rows open Android settings so you can change them.",
                    subtitleLines = 3,
                    leading = { IosStatusDot(on = report != null && review == 0) },
                )
            }
        }

        item(key = "msg-header") { IosSectionHeader("Secure messaging") }
        if (messaging.isEmpty()) {
            item(key = "msg-empty") {
                IosGroup { IosEmpty(if (viewModel.loading) "Reading this device." else "No messaging checks.") }
            }
        }
        findingGroups(messaging, open)

        item(key = "dev-header") { IosSectionHeader("Device") }
        findingGroups(device, open)

        sensorSection("Camera", report?.camera.orEmpty(), "No app has camera permission granted.", context)
        sensorSection("Microphone", report?.microphone.orEmpty(), "No app has microphone permission granted.", context)
        sensorSection("Location", report?.location.orEmpty(), "No app has location permission granted.", context)

        item(key = "perm-header") {
            IosSectionHeader("Permissions")
            IosSearchField(
                value = viewModel.query,
                onValueChange = { viewModel.query = it.take(80) },
                placeholder = "Filter by app or permission",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item(key = "perm-list") {
            IosGroup {
                if (apps.isEmpty()) {
                    IosEmpty(if (query.isEmpty()) "No apps to show." else "No apps match “$query”.")
                }
                apps.forEachIndexed { index, app ->
                    if (index > 0) IosDivider()
                    IosRow(
                        title = app.label,
                        subtitle = if (app.grantedDangerous.isEmpty()) {
                            "No dangerous permissions granted."
                        } else {
                            app.grantedDangerous.joinToString(", ") { it.removePrefix("android.permission.") }
                        },
                        subtitleLines = 3,
                        chevron = true,
                        onClick = { GuardIntents.open(context, "app:${app.packageName}") },
                    )
                }
            }
            IosFooter("No analytics. The only network call is the Private DNS check you start yourself.")
        }
    }
}

private fun LazyListScope.findingGroups(findings: List<Finding>, onOpen: (String) -> Unit) {
    findings.forEach { finding ->
        item(key = "finding-${finding.id}") {
            FindingGroup(finding, onOpen)
        }
    }
}

@Composable
private fun FindingGroup(finding: Finding, onOpen: (String) -> Unit) {
    IosGroup(Modifier.padding(bottom = 12.dp)) {
        IosRow(
            title = finding.title,
            value = statusLabel(finding.status),
            leading = { IosStatusDot(on = finding.status == FindingStatus.CLEAR) },
        )
        Text(
            finding.summary,
            style = IosText.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = ThroneMuted,
            modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 12.dp),
        )
        finding.rows.forEach { row ->
            val destination = row.destination
            IosDivider(inset = 40.dp)
            IosRow(
                title = row.title,
                subtitle = row.detail,
                subtitleLines = 3,
                chevron = destination != null,
                modifier = Modifier.padding(start = 24.dp),
                onClick = destination?.let { { onOpen(it) } },
            )
        }
        val actionLabel = finding.actionLabel
        val actionDestination = finding.actionDestination
        if (actionLabel != null && actionDestination != null) {
            IosDivider(inset = 40.dp)
            IosRow(
                title = actionLabel,
                chevron = true,
                modifier = Modifier.padding(start = 24.dp),
                onClick = { onOpen(actionDestination) },
            )
        }
    }
}

private fun LazyListScope.sensorSection(
    title: String,
    apps: List<InstalledApp>,
    empty: String,
    context: android.content.Context,
) {
    item(key = "sensor-$title") {
        IosSectionHeader(title)
        IosGroup {
            if (apps.isEmpty()) {
                IosRow(title = empty, titleColor = ThroneMuted)
            }
            apps.forEachIndexed { index, app ->
                if (index > 0) IosDivider()
                IosRow(
                    title = app.label,
                    subtitle = app.packageName,
                    subtitleLines = 1,
                    chevron = true,
                    onClick = { GuardIntents.open(context, "app:${app.packageName}") },
                )
            }
        }
    }
}

@Composable
private fun DnsGuide(viewModel: GuardViewModel) {
    val context = LocalContext.current
    val specifier = viewModel.snapshot?.privateDnsSpecifier?.trim().orEmpty()
    val mode = viewModel.snapshot?.privateDnsMode ?: "unread"
    IosScreen(title = "Private DNS", backLabel = "Guard", onBack = viewModel::showDashboard) {
        item(key = "current") {
            IosGroup {
                IosRow(title = "Mode", value = mode)
                IosDivider()
                IosRow(title = "Hostname", value = specifier.ifEmpty { "None" })
            }
            IosFooter("Android does not let this app set Private DNS. You type the hostname. Nothing is contacted until you tap a check.")
        }
        item(key = "steps") {
            IosSectionHeader("Set it up")
            IosGroup {
                listOf(
                    "Open network settings.",
                    "On HiOS: Network and internet, then Private DNS.",
                    "Choose Private DNS provider hostname.",
                    "Enter ${PrivateDnsPolicy.RECOMMENDED_HOST}",
                    "Return here and refresh.",
                ).forEachIndexed { index, step ->
                    if (index > 0) IosDivider(inset = 48.dp)
                    IosRow(
                        title = step,
                        leading = {
                            Text(
                                "${index + 1}",
                                style = IosText.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(end = 2.dp),
                            )
                        },
                    )
                }
            }
            IosFooter(
                "${PrivateDnsPolicy.RECOMMENDED_HOST} is a public tracker-blocking resolver, not an Infinitum server. dns.quad9.net is another public choice. Use VEIL’s own resolver when you have its hostname. Private DNS is not a substitute for always-on VEIL with lockdown.",
            )
        }
        item(key = "actions") {
            IosSectionHeader("Actions")
            IosGroup {
                IosRow(
                    title = "Open network settings",
                    chevron = true,
                    onClick = { GuardIntents.open(context, "wireless") },
                )
                IosDivider()
                IosRow(
                    title = if (viewModel.probing) "Checking…" else "Check recommended resolver",
                    titleColor = if (viewModel.probing) ThroneMuted else ThroneInk,
                    onClick = if (viewModel.probing) null else viewModel::probeRecommended,
                )
                if (specifier.isNotEmpty()) {
                    IosDivider()
                    IosRow(
                        title = "Check the hostname set on this phone",
                        titleColor = if (viewModel.probing) ThroneMuted else ThroneInk,
                        onClick = if (viewModel.probing) null else viewModel::probeConfigured,
                    )
                }
            }
            viewModel.probeMessage?.let { IosFooter(it) }
        }
    }
}
