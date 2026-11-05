package com.hris.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感列 MyBatis TypeHandler（ADR-007 §4：写一律密文，读双读兼容存量明文）。
 *
 * <p>写：经 {@link CryptoService#encrypt} 加密后写 VARCHAR 列；读：无 {@code enc1:} 前缀的
 * 存量明文原样返回，密文解密。业务代码读写实体字段始终是明文语义，零改动。</p>
 *
 * <p><b>AAD 方案（最小侵入取舍）</b>：ADR 要求 AAD = 表名:列名:主键，但 MyBatis TypeHandler
 * 在参数/结果映射时拿不到主键（接口只暴露值与列名），故 AAD 取 {@code 表名:逻辑列名}
 * （不含主键）。列级绑定已能防「密文挪列」，行级防重放由 GCM 随机 IV + 验签兜底；
 * 该差异已登记，待 KMS/实体转换层演进时补齐。</p>
 *
 * <p>本基类带参构造，MyBatis 反射实例化要求无参构造，故逐列以静态内部类落地
 * （每列一个 AAD 上下文）。加密未启用（迁移期）时读写均透传明文。</p>
 */
public abstract class CipherTypeHandler extends BaseTypeHandler<String> {

    private final String aad;

    protected CipherTypeHandler(String table, String logicalColumn) {
        this.aad = table + ":" + logicalColumn;
    }

    String aad() {
        return aad;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, CryptoService.get().encrypt(parameter, aad));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CryptoService.get().decryptToPlain(rs.getString(columnName), aad);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CryptoService.get().decryptToPlain(rs.getString(columnIndex), aad);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CryptoService.get().decryptToPlain(cs.getString(columnIndex), aad);
    }

    /** emp_employee.cert_no（逻辑列，物理列 cert_no_cipher）。 */
    public static class EmpCertNo extends CipherTypeHandler {
        public EmpCertNo() { super("emp_employee", "cert_no"); }
    }

    /** emp_employee.mobile（物理列 mobile_cipher）。 */
    public static class EmpMobile extends CipherTypeHandler {
        public EmpMobile() { super("emp_employee", "mobile"); }
    }

    /** cmp_employee_pay.salary_amount（物理列 salary_amount_cipher）。 */
    public static class CmpEmployeePaySalary extends CipherTypeHandler {
        public CmpEmployeePaySalary() { super("cmp_employee_pay", "salary_amount"); }
    }

    /** cmp_employee_pay.bank_account（物理列 bank_account_cipher）。 */
    public static class CmpEmployeePayBankAccount extends CipherTypeHandler {
        public CmpEmployeePayBankAccount() { super("cmp_employee_pay", "bank_account"); }
    }

    /** cmp_payslip.net_pay（物理列 net_pay_cipher）。 */
    public static class CmpPayslipNetPay extends CipherTypeHandler {
        public CmpPayslipNetPay() { super("cmp_payslip", "net_pay"); }
    }

    /** cmp_tax_deduction.deduction_amount（物理列 deduction_amount_cipher）。 */
    public static class CmpTaxDeductionAmount extends CipherTypeHandler {
        public CmpTaxDeductionAmount() { super("cmp_tax_deduction", "deduction_amount"); }
    }

    /** cmp_payroll_detail.amount（物理列 amount_cipher）。 */
    public static class CmpPayrollDetailAmount extends CipherTypeHandler {
        public CmpPayrollDetailAmount() { super("cmp_payroll_detail", "amount"); }
    }

    /** cmp_payroll_detail.net_amount（物理列 net_amount_cipher）。 */
    public static class CmpPayrollDetailNetAmount extends CipherTypeHandler {
        public CmpPayrollDetailNetAmount() { super("cmp_payroll_detail", "net_amount"); }
    }

    /** rcu_candidate.mobile（物理列 mobile_cipher）。 */
    public static class RcuCandidateMobile extends CipherTypeHandler {
        public RcuCandidateMobile() { super("rcu_candidate", "mobile"); }
    }

    /** rcu_offer.salary_amount（物理列 salary_amount_cipher）。 */
    public static class RcuOfferSalary extends CipherTypeHandler {
        public RcuOfferSalary() { super("rcu_offer", "salary_amount"); }
    }
}
