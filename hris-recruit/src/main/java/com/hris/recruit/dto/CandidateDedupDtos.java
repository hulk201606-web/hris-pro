package com.hris.recruit.dto;

import java.util.List;

/**
 * 候选人查重 DTO（RCS-F-009）。
 *
 * <p>需求原文要求「手机号 / 邮箱 / 证件匹配查重」，但 rcu_candidate 表当前
 * 只有 mobile_cipher / mobile_mask / email 三个联系维度，**没有证件字段**，
 * 因此本轮只覆盖邮箱与姓名两个维度：</p>
 * <ul>
 *   <li>email：明文存储，等值匹配可靠，命中即视为同一人（强提示）</li>
 *   <li>name：仅作辅助（弱提示），同名不必然是同一人，需人工确认</li>
 *   <li>mobile：未纳入——该列由调用方传入 mobileCipher 后原样落库，
 *       若上游用随机 IV 加密则同号密文不同，等值查询不可靠；
 *       若确认上游使用确定性加密可后续补上</li>
 * </ul>
 */
public final class CandidateDedupDtos {

    private CandidateDedupDtos() {
    }

    /** 查重结果。duplicated 为 true 表示至少命中一条。 */
    public record CandidateDedupResponse(boolean duplicated, int matchCount,
                                         List<CandidateDedupItem> matches) {
    }

    /**
     * 一条命中记录。
     *
     * @param matchField 命中维度：EMAIL（强）/ NAME（弱，需人工确认）
     */
    public record CandidateDedupItem(Long id, String name, String email,
                                     String mobileMask, String status, String matchField) {
    }
}
