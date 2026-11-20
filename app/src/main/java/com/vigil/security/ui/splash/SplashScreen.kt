package com.vigil.security.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vigil.security.core.design.components.DottedShield
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Allow the full dotted shield intro animation to draw in (~2600ms)
        delay(2700)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Allow tapping anywhere to skip splash screen immediately
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // Pure Animated Dotted Cyber Shield (No text, perfectly centered)
        DottedShield(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .aspectRatio(1f),
            hueCycle = false,
            replayKey = 0
        )
    }
}
