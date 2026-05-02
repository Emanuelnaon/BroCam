package com.example.brocam.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Aquí vivirá tu icono personalizado, aislado y sin errores
val ManoLapizIcon: ImageVector
   get() = ImageVector.Builder(
      name = "ManoLapiz",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
   ).apply {
      path(fill = SolidColor(Color.White)) {
         // Punta del lápiz
         moveTo(3f, 17.25f)
         lineTo(3f, 21f)
         lineTo(6.75f, 21f)
         lineTo(17.81f, 9.94f)
         lineTo(14.06f, 6.19f)
         lineTo(3f, 17.25f)
         close()
         // Cuerpo del lápiz / mano
         moveTo(20.71f, 7.04f)
         curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
         lineTo(18.37f, 3.29f)
         curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
         lineTo(15.13f, 5.12f)
         lineTo(18.88f, 8.87f)
         lineTo(20.71f, 7.04f)
         close()
         // Trazo tipo "garabato" debajo
         moveTo(2f, 22f)
         lineTo(22f, 22f)
         lineTo(22f, 24f)
         lineTo(2f, 24f)
         close()
      }
   }.build()