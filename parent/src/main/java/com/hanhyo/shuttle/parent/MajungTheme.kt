package com.hanhyo.shuttle.parent

import coil.compose.AsyncImage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma 마중 노트 tokens. Functional copy stays in Plex; Gaegu is for short headings. */
object Majung {
    val App = Color(0xFFF7F8FA)
    val Card = Color.White
    val Control = Color(0xFFF0F1F3)
    val Capsule = Color(0xFFE3E6E9)
    val Text = Color(0xFF252A32)
    val Secondary = Color(0xFF626A73)
    val Muted = Color(0xFF858C94)
    val Border = Color(0xFFD6DADF)
    val Yellow = Color(0xFFFFCC00)
    val Pale = Color(0xFFFFF6D2)
    val Error = Color(0xFF9A352F)
    val Plex = FontFamily(Font(R.font.plex_regular), Font(R.font.plex_semibold, FontWeight.SemiBold), Font(R.font.plex_bold, FontWeight.Bold))
    val Hand = FontFamily(Font(R.font.gaegu_bold, FontWeight.Bold))
}

@Composable fun MajungTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily=Majung.Plex, color=Majung.Text)
    MaterialTheme(colorScheme=lightColorScheme(primary=Majung.Yellow, onPrimary=Majung.Text,
        secondary=Majung.Text, background=Majung.App, surface=Majung.Card, onSurface=Majung.Text,
        onBackground=Majung.Text, outline=Majung.Border, error=Majung.Error, surfaceTint=Majung.Card), typography=Typography(
        bodyLarge=base.copy(fontSize=16.sp,lineHeight=24.sp), bodyMedium=base.copy(fontSize=14.sp,lineHeight=20.sp),
        bodySmall=base.copy(fontSize=12.sp,lineHeight=18.sp), titleLarge=base.copy(fontSize=24.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold),
        titleMedium=base.copy(fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold),
        titleSmall=base.copy(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold),
        labelLarge=base.copy(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold),
        labelMedium=base.copy(fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)), content=content)
}

/** Exact exported Figma artwork. SVG viewport and PNG aspect ratio are preserved. */
@Composable fun DesignAsset(file: String, description: String?, modifier: Modifier=Modifier) {
    AsyncImage(model="file:///android_asset/design/$file",contentDescription=description,
        modifier=modifier,contentScale=ContentScale.Fit)
}

@Composable fun NoteTitle(text: String) {
    Box(Modifier.height(44.dp)) {
        DesignAsset("eb4b3.svg",null,Modifier.align(Alignment.BottomStart).padding(bottom=5.dp).width(124.dp).height(7.dp))
        Text(text,fontFamily=Majung.Hand,fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=36.sp)
    }
}

@Composable fun NoteButton(text: String, enabled: Boolean=true, modifier: Modifier=Modifier, secondary: Boolean=false, action: ()->Unit) {
    Button(onClick=action,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp),
        border=if(secondary) androidx.compose.foundation.BorderStroke(1.dp,Majung.Border) else null,
        colors=ButtonDefaults.buttonColors(containerColor=if(secondary) Majung.Card else Majung.Yellow,contentColor=Majung.Text,
            disabledContainerColor=Majung.Capsule,disabledContentColor=Majung.Secondary)) { Text(text) }
}

@Composable fun NoteRow(title: String, subtitle: String, modifier: Modifier=Modifier, action: ()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Majung.Card) {
        Row(Modifier.clickable(onClick=action).heightIn(min=64.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
            }
            Spacer(Modifier.width(12.dp))
            DesignAsset("2e5ee.svg",null,Modifier.size(18.dp))
        }
    }
}

@Composable fun WaitingArt() {
    Box(Modifier.size(128.dp,96.dp)) {
        DesignAsset("cee5a.svg",null,Modifier.offset(25.6.dp,10.67.dp).size(68.267.dp))
        DesignAsset("a4ef5.png",null,Modifier.offset(32.dp,16.53.dp).size(55.467.dp))
        Box(Modifier.offset(77.dp,59.dp).size(27.dp).background(Color.White,RoundedCornerShape(50))) {
            DesignAsset("1eb5a.png",null,Modifier.padding(3.dp).fillMaxSize())
        }
        DesignAsset("6068d.svg",null,Modifier.offset(24.dp,86.dp).size(80.dp,4.dp))
    }
}
