package com.mdframe.forge.plugin.capability.controlplane.vo;

public record CapabilityCallGuideCheckVO(
        String code,
        String label,
        String status,
        boolean blocking,
        String message) {
}
