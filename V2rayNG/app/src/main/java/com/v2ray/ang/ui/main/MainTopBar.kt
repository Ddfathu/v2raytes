package com.v2ray.ang.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.verticalScrollbar

private val TopBarBgDark  = Color(0xFF160B24)
private val ContentWhite  = Color(0xFFFFFFFF)
private val MenuPillColor = Color(0xFF2C1647)
private val MenuBorder    = Color(0xFF4A2574)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                }
            }
        }
    }

    val context = LocalContext.current
    var isAutoPingOn by remember { mutableStateOf(MmkvManager.decodeSettingsBool(MmkvManager.KEY_PREF_AUTO_PING_ENABLED, false)) }
    var showMenu by remember { mutableStateOf(false) }
    val moreMenuScrollState = rememberScrollState()
    val maxMenuHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.7f)

    TopAppBar(
        title = { },
        navigationIcon = {
            Surface(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onMenuClick() },
                color = MenuPillColor,
                border = BorderStroke(1.dp, MenuBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_24dp),
                        contentDescription = "Menu",
                        tint = ContentWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MENU",
                        color = ContentWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TopBarBgDark,
            titleContentColor = ContentWhite,
            navigationIconContentColor = ContentWhite,
            actionIconContentColor = ContentWhite
        ),
        actions = {
            // Tombol Quick-Import / Paste Clipboard
            Surface(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onAction(MainAction.ImportClipboard)
                    },
                color = Color(0xFF7B1FA2),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "PASTE",
                    color = ContentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // Tombol Toggle Auto-Ping
            Surface(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val newState = !isAutoPingOn
                        isAutoPingOn = newState
                        MmkvManager.encodeSettings(MmkvManager.KEY_PREF_AUTO_PING_ENABLED, newState)
                        val msg = if (newState) "Auto-Ping Aktif" else "Auto-Ping Mati"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                color = if (isAutoPingOn) Color(0xFF2E7D32) else Color(0xFF332047),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isAutoPingOn) "PING ON" else "PING OFF",
                    color = ContentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // Tombol Search
            IconButton(onClick = { onSearchToggle(true) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_24dp),
                    contentDescription = stringResource(R.string.acc_search),
                    tint = ContentWhite
                )
            }

            // More Menu
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.acc_more),
                        tint = ContentWhite
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    scrollState = moreMenuScrollState,
                    containerColor = Color.White,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(moreMenuScrollState)
                ) {
                    MoreMenuContent { action ->
                        showMenu = false
                        onMoreMenuAction(action)
                    }
                }
            }
        }
    )
}
