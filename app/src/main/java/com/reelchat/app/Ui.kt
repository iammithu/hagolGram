package com.reelchat.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val IgBlue = Color(0xFF0095F6)
val IgRed = Color(0xFFFF3040)
val IgGray = Color(0xFFA8A8A8)
val IgCard = Color(0xFF262626)

/** Navigation callbacks shared by all screens. */
class Nav(
    val openUser: (String) -> Unit,
    val openChat: (String) -> Unit,
    val openNotifs: () -> Unit,
    val openReel: (Post) -> Unit
)

fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 604800 -> "${s / 86400}d"
        else -> "${s / 604800}w"
    }
}

@Composable
fun Avatar(name: String, size: Dp, ring: Boolean = false) {
    val hue = (kotlin.math.abs(name.fold(7) { a, c -> a * 31 + c.code }) % 360).toFloat()
    val circle: @Composable (Dp) -> Unit = { s ->
        Box(Modifier.size(s).clip(CircleShape).background(Color.hsv(hue, 0.45f, 0.6f)), Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (s.value * 0.42f).sp)
        }
    }
    if (ring) {
        val brush = Brush.sweepGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
            Color(0xFF962FBF), Color(0xFF4F5BD5), Color(0xFFFEDA75)))
        Box(Modifier.size(size + 12.dp).border(2.dp, brush, CircleShape), Alignment.Center) { circle(size) }
    } else circle(size)
}

@Composable
fun IgTextField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    password: Boolean = false, singleLine: Boolean = true
) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier,
        placeholder = { Text(hint, color = IgGray) },
        singleLine = singleLine, maxLines = if (singleLine) 1 else 4,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = IgCard, unfocusedContainerColor = IgCard,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = IgBlue
        )
    )
}

@Composable
fun FollowButton(following: Boolean, followsYou: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.height(34.dp), shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (following) IgCard else IgBlue, contentColor = Color.White)
    ) {
        Text(if (following) "Following" else if (followsYou) "Follow back" else "Follow", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
