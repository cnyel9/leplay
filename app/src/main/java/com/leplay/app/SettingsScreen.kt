package com.leplay.app
import androidx.compose.ui.window.Dialog
import android.provider.DocumentsContract
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File

/**
 * The Settings screens' own backdrop layer (0.0.8-test3 rebuild 123).
 * Settings lives inside the layer the app-wide page backdrop records, so
 * its cards must never refract that backdrop (the first test-3 build
 * crashed doing exactly that). Instead each Settings page records its OWN
 * layer, the CollapsingHeaderLayout pattern, and its cards refract that
 * with the full 0.0.1 glass engine. Null outside Settings pages, where
 * surfaces fall back to the shared pageGlass path.
 */
val LocalSettingsBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    onAccountSwitched: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCheckUpdate: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = Account.profile
    val scrollState = rememberScrollState()

    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var activeFloatingSection by remember { mutableStateOf<SettingsFloatingSection?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateFeedback by remember { mutableStateOf<String?>(null) }

    val isOverlayOpen = showSignOutConfirm || checkingUpdate || updateFeedback != null
    // The overlay blur is a glass-era effect: with Liquid Glass off the
    // Settings column must not blur at all (0.0.9 audit L3).
    val glassOn = GlassQuality.effectsSupported
    val blurRadius by animateDpAsState(
        targetValue = if (isOverlayOpen && glassOn && Build.VERSION.SDK_INT >= 31) 16.dp else 0.dp,
        animationSpec = tween(220),
        label = "settingsBlur",
    )

    // On TV / wide tablets the settings column is capped and centered
    // instead of stretching edge to edge; the Box keeps the full-bleed
    // surface color behind it. Phone layout is unchanged.
    val wideSettings = TvMode.isTv ||
        androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600
    // Settings' own backdrop layer for real 0.0.1 refraction on its cards:
    // this Box records the layer, the scrolling column is composed outside
    // it (below), its cards refract it. Never the page backdrop (see
    // LocalSettingsBackdrop).
    val settingsBackdrop = rememberLayerBackdrop()
    Box(modifier = Modifier.fillMaxSize().background(c.surface)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(settingsBackdrop)
                .background(c.surface)
        )
        CompositionLocalProvider(LocalSettingsBackdrop provides settingsBackdrop) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (wideSettings) Modifier.widthIn(max = 860.dp) else Modifier.fillMaxWidth())
                .align(Alignment.Center)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            BackScreenHeader(onBack = onBack) {
                Text(
                    text = I18n.t("settings", "Settings"),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                    color = c.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
            ) {
                Spacer(Modifier.height(2.dp))

                SettingsProfileCard(
                    profile = profile,
                    onSignIn = onSignIn,
                    onRequestSignOut = { showSignOutConfirm = true },
                    onAccountSwitched = onAccountSwitched,
                )

                SettingsSectionHeader(I18n.t("customization", "Customization"))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        title = I18n.t("appearance_theme", "Appearance & Theme"),
                        subtitle = I18n.t("appearance_sub", "Theme mode, color palette and UI style"),
                        onClick = { activeFloatingSection = SettingsFloatingSection.APPEARANCE },
                        padded = false,
                    )
                }

                SettingsSectionHeader(I18n.t("player_interface", "Player & Interface"))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Tune,
                        title = I18n.t("general_modification", "General Modification"),
                        subtitle = I18n.t("general_modification_sub", "Font, gestures, quality & downloads"),
                        onClick = { activeFloatingSection = SettingsFloatingSection.GENERAL_MODS },
                        padded = false,
                    )
                }

                SettingsSectionHeader(I18n.t("language_section", "Language"))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Translate,
                        title = I18n.t("language", "Language"),
                        subtitle = LanguageSettings.currentDisplay(),
                        onClick = { activeFloatingSection = SettingsFloatingSection.LANGUAGE },
                        padded = false,
                    )
                }

                SettingsSectionHeader(I18n.t("data_storage", "Data & Storage"))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.DataUsage,
                        title = I18n.t("storage_usage", "Storage Usage"),
                        subtitle = I18n.t("storage_sub", "See phone storage & clear LePlay cache"),
                        onClick = { activeFloatingSection = SettingsFloatingSection.STORAGE_USAGE },
                        padded = false,
                    )
                }

                SettingsSectionHeader(I18n.t("about", "About"))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = I18n.t("version", "Version"),
                        subtitle = "LePlay ${BuildConfig.VERSION_NAME}",
                        padded = false,
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF132818))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2ECC71))
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = I18n.t("up_to_date", "Up to date"),
                                        color = Color(0xFF2ECC71),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.History,
                        title = I18n.t("version_history", "Version history"),
                        subtitle = I18n.t("version_history_sub", "Changelogs from 0.0.1 to the latest"),
                        onClick = { activeFloatingSection = SettingsFloatingSection.VERSION_HISTORY },
                        padded = false,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.FileDownload,
                        title = I18n.t("check_updates", "Check for updates"),
                        subtitle = I18n.t("check_updates_sub", "New GitHub releases install from inside the app"),
                        onClick = {
                            if (checkingUpdate) return@SettingsRow
                            checkingUpdate = true
                            updateFeedback = null
                            scope.launch {
                                val start = System.currentTimeMillis()
                                onCheckUpdate()
                                val elapsed = System.currentTimeMillis() - start
                                if (elapsed < 1200) delay(1200 - elapsed)
                                checkingUpdate = false
                                updateFeedback = I18n.t("latest_version_notice", "You're on the latest version ({version}).").replace("{version}", BuildConfig.VERSION_NAME)
                            }
                        },
                        padded = false,
                    )
                    val openRepo: (String) -> Unit = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                    val leplayRepo = "https://github.com/cnyel9/leplay"
                    SettingsRow(
                        icon = Icons.Rounded.Code,
                        title = I18n.t("open_source", "Open Source"),
                        subtitle = I18n.t("open_source_sub", "View source code on GitHub"),
                        onClick = { openRepo(leplayRepo) },
                        padded = false,
                    )
                    val telegram = "https://github.com/cnyel9"
                    val openTelegram: () -> Unit = {
                        val app = Intent(Intent.ACTION_VIEW, Uri.parse(telegram))
                        val web = Intent(Intent.ACTION_VIEW, Uri.parse(telegram))
                        runCatching { context.startActivity(app) }
                            .onFailure { runCatching { context.startActivity(web) } }
                    }
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = I18n.t("developer", "Developer"),
                        subtitle = "Levi Setiadi (@cnyel9)",
                        onClick = openTelegram,
                        padded = false,
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Favorite,
                        title = I18n.t("support_developer", "Support Developer"),
                        subtitle = I18n.t("support_developer_sub", "Buy the developer a coffee"),
                        onClick = { activeFloatingSection = SettingsFloatingSection.SUPPORT },
                    )
                }
            }
        }
        }

        if (isOverlayOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        AnimatedContent(
            targetState = activeFloatingSection,
            transitionSpec = {
                if (GlassSettings.liteMode) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else if (targetState == null) {
                    (slideInHorizontally(tween(300, easing = Motion.Emphasized)) { -it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(260, easing = Motion.Emphasized)) { it } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(260, easing = Motion.Emphasized)) { -it } + fadeOut(tween(180)))
                }
            },
            label = "settingsFloatingSection",
        ) { section ->
            when (section) {
                SettingsFloatingSection.APPEARANCE -> {
                    SettingsFloatingDialog(
                        title = I18n.t("appearance_theme", "Appearance & Theme"),
                        subtitle = I18n.t("appearance_sub", "Theme mode, color palette & UI style"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        AppearanceThemeContent(context = context)
                    }
                }
                SettingsFloatingSection.GENERAL_MODS -> {
                    SettingsFloatingDialog(
                        title = I18n.t("general_modification", "General Modification"),
                        subtitle = I18n.t("general_modification_sub", "Font, gestures, quality & downloads"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        GeneralModificationContent(context = context)
                    }
                }
                SettingsFloatingSection.STORAGE_USAGE -> {
                    SettingsFloatingDialog(
                        title = I18n.t("storage_usage", "Storage Usage"),
                        subtitle = I18n.t("storage_sub", "Device memory & LePlay data"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        StorageUsageContent(context = context) {
                            cacheMessage = I18n.t("cache_cleared", "Cache cleared successfully")
                        }
                    }
                }
                SettingsFloatingSection.LANGUAGE -> {
                    SettingsFloatingDialog(
                        title = I18n.t("language", "Language"),
                        subtitle = I18n.t("language_sub", "Choose application language"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        LanguageContent(context = context) {
                            activeFloatingSection = null
                        }
                    }
                }
                SettingsFloatingSection.VERSION_HISTORY -> {
                    SettingsFloatingDialog(
                        title = I18n.t("version_history", "Version history"),
                        subtitle = I18n.t("version_history_sub", "Changelogs from 0.0.1 to the latest"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        VersionHistoryContent()
                    }
                }
                SettingsFloatingSection.SUPPORT -> {
                    SettingsFloatingDialog(
                        title = I18n.t("support_developer", "Support Developer"),
                        subtitle = I18n.t("support_developer_sub", "Buy the developer a coffee"),
                        onDismiss = { activeFloatingSection = null },
                    ) {
                        SupportDeveloperContent()
                    }
                }
                null -> {}
            }
        }

        AnimatedVisibility(
            visible = showSignOutConfirm,
            enter = if (GlassSettings.liteMode) fadeIn(snap()) else scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.88f) + fadeIn(tween(160)),
            exit = if (GlassSettings.liteMode) fadeOut(snap()) else scaleOut(tween(120), targetScale = 0.92f) + fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSignOutConfirm = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The sign-out dialog is glass over the Settings root page's
                // own layer too (rebuild 124): composed outside that layer,
                // so drawing it is the legal direction.
                val signOutGlass = LocalSettingsBackdrop.current
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .then(
                            if (signOutGlass != null) {
                                Modifier.ownLayerGlass(signOutGlass, RoundedCornerShape(24.dp), c.surfaceContainerHighest)
                            } else {
                                Modifier.clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHighest)
                            },
                        )
                        .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c.error.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = c.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = I18n.t("sign_out_question", "Sign out?"),
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = I18n.t("sign_out_confirm_body", "Are you sure you want to sign out from {name}?").replace("{name}", Account.profile?.name ?: I18n.t("this_account", "this account")),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = c.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceContainerHigh)
                                .pressable { showSignOutConfirm = false }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(I18n.t("cancel", "Cancel"), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = c.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.error)
                                .pressable {
                                    showSignOutConfirm = false
                                    Account.signOut()
                                    onSignedOut()
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(I18n.t("sign_out", "Sign out"), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = c.onError)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = checkingUpdate || updateFeedback != null,
            enter = if (GlassSettings.liteMode) fadeIn(snap()) else scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.88f) + fadeIn(tween(160)),
            exit = if (GlassSettings.liteMode) fadeOut(snap()) else scaleOut(tween(120), targetScale = 0.92f) + fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!checkingUpdate) updateFeedback = null
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The update dialog refracts the Settings root page's own
                // layer (rebuild 124): composed outside that layer, so
                // drawing it is the legal direction, and the blurred page
                // behind gives the glass something real to bend.
                val updateGlass = LocalSettingsBackdrop.current
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .then(
                            if (updateGlass != null) {
                                Modifier.ownLayerGlass(updateGlass, RoundedCornerShape(24.dp), c.surfaceContainerHighest)
                            } else {
                                Modifier.clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHighest)
                            },
                        )
                        .border(1.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(
                            color = c.primary,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = I18n.t("checking_updates", "Checking for updates…"),
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = I18n.t("connecting_github_releases", "Connecting to GitHub releases…"),
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                        )
                    } else if (updateFeedback != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF132818)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = I18n.t("up_to_date", "Up to date"),
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = updateFeedback.orEmpty(),
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.primary)
                                .pressable { updateFeedback = null }
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("OK", color = c.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private enum class SettingsFloatingSection {
    APPEARANCE,
    GENERAL_MODS,
    STORAGE_USAGE,
    LANGUAGE,
    VERSION_HISTORY,
    SUPPORT,
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val c = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = c.onSurfaceVariant.copy(alpha = 0.72f),
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val settingsGlass = LocalSettingsBackdrop.current
    // The card owns the horizontal inset for ALL its rows (rebuild 125):
    // rows designed for the root page (SettingsRow) bring their own 14.dp,
    // so the padding must live in the card and the root-page usages pass
    // padded = false. Rebuild 124 wrapped bare switch rows in cards
    // without any inset, so titles touched the card edge and switches
    // overflowed it: the sub-screens read as broken, not glass.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (settingsGlass != null) {
                    Modifier.ownLayerGlass(settingsGlass, RoundedCornerShape(18.dp), c.surfaceContainerHigh)
                } else {
                    Modifier.pageGlass(RoundedCornerShape(18.dp), c.surfaceContainerHigh)
                },
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    padded: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .then(if (padded) Modifier.padding(horizontal = 14.dp, vertical = 11.dp) else Modifier.padding(vertical = 11.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = c.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null,
                tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsFloatingDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)

    // Each sub-screen records its own backdrop layer, like the root page,
    // so its cards get real 0.0.1 refraction (see LocalSettingsBackdrop).
    val dialogBackdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(dialogBackdrop)
                .background(c.surface)
        )
        CompositionLocalProvider(LocalSettingsBackdrop provides dialogBackdrop) {
        Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        BackScreenHeader(onBack = onDismiss) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.4).sp,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        }
        }
    }
}

@Composable
private fun VersionHistoryContent() {
    val c = MaterialTheme.colorScheme
    // Written manually per version (0.0.8 decision): the old screen fetched
    // the release list from GitHub and force-closed when its LazyColumn was
    // measured inside the scrolling dialog. A static list cannot crash.
    val entries = listOf(
        Triple("0.0.8", "9 Nov 2026", "Unified history for videos, music and Shorts with filter chips, updater jumps straight to the latest release with the full changelog, sleep timer, vibration switch, fullscreen vertical mode, faster subtitle rendering and Latest search, Liquid Glass across every screen including Settings (off means no glass at all), stretch-to-fill zoom mode, instant tap controls with double-tap seek, brightness and volume edge gestures in portrait and fullscreen, faster and more reliable sign-in, Support Developer page."),
        Triple("0.0.7", "8 Nov 2026", "Music engine (Package A): queue, auto radio, queue panel, prev/next everywhere. Autoplay follows the on-screen Up Next list, endless related videos, search sort chips, MP3 audio-only downloads, double-tap seek without the controls flashing, new icon, single Liquid Glass switch, TV and tablet polish, voice search."),
        Triple("0.0.6", "8 Nov 2026", "Lite Mode, live chat and comments panels fixed, next/previous video buttons, responsive player buttons, animation pass."),
        Triple("0.0.5", "7 Nov 2026", "Indonesian by default, full Indonesian/English UI dictionaries, selectable descriptions, rb/jt/M number formats."),
        Triple("0.0.4", "7 Nov 2026", "Channel sort chips fixed (labels and order), live chat replay fallback."),
        Triple("0.0.3", "7 Nov 2026", "Channel sort chips (Latest/Popular/Oldest), live chat replay."),
        Triple("0.0.2", "7 Nov 2026", "New in-app brand icon and TV banner."),
        Triple("0.0.1", "7 Nov 2026", "First release: live chat and comments panels fixed, official icon."),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.forEach { (version, date, body) ->
            SettingsCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "LePlay $version",
                            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = c.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (version == BuildConfig.VERSION_NAME.substringBefore('-')) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF132818))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2ECC71)),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = I18n.t("up_to_date", "Up to date"),
                                        color = Color(0xFF2ECC71),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(date, fontSize = 12.sp, color = c.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body,
                        fontSize = 13.sp, lineHeight = 19.sp,
                        color = c.onSurface.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportDeveloperContent() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            I18n.t("support_body", "LePlay is free, with no ads. If it makes your day a little better, a voluntary coffee keeps development going. Thank you!"),
            fontSize = 13.5.sp, lineHeight = 19.sp,
            color = c.onSurface.copy(alpha = 0.92f),
        )
        Spacer(Modifier.height(16.dp))
        DonationCard(
            iconRes = R.drawable.dana,
            name = "DANA",
            number = "0831-5475-6450",
            holder = "Levi Setiadi",
            onCopy = { copyDonationNumber(context, "083154756450") },
        )
        Spacer(Modifier.height(10.dp))
        DonationCard(
            iconRes = R.drawable.seabank,
            name = "SeaBank",
            number = "901587956054",
            holder = "Levi Setiadi",
            onCopy = { copyDonationNumber(context, "901587956054") },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            I18n.t("tap_to_copy", "Tap a card to copy the number"),
            fontSize = 11.5.sp, color = c.onSurfaceVariant,
        )
    }
}

private fun copyDonationNumber(context: Context, number: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LePlay", number))
        android.widget.Toast.makeText(context, I18n.t("copied", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DonationCard(
    iconRes: Int,
    name: String,
    number: String,
    holder: String,
    onCopy: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = onCopy)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = name,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface)
                Spacer(Modifier.height(1.dp))
                Text(number, fontSize = 13.sp, color = c.onSurface)
                Text(holder, fontSize = 11.5.sp, color = c.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = I18n.t("copied", "Copied"),
                tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AppearanceThemeContent(context: Context) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SettingsCard {
        SettingsSwitchRow(
            title = I18n.t("liquid_glass", "Liquid Glass"),
            subtitle = if (GlassSettings.enabled && !GlassSettings.liteMode) {
                I18n.t("liquid_glass_sub_on", "Frosted, see-through surfaces with depth and blur")
            } else {
                I18n.t("lite_mode_sub", "Solid surfaces, animations off, no Shorts preloading")
            },
            checked = GlassSettings.enabled && !GlassSettings.liteMode,
            onCheckedChange = { GlassSettings.setGlass(it) },
        )

        SettingsSwitchRow(
            title = I18n.t("haptics", "Haptics"),
            subtitle = I18n.t("haptics_sub", "Vibrate on taps and controls"),
            checked = HapticsSettings.enabled,
            onCheckedChange = { HapticsSettings.setHaptics(it) },
        )
      }

      SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("theme_mode", "Theme mode"), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(I18n.t("theme_mode_sub", "Choose how the app looks"), color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            listOf(
                "system" to I18n.t("theme_system", "System"),
                "dark" to I18n.t("theme_dark", "Dark"),
                "light" to I18n.t("theme_light", "Light"),
                "amoled" to I18n.t("theme_amoled", "AMOLED"),
            ).forEach { (mode, label) ->
                val on = ThemeSettings.mode == mode
                val chipBg by animateColorAsState(
                    targetValue = if (on) c.primary else c.surfaceContainerHigh,
                    animationSpec = tween(260, easing = Motion.Emphasized),
                    label = "themeChipBg",
                )
                // Unselected theme chips are glass like every other chip in
                // the app (0.0.8-test3 rebuild 124, owner: sub-screens must
                // be Liquid Glass too); the selected one keeps its solid
                // primary ink so the choice stays unmistakable.
                val themeGlass = LocalSettingsBackdrop.current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressable { ThemeSettings.chooseMode(mode) }
                        .then(
                            when {
                                on -> Modifier.clip(RoundedCornerShape(14.dp)).background(chipBg)
                                themeGlass != null -> Modifier.ownLayerGlass(themeGlass, RoundedCornerShape(14.dp), c.surfaceContainerHigh)
                                else -> Modifier.pageGlass(RoundedCornerShape(14.dp), c.surfaceContainerHigh)
                            },
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (on) c.onPrimary else c.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
            }
        }
      }

    }
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.NavigateNext,
            contentDescription = null,
            tint = c.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ScaleInCheckBadge(selected: Boolean, size: Dp, iconSize: Dp) {
    val c = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = selected,
        enter = if (GlassSettings.liteMode) fadeIn(snap()) else scaleIn(spring(dampingRatio = 0.62f, stiffness = 420f)) + fadeIn(tween(150, easing = Motion.Emphasized)),
        exit = if (GlassSettings.liteMode) fadeOut(snap()) else fadeOut(tween(120, easing = Motion.Emphasized)),
    ) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = I18n.t("selected", "Selected"), tint = c.onPrimary, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun ScaleInCheckIcon(visible: Boolean, size: Dp, tint: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = if (GlassSettings.liteMode) fadeIn(snap()) else scaleIn(spring(dampingRatio = 0.62f, stiffness = 420f)) + fadeIn(tween(150, easing = Motion.Emphasized)),
        exit = if (GlassSettings.liteMode) fadeOut(snap()) else fadeOut(tween(120, easing = Motion.Emphasized)),
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun QualitySelectionDialog(
    title: String,
    currentMode: PlaybackQualityMode,
    currentSpecificHeight: Int?,
    onSelectMode: (PlaybackQualityMode) -> Unit,
    onSelectSpecific: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val specificOptions = listOf(
        2160 to "2160p (4K UHD)",
        1440 to "1440p (2K QHD)",
        1080 to "1080p (Full HD)",
        720 to "720p (HD)",
        480 to "480p (SD)",
        360 to "360p",
        240 to "240p",
        144 to "144p",
    )
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = c.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = I18n.t("select_quality_sub", "Select default playback quality"),
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                listOf(
                    PlaybackQualityMode.AUTO,
                    PlaybackQualityMode.HIGH,
                    PlaybackQualityMode.DATA_SAVER
                ).forEach { mode ->
                    val selected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .pressable { onSelectMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.displayLabel,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (selected) c.primary else c.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = mode.displaySubtitle,
                                fontSize = 11.5.sp,
                                color = c.onSurfaceVariant,
                            )
                        }
                        ScaleInCheckBadge(selected, 22.dp, 14.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = I18n.t("lock_resolution", "Or lock to specific resolution"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = c.primary,
                )
                Spacer(Modifier.height(8.dp))

                specificOptions.forEach { (res, label) ->
                    val isChosen = currentMode == PlaybackQualityMode.SPECIFIC && currentSpecificHeight == res
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isChosen) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .pressable { onSelectSpecific(res) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.5.sp,
                            color = if (isChosen) c.primary else c.onSurface,
                        )
                        ScaleInCheckBadge(isChosen, 20.dp, 13.dp)
                    }
                    Spacer(Modifier.height(3.dp))
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(I18n.t("cancel", "Cancel"), color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFolderSelectionDialog(
    currentPath: String,
    onResetDefault: () -> Unit,
    onSelectMusic: () -> Unit,
    onCustomPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = I18n.t("download_location", "Download Location"),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = c.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = I18n.t("active_path", "Active: {path}").replace("{path}", currentPath),
                    fontSize = 11.5.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))

                val defaultDir = Downloads.defaultDownloadDir().absolutePath
                val isDefault = currentPath == defaultDir

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDefault) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onResetDefault() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = if (isDefault) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download/LePlay Downloads",
                            fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isDefault) c.primary else c.onSurface,
                        )
                        Text(
                            text = I18n.t("default_dir_sub", "Default public Downloads directory"),
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                    ScaleInCheckIcon(visible = isDefault, size = 18.dp, tint = c.primary)
                }

                Spacer(Modifier.height(6.dp))

                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "LePlay").absolutePath
                val isMusic = currentPath == musicDir
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isMusic) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onSelectMusic() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (isMusic) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music/LePlay",
                            fontWeight = if (isMusic) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isMusic) c.primary else c.onSurface,
                        )
                        Text(
                            text = I18n.t("music_dir_sub", "Dedicated Music directory"),
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                    ScaleInCheckIcon(visible = isMusic, size = 18.dp, tint = c.primary)
                }

                Spacer(Modifier.height(6.dp))

                val isCustom = !isDefault && !isMusic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCustom) c.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .pressable { onCustomPick() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = if (isCustom) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = I18n.t("choose_custom_folder", "Choose Custom Folder..."),
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isCustom) c.primary else c.onSurface,
                        )
                        Text(
                            text = if (isCustom) currentPath else I18n.t("custom_dir_sub", "Select any directory on device"),
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ScaleInCheckIcon(visible = isCustom, size = 18.dp, tint = c.primary)
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(I18n.t("close", "Close"), color = c.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralModificationContent(context: Context) {
    val c = MaterialTheme.colorScheme
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showShortsQualityDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val path = if (docId.startsWith("primary:")) {
                File(Environment.getExternalStorageDirectory(), docId.removePrefix("primary:"))
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LePlay Downloads")
            }
            Downloads.setCustomDownloadDir(path)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SettingsCard {
        SettingsSwitchRow(
            title = I18n.t("use_system_font", "Use System Font"),
            subtitle = I18n.t("use_system_font_sub", "Use device default font throughout the app"),
            checked = ThemeSettings.useSystemFont,
            onCheckedChange = { ThemeSettings.updateUseSystemFont(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsSwitchRow(
            title = I18n.t("video_gestures", "Video gestures"),
            subtitle = I18n.t("video_gestures_sub", "Swipe vertically on the left for brightness, right for volume"),
            checked = VideoGestureSettings.enabled,
            onCheckedChange = { VideoGestureSettings.set(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsSwitchRow(
            title = I18n.t("quick_player_controls", "Player speed & quality buttons"),
            subtitle = I18n.t("quick_player_controls_sub", "Show quick speed and quality icons next to fullscreen button"),
            checked = PlayerControlSettings.showQuickControls,
            onCheckedChange = { PlayerControlSettings.set(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsSwitchRow(
            title = I18n.t("enable_pip", "Picture-in-Picture (PiP)"),
            subtitle = I18n.t("enable_pip_sub", "Allow video to keep playing in a floating window when leaving the app"),
            checked = PipSettings.enabled,
            onCheckedChange = { PipSettings.set(it) },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsSwitchRow(
            title = I18n.t("enable_liquid_mini_player", "Enable Liquid Mini Player"),
            subtitle = I18n.t("enable_liquid_mini_player_sub", "Use classic bottom bar miniplayer instead of floating draggable player"),
            checked = MiniPlayerSettings.useLiquidMiniPlayer,
            onCheckedChange = { MiniPlayerSettings.set(it) },
        )

      }

      SettingsCard {
        Text(
            text = I18n.t("sponsorblock", "SponsorBlock"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(1.dp))
        SettingsSwitchRow(
            title = I18n.t("auto_skip_segments", "Automatically skip segments"),
            subtitle = I18n.t("auto_skip_sub", "Choose what happens for each type"),
            checked = SponsorBlock.enabled,
            onCheckedChange = SponsorBlock::updateEnabled,
        )

        AnimatedVisibility(
            visible = SponsorBlock.enabled,
            enter = if (GlassSettings.liteMode) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(tween(260)),
            exit = if (GlassSettings.liteMode) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column {
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SponsorBlock.Category.entries.forEach { category ->
                    val current = SponsorBlock.actionFor(category)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(category.color)
                            )
                            Text(category.displayLabel, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 2)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            SponsorBlock.Action.entries.forEach { action ->
                                val on = action == current
                                val chipBg by animateColorAsState(
                                    targetValue = if (on) c.secondaryContainer else Color.Transparent,
                                    animationSpec = tween(260, easing = Motion.Emphasized),
                                    label = "sbSettingsChipBg",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressable { SponsorBlock.setAction(category, action) }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        action.displayLabel,
                                        color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

      }

      SettingsCard {
        Text(
            text = I18n.t("quality", "Quality"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(2.dp))

        SettingsClickRow(
            title = I18n.t("video_quality", "Video Quality"),
            subtitle = QualityPreference.videoQualityDisplay,
            onClick = { showVideoQualityDialog = true },
        )

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))

        SettingsClickRow(
            title = I18n.t("shorts_quality", "Shorts Quality"),
            subtitle = QualityPreference.shortsQualityDisplay,
            onClick = { showShortsQualityDialog = true },
        )

      }

      SettingsCard {
        Text(
            text = I18n.t("downloads", "Downloads"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(2.dp))

        val currentPath = Downloads.currentDownloadDir.absolutePath
        SettingsClickRow(
            title = I18n.t("select_download_folder", "Select Download folder"),
            subtitle = currentPath,
            onClick = { showFolderDialog = true },
        )
      }
    }

    if (showVideoQualityDialog) {
        QualitySelectionDialog(
            title = I18n.t("video_quality", "Video Quality"),
            currentMode = QualityPreference.videoQualityMode,
            currentSpecificHeight = QualityPreference.specificVideoHeight,
            onSelectMode = {
                QualityPreference.updateVideoQualityMode(it)
                showVideoQualityDialog = false
            },
            onSelectSpecific = {
                QualityPreference.setSpecificVideoQuality(it)
                showVideoQualityDialog = false
            },
            onDismiss = { showVideoQualityDialog = false }
        )
    }

    if (showShortsQualityDialog) {
        QualitySelectionDialog(
            title = I18n.t("shorts_quality", "Shorts Quality"),
            currentMode = QualityPreference.shortsQualityMode,
            currentSpecificHeight = QualityPreference.specificShortsHeight,
            onSelectMode = {
                QualityPreference.updateShortsQualityMode(it)
                showShortsQualityDialog = false
            },
            onSelectSpecific = {
                QualityPreference.setSpecificShortsQuality(it)
                showShortsQualityDialog = false
            },
            onDismiss = { showShortsQualityDialog = false }
        )
    }

    if (showFolderDialog) {
        DownloadFolderSelectionDialog(
            currentPath = Downloads.currentDownloadDir.absolutePath,
            onResetDefault = {
                Downloads.resetToDefaultDownloadDir()
                showFolderDialog = false
            },
            onSelectMusic = {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "LePlay")
                Downloads.setCustomDownloadDir(musicDir)
                showFolderDialog = false
            },
            onCustomPick = {
                runCatching {
                    val initialUri = Uri.fromFile(Downloads.currentDownloadDir)
                    folderPicker.launch(initialUri)
                }.onFailure {
                    folderPicker.launch(null)
                }
                showFolderDialog = false
            },
            onDismiss = { showFolderDialog = false }
        )
    }
}
private data class StorageData(
    val deviceTotal: Long,
    val deviceUsed: Long,
    val deviceFree: Long,
    val leplayApp: Long,
    val leplayCache: Long,
    val leplayDownloads: Long,
    val leplayData: Long,
    val leplayTotal: Long,
)

private fun getFolderSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getFolderSize(file) else file.length()
    }
    return size
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return if (digitGroups >= 3) {
        "%.1f %s".format(value, units[digitGroups])
    } else {
        "%.0f %s".format(value, units[digitGroups])
    }
}

private var cachedStorageData: StorageData? = null

private fun fastStorageData(): StorageData {
    return runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)
        StorageData(
            deviceTotal = total,
            deviceUsed = used,
            deviceFree = free,
            leplayApp = cachedStorageData?.leplayApp ?: 18_000_000L,
            leplayCache = cachedStorageData?.leplayCache ?: 0L,
            leplayDownloads = cachedStorageData?.leplayDownloads ?: 0L,
            leplayData = cachedStorageData?.leplayData ?: 0L,
            leplayTotal = cachedStorageData?.leplayTotal ?: 18_000_000L,
        )
    }.getOrDefault(
        cachedStorageData ?: StorageData(128_000_000_000L, 45_000_000_000L, 83_000_000_000L, 18_000_000L, 0L, 0L, 0L, 18_000_000L)
    )
}

private fun calculateStorageData(context: Context): StorageData {
    return runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)

        val appSize = runCatching { File(context.packageCodePath).length() }.getOrDefault(18_000_000L)
        val cacheSize = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
        val downloadsSize = getFolderSize(Downloads.currentDownloadDir)
        val dataSize = getFolderSize(context.filesDir)

        StorageData(
            deviceTotal = total,
            deviceUsed = used,
            deviceFree = free,
            leplayApp = appSize,
            leplayCache = cacheSize,
            leplayDownloads = downloadsSize,
            leplayData = dataSize,
            leplayTotal = appSize + cacheSize + downloadsSize + dataSize,
        )
    }.getOrDefault(
        StorageData(128_000_000_000L, 45_000_000_000L, 83_000_000_000L, 18_000_000L, 24_000_000L, 0L, 10_000_000L, 52_000_000L)
    )
}

@Composable
private fun StorageUsageContent(context: Context, onCacheCleared: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var storageData by remember { mutableStateOf(cachedStorageData ?: fastStorageData()) }
    var isCalculating by remember { mutableStateOf(cachedStorageData == null) }
    var isClearing by remember { mutableStateOf(false) }
    var clearedNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fresh = calculateStorageData(context)
            cachedStorageData = fresh
            withContext(Dispatchers.Main) {
                storageData = fresh
                isCalculating = false
            }
        }
    }

    val usedGb = storageData.deviceUsed / 1_000_000_000.0
    val totalGb = storageData.deviceTotal / 1_000_000_000.0
    val freeGb = storageData.deviceFree / 1_000_000_000.0
    val progress = (storageData.deviceUsed.toFloat() / storageData.deviceTotal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = I18n.t("device_storage", "Device storage"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        var animatedTarget by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(progress) {
            animatedTarget = progress
        }
        val animatedProgress by animateFloatAsState(
            targetValue = animatedTarget,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
            label = "storageProgress",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                c.primary,
                                c.tertiary,
                                c.secondary,
                            )
                        )
                    )
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${"%.1f".format(usedGb)} GB ${I18n.t("used_of", "used of")} ${"%.1f".format(totalGb)} GB",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurface,
            )
            Text(
                text = "${"%.1f".format(freeGb)} GB ${I18n.t("free", "free")}",
                fontSize = 11.5.sp,
                color = c.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = I18n.t("used_by_leplay", "Used by LePlay"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isCalculating) {
                CircularProgressIndicator(
                    color = c.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${I18n.t("total_space", "Total space")}: ${if (isCalculating && storageData.leplayTotal <= 18_000_000L) "..." else formatStorageBytes(storageData.leplayTotal)}",
            fontSize = 12.sp,
            color = c.primary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(10.dp))

        StorageItemRow(label = I18n.t("app_core_engine", "App & Core engine"), sizeStr = formatStorageBytes(storageData.leplayApp))
        StorageItemRow(label = I18n.t("stream_video_cache", "Stream & video cache"), sizeStr = if (isCalculating && storageData.leplayCache == 0L) "..." else formatStorageBytes(storageData.leplayCache))
        StorageItemRow(label = I18n.t("downloaded_media", "Downloaded videos & music"), sizeStr = if (isCalculating && storageData.leplayDownloads == 0L) "..." else formatStorageBytes(storageData.leplayDownloads))
        StorageItemRow(label = I18n.t("saved_offline_data", "Saved offline data & config"), sizeStr = if (isCalculating && storageData.leplayData == 0L) "..." else formatStorageBytes(storageData.leplayData))

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2E1517))
                .pressable(enabled = !isClearing) {
                    isClearing = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            StreamCache.invalidateAll()
                            Downloads.deleteAllDownloadsAndWork()
                            runCatching {
                                context.cacheDir.deleteRecursively()
                                context.externalCacheDir?.deleteRecursively()
                            }
                            val fresh = calculateStorageData(context)
                            cachedStorageData = fresh
                            withContext(Dispatchers.Main) {
                                storageData = fresh
                                isClearing = false
                                clearedNotice = I18n.t("cache_cleared_notice", "Cache & downloads cleared!")
                                onCacheCleared()
                            }
                        }
                    }
                }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isClearing) {
                    CircularProgressIndicator(
                        color = Color(0xFFEF5350),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = clearedNotice ?: I18n.t("clear_cache_downloads", "Clear LePlay Cache & Downloads"),
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
private fun StorageItemRow(label: String, sizeStr: String) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(sizeStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
    }
}

@Composable
private fun LanguageContent(context: Context, onLanguageSelected: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    val currentCode = LanguageSettings.currentCode
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            LanguageSettings.ALL_LANGUAGES
        } else {
            LanguageSettings.ALL_LANGUAGES.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.nativeName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val langGlass = LocalSettingsBackdrop.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (langGlass != null) {
                        Modifier.ownLayerGlass(langGlass, RoundedCornerShape(14.dp), c.surfaceContainerLow)
                    } else {
                        Modifier.clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLow)
                    },
                )
                .border(1.dp, c.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = I18n.t("search", "Search"),
                tint = c.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(
                    color = c.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(c.primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = I18n.t("search_languages", "Search languages..."),
                            color = c.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 13.5.sp,
                        )
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = I18n.t("clear", "Clear"),
                    tint = c.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Plain Column inside the dialog's own scroll (0.0.8): the old
        // LazyColumn was hard-capped at 380.dp, which clipped the list
        // halfway down the screen. ~26 rows render fine eagerly.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            filteredLanguages.forEach { item ->
                val isSelected = currentCode.equals(item.code, ignoreCase = true)
                // Unselected language rows are glass over the page's own
                // layer (rebuild 124); the selected row keeps its red tint,
                // refracted through the same engine so it still reads as
                // the current choice.
                val rowGlass = LocalSettingsBackdrop.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                isSelected && rowGlass != null -> Modifier.ownLayerGlass(
                                    rowGlass,
                                    RoundedCornerShape(14.dp),
                                    c.surfaceContainerLow,
                                    tint = c.primary.copy(alpha = 0.30f),
                                )
                                isSelected -> Modifier.clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = 0.14f))
                                rowGlass != null -> Modifier.ownLayerGlass(rowGlass, RoundedCornerShape(14.dp), c.surfaceContainerLow)
                                else -> Modifier.clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLow.copy(alpha = 0.5f))
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) c.primary.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            LanguageSettings.choose(context, item.code)
                            onLanguageSelected()
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.nativeName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isSelected) c.primary else c.onSurface,
                        )
                        Text(
                            text = if (item.code == "auto") I18n.t("follow_system", "Follow device language") else item.name,
                            fontSize = 11.5.sp,
                            color = c.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(c.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = I18n.t("selected", "Selected"),
                                tint = c.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsProfileCard(
    profile: Profile?,
    onSignIn: () -> Unit,
    onRequestSignOut: () -> Unit,
    onAccountSwitched: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val accounts = Account.accounts
    val activeId = Account.activeAccountId
    var expanded by remember { mutableStateOf(false) }
    val chevronReduced = rememberReducedMotion() || GlassSettings.liteMode
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (chevronReduced) tween(0) else tween(220, easing = Motion.Emphasized),
        label = "profileChevron",
    )

    val settingsGlass = LocalSettingsBackdrop.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (settingsGlass != null) {
                    Modifier.ownLayerGlass(settingsGlass, RoundedCornerShape(18.dp), c.surfaceContainerHigh)
                } else {
                    Modifier.pageGlass(RoundedCornerShape(18.dp), c.surfaceContainerHigh)
                },
            )
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable {
                    if (profile == null) onSignIn() else expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = profile,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (GlassSettings.liteMode) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 8 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 8 })
                    }
                },
                label = "accountHeader",
            ) { current ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (current?.avatar != null) {
                        ChannelAvatar(current.avatar, current.name, size = 44)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    val displayName = when {
                        current?.name != null && Account.isCleanAccountName(current.name) -> current.name
                        !current?.handle.isNullOrBlank() -> current!!.handle!!
                        else -> I18n.t("youtube_account", "YouTube account")
                    }
                    val subtitleText = when {
                        current != null -> {
                            val h = current.handle
                            if (!h.isNullOrBlank() && h != displayName) h
                            else "${accounts.size} ${if (accounts.size == 1) I18n.t("account_singular", "account") else I18n.t("accounts_count", "accounts")}"
                        }
                        else -> I18n.t("sign_in_sync_sub", "Sign in to sync your account")
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }

            if (profile == null) {
                Text(
                    I18n.t("sign_in", "Sign in"),
                    modifier = Modifier.pressable(onClick = onSignIn).padding(8.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = c.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                    contentDescription = if (expanded) I18n.t("hide_accounts", "Hide accounts") else I18n.t("switch_account", "Switch account"),
                    tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = chevronRotation
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && profile != null,
            enter = if (GlassSettings.liteMode) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(tween(260)),
            exit = if (GlassSettings.liteMode) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 6.dp))
                accounts.forEach { account ->
                    SettingsAccountRow(
                        account = account,
                        active = account.id == activeId,
                        onSwitch = {
                            if (Account.switchTo(account.id)) {
                                expanded = false
                                onAccountSwitched()
                            }
                        },
                        onRemove = if (account.id == activeId) null else {
                            { Account.removeAccount(account.id) }
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressable(onClick = onSignIn)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(I18n.t("add_account", "Add account"), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = c.primary)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable(onClick = onRequestSignOut)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            null,
                            tint = c.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(I18n.t("sign_out", "Sign out"), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = c.error)
                }
            }
        }
    }
}

@Composable
private fun SettingsAccountRow(
    account: AccountSummary,
    active: Boolean,
    onSwitch: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (active) c.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(260),
        label = "accountBackground",
    )
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "accountActive",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressable(onClick = onSwitch)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.avatar != null) {
            ChannelAvatar(account.avatar, account.name, size = 32)
        } else {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AccountCircle, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        val displayName = when {
            Account.isCleanAccountName(account.name) -> account.name
            !account.email.isNullOrBlank() -> account.email
            !account.handle.isNullOrBlank() && Account.isCleanAccountName(account.handle) -> account.handle
            else -> I18n.t("google_account", "Google Account")
        }
        val handleText = when {
            account.handle != null && account.handle != displayName && Account.isCleanAccountName(account.handle) -> account.handle
            account.email != null && account.email != displayName -> account.email
            else -> null
        }

        Column(Modifier.weight(1f)) {
            Text(displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            handleText?.let {
                Text(it, fontSize = 11.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) {
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = activeProgress
                        scaleY = activeProgress
                        alpha = activeProgress
                    }
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, I18n.t("active_account", "Active account"), tint = c.onPrimary, modifier = Modifier.size(13.dp))
            }
        } else if (onRemove != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, I18n.t("remove_account", "Remove account"), tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
        )
    }
}
