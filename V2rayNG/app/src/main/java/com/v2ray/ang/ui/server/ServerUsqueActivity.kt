package com.v2ray.ang.ui.server

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

class ServerUsqueActivity : BaseServerActivity() {
    override val serverConfigType: EConfigType = EConfigType.USQUE

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig ?: ProfileItem.create(serverConfigType)
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = "Cloudflare Zero Trust (Usque)",
            onSaveClick = { saveServer(uiState) }
        ) {
            // Tombol Login Browser Otomatis
            Button(
                onClick = {
                    val endpoint = if (uiState.usqueEndpoint.isNotBlank()) {
                        uiState.usqueEndpoint.trim()
                    } else {
                        "https://dfathu.cloudflareaccess.com"
                    }
                    val loginUrl = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                        "$endpoint/warp"
                    } else {
                        "https://$endpoint/warp"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
                    startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Login Cloudflare Access di Browser")
            }

            Text(
                text = "Klik tombol di atas untuk login. Setelah selesai di browser, klik 'Open in Cloudflare One Client' dan token akan otomatis tersimpan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormTextField(
                label = "Remarks / Nama Profil",
                value = uiState.remarks,
                onValueChange = { uiState.remarks = it }
            )

            FormTextField(
                label = "Access Team Endpoint",
                value = uiState.usqueEndpoint,
                onValueChange = { uiState.usqueEndpoint = it }
            )

            FormTextField(
                label = "Connect Port (Default: 443)",
                value = uiState.port,
                onValueChange = { uiState.port = it }
            )

            FormTextField(
                label = "SNI Bug (Contoh: cdn.whatsapp.com)",
                value = uiState.sni,
                onValueChange = { uiState.sni = it }
            )

            FormTextField(
                label = "JWT Token (Otomatis dari Browser)",
                value = uiState.usqueJwt,
                onValueChange = { uiState.usqueJwt = it },
                maxLines = 4
            )

            // Switch HTTP/2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uiState.usqueUseH2 = !uiState.usqueUseH2 }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.usqueUseH2,
                    onCheckedChange = { uiState.usqueUseH2 = it }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Gunakan HTTP/2 (TCP)")
                    Text(
                        "Default: HTTP/3 (QUIC/UDP). Centang ini jika operator memblokir UDP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
