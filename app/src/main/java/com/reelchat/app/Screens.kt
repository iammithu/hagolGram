@file:OptIn(ExperimentalMaterial3Api::class)

package com.reelchat.app

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun App(shared: MutableState<String?>) {
    val me by Repo.me.collectAsState()
    if (me == null) AuthScreen() else MainApp(shared)
}

@Composable
fun AuthScreen() {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text("ReelChat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (signup) OutlinedTextField(name, { name = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; err = null
            scope.launch {
                try { if (signup) Repo.signUp(email.trim(), pw, name) else Repo.signIn(email.trim(), pw) }
                catch (e: Exception) { err = e.message }
                busy = false
            }
        }) { Text(if (signup) "Create account" else "Log in") }
        TextButton({ signup = !signup }) { Text(if (signup) "Have an account? Log in" else "New here? Sign up") }
    }
}

@Composable
fun MainApp(shared: MutableState<String?>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(shared.value) { if (shared.value != null) tab = 1 }
    val notifs by Repo.notifs.collectAsState()
    val unread = notifs.count { !it.read }
    val items = listOf(Icons.Filled.Home to "Reels", Icons.Filled.AddCircle to "Add", Icons.Filled.People to "People",
        Icons.Filled.Notifications to "Alerts", Icons.Filled.Person to "Profile")
    Scaffold(bottomBar = {
        NavigationBar {
            items.forEachIndexed { i, (ic, l) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(l, fontSize = 10.sp) },
                    icon = {
                        BadgedBox(badge = { if (i == 3 && unread > 0) Badge { Text(unread.toString()) } }) { Icon(ic, l) }
                    })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> FeedScreen(); 1 -> AddScreen(shared); 2 -> PeopleScreen(); 3 -> NotificationsScreen(); else -> ProfileScreen() }
        }
    }
}

// ---------------- Feed ----------------
@Composable
fun FeedScreen() {
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val owners = remember(following) { (following + Repo.uid).toList() }
    // re-subscribe when mutual status changes so private profiles unlock right away
    val posts by remember(owners, followers) { Repo.feed(owners) }.collectAsState(emptyList<Post>())
    if (posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No reels yet.\nAdd one, or follow your friend.", textAlign = TextAlign.Center)
        }
    } else {
        val state = rememberPagerState { posts.size }
        VerticalPager(state, Modifier.fillMaxSize(), key = { posts[it].id }) { ReelPage(posts[it]) }
    }
}

@Composable
fun ReelPage(post: Post) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showComments by remember { mutableStateOf(false) }
    val likes by remember(post.id) { Repo.likes(post) }.collectAsState(emptySet<String>())
    val comments by remember(post.id) { Repo.comments(post) }.collectAsState(emptyList<Comment>())
    val saved by remember { Repo.saved() }.collectAsState(emptyList<Post>())
    val liked = Repo.uid in likes
    val isSaved = saved.any { it.id == post.id }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        key(post.reelId) { ReelWebView(post.reelId) }
        Text(
            "@${post.ownerName}" + if (post.caption.isNotBlank()) " · ${post.caption}" else "",
            color = Color.White, fontSize = 13.sp, maxLines = 2,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .5f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = .4f)).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionItem(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, likes.size.toString(),
                if (liked) Color(0xFFFF3040) else Color.White) { scope.launch { runCatching { Repo.toggleLike(post, liked) } } }
            ActionItem(Icons.Outlined.ChatBubbleOutline, comments.size.toString(), Color.White) { showComments = true }
            ActionItem(Icons.Outlined.Send, post.shareCount.toString(), Color.White) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, post.url) }
                ctx.startActivity(Intent.createChooser(send, null))
                scope.launch { runCatching { Repo.bumpShare(post) } }
            }
            ActionItem(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "", Color.White) {
                scope.launch { runCatching { Repo.toggleSave(post, isSaved) } }
            }
        }
    }
    if (showComments) ModalBottomSheet(onDismissRequest = { showComments = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { CommentsPanel(post) }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        if (label.isNotEmpty()) Text(label, color = Color.White, fontSize = 12.sp)
    }
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
                // keep the user inside the embed (blocks "open in Instagram"/login redirects)
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !r.url.toString().contains("/embed")
            }
            loadUrl("https://www.instagram.com/reel/$reelId/embed/captioned/")
        }
    }, onRelease = { it.destroy() })
}

// ---------------- Add ----------------
@Composable
fun AddScreen(shared: MutableState<String?>) {
    var url by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(shared.value) {
        shared.value?.let { url = Regex("https?://\\S+").find(it)?.value ?: it; shared.value = null }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add a reel", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(url, { url = it }, label = { Text("Instagram reel link") }, modifier = Modifier.fillMaxWidth())
        TextButton({ clip.getText()?.text?.let { url = it } }) { Text("Paste from clipboard") }
        OutlinedTextField(caption, { caption = it }, label = { Text("Caption (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                val ok = runCatching { Repo.addPost(url, caption) }.getOrDefault(false)
                Toast.makeText(ctx, if (ok) "Reel added" else "Invalid reel link", Toast.LENGTH_SHORT).show()
                if (ok) { url = ""; caption = "" }
            }
        }) { Text("Post reel") }
        Text("Tip: in Instagram tap Share → ReelChat to add a reel in one step.", fontSize = 12.sp)
    }
}

// ---------------- People ----------------
@Composable
fun PeopleScreen() {
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize()) {
        items(users.filter { it.uid != Repo.uid }, key = { it.uid }) { u ->
            val f = u.uid in following
            val b = u.uid in followers
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.username, fontWeight = FontWeight.Bold)
                    Text((if (u.locked) "🔒 Private · " else "") + when { f && b -> "Mutual · chat enabled"; b -> "Follows you"; else -> "" }, fontSize = 12.sp)
                }
                if (f) OutlinedButton({ scope.launch { runCatching { Repo.toggleFollow(u.uid, true) } } }) { Text("Following") }
                else Button({ scope.launch { runCatching { Repo.toggleFollow(u.uid, false) } } }) { Text(if (b) "Follow back" else "Follow") }
            }
        }
    }
}

// ---------------- Profile ----------------
@Composable
fun ProfileScreen() {
    val me by Repo.me.collectAsState()
    val mine by remember { Repo.feed(listOf(Repo.uid)) }.collectAsState(emptyList<Post>())
    val saved by remember { Repo.saved() }.collectAsState(emptyList<Post>())
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Post?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(me?.username ?: "", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton({ Repo.signOut() }) { Text("Log out") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Private profile", fontWeight = FontWeight.Bold)
                Text(if (me?.locked == true) "Only mutual followers can see your reels"
                     else "Public: everyone who follows you can see your reels", fontSize = 12.sp)
            }
            Switch(checked = me?.locked == true, onCheckedChange = { v -> scope.launch { runCatching { Repo.setLocked(v) } } })
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(mine.size, "Reels"); Stat(followers.size, "Followers"); Stat(following.size, "Following")
        }
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }) { Text("Reels", Modifier.padding(12.dp)) }
            Tab(tab == 1, { tab = 1 }) { Text("Saved", Modifier.padding(12.dp)) }
        }
        val list = if (tab == 0) mine else saved
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list, key = { it.id }) { p ->
                Box(Modifier.aspectRatio(9f / 16f).background(MaterialTheme.colorScheme.surfaceVariant).clickable { open = p },
                    Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(36.dp))
                    Text(p.caption.take(24), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
            }
        }
    }
    open?.let { p ->
        Dialog({ open = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                ReelPage(p)
                IconButton({ open = null }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
        }
    }
}

@Composable
fun Stat(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 12.sp)
    }
}


// ---------------- Notifications ----------------
@Composable
fun NotificationsScreen() {
    val list by Repo.notifs.collectAsState()
    var open by remember { mutableStateOf<Post?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton({ scope.launch { Repo.markAllRead() } }) { Text("Mark all read") }
        }
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing yet.") }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { n ->
                Row(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        Repo.markRead(n.id)
                        if (n.postId.isNotEmpty()) {
                            val p = Repo.getPost(n.ownerId, n.postId)
                            if (p != null) open = p else Toast.makeText(ctx, "You can't view that reel", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (n.read) Color.Transparent else MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Text(n.body, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                }
            }
        }
    }
    open?.let { p ->
        Dialog({ open = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                ReelPage(p)
                IconButton({ open = null }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
        }
    }
}
