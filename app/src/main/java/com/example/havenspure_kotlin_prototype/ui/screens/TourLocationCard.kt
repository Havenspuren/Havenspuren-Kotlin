// File: TourLocationCard.kt
// This is a new component file

package com.example.havenspure_kotlin_prototype.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havenspure_kotlin_prototype.data.model.Location

@Composable
fun TourLocationCard(
    location: Location,
    isVisited: Boolean,
    isActive: Boolean,
    distanceText: String?,
    onNavigateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFE8F5E9) else Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location order number with circle
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isVisited) Color(0xFF4CAF50) else Color.LightGray
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${location.order}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Location name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    if (distanceText != null) {
                        Text(
                            text = distanceText,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Visited indicator
                if (isVisited) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Besucht",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isActive && !isVisited) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNavigateClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF03A9F4)
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigation starten")
                }
            }
        }
    }
}