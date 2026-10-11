package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object LiveSession : Screen("live_session/{sessionId}") {
        fun createRoute(sessionId: String) = "live_session/$sessionId"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object TermuxGuide : Screen("termux_guide")
}
