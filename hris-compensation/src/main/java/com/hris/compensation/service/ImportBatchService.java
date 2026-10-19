package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.ImportBatchDtos.ImportBatchCreateRequest;
import com.hris.compensation.dto.ImportBatchDtos.ImportBatchResponse;
import com.hris.compensation.dto.ImportBatchDtos.ImportBatchUpdateRequest;
import com.hris.compensation.entity.ImportBatch;
import com.hris.compensation.mapper.ImportBatchMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ImportBatch 服务（CRUD）。
 */
@Service
public class ImportBatchService {

    private final ImportBatchMapper mapper;

    public ImportBatchService(ImportBatchMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public ImportBatchResponse get(Long id) {
        ImportBatch e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ImportBatch 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<ImportBatchResponse> list(long page, long size) {
        IPage<ImportBatch> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ImportBatchResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public ImportBatchResponse create(ImportBatchCreateRequest req) {
        ImportBatch e = new ImportBatch();
        e.setBatchNo(req.batchNo());
        e.setImportType(req.importType());
        e.setTotalRows(req.totalRows());
        e.setSuccessRows(req.successRows());
        e.setFailRows(req.failRows());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public ImportBatchResponse update(Long id, ImportBatchUpdateRequest req) {
        ImportBatch e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "ImportBatch 不存在");
        }
        e.setBatchNo(req.batchNo());
        e.setImportType(req.importType());
        e.setTotalRows(req.totalRows());
        e.setSuccessRows(req.successRows());
        e.setFailRows(req.failRows());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "ImportBatch 不存在");
        }
    }

    private ImportBatchResponse toResponse(ImportBatch e) {
        return new ImportBatchResponse(e.getId(), e.getBatchNo(), e.getImportType(), e.getTotalRows(), e.getSuccessRows(), e.getFailRows(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
