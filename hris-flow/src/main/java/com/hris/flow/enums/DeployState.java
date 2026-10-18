package com.hris.flow.enums;

/**
 * 模型状态（flow_ext_model.deploy_state）。
 */
public enum DeployState {

    /** 草稿（未部署） */
    DRAFT,
    /** 已部署 */
    DEPLOYED;

    public static DeployState from(String value) {
        for (DeployState s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        return null;
    }
}
