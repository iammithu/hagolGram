package com.reelchat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Inbox: every mutual follower is a possible chat; most recent conversations first. */
@Composable
fun MessagesScreen(nav: Nav) {
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val chats by Repo.chats.collectAsState()
    val me by Repo.me.collectAsState()
    val mutuals = users.filter { it.uid != Repo.uid && it.uid in following && it.uid in followers }
    val byOther = chats.associateBy { c -> c.members.firstOrNull { it != Repo.uid } ?: "" }
    val rows = mutuals.sortedByDescending { byOther[it.uid]?.lastAt ?: 0L }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(me?.username ?: "Messages", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
        Text("Messages", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Follow each other to start chatting.", color = IgGray)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.uid }) { u ->
                val c = byOther[u.uid]
                val unread = c != null && c.lastFrom != Repo.uid && c.lastAt > (c.seen[Repo.uid] ?: 0L)
                Row(
                    Modifier.fillMaxWidth().clickable { nav.openChat(u.uid) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(u.username, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.username, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            if (c == null) "Say hi 👋" else c.lastText + " · " + ago(c.lastAt),
                            color = if (unread) Color.White else IgGray, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(IgBlue))
                }
            }
        }
    }
}

@Composable
fun ChatScreen(uid: String, nav: Nav, onBack: () -> Unit) {
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val name = users.find { it.uid == uid }?.username ?: ""
    val msgs by remember(uid) { Repo.messages(uid) }.collectAsState(emptyList<Msg>())
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(msgs.size) { Repo.markChatSeen(uid) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Row(Modifier.clickable { nav.openUser(uid) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(name.ifEmpty { "?" }, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        HorizontalDivider(color = IgCard, thickness = 0.5.dp)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), reverseLayout = true) {
            items(msgs.reversed(), key = { it.id }) { m ->
                val mine = m.from == Repo.uid
                val isReel = m.reelId.isNotEmpty()
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (mine) IgBlue else IgCard)
                            .clickable(enabled = isReel) {
                                scope.launch { Repo.getPost(m.reelOwner, m.reelId)?.let { nav.openReel(it) } }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        if (isReel) Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                                Text("Reel · @" + m.reelOwnerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (m.reelCaption.isNotBlank()) Text(m.reelCaption, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Tap to watch", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        } else Text(m.text, fontSize = 15.sp)
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IgTextField(text, { text = it }, "Message…", Modifier.weight(1f), singleLine = false)
            Text("Send", color = if (text.isBlank()) IgGray else IgBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = text.isNotBlank()) {
                    val t = text
                    text = ""
                    scope.launch { runCatching { Repo.sendMessage(uid, t, null) } }
                }.padding(12.dp))
        }
    }
}
