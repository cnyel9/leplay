package com.leplay.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page


val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class ChannelTab(val raw: String) {
    Videos("Videos"),
    Shorts("Shorts"),
    Live("Live"),
    Community("Community");

    val label: String get() = when (this) {
        Videos -> I18n.t("videos", "Videos")
        Shorts -> I18n.t("tab_shorts", "Shorts")
        Live -> I18n.t("live_tab", "Live")
        Community -> I18n.t("community_tab", "Community")
    }
}

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set
    var activeTab by mutableStateOf(ChannelTab.Videos)
        private set
    var videoSort by mutableStateOf(ChannelVideoSort.Latest)
        private set
    var sortTokens by mutableStateOf<Map<ChannelVideoSort, String>>(emptyMap())
        private set
    var liveVideos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var liveLoading by mutableStateOf(false)
        private set
    var liveSort by mutableStateOf(ChannelVideoSort.Latest)
        private set
    var shorts by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var shortsLoading by mutableStateOf(false)
        private set
    var communityPosts by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var communityLoading by mutableStateOf(false)
        private set

    private var next: String? = null
    private var shortsNext: String? = null
    private var liveNext: String? = null
    private var liveDraining = false
    val hasMore get() = next != null
    val hasMoreLive get() = liveNext != null

    init { load() }

    fun selectTab(tab: ChannelTab) {
        activeTab = tab
        when (tab) {
            ChannelTab.Shorts -> if (shorts.isEmpty() && !shortsLoading) loadShorts()
            ChannelTab.Live -> if (liveVideos.isEmpty() && !liveLoading) loadLive()
            ChannelTab.Community -> if (communityPosts.isEmpty() && !communityLoading) loadCommunity()
            ChannelTab.Videos -> {}
        }
    }

    fun load() {
        loading = true
        error = null
        liveNext = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { base ->
                    var d = base
                    details = d
                    if (Account.signedIn) launch { subscribed = runCatching { InnerTube.channelSubscribed(d.id) }.getOrNull() }
                    runCatching { YouTube.channelVideos(d, null) }
                        .onSuccess { p ->
                            videos = p.videos
                            next = p.next
                            videoSort = ChannelVideoSort.Latest
                            liveSort = ChannelVideoSort.Latest
                            if (p.sortTokens.isNotEmpty()) sortTokens = p.sortTokens
                            p.channelHeader?.let { d = d.withHeader(it) }
                            details = d
                        }
                        .onFailure {
                            details = d
                            error = YouTube.friendlyError(it)
                        }
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadLive() {
        val d = details ?: return
        if (liveLoading) return
        liveLoading = true
        scope.launch {
            runCatching { YouTube.channelLive(d, null) }
                .onSuccess { p ->
                    val fromServer = p.videos
                    liveVideos = fromServer.ifEmpty { videos.filter { it.isLive } }
                    liveNext = if (fromServer.isEmpty()) null else p.next
                }
                .onFailure {
                    liveVideos = videos.filter { it.isLive }
                    liveNext = null
                }
            liveLoading = false
        }
    }

    fun loadShorts() {
        val d = details ?: return
        if (shortsLoading) return
        shortsLoading = true
        scope.launch {
            runCatching { YouTube.channelShorts(d, null) }
                .onSuccess { p ->
                    shorts = p.videos
                    shortsNext = p.next
                }
            shortsLoading = false
        }
    }

    fun loadMoreShorts() {
        val d = details ?: return
        val page = shortsNext ?: return
        if (shortsLoading) return
        shortsLoading = true
        scope.launch {
            runCatching { YouTube.channelShorts(d, page) }
                .onSuccess { p ->
                    val seen = shorts.mapTo(HashSet()) { it.id }
                    shorts = shorts + p.videos.filter { seen.add(it.id) }
                    shortsNext = p.next
                }
            shortsLoading = false
        }
    }

    fun loadMoreLive() {
        val d = details ?: return
        val page = liveNext ?: return
        if (liveLoading) return
        liveLoading = true
        scope.launch {
            runCatching { YouTube.channelLive(d, page) }
                .onSuccess { p ->
                    val seen = liveVideos.mapTo(HashSet()) { it.id }
                    liveVideos = liveVideos + p.videos.filter { seen.add(it.id) }
                    liveNext = p.next
                }
            liveLoading = false
        }
    }

    fun selectLiveSort(sort: ChannelVideoSort) {
        if (sort == liveSort) return
        liveSort = sort
        if (sort != ChannelVideoSort.Latest) drainLive()
    }

    // YouTube serves no sort chips for the Live tab, so Popular / Oldest are
    // computed client-side over the full stream list: drain every remaining
    // page first (bounded), then liveDisplay reorders. liveLoading stays true
    // for the whole drain so loadMoreLive cannot double-fetch meanwhile.
    private fun drainLive() {
        val d = details ?: return
        if (liveDraining) return
        liveDraining = true
        scope.launch {
            liveLoading = true
            try {
                var iterations = 0
                while (liveSort != ChannelVideoSort.Latest && liveNext != null && iterations < 40) {
                    iterations++
                    val p = runCatching { YouTube.channelLive(d, liveNext!!) }.getOrNull() ?: break
                    val seen = liveVideos.mapTo(HashSet()) { it.id }
                    liveVideos = liveVideos + p.videos.filter { seen.add(it.id) }
                    liveNext = p.next
                }
            } finally {
                liveLoading = false
                liveDraining = false
            }
        }
    }

    // Display order for the Live tab: currently-live streams stay pinned on
    // top in every order; the rest follow the selected sort.
    val liveDisplay: List<VideoItem>
        get() {
            val pinned = liveVideos.filter { it.isLive }
            val rest = liveVideos.filter { !it.isLive }
            return when (liveSort) {
                ChannelVideoSort.Latest -> liveVideos
                ChannelVideoSort.Popular -> pinned + rest.sortedWith(
                    compareByDescending<VideoItem> { it.views >= 0 }.thenByDescending { it.views },
                )
                ChannelVideoSort.Oldest -> pinned + rest.sortedByDescending { ageSeconds(it.uploaded) ?: -1L }
            }
        }

    fun loadCommunity() {
        val d = details ?: return
        communityLoading = true
        scope.launch {
            runCatching { YouTube.channelCommunity(d, null) }
                .onSuccess { p ->
                    communityPosts = p.posts
                }
                .onFailure {
                    communityPosts = emptyList()
                }
            communityLoading = false
        }
    }

    fun loadMore() {
        val d = details ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, page) }
                .onSuccess { p ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    videos = videos + p.videos.filter { seen.add(it.id) }
                    next = p.next
                }
            loading = false
        }
    }

    fun selectSort(sort: ChannelVideoSort) {
        if (sort == videoSort) return
        val d = details ?: return
        val token = sortTokens[sort]
        // Popular / Oldest only exist as chip continuations; Latest can always
        // fall back to the default first-page load.
        if (token == null && sort != ChannelVideoSort.Latest) return
        videoSort = sort
        videos = emptyList()
        next = null
        error = null
        loading = true
        scope.launch {
            val result = if (token != null) {
                runCatching { YouTube.channelVideos(d, null, sortToken = token) }
            } else {
                runCatching { YouTube.channelVideos(d, null) }
            }
            result
                .onSuccess { p ->
                    videos = p.videos
                    next = p.next
                    if (p.sortTokens.isNotEmpty()) sortTokens = p.sortTokens
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError(I18n.t("subscription_update_failed", "Couldn't update your subscription.")) }
        }
    }
}

@Composable
fun ChannelTabStrip(selected: ChannelTab, onSelect: (ChannelTab) -> Unit, modifier: Modifier = Modifier) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChannelTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val background by animateColorAsState(
                targetValue = when {
                    isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                    isAmoled -> Color(0xFF161616)
                    isDark -> Color(0xFF272727)
                    else -> Color(0xFFE5E5E5)
                },
                animationSpec = tween(220, easing = Motion.Emphasized),
                label = "channelTabBackground",
            )
            val foreground by animateColorAsState(
                targetValue = when {
                    isSelected -> if (isDark || isAmoled) Color.Black else Color.White
                    isDark || isAmoled -> Color.White
                    else -> Color(0xFF0F0F0F)
                },
                animationSpec = tween(180, easing = Motion.Emphasized),
                label = "channelTabForeground",
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.94f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "channelTabScale",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .pressable(pressedScale = 0.94f) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun ChannelSortChips(selected: ChannelVideoSort, onSelect: (ChannelVideoSort) -> Unit, modifier: Modifier = Modifier) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChannelVideoSort.entries.forEach { sort ->
            val isSelected = sort == selected
            val background by animateColorAsState(
                targetValue = when {
                    isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                    isAmoled -> Color(0xFF161616)
                    isDark -> Color(0xFF272727)
                    else -> Color(0xFFE5E5E5)
                },
                animationSpec = tween(220, easing = Motion.Emphasized),
                label = "channelSortBackground",
            )
            val foreground by animateColorAsState(
                targetValue = when {
                    isSelected -> if (isDark || isAmoled) Color.Black else Color.White
                    isDark || isAmoled -> Color.White
                    else -> Color(0xFF0F0F0F)
                },
                animationSpec = tween(180, easing = Motion.Emphasized),
                label = "channelSortForeground",
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.94f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "channelSortScale",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .pressable(pressedScale = 0.94f) { onSelect(sort) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (sort) {
                        ChannelVideoSort.Latest -> I18n.t("sort_latest", "Latest")
                        ChannelVideoSort.Popular -> I18n.t("sort_popular", "Popular")
                        ChannelVideoSort.Oldest -> I18n.t("sort_oldest", "Oldest")
                    },
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun ChannelScreen(
    state: ChannelState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.activeTab, state.videos.size, state.shorts.size, state.liveVideos.size) {
        if (!nearEnd) return@LaunchedEffect
        when (state.activeTab) {
            ChannelTab.Videos -> if (state.videos.isNotEmpty()) state.loadMore()
            ChannelTab.Shorts -> if (state.shorts.isNotEmpty()) state.loadMoreShorts()
            ChannelTab.Live -> if (state.liveVideos.isNotEmpty()) state.loadMoreLive()
            else -> {}
        }
    }
    LaunchedEffect(state.videoSort, state.liveSort) { list.scrollToItem(0) }
    Box(Modifier.fillMaxSize().background(c.surface)) {
    CollapsingHeaderLayout(
        header = {
        BackScreenHeader(onBack = onBack) {
            ChannelNameWithBadge(
                state.details?.name.orEmpty(),
                verified = state.details?.verified == true,
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                maxLines = 1,
                letterSpacing = (-0.6).sp,
            )
        }
        },
    ) { top ->
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = top)) { EmptyState(
                Icons.Rounded.CloudOff, I18n.t("couldnt_load_channel", "Couldn't load this channel"), state.error.orEmpty(),
                action = { PillButton(I18n.t("try_again", "Try again"), state::load) },
            ) }
            d == null -> Box(Modifier.fillMaxWidth().padding(top = top + 60.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
            }
            else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                item(key = "header") {
                    AnimatedVisibility(
                        visible = true,
                        enter = if (GlassSettings.liteMode) fadeIn(snap()) else fadeIn(tween(260, easing = Motion.Emphasized)) +
                            slideInVertically(tween(320, easing = Motion.Emphasized)) { it / 8 },
                    ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (d.banner != null) {
                            Thumbnail(d.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = 12)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(d.avatar, d.name, size = 76)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                ChannelNameWithBadge(d.name, verified = d.verified, maxLines = 2)
                                d.handle?.let { handle ->
                                    Spacer(Modifier.height(2.dp))
                                    Text("@$handle", color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                channelStatLine(d.subscribers, d.videoCount).takeIf { it.isNotBlank() }?.let { stats ->
                                    Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        state.subscribed?.let { subscribed ->
                            Spacer(Modifier.height(14.dp))
                            ChannelSubscribeButton(subscribed) { state.toggleSubscribe(onMessage) }
                        }
                        if (d.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(d.description, color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    }
                }
                item(key = "tabs") {
                    ChannelTabStrip(selected = state.activeTab, onSelect = { state.selectTab(it) })
                }
                if (state.activeTab == ChannelTab.Videos && state.sortTokens.size >= 2) {
                    item(key = "videoSort") {
                        ChannelSortChips(selected = state.videoSort, onSelect = { state.selectSort(it) })
                    }
                }
                if (state.activeTab == ChannelTab.Live && state.liveVideos.isNotEmpty()) {
                    item(key = "liveSort") {
                        ChannelSortChips(selected = state.liveSort, onSelect = { state.selectLiveSort(it) })
                    }
                }
                when (state.activeTab) {
                    ChannelTab.Videos -> {
                        items(state.videos, key = { it.id }) { video ->
                            VideoCard(video, onClick = { onOpen(video) }, modifier = if (GlassSettings.liteMode) Modifier else Modifier.animateItem())
                        }
                        if (state.loading || state.hasMore) item {
                            Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    ChannelTab.Shorts -> {
                        if (state.shortsLoading && state.shorts.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.shorts.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    I18n.t("no_shorts_yet", "No Shorts yet"),
                                    I18n.t("channel_no_shorts", "This channel hasn't posted any Shorts."),
                                )
                            }
                        } else {
                            items(state.shorts.chunked(3), key = { row -> row.first().id }) { row ->
                                ShortsRow(row, onOpenShort, if (GlassSettings.liteMode) Modifier else Modifier.animateItem())
                            }
                            if (state.shortsLoading) item {
                                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    ChannelTab.Live -> {
                        if (state.liveLoading && state.liveVideos.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.liveVideos.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    I18n.t("no_live_streams", "No live streams"),
                                    I18n.t("channel_no_live", "This channel has no active live streams or past live broadcasts."),
                                )
                            }
                        } else {
                            items(state.liveDisplay, key = { it.id }) { video ->
                                VideoCard(video, onClick = { onOpen(video) }, modifier = if (GlassSettings.liteMode) Modifier else Modifier.animateItem())
                            }
                            if (state.liveLoading || state.hasMoreLive) item {
                                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    ChannelTab.Community -> {
                        if (state.communityLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.communityPosts.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    I18n.t("no_community_posts", "No community posts"),
                                    I18n.t("channel_no_community", "This channel hasn't posted anything to their community tab yet."),
                                )
                            }
                        } else {
                            items(state.communityPosts, key = { it.id }) { post -> CommunityCard(post, modifier = if (GlassSettings.liteMode) Modifier else Modifier.animateItem()) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (subscribed) c.secondaryContainer else c.onSurface,
        animationSpec = tween(240, easing = Motion.Emphasized),
        label = "subscribeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (subscribed) c.onSecondaryContainer else c.surface,
        animationSpec = tween(240, easing = Motion.Emphasized),
        label = "subscribeContent",
    )
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(46.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(container),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = true, kind = Flourish.Ring, tint = content, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(if (subscribed) I18n.t("subscribed", "Subscribed") else I18n.t("subscribe", "Subscribe"), color = content, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun ShortsRow(row: List<VideoItem>, onOpen: (VideoItem) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { video -> ShortTile(video, Modifier.weight(1f), onOpen) }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ShortTile(video: VideoItem, modifier: Modifier, onOpen: (VideoItem) -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.95f) { onOpen(video) },
    ) {
        Thumbnail(video.thumbnail, Modifier.fillMaxSize(), radius = 0, videoId = video.id)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(9.dp),
        ) {
            Text(
                video.title,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            video.views.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    "${formatCount(it)} views",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val AGE_PATTERN = Regex(
    """(\d+)\s*(detik|menit|jam|hari|minggu|bulan|tahun|seconds?|minutes?|hours?|days?|weeks?|months?|years?)""",
    RegexOption.IGNORE_CASE,
)

// Parses a relative upload label ("7 hari yang lalu", "Streamed 5 days ago")
// into an age in seconds. The match is searched, not anchored, so prefixes
// like "Streamed" / "Disiarkan" are fine. Null when the label carries no
// number+unit (including null input and the live-now label).
private fun ageSeconds(uploaded: String?): Long? {
    val text = uploaded ?: return null
    val match = AGE_PATTERN.find(text) ?: return null
    val n = match.groupValues[1].toLongOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    val mult = when {
        unit.startsWith("detik") || unit.startsWith("second") -> 1L
        unit.startsWith("menit") || unit.startsWith("minute") -> 60L
        unit.startsWith("jam") || unit.startsWith("hour") -> 3_600L
        unit.startsWith("hari") || unit.startsWith("day") -> 86_400L
        unit.startsWith("minggu") || unit.startsWith("week") -> 604_800L
        unit.startsWith("bulan") || unit.startsWith("month") -> 2_592_000L
        unit.startsWith("tahun") || unit.startsWith("year") -> 31_536_000L
        else -> return null
    }
    return n * mult
}
