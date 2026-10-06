package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVHandler
import com.tencent.mmkv.MMKVLogLevel
import com.tencent.mmkv.MMKVRecoverStrategic
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.AppConfig.TAG
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isGroupType
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

internal class ProfileStorageException(message: String) : IllegalStateException(message)

object MmkvManager {
    const val KEY_PREF_FAKE_DNS_FILTER = "pref_fake_dns_filter"
    const val KEY_PREF_AUTO_PING_ENABLED = "pref_auto_ping_enabled"
    const val KEY_PREF_AUTO_PING_URL = "pref_auto_ping_url"
    const val KEY_PREF_AUTO_PING_INTERVAL = "pref_auto_ping_interval"

    //region private

    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"

    // KEY KHUSUS CUSTOM DOH & UDP RELAY (DEFAULT OFF)
    const val KEY_PREF_ENABLE_DOH = "pref_enable_custom_doh"
    const val KEY_PREF_CUSTOM_DOH_URL = "pref_custom_doh_url"
    const val KEY_PREF_ENABLE_CHAIN_RELAY = "pref_enable_chain_relay"
    const val KEY_PREF_CHAIN_RELAY_SERVER = "pref_chain_relay_server"
    const val KEY_PREF_CHAIN_RELAY_UUID = "pref_chain_relay_uuid"
    const val KEY_PREF_CHAIN_RELAY_PATH = "pref_chain_relay_path"

    private val recoveryHandler = object : MMKVHandler {
        override fun onMMKVCRCCheckFail(mmapID: String) =
            recoverFromStorageError(mmapID, "CRC check")

        override fun onMMKVFileLengthError(mmapID: String) =
            recoverFromStorageError(mmapID, "file length check")

        override fun wantLogRedirecting(): Boolean = false

        override fun mmkvLog(
            level: MMKVLogLevel,
            file: String,
            line: Int,
            function: String,
            message: String
        ) = Unit
    }

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    private inline fun <T> withProfileIndexLock(block: () -> T): T {
        return synchronized(mainStorage) {
            mainStorage.lock()
            try {
                block()
            } finally {
                mainStorage.unlock()
            }
        }
    }

    private fun removeProfilePayloads(guids: Collection<String>) {
        if (guids.isEmpty()) return
        val keys = guids.toTypedArray()
        profileFullStorage.removeValuesForKeys(keys)
        serverAffStorage.removeValuesForKeys(keys)
        serverRawStorage.removeValuesForKeys(keys)
    }

    private fun requireStorageWrite(success: Boolean, message: String) {
        if (!success) throw ProfileStorageException(message)
    }

    private fun persistServerList(serverList: List<String>, subscriptionId: String): Boolean {
        return mainStorage.encode(serverListKey(subscriptionId), JsonUtil.toJson(serverList))
    }

    private fun serverListKey(subscriptionId: String): String {
        return "$KEY_SUB_SERVER_PREFIX${getSubscriptionId(subscriptionId)}"
    }

    private fun decodeServersReferencedByOtherGroups(subscriptionId: String): Set<String>? {
        val targetKey = serverListKey(subscriptionId)
        val keys = mainStorage.allKeys() ?: return null
        if (targetKey !in keys) return null

        val referencedServers = mutableSetOf<String>()
        for (key in keys) {
            if (!key.startsWith(KEY_SUB_SERVER_PREFIX) || key == targetKey) continue

            val json = mainStorage.decodeString(key)
            if (json.isNullOrBlank()) return null
            val serverIds = JsonUtil.fromJsonSafe(json, Array<String>::class.java) ?: return null
            referencedServers.addAll(serverIds)
        }
        return referencedServers
    }

    //endregion

    fun initialize(context: Context) {
        val logLevel = if (BuildConfig.DEBUG) {
            MMKVLogLevel.LevelDebug
        } else {
            MMKVLogLevel.LevelInfo
        }
        MMKV.initialize(
            context,
            context.filesDir.resolve("mmkv").absolutePath,
            null,
            logLevel,
            recoveryHandler
        )
    }

    private fun recoverFromStorageError(mmapID: String, error: String): MMKVRecoverStrategic {
        Log.e(TAG, "MMKV $error failed for $mmapID; attempting data recovery")
        return MMKVRecoverStrategic.OnErrorRecover
    }

    //region Server

    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }

    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    fun setSelectServer(guid: String) {
        withProfileIndexLock {
            mainStorage.encode(KEY_SELECTED_SERVER, guid)
        }
    }

    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String) {
        withProfileIndexLock {
            persistServerList(serverList, subscriptionId)
        }
    }

    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val json = mainStorage.decodeString(serverListKey(subscriptionId))
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()

        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }

        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }

        return allServers
    }

    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }

    fun encodeServerConfig(guid: String, config: ProfileItem): String {
        val key = guid.ifBlank { Utils.getUuid() }
        withProfileIndexLock {
            requireStorageWrite(
                profileFullStorage.encode(key, JsonUtil.toJson(config)),
                "Failed to save profile payload",
            )

            val subId = getSubscriptionId(config.subscriptionId)
            val serverList = decodeServerList(subId)

            if (!serverList.contains(key)) {
                serverList.add(0, key)
                requireStorageWrite(
                    persistServerList(serverList, subId),
                    "Failed to publish profile index",
                )
                if (getSelectServer().isNullOrBlank()) {
                    requireStorageWrite(
                        mainStorage.encode(KEY_SELECTED_SERVER, key),
                        "Failed to update selected profile",
                    )
                }
            }
        }

        return key
    }

    internal fun saveServerProfiles(
        profiles: Map<String, ProfileItem>,
        rawConfigs: Map<String, String>,
        subscriptionId: String,
        append: Boolean,
    ) {
        if (profiles.isEmpty()) return

        withProfileIndexLock {
            val replacedServers = if (append) {
                emptyList()
            } else {
                decodeServerList(subscriptionId).toList()
            }
            val previousSelection = getSelectServer()
            val selectedProfile = if (!append &&
                previousSelection != null &&
                previousSelection in replacedServers
            ) {
                decodeServerConfig(previousSelection)
            } else {
                null
            }
            val replacementSelection = ProfileReplacement.findSelectedReplacement(
                profiles = profiles,
                currentSelection = previousSelection,
                selectedProfile = selectedProfile,
            )

            profiles.forEach { (guid, profile) ->
                requireStorageWrite(
                    profileFullStorage.encode(guid, JsonUtil.toJson(profile)),
                    "Failed to save profile payload",
                )
                rawConfigs[guid]?.let { raw ->
                    requireStorageWrite(
                        serverRawStorage.encode(guid, raw),
                        "Failed to save raw profile payload",
                    )
                }
            }

            val serverList = if (append) {
                decodeServerList(subscriptionId)
            } else {
                replacedServers.filter { guid ->
                    decodeServerConfig(guid)?.configType?.isGroupType() == true
                }.toMutableList()
            }
            val indexedServers = serverList.toHashSet()
            profiles.keys.forEach { guid ->
                if (indexedServers.add(guid)) {
                    serverList.add(0, guid)
                }
            }
            requireStorageWrite(
                persistServerList(serverList, subscriptionId),
                "Failed to publish profile index",
            )
            replacementSelection?.let { guid ->
                requireStorageWrite(
                    mainStorage.encode(KEY_SELECTED_SERVER, guid),
                    "Failed to update selected profile",
                )
            }
            if (replacedServers.isEmpty()) return@withProfileIndexLock

            val protectedServer = replacementSelection ?: previousSelection
            val referencedByOtherGroups = decodeServersReferencedByOtherGroups(subscriptionId)
            val removablePayloads = ProfileReplacement.findRemovablePayloads(
                replacedServers = replacedServers,
                replacementServers = serverList.toSet(),
                protectedServer = protectedServer,
                serversReferencedByOtherGroups = referencedByOtherGroups,
            )
            removeProfilePayloads(removablePayloads)
        }
    }

    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }

        val config = decodeServerConfig(guid)
        val subId = getSubscriptionId(config?.subscriptionId)

        val serverList = decodeServerList(subId)
        serverList.remove(guid)
        encodeServerList(serverList, subId)

        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        profileFullStorage.remove(guid)
        serverAffStorage.remove(guid)
    }

    fun removeServerViaSubid(subscriptionId: String?) {
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)

        serverList.forEach { guid ->
            if (getSelectServer() == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
        }

        serverList.clear()
        encodeServerList(serverList, subId)
    }

    fun removeServers(guids: List<String>, subscriptionId: String) {
        if (guids.isEmpty()) return
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)
        if (serverList.removeAll(guids)) {
            encodeServerList(serverList, subId)
        }

        val selectedServer = getSelectServer()
        guids.forEach { guid ->
            if (selectedServer == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
            serverRawStorage.remove(guid)
        }
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    fun removeAllServer(): Int {
        val count = profileFullStorage.allKeys()?.count() ?: 0
        profileFullStorage.clearAll()
        serverAffStorage.clearAll()
        serverRawStorage.clearAll()

        decodeSubscriptions().forEach { sub ->
            encodeServerList(mutableListOf(), sub.guid)
        }
        return count
    }

    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                    count++
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                        count++
                    }
                }
            }
        }
        return count
    }

    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }

    internal fun removeOrphanedServerProfiles(): Int? = synchronized(mainStorage) {
        mainStorage.lock()
        try {
            val indexedServersBySubscription = mainStorage.allKeys().orEmpty()
                .asSequence()
                .filter { key -> key.startsWith(KEY_SUB_SERVER_PREFIX) }
                .associate { key ->
                    val subscriptionId = key.removePrefix(KEY_SUB_SERVER_PREFIX)
                    val json = mainStorage.decodeString(key)
                    val serverIds = if (json.isNullOrBlank()) {
                        null
                    } else {
                        JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toSet()
                    }
                    subscriptionId to serverIds
                }

            val profiles = profileFullStorage.allKeys().orEmpty().map { guid ->
                StoredProfileReference(
                    guid = guid,
                    subscriptionId = decodeServerConfig(guid)?.subscriptionId,
                )
            }
            val orphans = OrphanProfileCleaner.findOrphans(
                profiles = profiles,
                indexedServersBySubscription = indexedServersBySubscription,
                selectedServer = getSelectServer(),
            ) ?: return@synchronized null

            if (orphans.isNotEmpty()) {
                val keys = orphans.toTypedArray()
                profileFullStorage.removeValuesForKeys(keys)
                serverAffStorage.removeValuesForKeys(keys)
                serverRawStorage.removeValuesForKeys(keys)
            }
            orphans.size
        } finally {
            mainStorage.unlock()
        }
    }

    //endregion

    //region Subscriptions

    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()

        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)

        removeServerViaSubid(subid)
    }

    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, JsonUtil.toJson(subItem))

        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, JsonUtil.toJson(subsList))
    }

    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.distinct()?.toMutableList() ?: mutableListOf()
        }
    }

    //endregion

    //region Asset

    fun decodeAssetUrls(): List<AssetUrlCache> {
        val assetUrlItems = mutableListOf<AssetUrlCache>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java) ?: AssetUrlItem()
                assetUrlItems.add(AssetUrlCache(key, item))
            }
        }
        return assetUrlItems.sortedBy { it.assetUrl.addedTime }
    }

    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, JsonUtil.toJson(assetItem))
    }

    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java)
    }

    //endregion

    //region Routing

    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)?.toMutableList() ?: mutableListOf()
    }

    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty())
            encodeSettings(PREF_ROUTING_RULESET, "")
        else
            encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(rulesetList))
    }

    //endregion

    //region settings

    fun encodeSettings(key: String, value: String?): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Int): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Long): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Float): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Boolean): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: MutableSet<String>): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun decodeSettingsString(key: String): String? {
        return settingsStorage.decodeString(key)
    }

    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        return settingsStorage.decodeString(key, defaultValue)
    }

    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        return settingsStorage.decodeInt(key, defaultValue)
    }

    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    fun decodeSettingsBool(key: String): Boolean {
        return settingsStorage.decodeBool(key, false)
    }

    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        return settingsStorage.decodeBool(key, defaultValue)
    }

    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }

    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }

    // --- HELPER UNTUK CUSTOM DOH & UNIVERSAL PROXY CHAIN (DEFAULT OFF) ---
    fun getFakeDnsFilter(): String {
        return decodeSettingsString(KEY_PREF_FAKE_DNS_FILTER, "") ?: ""
    }

        fun isAutoPingEnabled(): Boolean = decodeSettingsBool(KEY_PREF_AUTO_PING_ENABLED, false)
    fun getAutoPingUrl(): String = decodeSettingsString(KEY_PREF_AUTO_PING_URL, "http://www.google.com/generate_204") ?: "http://www.google.com/generate_204"
    fun getAutoPingInterval(): Long = decodeSettingsString(KEY_PREF_AUTO_PING_INTERVAL, "3")?.toLongOrNull()?.coerceAtLeast(1L) ?: 3L

    fun isCustomDohEnabled(): Boolean {
        return decodeSettingsBool(KEY_PREF_ENABLE_DOH, false)
    }

    fun getCustomDohUrl(): String {
        return decodeSettingsString(KEY_PREF_CUSTOM_DOH_URL, "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query"
    }

    fun isChainRelayEnabled(): Boolean {
        return decodeSettingsBool(KEY_PREF_ENABLE_CHAIN_RELAY, false)
    }

    fun getChainRelayServer(): String {
        return decodeSettingsString(KEY_PREF_CHAIN_RELAY_SERVER, "") ?: ""
    }

    fun getChainRelayUuid(): String {
        return decodeSettingsString(KEY_PREF_CHAIN_RELAY_UUID, "00000000-0000-0000-0000-000000000000") ?: "00000000-0000-0000-0000-000000000000"
    }

    fun getChainRelayPath(): String {
        return decodeSettingsString(KEY_PREF_CHAIN_RELAY_PATH, "/udprelay") ?: "/udprelay"
    }

    //endregion

    //region WebDAV

    fun encodeWebDavConfig(config: WebDavConfig): Boolean {
        return mainStorage.encode(KEY_WEBDAV_CONFIG, JsonUtil.toJson(config))
    }

    fun decodeWebDavConfig(): WebDavConfig? {
        val json = mainStorage.decodeString(KEY_WEBDAV_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, WebDavConfig::class.java)
    }

    //endregion

    //region Compose helpers for Settings

    @Composable
    fun rememberMmkvString(
        key: String,
        default: String = ""
    ): MutableState<String> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsString(key, default) ?: default)
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    @Composable
    fun rememberMmkvBool(
        key: String,
        default: Boolean = false
    ): MutableState<Boolean> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsBool(key, default))
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    //endregion
}
