package com.leplay.app

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(video: VideoItem, player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnail, Modifier.width(112.dp).aspectRatio(16f / 9f), radius = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, I18n.t("play_next", "Play next")) { player.playNext(video); onDismiss() }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, I18n.t("add_to_queue", "Add to queue")) { player.addToQueue(video); onDismiss() }
        OptionRow(Icons.Rounded.Download, I18n.t("download", "Download")) { player.downloadFor = video; onDismiss() }
        if (Account.signedIn) {
            OptionRow(Icons.Rounded.WatchLater, I18n.t("save_to_watch_later", "Save to Watch later")) { player.saveToWatchLater(video); onDismiss() }
        }
        OptionRow(Icons.Rounded.Share, I18n.t("share", "Share")) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
            context.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurface)
        Spacer(Modifier.width(18.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}




@Composable
fun QueueCard(player: PlayerState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val next = player.queue.firstOrNull() ?: return
    AnimatedVisibility(
        visible = true,
        enter = if (GlassSettings.liteMode) fadeIn(snap()) else fadeIn(tween(260, easing = Motion.Emphasized)) + slideInVertically(tween(320, easing = Motion.Emphasized)) { it / 8 },
    ) {
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(c.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(next.thumbnail, Modifier.width(88.dp).aspectRatio(16f / 9f), radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${I18n.t("queue", "Queue")} · ${player.queue.size}",
                color = c.onSecondaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(next.title, color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onSecondaryContainer)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(I18n.t("queue", "Queue"), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            if (player.queue.isNotEmpty()) {
                Text(
                    I18n.t("clear", "Clear"), color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.pressable { player.clearQueue() }.padding(8.dp),
                )
            }
        }
        if (player.queue.isEmpty()) {
            Text(
                I18n.t("queue_empty", "Nothing queued. Press and hold a video to add it."),
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                itemsIndexed(player.queue, key = { i, v -> "$i-${v.id}" }) { index, video ->
                    Box((if (GlassSettings.liteMode) Modifier else Modifier.animateItem()).padding(horizontal = 20.dp)) {
                        VideoRow(video, onClick = { player.playFromQueue(index); onDismiss() }) {
                            Box(Modifier.size(40.dp).pressable { player.removeFromQueue(index) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, I18n.t("remove_from_queue", "Remove from queue"), tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}



private val MusicQueueRowHeight = 64.dp

/** Up-next panel for music mode, bound to the living music queue: rows
 *  play/remove, long-press drag reorders, and a Related section fed by
 *  the current song's watch-next related endpoint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQueuePanel(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var related by remember(player.current?.id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    LaunchedEffect(player.current?.id) {
        val currentId = player.current?.id ?: return@LaunchedEffect
        runCatching {
            val page = InnerTube.musicNext(videoId = currentId)
            val endpoint = page.relatedEndpoint ?: return@runCatching
            val queuedIds = player.musicQueue.map { it.id }.toHashSet()
            related = InnerTube.musicRelated(endpoint)
                .filter { it.id != currentId && it.id !in queuedIds }
                .take(20)
        }
    }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                I18n.t("up_next_title", "Up next") + " · " + player.musicQueue.size,
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
                modifier = Modifier.weight(1f),
            )
            if (player.musicQueue.isNotEmpty()) {
                Text(
                    I18n.t("clear", "Clear"), color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.pressable { player.clearMusicQueue() }.padding(8.dp),
                )
            }
        }
        if (player.musicQueueSource is MusicQueueSource.RadioSong ||
            player.musicQueueSource is MusicQueueSource.RadioPlaylist
        ) {
            Text(
                I18n.t("playing_from_radio", "Playing from radio"),
                color = c.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
            )
        }
        if (player.musicQueue.isEmpty() && related.isEmpty()) {
            Text(
                I18n.t("queue_music_empty", "Nothing up next yet"),
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            val density = LocalDensity.current
            val rowHeightPx = with(density) { MusicQueueRowHeight.toPx() }
            LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                itemsIndexed(player.musicQueue, key = { _, v -> "mq-${v.id}" }) { index, song ->
                    val isDragging = dragId == song.id
                    Box(
                        (if (GlassSettings.liteMode) Modifier else Modifier.animateItem())
                            .padding(horizontal = 20.dp)
                            .then(
                                if (isDragging) {
                                    Modifier
                                        .zIndex(1f)
                                        .graphicsLayer { translationY = dragOffsetPx }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        MusicQueueRow(
                            song = song,
                            onClick = { player.playFromMusicQueue(index); onDismiss() },
                            onRemove = { player.removeFromMusicQueue(index) },
                            dragModifier = Modifier.pointerInput(song.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragId = song.id
                                        dragOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        dragId = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        dragId = null
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx += dragAmount.y
                                        val from = player.musicQueue.indexOfFirst { it.id == song.id }
                                        if (from >= 0) {
                                            val target = (from + (dragOffsetPx / rowHeightPx).roundToInt())
                                                .coerceIn(0, player.musicQueue.lastIndex)
                                            if (target != from) {
                                                player.moveMusicQueue(from, target)
                                                dragOffsetPx -= (target - from) * rowHeightPx
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
                if (related.isNotEmpty()) {
                    item(key = "mq-related-title") {
                        Text(
                            I18n.t("related_title", "Related"),
                            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    itemsIndexed(related, key = { _, v -> "mq-rel-${v.id}" }) { _, song ->
                        Box((if (GlassSettings.liteMode) Modifier else Modifier.animateItem()).padding(horizontal = 20.dp)) {
                            MusicQueueRow(
                                song = song,
                                onClick = {
                                    player.startRadio(song)
                                    onDismiss()
                                },
                                onRemove = null,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MusicQueueRow(
    song: VideoItem,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    dragModifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MusicQueueRowHeight)
            .then(dragModifier)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(song.thumbnail, Modifier.size(48.dp), radius = 10, videoId = song.id)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.channel,
                color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationSec > 0) {
            Text(
                formatDuration(song.durationSec * 1000),
                color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (onRemove != null) {
            Box(Modifier.size(40.dp).pressable { onRemove() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, I18n.t("remove_from_queue", "Remove from queue"), tint = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SponsorBlockSettings() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(I18n.t("sponsorblock", "SponsorBlock"), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(I18n.t("sponsorblock_sheet_sub", "Skip sponsor reads and other segments"), color = c.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(
                checked = SponsorBlock.enabled,
                onCheckedChange = SponsorBlock::updateEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        AnimatedVisibility(
            visible = SponsorBlock.enabled,
            enter = if (GlassSettings.liteMode) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(tween(260)),
            exit = if (GlassSettings.liteMode) fadeOut(snap()) else fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                SponsorBlock.Category.entries.forEach { category -> CategoryRow(category) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            I18n.t("sponsorblock_data_note", "Segments from SponsorBlock (sponsor.ajay.app), a community project. Data licensed CC BY-NC-SA 4.0."),
            color = c.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CategoryRow(category: SponsorBlock.Category) {
    val c = MaterialTheme.colorScheme
    val current = SponsorBlock.actionFor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
            Spacer(Modifier.width(8.dp))
            Text(category.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SponsorBlock.Action.entries.forEach { action ->
                val on = action == current
                val chipBg by animateColorAsState(
                    targetValue = if (on) c.secondaryContainer else c.surfaceContainerHighest,
                    animationSpec = tween(260, easing = Motion.Emphasized),
                    label = "sbChipBg",
                )
                Box(
                    Modifier
                        .pressable { SponsorBlock.setAction(category, action) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(chipBg)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(action.displayLabel, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}




@Composable
fun AppearanceSettings() {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = isDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        Text(I18n.t("appearance", "Appearance"), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to I18n.t("theme_system", "System"), "light" to I18n.t("theme_light", "Light"), "dark" to I18n.t("theme_dark", "Dark"), "amoled" to I18n.t("theme_amoled", "AMOLED")).forEach { (key, label) ->
                val on = ThemeSettings.mode == key
                Box(
                    Modifier
                        .pressable { ThemeSettings.chooseMode(key) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}

/** Subtitles / Closed Captions selection bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlesSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val videoId = player.current?.id
    var info by remember(videoId) { mutableStateOf(videoId?.let { StreamCache.get(it) }) }

    LaunchedEffect(videoId) {
        if (info == null && videoId != null) {
            info = withContext(Dispatchers.IO) { runCatching { StreamCache.getOrLoad(videoId) }.getOrNull() }
        }
    }

    data class SubtitleOption(
        val code: String,
        val label: String,
        val url: String?,
    )

    val subtitleTracks = remember(info, videoId) {
        val streamTracks = info?.subtitles.orEmpty().filter { it.isUrl && it.content.isNotBlank() }
        if (streamTracks.isNotEmpty()) {
            streamTracks.map { sub ->
                val code = sub.languageTag?.ifBlank { "en" } ?: "en"
                val name = sub.displayLanguageName?.ifBlank { code } ?: code
                val label = if (sub.isAutoGenerated) I18n.t("auto_generated_suffix", "{name} (auto-generated)").replace("{name}", name) else name
                SubtitleOption(code = code, label = label, url = sub.content)
            }
        } else {
            val fallbackTracks = videoId?.let { CaptionTracks.get(it) ?: runCatching { CaptionTracks.load(it) }.getOrNull() }.orEmpty()
            fallbackTracks.map { track ->
                SubtitleOption(code = track.languageTag, label = track.label, url = track.url)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(I18n.t("captions_subtitles", "Captions / Subtitles"), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))

            // Option 1: Off (Turn off captions)
            val isOff = player.selectedSubtitleLanguage == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        player.setSubtitleLanguage(null)
                        onDismiss()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOff) c.surfaceContainerHigh else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SubtitlesOff, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(I18n.t("off", "Off"), fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                if (isOff) {
                    Icon(Icons.Rounded.Check, contentDescription = I18n.t("selected", "Selected"), tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(4.dp))

            if (subtitleTracks.isEmpty()) {
                Text(
                    I18n.t("no_captions", "No captions available for this video."),
                    color = c.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subtitleTracks.forEach { track ->
                        val selected = player.selectedSubtitleLanguage == track.code

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    player.setSubtitleLanguage(track.code, track.url)
                                    onDismiss()
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) c.surfaceContainerHigh else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = if (selected) c.primary else c.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(track.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = I18n.t("selected", "Selected"), tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

