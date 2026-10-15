package com.reelchat.app

// locked = private profile (only mutual followers see reels); false = public (followers see reels)
data class UserProfile(val uid: String = "", val username: String = "", val locked: Boolean = false)

data class Post(
    val id: String = "", val ownerId: String = "", val ownerName: String = "",
    val reelId: String = "", val url: String = "", val caption: String = "",
    val shareCount: Long = 0, val createdAt: Long = 0
)

data class Comment(
    val id: String = "", val authorId: String = "", val authorName: String = "",
    val text: String = "", val parentId: String? = null, val createdAt: Long = 0,
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji
    val secret: Boolean = false,
    val visibleTo: List<String> = emptyList()        // only used when secret
)

data class Notif(
    val id: String = "", val toUid: String = "", val fromId: String = "", val fromName: String = "",
    val type: String = "", val ownerId: String = "", val postId: String = "",
    val body: String = "", val createdAt: Long = 0, val read: Boolean = false
)

// Direct messages. Chat id = the two uids sorted and joined with "_".
data class Chat(
    val id: String = "", val members: List<String> = emptyList(), val lastText: String = "",
    val lastAt: Long = 0, val lastFrom: String = "", val seen: Map<String, Long> = emptyMap()
)

data class Msg(
    val id: String = "", val from: String = "", val text: String = "", val createdAt: Long = 0,
    val reelOwner: String = "", val reelId: String = "", val reelCaption: String = "", val reelOwnerName: String = ""
)
