package com.hris.common.crypto;

import org.apache.ibatis.reflection.MetaObject;

/**
 * hash 列应用层双写（ADR-007 §3：需要等值查询的密文列配 xxx_hash，SHA-256 小写 hex）。
 *
 * <p><b>最小侵入方案</b>：TypeHandler 在参数绑定阶段无法顺带写另一列，故挂接
 * MybatisPlusConfig 的 MetaObjectHandler（insertFill/updateFill 尾部调用 {@link #fill}）：
 * 凡实体同时存在 {@code xxCipher} 与 {@code xxHash} 属性且 cipher 值为明文、hash 为空，
 * 自动以 SHA-256(明文) 回填 hash。业务代码已自行赋 hash 的场景（EmployeeService 建档/
 * 证件变更）不覆盖，保持零改动。</p>
 *
 * <p>密文值（enc1: 前缀）无法派生明文 hash，跳过——该场景只出现在未走 TypeHandler 读侧
 * 的写回路径，hash 已随行存储，无需重建。</p>
 */
public final class CipherHashFiller {

    private CipherHashFiller() {
    }

    /** 对单个实体的全部 cipher/hash 属性对做回填（insert 与 update 填充尾部调用）。 */
    public static void fill(MetaObject metaObject) {
        for (String name : metaObject.getSetterNames()) {
            if (!name.endsWith("Cipher") || name.length() <= "Cipher".length()) {
                continue;
            }
            String hashName = name.substring(0, name.length() - "Cipher".length()) + "Hash";
            if (!metaObject.hasGetter(hashName) || !metaObject.hasSetter(hashName)) {
                continue;
            }
            Object cipherValue = metaObject.getValue(name);
            if (!(cipherValue instanceof String plain) || plain.isBlank()
                    || CryptoService.isCiphertext(plain)) {
                continue;
            }
            Object hashValue = metaObject.getValue(hashName);
            if (hashValue instanceof String hash && !hash.isBlank()) {
                continue;
            }
            metaObject.setValue(hashName, CryptoService.sha256Hex(plain.trim()));
        }
    }
}
