package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            parseUri(it, null)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    val uri: Uri? = intent.data
                    if (uri?.scheme == "com.cloudflare.warp") {
                        handleCloudflareWarpUri(uri)
                    } else {
                        when (uri?.host) {
                            "install-config" -> {
                                val shareUrl = uri.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri.fragment)
                            }

                            "install-sub" -> {
                                val shareUrl = uri.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri.fragment)
                            }

                            else -> {
                                toastError(R.string.toast_failure)
                            }
                        }
                    }
                }
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun handleCloudflareWarpUri(uri: Uri) {
        // Ambil string asli langsung dari Intent, jangan percaya uri.toString()
        val rawStr = intent?.dataString ?: uri.toString()
        val token = if (rawStr.contains("token=")) {
            rawStr.substringAfter("token=")
                  .substringBefore("&")
                  .substringBefore("#")
                  .substringBefore(""")
                  .substringBefore("'")
                  .trim()
        } else {
            uri.getQueryParameter("token").orEmpty().trim()
        }
        val host = uri.host.orEmpty().ifEmpty {
            rawStr.substringAfter("://").substringBefore("/").substringBefore("?")
        }
        val endpoint = if (host.isNotEmpty()) "https://$host" else ""

        if (token.isEmpty()) {
            Toast.makeText(this, "JWT Token kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val allGuids = MmkvManager.decodeServerList("")
            var targetGuid: String? = null
            var targetProfile: ProfileItem? = null

            for (guid in allGuids) {
                val profile = MmkvManager.decodeServerConfig(guid)
                if (profile?.configType == EConfigType.USQUE) {
                    val sameEndpoint = endpoint.isNotEmpty() && profile.usqueEndpoint == endpoint
                    val sameHost = profile.server == host
                    if (sameEndpoint || sameHost) {
                        targetGuid = guid
                        targetProfile = profile
                        break
                    }
                }
            }

            val profileToSave = targetProfile ?: ProfileItem.create(EConfigType.USQUE)
            profileToSave.remarks = if (targetProfile != null && !targetProfile.remarks.isNullOrEmpty()) {
                targetProfile.remarks
            } else {
                "Cloudflare Access ($host)"
            }
            profileToSave.usqueJwt = token
            if (endpoint.isNotEmpty()) {
                profileToSave.usqueEndpoint = endpoint
            }
            if (profileToSave.server.isNullOrEmpty()) {
                profileToSave.server = host
            }
            if (profileToSave.serverPort.isNullOrEmpty()) {
                profileToSave.serverPort = "443"
            }

            profileToSave.description = AngConfigManager.generateDescription(profileToSave)
            val savedGuid = MmkvManager.encodeServerConfig(targetGuid ?: "", profileToSave)
            if (targetGuid == null) {
                allGuids.add(0, savedGuid)
                MmkvManager.encodeServerList(allGuids, "")
            }
            MmkvManager.setSelectServer(savedGuid)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@UrlSchemeActivity,
                    "Berhasil import token JWT Usque!",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(this@UrlSchemeActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            LogUtil.i(AppConfig.TAG, decodedUrl)
            lifecycleScope.launch(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
                withContext(Dispatchers.Main) {
                    if (count + countSub > 0) {
                        toast(R.string.import_subscription_success)
                    } else {
                        toast(R.string.import_subscription_failure)
                    }
                }
            }
        }
    }
}
