package com.v2ray.ang.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

class ServerUsqueActivity : BaseServerActivity() {
    override val serverConfigType: EConfigType = EConfigType.USQUE

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = "Cloudflare Zero Trust (Usque)",
            onSaveClick = { saveServer(uiState) }
        ) {
            FormTextField(
                label = "Remarks / Name",
                value = uiState.remarks,
                onValueChange = { uiState.remarks = it }
            )
            FormTextField(
                label = "Access Team Endpoint",
                value = uiState.usqueEndpoint,
                onValueChange = { uiState.usqueEndpoint = it }
            )
            FormTextField(
                label = "JWT Token",
                value = uiState.usqueJwt,
                onValueChange = { uiState.usqueJwt = it },
                maxLines = 6
            )
            FormTextField(
                label = "SNI Bug (Optional)",
                value = uiState.sni,
                onValueChange = { uiState.sni = it }
            )
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
                Text(
                    text = "Use HTTP/2 (TCP)",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
