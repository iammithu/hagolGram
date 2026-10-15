@file:OptIn(ExperimentalMaterial3Api::class)

package com.reelchat.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------------- Profile (own + other people) ----------------
@Composable
fun ProfileScreen(uid: String, nav: Nav, onBack: (() -> Unit)?) {
    val isMe = uid == Repo.uid
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val user = users.find { it.uid == uid }
    val myFollowing by remember { Repo.following() }.collectAsState(emptySet<String>())
    val myFollowers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val theirFollowing by remember(uid) { Repo.following(uid) }.collectAsState(emptySet<String>())
    val theirFollowers by remember(uid) { Repo.followers(uid) }.collectAsState(emptySet<String>())
    val posts by remember(uid, myFollowing, myFollowers) { Repo.feed(listOf(uid)) }.collectAsState(emptyList<Post>())
    val saved by remember { Repo.saved() }.collectAsState(emptyList<Post>())
    var tab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val iFollow = uid in myFollowing
    val theyFollow = uid in myFollowers
    val mutual = iFollow && theyFollow
    val locked = user?.locked == true
    val canSee = isMe || (iFollow && (!locked || theyFollow))
    val list = if (isMe && tab == 1) saved else posts
    val name = user?.username ?: ""

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } else Spacer(Modifier.width(8.dp))
            if (locked) Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp))
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = if (locked) 4.dp else 0.dp))
            if (isMe) IconButton({ showMenu = true }) { Icon(Icons.Filled.Menu, "Menu") }
        }
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(6.dp)) { Avatar(name.ifEmpty { "?" }, 80.dp, ring = true) }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat(if (canSee) posts.size else 0, "reels")
                            Stat(theirFollowers.size, "followers")
                            Stat(theirFollowing.size, "following")
                        }
                    }
                    Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    if (locked) Text("🔒 Private account", color = IgGray, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (isMe) {
                        Button(
                            onClick = { scope.launch { runCatching { Repo.setLocked(!locked) } } },
                            modifier = Modifier.fillMaxWidth().height(34.dp), shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IgCard, contentColor = Color.White)
                        ) { Text(if (locked) "Switch to public account" else "Switch to private account", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FollowButton(iFollow, theyFollow, { scope.launch { runCatching { Repo.toggleFollow(uid, iFollow) } } }, Modifier.weight(1f))
                            Button(
                                onClick = { nav.openChat(uid) }, enabled = mutual,
                                modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IgCard, contentColor = Color.White,
                                    disabledContainerColor = IgCard, disabledContentColor = IgGray)
                            ) { Text("Message", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    HorizontalDivider(color = IgCard, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth()) {
                        TabIcon(tab == 0, { tab = 0 }, Modifier.weight(1f)) { Icon(Icons.Outlined.GridOn, "Reels") }
                        if (isMe) TabIcon(tab == 1, { tab = 1 }, Modifier.weight(1f)) { Icon(Icons.Outlined.BookmarkBorder, "Saved") }
                    }
                }
            }
            if (!canSee && !(isMe && tab == 1)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (locked) "This account is private.\nYou both need to follow each other to see reels."
                            else "Follow to see their reels.",
                            textAlign = TextAlign.Center, color = IgGray
                        )
                    }
                }
            } else {
                items(list, key = { it.id }) { p ->
                    val h = (kotlin.math.abs(p.reelId.hashCode()) % 360).toFloat()
                    Box(
                        Modifier.aspectRatio(3f / 4f)
                            .background(Brush.verticalGradient(listOf(Color.hsv(h, 0.45f, 0.45f), Color.hsv((h + 40f) % 360f, 0.5f, 0.2f))))
                            .clickable { nav.openReel(p) }
                    ) {
                        Icon(Icons.Outlined.Movie, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                        if (p.caption.isNotBlank()) Text(p.caption, color = Color.White, fontSize = 11.sp, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
            }
        }
    }

    if (showMenu) ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Color(0xFF1C1C1C)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Private account", fontWeight = FontWeight.Bold)
                    Text(if (locked) "Only mutual followers can see your reels" else "Anyone who follows you can see your reels",
                        fontSize = 12.sp, color = IgGray)
                }
                Switch(checked = locked, onCheckedChange = { v -> scope.launch { runCatching { Repo.setLocked(v) } } })
            }
            Spacer(Modifier.height(12.dp))
            Text("Log out", color = IgRed, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { showMenu = false; Repo.signOut() }.padding(vertical = 14.dp))
        }
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun TabIcon(selected: Boolean, onClick: () -> Unit, modifier: Modifier, icon: @Composable () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(vertical = 10.dp)) { icon() }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (selected) Color.White else Color.Transparent))
    }
}

// ---------------- Search / people ----------------
@Composable
fun SearchScreen(nav: Nav) {
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shown = users.filter { it.uid != Repo.uid && it.username.contains(q.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IgTextField(q, { q = it }, "Search", Modifier.fillMaxWidth().padding(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (shown.isEmpty()) item { Text("No accounts found.", color = IgGray, modifier = Modifier.padding(16.dp)) }
            items(shown, key = { it.uid }) { u ->
                val f = u.uid in following
                val b = u.uid in followers
                Row(Modifier.fillMaxWidth().clickable { nav.openUser(u.uid) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.username, 50.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(u.username, fontWeight = FontWeight.Bold)
                        Text((if (u.locked) "🔒 Private · " else "") + when { f && b -> "Mutual"; b -> "Follows you"; else -> "" },
                            fontSize = 12.sp, color = IgGray)
                    }
                    FollowButton(f, b, { scope.launch { runCatching { Repo.toggleFollow(u.uid, f) } } })
                }
            }
        }
    }
}

// ---------------- Create ----------------
@Composable
fun CreateScreen(shared: MutableState<String?>) {
    var url by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(shared.value) {
        shared.value?.let { url = Regex("https?://\\S+").find(it)?.value ?: it; shared.value = null }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New reel", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Share", color = if (url.isBlank()) IgGray else IgBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = url.isNotBlank()) {
                    scope.launch {
                        val ok = runCatching { Repo.addPost(url, caption) }.getOrDefault(false)
                        Toast.makeText(ctx, if (ok) "Shared to your profile" else "That isn't a valid reel link", Toast.LENGTH_SHORT).show()
                        if (ok) { url = ""; caption = "" }
                    }
                }.padding(8.dp))
        }
        HorizontalDivider(color = IgCard, thickness = 0.5.dp)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IgTextField(url, { url = it }, "Paste Instagram reel link", Modifier.fillMaxWidth())
            Text("Paste from clipboard", color = IgBlue, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { clip.getText()?.text?.let { url = it } })
            IgTextField(caption, { caption = it }, "Write a caption…", Modifier.fillMaxWidth(), singleLine = false)
            Text("Tip: in Instagram tap Share → hagolGram to add a reel in one step.", color = IgGray, fontSize = 12.sp)
        }
    }
}

// ---------------- Activity (notifications) ----------------
@Composable
fun NotificationsScreen(nav: Nav, onBack: () -> Unit) {
    val list by Repo.notifs.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton({ scope.launch { Repo.markAllRead() } }) { Text("Mark all read", color = IgBlue) }
        }
        HorizontalDivider(color = IgCard, thickness = 0.5.dp)
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing yet.", color = IgGray) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { n ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            Repo.markRead(n.id)
                            when {
                                n.type == "follow" -> nav.openUser(n.fromId)
                                n.type == "dm" -> nav.openChat(n.fromId)
                                n.postId.isNotEmpty() -> Repo.getPost(n.ownerId, n.postId)?.let { nav.openReel(it) }
                                else -> Unit
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(n.fromName.ifEmpty { "?" }, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(n.body, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                        Text(ago(n.createdAt), fontSize = 12.sp, color = IgGray)
                    }
                    if (!n.read) Box(Modifier.size(9.dp).clip(CircleShape).background(IgBlue))
                }
            }
        }
    }
}
