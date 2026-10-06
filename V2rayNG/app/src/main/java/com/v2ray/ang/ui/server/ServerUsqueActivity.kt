package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormCheckBox
import com.v2ray.ang.ui.compose.FormTextField

class ServerUsqueActivity : BaseServerActivity() {
    override val serverConfigType: EConfigType = EConfigType.USQUE

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
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
                title = "Remarks / Name",
                value = uiState.remarks,
                onValueChange = { uiState.remarks = it }
            )
            FormTextField(
                title = "Access Team Endpoint",
                value = uiState.usqueEndpoint,
                onValueChange = { uiState.usqueEndpoint = it }
            )
            FormTextField(
                title = "JWT Token",
                value = uiState.usqueJwt,
                onValueChange = { uiState.usqueJwt = it },
                singleLine = false,
                maxLines = 6
            )
            FormTextField(
                title = "SNI Bug (Optional)",
                value = uiState.sni,
                onValueChange = { uiState.sni = it }
            )
            FormCheckBox(
                title = "Use HTTP/2 (TCP)",
                checked = uiState.usqueUseH2,
                onCheckedChange = { uiState.usqueUseH2 = it }
            )
        }
    }
}
