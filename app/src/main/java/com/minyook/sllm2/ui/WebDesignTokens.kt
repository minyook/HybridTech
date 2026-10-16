// Generated from web/src/style.css and ui.tsx. Do not edit by hand.
package com.minyook.sllm2.ui

import androidx.compose.ui.graphics.Color
import com.minyook.sllm2.R

internal object WorkspaceDesign {
    val Blue = Color(0xFF3182F6)
    val BlueWeak = Color(0xFFEAF3FF)
    val Ink = Color(0xFF191F28)
    val Muted = Color(0xFF6B7684)
    val Border = Color(0xFFE8EDF3)
    val Green = Color(0xFF178265)
    val Canvas = Color(0xFFF7F9FC)
    const val SidebarWidth = 242
    const val TopbarHeight = 76
    const val CardRadius = 20
    const val CardPadding = 25
    const val SidebarBreakpoint = 850
}

internal enum class WorkspaceSymbol(val resource: Int) {
    HOME(R.drawable.workspace_icon_home),
    CHAT(R.drawable.workspace_icon_chat),
    BOOK(R.drawable.workspace_icon_book),
    CHECK(R.drawable.workspace_icon_check),
    SETTINGS(R.drawable.workspace_icon_settings),
    HISTORY(R.drawable.workspace_icon_history),
    ARROW(R.drawable.workspace_icon_arrow),
    CHEVRON(R.drawable.workspace_icon_chevron),
    SHIELD(R.drawable.workspace_icon_shield),
    DOWNLOAD(R.drawable.workspace_icon_download),
    CPU(R.drawable.workspace_icon_cpu),
    SEARCH(R.drawable.workspace_icon_search),
    INFO(R.drawable.workspace_icon_info),
    PLUS(R.drawable.workspace_icon_plus),
    TRASH(R.drawable.workspace_icon_trash),
    CLOSE(R.drawable.workspace_icon_close),
    WIFI(R.drawable.workspace_icon_wifi),
}
