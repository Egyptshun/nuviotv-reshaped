package com.nuvio.tv.reshaped.doh

import com.nuvio.tv.reshaped.doh.NuvioOkHttpDns
import okhttp3.Dns

/** IPv4-first ordering over system or DNS-over-HTTPS, per user network settings. */
object NuvioOkHttpDns {
    val dns: Dns = IPv4FirstDns(NuvioDns)
}
