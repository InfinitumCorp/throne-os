package com.infinitum.throne.suite

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.infinitum.throne.core.catalog.Product
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.catalog.ShelfKind
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.ui.HydratedTile
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.infinitum.throne.ui.IosDivider
import com.infinitum.throne.ui.IosEmpty
import com.infinitum.throne.ui.IosFooter
import com.infinitum.throne.ui.IosGroup
import com.infinitum.throne.ui.IosPillButton
import com.infinitum.throne.ui.IosRow
import com.infinitum.throne.ui.IosScreen
import com.infinitum.throne.ui.IosSearchField
import com.infinitum.throne.ui.IosSectionHeader
import com.infinitum.throne.ui.IosText
import com.infinitum.throne.ui.IosTextButton
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThroneTheme

/**
 * Opens a catalog product in this order: the installed native app, then this
 * secure WebView, then a Coming soon page. Trusted Web Activity is not used.
 * No product host published a Digital Asset Link, and a Chrome fallback would
 * leave this no-tracker shell.
 */
class ShellActivity : ComponentActivity() {
    private var products: List<Product> = emptyList()
    private var productId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        val edition = EditionPolicy.parse(BuildConfig.EDITION)
        products = loadProducts().let { ProductCatalog.visible(it, edition) }
        productId = intent.getStringExtra(ProductCatalog.EXTRA_PRODUCT_ID)
        if (productId == "nether") {
            startActivity(Intent(this, NetherActivity::class.java))
            productId = null
        }
        if (openNative(productId)) return
        setContent {
            ThroneTheme {
                SuiteScreen(
                    edition = edition.label,
                    products = products,
                    productId = productId,
                    onOpen = { product ->
                        if (!openNative(product.id)) productId = product.id
                    },
                    onBack = { productId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = intent.getStringExtra(ProductCatalog.EXTRA_PRODUCT_ID)
        if (requested == "nether") {
            startActivity(Intent(this, NetherActivity::class.java))
            productId = null
            return
        }
        if (!openNative(requested)) productId = requested
    }

    private fun loadProducts(): List<Product> {
        return assets.open("products.json").bufferedReader().use { reader ->
            ProductCatalog.parse(reader.readText())
        }
    }

    private fun openNative(id: String?): Boolean {
        val product = products.firstOrNull { it.id == id } ?: return false
        if (!NativeApps.launch(this, product)) return false
        finish()
        return true
    }
}

internal object NativeApps {
    fun isInstalled(context: Context, product: Product): Boolean {
        val manager = context.packageManager
        return product.packages.any { manager.getLaunchIntentForPackage(it) != null }
    }

    fun launch(context: Context, product: Product): Boolean {
        val manager = context.packageManager
        for (packageName in product.packages) {
            val intent = manager.getLaunchIntentForPackage(packageName) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        return false
    }
}

@Composable
private fun SuiteScreen(
    edition: String,
    products: List<Product>,
    productId: String?,
    onOpen: (Product) -> Unit,
    onBack: () -> Unit,
) {
    val product = products.firstOrNull { it.id == productId }
    when {
        productId == null -> Directory(edition, products, onOpen)
        product == null -> Missing(edition, onBack)
        product.url == null -> ComingSoon(edition, product, onBack)
        else -> WebShell(product, onBack)
    }
}

@Composable
private fun Directory(edition: String, products: List<Product>, onOpen: (Product) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(query, products) {
        val q = query.trim()
        if (q.isEmpty()) products else products.filter { it.name.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true) }
    }
    IosScreen(title = "Suite") {
        item(key = "search") {
            IosSearchField(value = query, onValueChange = { query = it }, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (query.isBlank()) {
            item(key = "nether") {
                IosSectionHeader("Vault")
                IosGroup {
                    IosRow(
                        title = "Nether",
                        subtitle = "Passwords, codes and notes. Stays offline.",
                        leading = { HydratedTile(productId = "nether", contentDescription = "Nether", flat = true, modifier = Modifier.size(44.dp)) },
                        trailing = { IosPillButton("OPEN", onClick = { context.startActivity(Intent(context, NetherActivity::class.java)) }) },
                        onClick = { context.startActivity(Intent(context, NetherActivity::class.java)) },
                    )
                }
            }
        }
        item(key = "products-header") { IosSectionHeader("Products") }
        if (shown.isEmpty()) {
            item(key = "none") { IosGroup { IosEmpty("No products match “${query.trim()}”.") } }
        } else {
            item(key = "products") {
                IosGroup {
                    shown.forEachIndexed { index, product ->
                        if (index > 0) IosDivider(inset = 74.dp)
                        val kind = when {
                            NativeApps.isInstalled(context, product) -> ShelfKind.NATIVE
                            product.url != null -> ShelfKind.WEB
                            else -> ShelfKind.PLACEHOLDER
                        }
                        IosRow(
                            title = product.name,
                            subtitle = product.summary,
                            leading = { HydratedTile(productId = product.id, contentDescription = product.name, flat = true, modifier = Modifier.size(44.dp)) },
                            trailing = {
                                IosPillButton(
                                    text = when (kind) {
                                        ShelfKind.NATIVE -> "OPEN"
                                        ShelfKind.WEB -> "VIEW"
                                        ShelfKind.PLACEHOLDER -> "SOON"
                                    },
                                    onClick = { onOpen(product) },
                                )
                            },
                            onClick = { onOpen(product) },
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            IosFooter("An installed app opens on its own. Otherwise this shell opens the product site, or Coming soon when no site is published. No tracker is added.")
            IosFooter("THRONE][OS™ $edition")
        }
    }
}

@Composable
private fun ComingSoon(edition: String, product: Product, onBack: () -> Unit) {
    IosScreen(title = product.name, backLabel = "Suite", onBack = onBack) {
        item(key = "hero") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            ) {
                HydratedTile(productId = product.id, contentDescription = product.name, flat = true, modifier = Modifier.size(96.dp))
                Text(
                    "Coming soon",
                    style = IosText.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "No site is published for this product yet.",
                    style = IosText.copy(fontSize = 15.sp),
                    color = ThroneMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "about") {
            IosSectionHeader("About")
            IosGroup {
                Text(
                    product.summary,
                    style = IosText.copy(fontSize = 17.sp, lineHeight = 23.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            IosFooter("THRONE][OS™ $edition")
        }
    }
}

@Composable
private fun Missing(edition: String, onBack: () -> Unit) {
    IosScreen(title = "Suite", backLabel = "Suite", onBack = onBack) {
        item(key = "missing") {
            IosGroup { IosEmpty("That product is not in this edition.") }
            IosFooter("THRONE][OS™ $edition")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebShell(product: Product, onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
        ) {
            IosTextButton("Done", onClick = onBack, bold = true, modifier = Modifier.align(Alignment.CenterStart))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 80.dp),
            ) {
                Text(
                    product.name,
                    style = IosText.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val host = remember(product.url) { product.url?.let { android.net.Uri.parse(it).host } }
                if (host != null) {
                    Text(host, style = IosText.copy(fontSize = 12.sp), color = ThroneMuted, maxLines = 1)
                }
            }
        }
        IosDivider(inset = 0.dp)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.safeBrowsingEnabled = false
                    WebView.setWebContentsDebuggingEnabled(false)
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, false)
                    webChromeClient = lockedChrome(product, this)
                    webViewClient = lockedClient(product)
                    loadUrl(product.url!!)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private fun lockedClient(product: Product) = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !ProductCatalog.allowsNavigation(product, request.url.toString())
    }

    @Suppress("DEPRECATION")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}

private fun lockedChrome(product: Product, host: WebView) = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        callback?.invoke(origin, false, false)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!isUserGesture) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        popup.settings.javaScriptEnabled = false
        popup.settings.allowFileAccess = false
        popup.settings.allowContentAccess = false
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                val target = request.url?.toString().orEmpty()
                if (ProductCatalog.allowsNavigation(product, target)) host.loadUrl(target)
                popupView.destroy()
                return true
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
