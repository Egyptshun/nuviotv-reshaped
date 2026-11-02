package com.nuvio.tv.reshaped.pinnedstreams

import com.nuvio.tv.domain.model.AddonStreams

internal fun applyPinnedSourceNameOrder(
    names: List<String>,
    pinnedKeys: Set<String>,
    keyForName: (String) -> String
): List<String> {
    if (pinnedKeys.isEmpty() || names.isEmpty()) return names
    val (pinned, rest) = names.partition { keyForName(it) in pinnedKeys }
    if (pinned.isEmpty()) return names
    return pinned + rest
}

internal fun applyPinnedAddonStreamOrder(
    groups: List<AddonStreams>,
    pinnedKeys: Set<String>,
    keyForName: (String) -> String
): List<AddonStreams> {
    if (pinnedKeys.isEmpty() || groups.isEmpty()) return groups
    val (pinned, rest) = groups.partition { keyForName(it.addonName) in pinnedKeys }
    if (pinned.isEmpty()) return groups
    return pinned + rest
}
