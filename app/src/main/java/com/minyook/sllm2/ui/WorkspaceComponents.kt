package com.minyook.sllm2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val workspaceQuestions = listOf("산소 및 유해가스 측정방법 알려줘", "밀폐공간 작업허가 절차 알려줘", "심폐소생술과 산소측정법 알려줘")
@Composable internal fun workspaceWide() = LocalConfiguration.current.screenWidthDp > WorkspaceDesign.SidebarBreakpoint
@Composable internal fun workspaceMobile() = LocalConfiguration.current.screenWidthDp <= 640
@Composable internal fun workspacePadding() = if (workspaceMobile()) 20.dp else if (LocalConfiguration.current.screenWidthDp <= 1150) 28.dp else 42.dp

@Composable internal fun WorkspaceIcon(symbol: WorkspaceSymbol, modifier: Modifier = Modifier, color: Color = WorkspaceDesign.Muted) {
    Icon(painterResource(symbol.resource), contentDescription = null, tint = color, modifier = modifier.size(22.dp))
}
@Composable internal fun WorkspacePage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1260.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = workspacePadding(), vertical = if (workspaceMobile()) 26.dp else 38.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable internal fun WorkspaceHeading(eyebrow: String, title: String, description: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(eyebrow, color = WorkspaceDesign.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .88.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        if (description != null) Text(description, color = WorkspaceDesign.Muted, fontSize = 14.sp, lineHeight = 23.sp)
    }
}
@Composable internal fun WorkspaceCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(WorkspaceDesign.CardRadius.dp), border = BorderStroke(1.dp, WorkspaceDesign.Border)) {
        Column(Modifier.padding(if (workspaceMobile()) 20.dp else WorkspaceDesign.CardPadding.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable internal fun WorkspaceButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, icon: WorkspaceSymbol? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 19.dp, vertical = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (primary) WorkspaceDesign.Blue else Color(0xFFF0F3F7), contentColor = if (primary) Color.White else Color(0xFF4E5968), disabledContainerColor = Color(0xFFDEE7F4), disabledContentColor = Color(0xFF8B95A1))) {
        if (icon != null && icon != WorkspaceSymbol.ARROW) { WorkspaceIcon(icon, Modifier.size(18.dp), if (primary) Color.White else WorkspaceDesign.Muted); Spacer(Modifier.width(9.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (!enabled) Color(0xFF8B95A1) else if (primary) Color.White else Color(0xFF4E5968))
        if (icon == WorkspaceSymbol.ARROW) { Spacer(Modifier.width(9.dp));WorkspaceIcon(icon,Modifier.size(18.dp),if(primary)Color.White else WorkspaceDesign.Muted) }
    }
}
@Composable internal fun WorkspaceBadge(label: String, green: Boolean = false, blue: Boolean = false) {
    Surface(color = if (green) Color(0xFFEAF7F1) else if (blue) WorkspaceDesign.BlueWeak else Color(0xFFF0F3F7), shape = RoundedCornerShape(7.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (green) WorkspaceDesign.Green else if (blue) WorkspaceDesign.Blue else Color(0xFF7A8697), modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}
@Composable internal fun WorkspaceTile(symbol: WorkspaceSymbol, color: Color = WorkspaceDesign.Blue, background: Color = WorkspaceDesign.BlueWeak) {
    Surface(color = background, shape = RoundedCornerShape(13.dp)) { Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { WorkspaceIcon(symbol, color = color) } }
}
@Composable internal fun WorkspaceCardTitle(symbol: WorkspaceSymbol, title: String, description: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
        WorkspaceTile(symbol)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (description != null) Text(description, color = Color(0xFF8B95A1), fontSize = 11.sp, lineHeight = 18.sp)
        }
    }
}
@Composable internal fun WorkspaceNotice(text: String, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFFFFF5E6) else Color(0xFFF1F5FB), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            WorkspaceIcon(WorkspaceSymbol.INFO, Modifier.size(17.dp), if (warning) Color(0xFF976222) else Color(0xFF66748A))
            Text(text, fontSize = 12.sp, lineHeight = 20.sp, color = if (warning) Color(0xFF976222) else Color(0xFF66748A))
        }
    }
}
@Composable internal fun WorkspaceSearch(value: String, change: (String) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WorkspaceDesign.Border)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceIcon(WorkspaceSymbol.SEARCH)
            BasicTextField(value, change, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = WorkspaceDesign.Ink), modifier = Modifier.weight(1f).testTag("librarySearch"), decorationBox = { inner -> if (value.isEmpty()) Text("문서 안의 용어나 절차를 검색하세요", fontSize = 14.sp, color = Color(0xFFA4AEBC)); inner() })
            if (value.isNotEmpty()) IconButton(onClick = { change("") }) { WorkspaceIcon(WorkspaceSymbol.CLOSE) }
        }
    }
}
@Composable internal fun WorkspaceEmpty(symbol: WorkspaceSymbol, title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().heightIn(min = 310.dp).padding(vertical = 35.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Spacer(Modifier.height(24.dp)); WorkspaceIcon(symbol, Modifier.size(40.dp), Color(0xFFB3C1D5))
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(description, fontSize = 13.sp, lineHeight = 22.sp, color = Color(0xFF8B95A1), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable internal fun WorkspaceField(label:String,value:String,change:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF586577))
        Surface(color=Color(0xFFFAFBFD),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFE1E7EF))) {
            BasicTextField(value,change,singleLine=true,textStyle=TextStyle(fontSize=13.sp,color=WorkspaceDesign.Ink),modifier=modifier.fillMaxWidth().heightIn(min=46.dp).padding(13.dp),decorationBox={inner->if(value.isEmpty())Text(placeholder,fontSize=13.sp,color=Color(0xFFA4AEBC));inner()})
        }
    }
}
