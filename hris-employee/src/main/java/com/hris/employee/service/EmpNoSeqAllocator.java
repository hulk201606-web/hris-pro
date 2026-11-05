package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.entity.EmpNoSeq;
import com.hris.employee.mapper.EmpNoSeqMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工号序号段分配器（EMP-F-002）。
 *
 * <p>并发安全依赖 DB 行锁：对 uk(tenant_id, prefix, seq_year) 行
 * {@code SELECT ... FOR UPDATE} 后自增。首次分配并发插入会撞唯一键，捕获后
 * 重新取行锁自增（MySQL 语句级回滚，不污染外层事务）。</p>
 *
 * <p>必须独立 Bean + REQUIRES_NEW：序号要立即提交，避免外层建档事务回滚后
 * 号段被回退造成重号；同时把远程配置读取留在事务外，减少连接占用。</p>
 */
@Service
public class EmpNoSeqAllocator {

    private final EmpNoSeqMapper mapper;

    public EmpNoSeqAllocator(EmpNoSeqMapper mapper) {
        this.mapper = mapper;
    }

    /** 分配下一个序号（年度内从 1 开始）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int allocate(String prefix, int year) {
        EmpNoSeq row = lockRow(prefix, year);
        if (row != null) {
            int next = (row.getCurrentSeq() == null ? 0 : row.getCurrentSeq()) + 1;
            row.setCurrentSeq(next);
            mapper.updateById(row);
            return next;
        }
        EmpNoSeq created = new EmpNoSeq();
        created.setPrefix(prefix);
        created.setSeqYear(year);
        created.setCurrentSeq(1);
        try {
            mapper.insert(created);
            return 1;
        } catch (DuplicateKeyException e) {
            EmpNoSeq raced = lockRow(prefix, year);
            if (raced == null) {
                throw e;
            }
            int next = (raced.getCurrentSeq() == null ? 0 : raced.getCurrentSeq()) + 1;
            raced.setCurrentSeq(next);
            mapper.updateById(raced);
            return next;
        }
    }

    private EmpNoSeq lockRow(String prefix, int year) {
        return mapper.selectOne(new LambdaQueryWrapper<EmpNoSeq>()
                .eq(EmpNoSeq::getPrefix, prefix)
                .eq(EmpNoSeq::getSeqYear, year)
                .last("FOR UPDATE"));
    }
}
