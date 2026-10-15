package com.reelchat.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

sealed interface Overlay {
    object Notifs : Overlay
    data class Chat(val uid: String) : Overlay
    data class User(val uid: String) : Overlay
}

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
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text("hagolGram", fontFamily = FontFamily.Cursive, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        IgTextField(email, { email = it }, "Email", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (signup) {
            IgTextField(name, { name = it }, "Username", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        IgTextField(pw, { pw = it }, "Password", Modifier.fillMaxWidth(), password = true)
        err?.let { Text(it, color = IgRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IgBlue, contentColor = Color.White),
            onClick = {
                busy = true; err = null
                scope.launch {
                    try { if (signup) Repo.signUp(email.trim(), pw, name) else Repo.signIn(email.trim(), pw) }
                    catch (e: Exception) { err = e.message }
                    busy = false
                }
            }
        ) { Text(if (signup) "Sign up" else "Log in", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = IgCard)
        Row(Modifier.padding(vertical = 18.dp)) {
            Text(if (signup) "Have an account? " else "Don't have an account? ", color = IgGray, fontSize = 13.sp)
            Text(if (signup) "Log in" else "Sign up", color = IgBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clickable { signup = !signup; err = null })
        }
    }
}

@Composable
fun MainApp(shared: MutableState<String?>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Overlay>()) }
    var reel by remember { mutableStateOf<Post?>(null) }
    val nav = remember {
        Nav(
            { stack = stack + Overlay.User(it) },
            { stack = stack + Overlay.Chat(it) },
            { stack = stack + Overlay.Notifs },
            { reel = it }
        )
    }
    val me by Repo.me.collectAsState()
    val chats by Repo.chats.collectAsState()
    val dmUnread = chats.count { it.lastFrom != Repo.uid && it.lastAt > (it.seen[Repo.uid] ?: 0L) }
    LaunchedEffect(shared.value) { if (shared.value != null) tab = 2 }
    BackHandler(enabled = reel != null || stack.isNotEmpty()) {
        if (reel != null) reel = null else stack = stack.dropLast(1)
    }
    val top = stack.lastOrNull()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f)) {
            // Reels stays composed so your position survives tab switches
            ReelsScreen(nav, playing = tab == 0 && top == null && reel == null)
            if (tab != 0) Box(Modifier.fillMaxSize().background(Color.Black)) {
                when (tab) {
                    1 -> SearchScreen(nav)
                    2 -> CreateScreen(shared)
                    3 -> MessagesScreen(nav)
                    else -> ProfileScreen(Repo.uid, nav, null)
                }
            }
            top?.let { o ->
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val back = { stack = stack.dropLast(1) }
                    when (o) {
                        is Overlay.Notifs -> NotificationsScreen(nav, back)
                        is Overlay.Chat -> ChatScreen(o.uid, nav, back)
                        is Overlay.User -> ProfileScreen(o.uid, nav, back)
                    }
                }
            }
        }
        if (top == null) {
            HorizontalDivider(color = IgCard, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                BarItem({ tab = 0 }) { Icon(if (tab == 0) Icons.Filled.Movie else Icons.Outlined.Movie, "Reels", Modifier.size(28.dp)) }
                BarItem({ tab = 1 }) { Icon(if (tab == 1) Icons.Filled.Search else Icons.Outlined.Search, "Search", Modifier.size(28.dp)) }
                BarItem({ tab = 2 }) { Icon(Icons.Outlined.AddBox, "Create", Modifier.size(28.dp)) }
                BarItem({ tab = 3 }) {
                    BadgedBox(badge = { if (dmUnread > 0) Badge(containerColor = IgRed) { Text(dmUnread.toString()) } }) {
                        Icon(if (tab == 3) Icons.Filled.Send else Icons.Outlined.Send, "Messages", Modifier.size(26.dp))
                    }
                }
                BarItem({ tab = 4 }) {
                    Box(Modifier.size(30.dp).then(if (tab == 4) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier).padding(2.dp)) {
                        Avatar(me?.username ?: "?", 26.dp)
                    }
                }
            }
        }
    }

    reel?.let { p ->
        Dialog({ reel = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                ReelPage(p, true, nav)
                IconButton({ reel = null }, Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick), Alignment.Center) { content() }
}
