package com.reelchat.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Set to true ONLY after you deploy functions/ (needs Firebase Blaze plan). Then the server sends background push. */
const val USE_CLOUD_FUNCTION = false

object Repo {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val me = MutableStateFlow<UserProfile?>(null)
    val notifs = MutableStateFlow<List<Notif>>(emptyList())
    val uid: String get() = auth.currentUser?.uid ?: ""

    private var watchJob: Job? = null

    init {
        auth.addAuthStateListener { a ->
            val u = a.currentUser
            if (u == null) { me.value = null; notifs.value = emptyList(); watchJob?.cancel() }
            else scope.launch {
                runCatching { db.collection("users").document(u.uid).get().await() }
                    .getOrNull()?.toObject(UserProfile::class.java)?.let { me.value = it }
                startNotifWatcher(u.uid)
                runCatching {
                    val t = FirebaseMessaging.getInstance().token.await()
                    db.collection("fcmTokens").document(u.uid).set(mapOf("token" to t)).await()
                }
            }
        }
    }

    private fun <T> Query.watch(empty: T, map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val reg = addSnapshotListener { s, _ -> trySend(if (s != null) map(s) else empty) }
        awaitClose { reg.remove() }
    }

    private fun reels(owner: String) = db.collection("users").document(owner).collection("reels")
    private fun reel(owner: String, id: String) = reels(owner).document(id)
    private fun DocumentSnapshot.post() = toObject(Post::class.java)?.copy(id = id)

    // ---- auth ----
    suspend fun signIn(email: String, pw: String) { auth.signInWithEmailAndPassword(email, pw).await() }
    suspend fun signUp(email: String, pw: String, username: String) {
        val u = auth.createUserWithEmailAndPassword(email, pw).await().user!!
        val p = UserProfile(u.uid, username.trim(), false)
        db.collection("users").document(u.uid).set(p).await()
        me.value = p
    }
    fun signOut() = auth.signOut()

    suspend fun setLocked(locked: Boolean) {
        db.collection("users").document(uid).update("locked", locked).await()
        me.value = me.value?.copy(locked = locked)
    }

    // ---- people / follows ----
    fun users(): Flow<List<UserProfile>> =
        db.collection("users").watch(emptyList<UserProfile>()) { it.toObjects(UserProfile::class.java) }

    fun following(): Flow<Set<String>> =
        db.collection("follows").whereEqualTo("follower", uid)
            .watch(emptySet<String>()) { s -> s.documents.mapNotNull { it.getString("followee") }.toSet() }

    fun followers(): Flow<Set<String>> =
        db.collection("follows").whereEqualTo("followee", uid)
            .watch(emptySet<String>()) { s -> s.documents.mapNotNull { it.getString("follower") }.toSet() }

    suspend fun toggleFollow(target: String, isFollowing: Boolean) {
        val ref = db.collection("follows").document("${uid}_$target")
        if (isFollowing) ref.delete().await()
        else {
            ref.set(mapOf("follower" to uid, "followee" to target)).await()
            notify(target, "follow", null, "@${name()} started following you")
        }
    }

    // ---- posts ----
    private val reelRegex = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:reels?|p|tv)/([A-Za-z0-9_-]+)""")
    fun reelId(text: String): String? = reelRegex.find(text)?.groupValues?.get(1)

    /** One listener per owner so the security rules can check follow/privacy per owner. Denied owners just yield nothing. */
    fun feed(owners: List<String>): Flow<List<Post>> {
        val flows = owners.map { o -> reels(o).watch(emptyList<Post>()) { s -> s.documents.mapNotNull { it.post() } } }
        return if (flows.isEmpty()) flowOf(emptyList())
        else combine(flows) { arr: Array<List<Post>> -> arr.flatMap { it }.sortedByDescending { it.createdAt } }
    }

    suspend fun getPost(ownerId: String, id: String): Post? =
        runCatching { reel(ownerId, id).get().await().post() }.getOrNull()

    suspend fun addPost(url: String, caption: String): Boolean {
        val id = reelId(url) ?: return false
        val ref = reels(uid).document()
        val post = Post(ref.id, uid, name(), id, url.trim(), caption.trim(), 0, System.currentTimeMillis())
        ref.set(post).await()
        runCatching {
            val followerIds = db.collection("follows").whereEqualTo("followee", uid).get().await()
                .documents.mapNotNull { it.getString("follower") }
            val followingIds = db.collection("follows").whereEqualTo("follower", uid).get().await()
                .documents.mapNotNull { it.getString("followee") }.toSet()
            val locked = me.value?.locked == true
            followerIds.filter { !locked || it in followingIds }
                .forEach { notify(it, "reel", post, "@${name()} shared a new reel") }
        }
        return true
    }

    suspend fun bumpShare(p: Post) {
        reel(p.ownerId, p.id).update("shareCount", FieldValue.increment(1)).await()
    }

    // ---- likes / saves ----
    fun likes(p: Post): Flow<Set<String>> =
        reel(p.ownerId, p.id).collection("likes")
            .watch(emptySet<String>()) { s -> s.documents.map { it.id }.toSet() }

    suspend fun toggleLike(p: Post, liked: Boolean) {
        val ref = reel(p.ownerId, p.id).collection("likes").document(uid)
        if (liked) ref.delete().await()
        else {
            ref.set(mapOf("t" to System.currentTimeMillis())).await()
            notify(p.ownerId, "like", p, "@${name()} liked your reel")
        }
    }

    fun saved(): Flow<List<Post>> =
        db.collection("users").document(uid).collection("saved")
            .watch(emptyList<Post>()) { s -> s.documents.mapNotNull { it.post() } }

    suspend fun toggleSave(post: Post, isSaved: Boolean) {
        val ref = db.collection("users").document(uid).collection("saved").document(post.id)
        if (isSaved) ref.delete().await() else ref.set(post).await()
    }

    // ---- comments (chat). Two listeners: public ones + secret ones addressed to me ----
    fun comments(p: Post): Flow<List<Comment>> {
        val col = reel(p.ownerId, p.id).collection("comments")
        val map = { s: QuerySnapshot -> s.documents.mapNotNull { d -> d.toObject(Comment::class.java)?.copy(id = d.id) } }
        val pub = col.whereEqualTo("secret", false).watch(emptyList<Comment>(), map)
        val sec = col.whereArrayContains("visibleTo", uid).watch(emptyList<Comment>(), map)
        return combine(pub, sec) { a, b -> (a + b).distinctBy { it.id }.sortedBy { it.createdAt } }
    }

    /** secret = only visibleTo can read it. Replies to a secret comment stay secret for the same people. */
    suspend fun addComment(p: Post, text: String, parent: Comment?, secret: Boolean, extra: Set<String>) {
        val ref = reel(p.ownerId, p.id).collection("comments").document()
        val isSecret = secret || parent?.secret == true
        val vis = if (isSecret)
            (setOf(uid, p.ownerId) + extra + (parent?.visibleTo ?: emptyList()) + listOfNotNull(parent?.authorId)).toList()
        else emptyList()
        ref.set(Comment(ref.id, uid, name(), text.trim(), parent?.id, System.currentTimeMillis(), emptyMap(), isSecret, vis)).await()

        if (isSecret) {
            vis.filter { it != uid }.forEach { notify(it, "secret", p, "@${name()} sent you a secret comment") }
        } else {
            val verb = if (parent != null) "replied" else "commented"
            (setOfNotNull(p.ownerId, parent?.authorId) - uid).forEach {
                notify(it, if (parent != null) "reply" else "comment", p, "@${name()} $verb: ${text.trim().take(60)}")
            }
        }
    }

    suspend fun react(p: Post, c: Comment, emoji: String?) {
        reel(p.ownerId, p.id).collection("comments").document(c.id)
            .update("reactions.$uid", emoji ?: FieldValue.delete()).await()
        if (emoji != null) notify(c.authorId, "react", p, "@${name()} reacted $emoji to your comment")
    }

    // ---- notifications ----
    private fun name() = me.value?.username ?: ""

    private suspend fun notify(to: String, type: String, post: Post?, body: String) {
        if (to.isEmpty() || to == uid) return
        val ref = db.collection("notifications").document()
        runCatching {
            ref.set(Notif(ref.id, to, uid, name(), type, post?.ownerId ?: "", post?.id ?: "", body, System.currentTimeMillis(), false)).await()
        }
    }

    private fun startNotifWatcher(u: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            var seen: MutableSet<String>? = null
            db.collection("notifications").whereEqualTo("toUid", u)
                .watch(emptyList<Notif>()) { s ->
                    s.documents.mapNotNull { d -> d.toObject(Notif::class.java)?.copy(id = d.id) }.sortedByDescending { it.createdAt }
                }.collect { list ->
                    notifs.value = list
                    val s = seen
                    if (s == null) seen = list.map { it.id }.toMutableSet()   // don't buzz for old ones
                    else list.filter { it.id !in s && !it.read }.forEach {
                        s.add(it.id)
                        if (!USE_CLOUD_FUNCTION) Notifier.show(ReelApp.instance, it.id.hashCode(), "ReelChat", it.body)
                    }
                }
        }
    }

    suspend fun markRead(id: String) { runCatching { db.collection("notifications").document(id).update("read", true).await() } }
    suspend fun markAllRead() { notifs.value.filter { !it.read }.forEach { markRead(it.id) } }
}
