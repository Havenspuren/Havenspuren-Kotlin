package com.example.havenspure_kotlin_prototype.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.R
import com.example.havenspure_kotlin_prototype.ui.theme.GradientStart
import com.example.havenspure_kotlin_prototype.ui.theme.PrimaryColor
import com.example.havenspure_kotlin_prototype.ui.theme.TextDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToMain: () -> Unit) {
    LaunchedEffect(key1 = true) {
        delay(3000)
        onNavigateToMain()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientStart),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HAVEN",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Text(
                text = "SPUREN",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        color = PrimaryColor,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.baseline_anchor_24),
                    contentDescription = "Anchor",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Row {
                Text(
                    text = "Tour ",
                    fontSize = 36.sp,
                    color = Color(0xFF3D8BFD)
                )
                Text(
                    text = "Guide",
                    fontSize = 36.sp,
                    color = Color(0xFFFF9500)
                )
            }
        }
    }
}