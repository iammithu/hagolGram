@file:OptIn(ExperimentalMaterial3Api::class)

package com.reelchat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CommentsPanel(post: Post) {
    val following by remember { Repo.following() }.collectAsState(emptySet<String>())
    val followers by remember { Repo.followers() }.collectAsState(emptySet<String>())
    val canWrite = post.ownerId == Repo.uid || (post.ownerId in following && post.ownerId in followers)
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()) {
        Text("Comments", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        CommentsBody(post, canWrite)
    }
}

@Composable
private fun CommentsBody(post: Post, canWrite: Boolean) {
    val users by remember { Repo.users() }.collectAsState(emptyList<UserProfile>())
    val names = remember(users) { users.associate { it.uid to it.username } }
    val comments by remember(post.id, canWrite) { Repo.comments(post) }.collectAsState(emptyList<Comment>())
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var text by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf(false) }
    var extra by remember { mutableStateOf(emptySet<String>()) }
    var pick by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val top = comments.filter { it.parentId == null }
    val replies = comments.filter { it.parentId != null }.groupBy { it.parentId }
    val forced = replyTo?.secret == true
    val isSecret = secret || forced
    val audience = if (isSecret) setOf(Repo.uid, post.ownerId) + extra + (replyTo?.visibleTo ?: emptyList()) + listOfNotNull(replyTo?.authorId)
                   else emptySet()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            if (!canWrite) item {
                Text("Public comments are only for mutual followers. Secret comments sent to you still show up here.",
                    fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
            if (top.isEmpty()) item { Text("No comments yet.", modifier = Modifier.padding(16.dp)) }
            top.forEach { c ->
                item(key = c.id) { CommentRow(post, c, false, names, canWrite) { replyTo = c; text = "" } }
                items(replies[c.id].orEmpty(), key = { it.id }) { r ->
                    CommentRow(post, r, true, names, canWrite) { replyTo = c; text = "@${r.authorName} " }
                }
            }
        }
        if (canWrite) {
            replyTo?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${it.authorName}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton({ replyTo = null; text = "" }) { Text("Cancel") }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = isSecret, onClick = { if (!forced) secret = !secret }, label = { Text("🔒 Secret") })
                if (isSecret) {
                    Text("Visible to: " + audience.joinToString { if (it == Repo.uid) "you" else "@" + (names[it] ?: "?") },
                        fontSize = 11.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    if (!forced) TextButton({ pick = true }) { Text("Add people") }
                }
            }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f),
                    placeholder = { Text(if (isSecret) "Secret message…" else "Add a comment…") }, maxLines = 4)
                IconButton({
                    if (text.isNotBlank()) {
                        val t = text; val rt = replyTo; val s = isSecret; val e = extra
                        text = ""; replyTo = null; secret = false; extra = emptySet()
                        scope.launch { runCatching { Repo.addComment(post, t, rt, s, e) } }
                    }
                }) { Icon(Icons.Filled.Send, "Send") }
            }
        }
    }
    if (pick) {
        val others = users.filter { it.uid != Repo.uid && it.uid != post.ownerId }
        AlertDialog(onDismissRequest = { pick = false },
            title = { Text("Who else can see this?") },
            text = {
                Column {
                    if (others.isEmpty()) Text("No one else yet — it's only between you and the uploader.")
                    others.forEach { u ->
                        Row(Modifier.fillMaxWidth().clickable { extra = if (u.uid in extra) extra - u.uid else extra + u.uid },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(u.uid in extra, null); Text(u.username, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton({ pick = false }) { Text("Done") } })
    }
}

private val quick = listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

@Composable
private fun CommentRow(post: Post, c: Comment, isReply: Boolean, names: Map<String, String>, canReact: Boolean, onReply: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    fun react(e: String?) { scope.launch { runCatching { Repo.react(post, c, e) } } }

    Column(Modifier.fillMaxWidth().padding(start = if (isReply) 48.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Text(c.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (c.secret) Text("🔒 Secret · only " + c.visibleTo.joinToString { if (it == Repo.uid) "you" else "@" + (names[it] ?: "?") },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        Text(c.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canReact) Text("Reply", fontSize = 12.sp, modifier = Modifier.clickable(onClick = onReply))
            Text("React", fontSize = 12.sp, modifier = Modifier.clickable { picker = !picker })
            c.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, n) ->
                val mine = c.reactions[Repo.uid] == emoji
                Text("$emoji $n", fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { react(if (mine) null else emoji) }.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        if (picker) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            quick.forEach { e -> Text(e, fontSize = 22.sp, modifier = Modifier.clickable { picker = false; react(e) }) }
            Text("＋", fontSize = 22.sp, modifier = Modifier.clickable { custom = true })
        }
    }
    if (custom) {
        var input by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { custom = false },
            title = { Text("Any emoji") },
            text = { OutlinedTextField(input, { input = it }, placeholder = { Text("Open emoji keyboard") }, singleLine = true) },
            confirmButton = { TextButton({
                val e = firstGrapheme(input)
                if (e.isNotEmpty()) react(e)
                custom = false; picker = false
            }) { Text("React") } })
    }
}

private fun firstGrapheme(s: String): String {
    val t = s.trim()
    if (t.isEmpty()) return ""
    val bi = java.text.BreakIterator.getCharacterInstance()
    bi.setText(t)
    return t.substring(0, bi.next())
}
