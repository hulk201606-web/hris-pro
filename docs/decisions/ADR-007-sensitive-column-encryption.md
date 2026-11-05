# ADR-007: 敏感列应用层加密（AES-256-GCM）方案

## Status: Accepted（方案锁定；实施排期待本轮完成后进行，见 Consequences）

日期：2026-11-05　关联：docs/19 §8.5-1/2、docs/20 §4.2、docs/14 §6-P1、docs/19 §9.2 遗留

## Background

全仓 `*_cipher` 列（emp_employee.cert_no_cipher/mobile_cipher、cmp_payslip.net_pay_cipher、
cmp_payroll_detail.amount_cipher/net_amount_cipher、cmp_employee_pay.salary_amount_cipher/
bank_account_cipher、cmp_tax_deduction.deduction_amount_cipher）**实际存明文**——实体注释自认
「MVP 明文占位」（Employee.java:11-13）。读侧密域双层设计本身正确（通用端点仅 mask、
SalarySecretService 独立权限点），缺陷只在存储层「cipher 名不副实」。

已踩到的连锁问题（文档化证据）：
1. 等值查询失效链：接入真加密后 `cert_no_cipher` 等值查重、RCS 候选人查重全部静默失效
   → 已预防：emp_employee.cert_no_hash（SHA-256）已落地（8.7-3，rehire-check 改哈希优先）。
2. 跨域语义错位：OfferOnboardService 把 candidate.mobileCipher 当明文传 employee
   preCreate——两侧都明文时能跑；一侧接入真加密即静默脏数据（密文入明文语义列、mask 对密文脱敏）。
3. RCS 候选人 mobileCipher 明文直出已修（2c60ac8，对齐 CMP 密域 mask 模式）。

## Decision

1. **算法与层次**：应用层加密（非磁盘/KMS 透明加密），AES-256-GCM。理由：GCM 自带认证
   （防篡改）；列级加密让「密域权限点」与「存储加密」职责分离；不依赖数据库 TDE（社区版无）。
2. **密钥管理**：`CryptoService` 单例，主密钥来自环境变量 `HRIS_CRYPTO_KEY`（32 字节
   base64，启动校验非空且长度合规，照 JwtSecretGuard 模式）；**本轮不做 KMS 信封加密**——
   数据密钥（DEK）直接派生自主密钥 + 列名上下文（AAD = 表名:列名:主键，防密文挪列重放）。
   KMS 演进登记 OPEN-DECISIONS，不阻塞。
3. **等值查询**：一律走 hash 列（cert_no_hash 模式）；**schema 增列规则**：需要等值查询的
   密文列必须配 `xxx_hash CHAR(64)`（SHA-256 小写 hex），未配 hash 列的密文列禁止参与
   WHERE 等值条件（gen_phase2 生成器规则待办：schema 注释口径修正——version 列按乐观锁、
   cipher 列必配 hash）。
4. **读写与迁移（双读渐进）**：
   - 写：一律密文（CryptoService.encrypt）。
   - 读：try decrypt，失败按明文返回（存量行兼容）；迁移脚本批量重写存量行（可回滚：
     迁移前整表备份，backup-mysql.sh 已具备）。
   - 迁移完成 + 一个发布周期后移除明文回退分支。
5. **作用范围（首批）**：emp_employee.cert_no/mobile；cmp_payslip.net_pay；
   cmp_employee_pay.salary_amount/bank_account；cmp_tax_deduction.deduction_amount。
   cmp_payroll_detail 明细列随第二批。RCS 候选人 mobile 同批纳入（读侧已 mask）。
6. **跨域契约**：内部 Feign 传递敏感原值时，字段名一律用明文语义（`mobile`，非
   `mobileCipher`）——OfferOnboard 的语义错位随本次一并修正；响应出参仍 mask，密文不外发。

## Consequences

- 正面：存储层满足规格「传输全程加密，明文只在受控端渲染」；查重经 hash 列稳定；等值
  泄露面收敛；密域权限点语义完整。
- 负面/成本：全部 cipher 列读写增加一次加解密（可接受，量级小）；**DBA/备份文件不再能
  直接读明文**（restore-drill 校验脚本中的明文抽样断言需同步调整）；「权限内明文」读取
  通道（EMP-F-025 toProfile 死代码）需随本次激活或显式裁决删除。
- 实施排期：独立一轮完成（预计 CryptoService + 5 列接入 + 迁移脚本 + restore-drill
  校验调整 + 既有测试适配），不与本轮 SELF/Redis 收尾混批。
