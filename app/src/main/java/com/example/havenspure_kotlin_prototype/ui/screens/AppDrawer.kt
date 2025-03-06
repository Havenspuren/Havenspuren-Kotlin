package com.example.havenspure_kotlin_prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppDrawer(onCloseDrawer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 50.dp)
    ) {
        // Menu items
        DrawerMenuItem(
            icon = Icons.Outlined.Explore,
            title = stringResource(R.string.menu_tours),
            onClick = {
                onCloseDrawer()
                // Navigation would go here
            }
        )

        Divider(color = Color.Black.copy(alpha = 0.2f), thickness = 1.dp)

        DrawerMenuItem(
            icon = Icons.Outlined.EmojiEvents,
            title = stringResource(R.string.menu_trophies),
            onClick = {
                onCloseDrawer()
                // Navigation would go here
            }
        )

        Divider(color = Color.Black.copy(alpha = 0.2f), thickness = 1.dp)

        DrawerMenuItem(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = stringResource(R.string.menu_feedback),
            onClick = {
                onCloseDrawer()
                // Navigation would go here
            }
        )

        Divider(color = Color.Black.copy(alpha = 0.2f), thickness = 1.dp)

        DrawerMenuItem(
            icon = Icons.Outlined.MailOutline,
            title = stringResource(R.string.menu_support),
            onClick = {
                onCloseDrawer()
                // Navigation would go here
            }
        )

        Divider(color = Color.Black.copy(alpha = 0.2f), thickness = 1.dp)

        DrawerMenuItem(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.menu_about),
            onClick = {
                onCloseDrawer()
                // Navigation would go here
            }
        )

        Divider(color = Color.Black.copy(alpha = 0.2f), thickness = 1.dp)
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(30.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}