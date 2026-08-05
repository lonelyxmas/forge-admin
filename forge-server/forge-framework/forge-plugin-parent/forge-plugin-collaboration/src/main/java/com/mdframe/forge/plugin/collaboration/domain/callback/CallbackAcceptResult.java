package com.mdframe.forge.plugin.collaboration.domain.callback;

/**
 * 回调事件收件箱受理结果。
 *
 * @param accepted  是否已受理（新事件或重复事件均视为受理成功）
 * @param duplicate 是否为重复事件（命中去重唯一键，未重复入库）
 * @param recordId  新入库记录 ID（重复事件为 null）
 */
public record CallbackAcceptResult(
        boolean accepted,
        boolean duplicate,
        Long recordId
) {

    public static CallbackAcceptResult accepted(Long recordId) {
        return new CallbackAcceptResult(true, false, recordId);
    }

    public static CallbackAcceptResult duplicated() {
        return new CallbackAcceptResult(true, true, null);
    }
}
