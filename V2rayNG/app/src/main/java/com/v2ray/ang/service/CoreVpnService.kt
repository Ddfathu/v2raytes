package com.v2ray.ang.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Network
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.contracts.Tun2SocksControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootLanSharing
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("VpnServicePolicy")
class CoreVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null
    private val isStartingLock = AtomicBoolean(false)
    private var usqueProcess: Process? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service created")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onRevoke() {
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: Permission revoked")
        stopAllService()
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service destroyed")
        stopUsqueDaemon()

        if (isRunning) {
            try {
                if (::mInterface.isInitialized) {
                    mInterface.close()
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: VPN interface closed in onDestroy")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to close interface in onDestroy", e)
            }
        }
        unlockStart()
        NotificationManager.cancelNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        val isSystemVpnStart = intent == null || intent.action == SERVICE_INTERFACE
        if (isSystemVpnStart) {
            unlockStart()
        }
        if (!tryLockStart()) {
            LogUtil.w(AppConfig.TAG, "StartCore-VPN: Start already in progress")
            return START_NOT_STICKY
        }
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service command received, systemVpnStart=$isSystemVpnStart")
        if (!setupVpnService()) {
            unlockStart()
            stopSelf()
            return START_NOT_STICKY
        }
        startService()
        return START_STICKY
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        if (!::mInterface.isInitialized) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Interface not initialized")
            return
        }

        // Start usque sidecar daemon if active profile is USQUE
        startUsqueDaemonIfNeeded()

        if (!CoreServiceManager.startCoreLoop(mInterface)) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to start core loop")
            stopAllService()
            return
        }
        RootLanSharing.startClientSharing(this)
    }

    override fun stopService() {
        stopAllService(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    override fun setUnderlyingNetworks(networks: Array<Network>?): Boolean {
        return super<VpnService>.setUnderlyingNetworks(networks)
    }

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }

    private fun setupVpnService(): Boolean {
        val prepare = prepare(this)
        if (prepare != null) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Permission not granted")
            return false
        }

        if (configureVpnService() != true) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Configuration failed")
            return false
        }

        runTun2socks()
        return true
    }

    private fun configureVpnService(): Boolean {
        val builder = Builder()
        configureNetworkSettings(builder)
        configurePerAppProxy(builder)

        try {
            if (::mInterface.isInitialized) {
                mInterface.close()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Failed to close old interface", e)
        }

        configurePlatformFeatures(builder)
        try {
            mInterface = builder.establish()!!
            isRunning = true
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopAllService()
        }
        return false
    }

    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }
    }

    private fun configurePlatformFeatures(builder: Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    builder.addDisallowedApplication(it)
                } else {
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to configure app", e)
            }
        }
    }

    private fun runTun2socks() {
        if (SettingsManager.isUsingHevTun()) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = null
        }

        tun2SocksService?.startTun2Socks()
    }

    private fun startUsqueDaemonIfNeeded() {
        try {
            val mainProfile = MmkvManager.decodeServerConfig(MmkvManager.getSelectServer().orEmpty())
            if (mainProfile?.configType == EConfigType.USQUE) {
                val nativeDir = applicationInfo.nativeLibraryDir
                val usqueBin = "$nativeDir/libusque.so"

                if (!File(usqueBin).exists()) {
                    LogUtil.e(AppConfig.TAG, "Usque binary not found at $usqueBin")
                    return
                }

                val configFile = File(filesDir, "usque_config.json")
                val jwt = mainProfile.usqueJwt.orEmpty()
                val sni = mainProfile.sni

                // Bersihkan file jika ukurannya 0 byte
                if (configFile.exists() && configFile.length() == 0L) {
                    configFile.delete()
                }

                // Hanya register jika file config belum ada atau kosong
                val needRegister = !configFile.exists() || configFile.length() == 0L
                if (needRegister) {
                    LogUtil.i(AppConfig.TAG, "Mendaftarkan perangkat ke Cloudflare Zero Trust via usque register...")
                    val rawEndpoint = mainProfile.usqueEndpoint.orEmpty().ifBlank {
                        mainProfile.server.orEmpty()
                    }
                    val cleanHost = rawEndpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
                    val endpointUrl = if (cleanHost.isNotEmpty()) "https://$cleanHost" else ""

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
                    if (jwt.isNotBlank()) {
                        regCmd.add("--jwt")
                        regCmd.add(jwt)
                    }

                    try {
                        val regProcess = ProcessBuilder(regCmd).redirectErrorStream(true).start()
                        regProcess.waitFor()
                        LogUtil.i(AppConfig.TAG, "Registrasi usque selesai dengan exit code: ${regProcess.exitValue()}")
                        // Kosongkan token agar tidak register berulang kali
                        if (jwt.isNotBlank() && regProcess.exitValue() == 0) {
                            mainProfile.usqueJwt = ""
                            val currentGuid = com.v2ray.ang.handler.MmkvManager.getSelectServer() ?: ""
                            if (currentGuid.isNotEmpty()) {
                                com.v2ray.ang.handler.MmkvManager.encodeServerConfig(currentGuid, mainProfile)
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Gagal registrasi usque", e)
                    }
                }

                // Jalankan daemon SOCKS menggunakan config yang sudah terdaftar
                val rawEp = mainProfile.usqueEndpoint.orEmpty().ifBlank {
                    mainProfile.server.orEmpty()
                }
                val endpoint = rawEp.removePrefix("https://").removePrefix("http://").trimEnd('/')
                val cmd = mutableListOf(
                    usqueBin,
                    "socks",
                    "-b", AppConfig.LOOPBACK,
                    "-p", "20808",
                    "-c", configFile.absolutePath
                )
                if (endpoint.isNotBlank()) {
                    cmd.add("-e")
                    cmd.add(endpoint)
                }

                val port = mainProfile.serverPort?.toIntOrNull() ?: 443
                if (port != 443) {
                    cmd.add("-P")
                    cmd.add(port.toString())
                }

                if (mainProfile.usqueUseH2 == true) {
                    cmd.add("--http2")
                }

                if (!sni.isNullOrBlank()) {
                    cmd.add("-s")
                    cmd.add(sni)
                }

                LogUtil.i(AppConfig.TAG, "Starting Usque socks daemon...")
                usqueProcess = ProcessBuilder(cmd).start()
                // Sleep briefly to let local socks port bind
                Thread.sleep(300)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to start Usque daemon", e)
        }
    }

    private fun stopUsqueDaemon() {
        try {
            usqueProcess?.destroy()
            usqueProcess = null
            LogUtil.i(AppConfig.TAG, "Usque daemon stopped")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to stop Usque daemon", e)
        }
    }

    private fun stopAllService(isForced: Boolean = true) {
        stopUsqueDaemon()
        isRunning = false
        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        RootLanSharing.stopClientSharing(this)
        CoreServiceManager.stopCoreLoop()

        if (isForced) {
            stopSelf()
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                LogUtil.w(AppConfig.TAG, "StartCore-VPN: Sleep interrupted", e)
            }
            try {
                if (::mInterface.isInitialized) {
                    mInterface.close()
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: VPN interface closed")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to close interface", e)
            }
        }
    }

    fun tryLockStart(): Boolean {
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: tryLockStart: ${isStartingLock.get()}")
        return isStartingLock.compareAndSet(false, true)
    }

    fun unlockStart() {
        isStartingLock.set(false)
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: unlockStart")
    }
}
