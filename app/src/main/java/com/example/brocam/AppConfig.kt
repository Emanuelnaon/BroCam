package com.example.brocam

// Define si somos Lente o Control (Roles de red)
enum class AppRole { LENTE, CONTROL }

// Define si somos Estándar o Viajero (Modo de uso)
enum class UserProfile { STANDARD, SOLO_TRAVELER }

// Define qué pantalla estamos viendo
enum class ScreenState { WELCOME, CAMERA, CONTROL }