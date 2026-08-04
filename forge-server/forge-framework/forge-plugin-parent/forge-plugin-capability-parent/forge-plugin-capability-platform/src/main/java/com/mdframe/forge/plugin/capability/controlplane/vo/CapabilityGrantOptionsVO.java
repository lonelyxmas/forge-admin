package com.mdframe.forge.plugin.capability.controlplane.vo;

import java.util.List;

public record CapabilityGrantOptionsVO(
        List<CapabilityClientVO> clients,
        List<CapabilityGrantCapabilityVO> capabilities) {
}
