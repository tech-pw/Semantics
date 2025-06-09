package io.github.farhazulmullick.sementics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Widget() {
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier
        .fillMaxWidth()
        .background(color = Color.Green)
        .height(56.dp)
    ) {}
}