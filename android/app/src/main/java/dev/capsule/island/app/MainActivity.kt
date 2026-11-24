package dev.capsule.island.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import dev.capsule.island.system.Hosts
import dev.capsule.island.ui.kit.Sf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CapsuleTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        Hosts.sync(this)
    }
}

private enum class Tab(val label: String, val glyph: Sf) {
    Stage("Stage", Sf.Play),
    Modules("Modules", Sf.CheckCircle),
    Geometry("Geometry", Sf.MirroringBold),
    System("System", Sf.Info),
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink.Background)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            Crossfade(Tab.entries[tab], label = "tab") { t ->
                when (t) {
                    Tab.Stage -> StageScreen()
                    Tab.Modules -> ModulesScreen()
                    Tab.Geometry -> GeometryScreen()
                    Tab.System -> SystemScreen()
                }
            }
        }
        NavigationBar(containerColor = Ink.Background, tonalElevation = 0.dp) {
            Tab.entries.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(painterResource(t.glyph.res), t.label, Modifier.padding(2.dp)) },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Ink.Primary,
                        selectedTextColor = Ink.Primary,
                        unselectedIconColor = Ink.Tertiary,
                        unselectedTextColor = Ink.Tertiary,
                        indicatorColor = Ink.Raised,
                    ),
                )
            }
        }
    }
}
