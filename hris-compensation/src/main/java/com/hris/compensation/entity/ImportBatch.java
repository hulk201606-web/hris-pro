package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 导入批次（域：薪酬，分层：实体/DO）。批量导入薪资数据的批次记录（成功/失败行数），状态机 PENDING→PROCESSING→DONE/FAILED。
 */
@TableName("cmp_import_batch")
public class ImportBatch extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String batchNo;  // 批次号

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }
    private String importType;  // 导入类型

    public String getImportType() {
        return importType;
    }

    public void setImportType(String importType) {
        this.importType = importType;
    }
    private Integer totalRows;  // 总行数

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }
    private Integer successRows;  // 成功行数

    public Integer getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(Integer successRows) {
        this.successRows = successRows;
    }
    private Integer failRows;  // 失败行数

    public Integer getFailRows() {
        return failRows;
    }

    public void setFailRows(Integer failRows) {
        this.failRows = failRows;
    }
    private String status;  // 状态 RUNNING/DONE/FAILED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
