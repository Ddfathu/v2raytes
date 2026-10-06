package com.v2ray.ang.ui.server

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ServerUsqueActivity : BaseServerActivity() {
    override val serverConfigType: EConfigType = EConfigType.USQUE

    @Composable
    override fun ScreenContent() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig ?: ProfileItem.create(serverConfigType)
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = "Cloudflare Zero Trust (Usque)",
            onSaveClick = {
                val cleanHost = uiState.usqueEndpoint.trim()
                    .replace("https://", "")
                    .replace("http://", "")
                    .split("/")[0]
                    .split(":")[0]
                uiState.address = if (uiState.sni.isNotBlank()) uiState.sni.trim() else cleanHost
                if (uiState.port.isBlank()) {
                    uiState.port = "443"
                }
                saveServer(uiState)
            }
        ) {
            // Tombol Login Browser
            Button(
                onClick = {
                    val rawEndpoint = uiState.usqueEndpoint.trim().ifBlank { "ddfathu.cloudflareaccess.com" }
                    val cleanHost = rawEndpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
                    val loginUrl = "https://$cleanHost/warp"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
                    startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("1. Login Cloudflare Access di Browser")
            }

            Text(
                text = "Klik tombol di atas untuk login. Jika redirect otomatis tidak jalan, salin token JWT dari browser lalu tempel ke kolom di bawah.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            FormTextField(
                label = "Remarks / Nama Profil",
                value = uiState.remarks,
                onValueChange = { uiState.remarks = it }
            )

            FormTextField(
                label = "Access Team Endpoint (Contoh: ddfathu.cloudflareaccess.com)",
                value = uiState.usqueEndpoint,
                onValueChange = { uiState.usqueEndpoint = it }
            )

            FormTextField(
                label = "JWT Token (Paste manual token lengkap di sini)",
                value = uiState.usqueJwt,
                onValueChange = { uiState.usqueJwt = it },
                maxLines = 6
            )

            // Tombol Registrasi Manual dengan JWT
            Button(
                onClick = {
                    val rawJwt = uiState.usqueJwt.trim()
                    val rawEndpoint = uiState.usqueEndpoint.trim().ifBlank { "ddfathu.cloudflareaccess.com" }
                    val cleanHost = rawEndpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')

                    if (rawJwt.isBlank()) {
                        Toast.makeText(context, "JWT Token masih kosong!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    Toast.makeText(context, "Mendaftarkan perangkat ke Cloudflare...", Toast.LENGTH_SHORT).show()

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val usqueBin = File(context.applicationInfo.nativeLibraryDir, "libusque.so").absolutePath
                            val configFile = File(context.filesDir, "usque_config.json")
                            if (configFile.exists()) {
                                configFile.delete()
                            }

                            val regCmd = mutableListOf(
                                usqueBin,
                                "register",
                                "-c", configFile.absolutePath,
                                "--accept-tos"
                            )
                            if (cleanHost.isNotBlank()) {
                                regCmd.add("-e")
                                regCmd.add(cleanHost)
                            }
                            regCmd.add("--jwt")
                            regCmd.add(rawJwt)

                            val process = ProcessBuilder(regCmd)
                                .redirectErrorStream(true)
                                .start()

                            val output = process.inputStream.bufferedReader().readText()
                            val exitCode = process.waitFor()

                            withContext(Dispatchers.Main) {
                                if (exitCode == 0 && configFile.exists() && configFile.length() > 0) {
                                    Toast.makeText(context, "Registrasi Berhasil! Config tersimpan.", Toast.LENGTH_LONG).show()
                                } else {
                                    val errPreview = if (output.isNotBlank()) output.take(200) else "Exit code: $exitCode"
                                    Toast.makeText(context, "Gagal Daftar: $errPreview", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error register: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("2. Register with JWT (Daftar Manual)")
            }

            Spacer(modifier = Modifier.height(6.dp))

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
