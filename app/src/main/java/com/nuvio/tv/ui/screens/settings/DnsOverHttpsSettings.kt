package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.reshaped.doh.DnsProvider
import com.nuvio.tv.reshaped.doh.DnsSettings

@Composable
internal fun DnsOverHttpsSettingsGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DnsSettings.initialize(context.applicationContext)
    val provider by DnsSettings.provider.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    SettingsGroupCard(
        modifier = modifier,
        title = stringResource(R.string.advanced_section_network),
    ) {
        SettingsActionRow(
            title = stringResource(R.string.settings_advanced_dns),
            subtitle = stringResource(R.string.settings_advanced_dns_subtitle),
            value = dnsProviderLabel(provider),
            onClick = { showDialog = true },
        )
    }

    if (showDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.settings_advanced_dns),
            subtitle = stringResource(R.string.settings_advanced_dns_note),
            options = DnsProvider.entries.map { entry ->
                SettingsPickerOption(
                    value = entry,
                    title = if (entry == DnsProvider.SYSTEM) {
                        stringResource(R.string.settings_advanced_dns_system)
                    } else {
                        entry.displayName
                    },
                )
            },
            selectedValue = provider,
            onOptionSelected = { selected ->
                DnsSettings.setProvider(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            maxHeight = 360.dp,
        )
    }
}

@Composable
private fun dnsProviderLabel(provider: DnsProvider): String =
    if (provider == DnsProvider.SYSTEM) {
        stringResource(R.string.settings_advanced_dns_system)
    } else {
        stringResource(R.string.settings_advanced_dns_doh_format, provider.displayName)
    }
