package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.verticalScrollbar

// Palet Tema Ungu & ID Card KTP
private val PurpleBackground = Color(0xFF1E0338) // Background samping gelap
private val PurpleIdCardStart = Color(0xFF4A148C) // Gradien KTP 1
private val PurpleIdCardEnd = Color(0xFF6A1B9A) // Gradien KTP 2
private val GoldChipColor = Color(0xFFFFD54F) // Warna chip KTP
private val PurpleCardItem = Color(0xFF32085A) // Kartu menu
private val PurpleIconTint = Color(0xFFE1BEE7) // Warna icon menu
private val TextWhiteColor = Color(0xFFFFFFFF)
private val TextMutedWhite = Color(0xFFD1C4E9)

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

private val primaryDrawerItems = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings
)

private val secondaryDrawerItems = listOf(
    MainDestination.Promotion,
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About
)

@Composable
fun MainDrawerContent(drawerState: DrawerState, onNavigate: (MainDestination) -> Unit) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.85f),
        drawerContainerColor = PurpleBackground
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
                .padding(bottom = 28.dp)
        ) {
            // === HEADER GAYA KTP / IDENTITY CARD (TANPA LOGO V) ===
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .border(1.dp, Color(0xFF8E24AA), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PurpleIdCardStart, PurpleIdCardEnd)
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Baris Header KTP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "KARTU TANDA PENGGUNA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = GoldChipColor
                            )
                            // Chip SIM / KTP Elektronik
                            Box(
                                modifier = Modifier
                                    .size(width = 30.dp, height = 22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldChipColor)
                                    .border(0.8.dp, Color(0xFFB78103), RoundedCornerShape(4.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Nama Identitas Utama
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextWhiteColor
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // NIK / Serial Unik Card
                        Text(
                            text = "NIK : 3201-V2RAY-CLIENT-2026",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextMutedWhite
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        HorizontalDivider(thickness = 0.8.dp, color = Color(0x55FFFFFF))

                        Spacer(modifier = Modifier.height(8.dp))

                        // Informasi Footer Kartu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS: BERLAKU SEUMUR HIDUP",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMutedWhite
                            )
                            Text(
                                text = "PRO EDITION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = GoldChipColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Menu Kotak Grid 2 Kolom Bagian Utama
            MenuCardGrid(items = primaryDrawerItems, onNavigate = onNavigate)

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = PurpleCardItem
            )

            // Menu Kotak Grid 2 Kolom Bagian Kedua
            MenuCardGrid(items = secondaryDrawerItems, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun MenuCardGrid(
    items: List<MainDestination>,
    onNavigate: (MainDestination) -> Unit
) {
    items.chunked(2).forEach { rowPair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowPair.forEach { item ->
                ElevatedCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(86.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigate(item) },
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = PurpleCardItem)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = PurpleIconTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = TextWhiteColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (rowPair.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
