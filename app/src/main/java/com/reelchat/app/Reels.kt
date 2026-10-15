@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.reelchat.app

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReelsScreen(nav: Nav, playing: Boolean) {
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val owners = remember(following) { (following + Repo.uid).toList() }
    // re-subscribe when mutual status changes so private profiles unlock right away
    val posts by remember(owners, followers) { Repo.feed(owners) }.collectAsState(emptyList<Post>())
    val notifs by Repo.notifs.collectAsState()
    val unread = notifs.count { !it.read }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No reels yet.\nAdd one with the + tab, or follow your friend.", textAlign = TextAlign.Center, color = IgGray)
            }
        } else {
            val state = rememberPagerState { posts.size }
            VerticalPager(state, Modifier.fillMaxSize(), key = { posts[it].id }) { page ->
                ReelPage(posts[page], playing && state.currentPage == page, nav)
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reels", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            BadgedBox(badge = { if (unread > 0) Badge(containerColor = IgRed) { Text(unread.toString()) } }) {
                Icon(Icons.Outlined.FavoriteBorder, "Activity", tint = Color.White,
                    modifier = Modifier.size(28.dp).clickable { nav.openNotifs() })
            }
        }
    }
}

@Composable
fun ReelPage(post: Post, play: Boolean, nav: Nav) {
    val scope = rememberCoroutineScope()
    val likes by remember(post.id) { Repo.likes(post) }.collectAsState(emptySet<String>())
    val comments by remember(post.id) { Repo.comments(post) }.collectAsState(emptyList<Comment>())
    val saved by remember { Repo.saved() }.collectAsState(emptyList<Post>())
    val liked = Repo.uid in likes
    val isSaved = saved.any { it.id == post.id }
    var paused by remember { mutableStateOf(false) }
    var fallback by remember(post.id) { mutableStateOf(false) }
    var burst by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    LaunchedEffect(burst) { if (burst) { delay(700); burst = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ReelPlayer(post.reelId, play && !paused) { fallback = true }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(280.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
        )
        // tap = pause, double tap = like (not in the embed fallback, which needs its own taps)
        if (!fallback) Box(
            Modifier.fillMaxSize().pointerInput(post.id, liked) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!liked) scope.launch { runCatching { Repo.toggleLike(post, false) } }
                        burst = true
                    },
                    onTap = { paused = !paused }
                )
            }
        )
        if (paused && !fallback) Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center).size(84.dp))
        AnimatedVisibility(burst, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(110.dp))
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionItem(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, likes.size.toString(),
                if (liked) IgRed else Color.White) { scope.launch { runCatching { Repo.toggleLike(post, liked) } } }
            ActionItem(Icons.Outlined.ChatBubbleOutline, comments.size.toString()) { showComments = true }
            ActionItem(Icons.Outlined.Send, post.shareCount.toString()) { showShare = true }
            ActionItem(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "") {
                scope.launch { runCatching { Repo.toggleSave(post, isSaved) } }
            }
        }

        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 80.dp, bottom = 22.dp)) {
            Row(Modifier.clickable { nav.openUser(post.ownerId) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.ownerName, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(post.ownerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (post.caption.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(post.caption, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (showComments) ModalBottomSheet(
        onDismissRequest = { showComments = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1C)
    ) { CommentsPanel(post) }
    if (showShare) ShareSheet(post) { showShare = false }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        if (label.isNotEmpty()) Text(label, color = Color.White, fontSize = 12.sp)
    }
}

// ---------- playback: own player first, Instagram embed as fallback ----------
@Composable
fun ReelPlayer(reelId: String, play: Boolean, onFail: () -> Unit) {
    var url by remember(reelId) { mutableStateOf<String?>(null) }
    var failed by remember(reelId) { mutableStateOf(false) }
    LaunchedEffect(reelId) {
        val u = VideoSource.resolve(reelId)
        if (u == null) { failed = true; onFail() } else url = u
    }
    val u = url
    when {
        failed -> ReelWebView(reelId)
        u != null -> ExoSurface(u, play) { failed = true; onFail() }
        else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun ExoSurface(url: String, play: Boolean, onError: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onError() }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }
    LaunchedEffect(play, exo) { exo.playWhenReady = play }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        PlayerView(c).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            player = exo
        }
    }, update = { it.player = exo })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReelWebView(reelId: String) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !r.url.toString().contains("/embed")
            }
            loadUrl("https://www.instagram.com/reel/$reelId/embed/")
        }
    }, onRelease = { it.destroy() })
}

// ---------- share to DM ----------
@Composable
fun ShareSheet(post: Post, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    var sent by remember { mutableStateOf(setOf<String>()) }
    val mutuals = users.filter { it.uid != Repo.uid && it.uid in following && it.uid in followers }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1C)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.6f).navigationBarsPadding()) {
            Text("Share", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (mutuals.isEmpty()) item {
                    Text("Follow each other to send reels in DMs.", color = IgGray, modifier = Modifier.padding(16.dp))
                }
                items(mutuals, key = { it.uid }) { u ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.username, 44.dp)
                        Text(u.username, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        val done = u.uid in sent
                        Button(
                            onClick = {
                                if (!done) {
                                    sent = sent + u.uid
                                    scope.launch {
                                        runCatching { Repo.sendMessage(u.uid, "", post); Repo.bumpShare(post) }
                                    }
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (done) IgCard else IgBlue, contentColor = Color.White)
                        ) { Text(if (done) "Sent" else "Send") }
                    }
                }
            }
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, post.url) }
                    ctx.startActivity(Intent.createChooser(send, null))
                    scope.launch { runCatching { Repo.bumpShare(post) } }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share via…", color = IgBlue) }
        }
    }
}
