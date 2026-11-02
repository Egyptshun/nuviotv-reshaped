package com.nuvio.tv.reshaped.doh

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DnsProvider(val storageId: String, val displayName: String) {
    SYSTEM("system", "System"),
    CLOUDFLARE("cloudflare", "Cloudflare"),
    GOOGLE("google", "Google"),
    ADGUARD("adguard", "AdGuard"),
    QUAD9("quad9", "Quad9"),
    ;

    companion object {
        fun fromStorage(id: String?): DnsProvider = entries.firstOrNull { it.storageId == id } ?: SYSTEM
    }
}

object DnsSettings {
    private const val preferencesName = "nuvio_network"
    private const val providerKey = "dns_provider"

    private val _provider = MutableStateFlow(DnsProvider.SYSTEM)
    val provider: StateFlow<DnsProvider> = _provider.asStateFlow()
    private var preferences: android.content.SharedPreferences? = null
    private var loaded = false

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        ensureLoaded()
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val stored = preferences?.getString(providerKey, null)
        val resolved = DnsProvider.fromStorage(stored)
        _provider.value = resolved
        NuvioDns.provider = resolved
    }

    fun setProvider(provider: DnsProvider) {
        ensureLoaded()
        if (_provider.value == provider) return
        _provider.value = provider
        preferences?.edit()?.putString(providerKey, provider.storageId)?.apply()
        NuvioDns.provider = provider
    }
}
