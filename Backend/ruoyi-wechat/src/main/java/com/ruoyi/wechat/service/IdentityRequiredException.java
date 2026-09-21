package com.ruoyi.wechat.service;

import com.ruoyi.wechat.domain.IdentityCandidate;

/**
 * 需要在用户确认说话人映射后才能继续分析。
 *
 * <p>刻意做成异常而不是返回 null：军师 SKILL.md 明确要求
 * "映射不明先问"，这属于正常业务流程而非错误，用异常向上传递可以
 * 强制调用方处理，避免误把未确认的映射当默认值用下去。
 */
public class IdentityRequiredException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final transient IdentityCandidate candidate;

    public IdentityRequiredException(IdentityCandidate candidate)
    {
        super(candidate.hint());
        this.candidate = candidate;
    }

    public IdentityCandidate getCandidate()
    {
        return candidate;
    }
}
