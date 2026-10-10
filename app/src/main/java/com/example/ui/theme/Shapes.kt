package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Recovo shape scale.
//
// A restrained rounded language derived from the values already used across the
// app (8 / 12 / 16 / 20 / 24 dp). These feed MaterialTheme.shapes so components
// can inherit the scale instead of each call re-declaring a RoundedCornerShape.
val RecovoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
