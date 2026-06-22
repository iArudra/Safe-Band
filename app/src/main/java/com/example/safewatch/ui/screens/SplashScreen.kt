package com.example.safewatch.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.safewatch.ui.theme.BackgroundDark
import com.example.safewatch.ui.theme.PrimaryBlue
import com.example.safewatch.ui.theme.SecondaryCyan
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToOnboarding: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(2000)
        onNavigateToOnboarding()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                // Pulse effect background
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .background(PrimaryBlue.copy(alpha = 0.2f), CircleShape)
                )
                
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield",
                    modifier = Modifier.size(80.dp),
                    tint = PrimaryBlue
                )
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    modifier = Modifier.size(30.dp),
                    tint = SecondaryCyan
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SafeWatch",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            
            Text(
                text = "Protecting what matters most",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
