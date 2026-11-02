package com.nuvio.tv.ui.screens.player

internal fun PlayerRuntimeController.isPrivateViewingActive(): Boolean =
    currentPlayerSettingsForReport.privateViewingEnabled
