package com.hris.platform.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.platform.config.MinioProperties;
import com.hris.platform.entity.SysExportTask;
import com.hris.platform.entity.SysFileMeta;
import com.hris.platform.entity.SysReportDef;
import com.hris.platform.mapper.SysFileMetaMapper;
import com.hris.platform.mapper.SysReportDefMapper;
import com.hris.platform.service.ExportTaskService;
import com.hris.platform.storage.MinioStorage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 异步报表导出执行器（PLT-F-005），也是调度中心的第一个真实任务。
 *
 * <p>在它出现之前，{@code ExportTaskService.create()} 只写入 PENDING，
 * markDone/markFailed 全仓没有调用方——用户点了导出，任务永远停在 PENDING。
 * 这条链断在「缺调度能力」上，所以修复两处审计更正其实是同一件事。</p>
 *
 * <p><b>不伪造成功</b>：文件服务(MinIO)不可用时任务退回 PENDING 并让本次调度
 * 记 FAILED 触发重试，绝不把状态写成 DONE——那会产生一个永远下载不到的结果。</p>
 *
 * <p><b>当前数据范围</b>：CSV 内容是租户内真实的报表定义清单。五类业务主题
 * （人力结构/异动/考勤/招聘/薪酬成本）的明细数据跨 5 个业务库，需要
 * PLT-F-006 只读聚合库就绪后接入；在此之前不生成看似完整实则空壳的报表。</p>
 */
@Component
public class ExportTaskDispatchHandler implements TaskHandler {

    public static final String KEY = "EXPORT_TASK_DISPATCH";

    private static final int BATCH_SIZE = 20;
    private static final String CONTENT_TYPE = "text/csv; charset=UTF-8";

    private final ExportTaskService exportTaskService;
    private final SysReportDefMapper reportDefMapper;
    private final SysFileMetaMapper fileMetaMapper;
    private final MinioStorage minioStorage;
    private final MinioProperties minioProperties;

    public ExportTaskDispatchHandler(ExportTaskService exportTaskService,
                                     SysReportDefMapper reportDefMapper,
                                     SysFileMetaMapper fileMetaMapper,
                                     MinioStorage minioStorage,
                                     MinioProperties minioProperties) {
        this.exportTaskService = exportTaskService;
        this.reportDefMapper = reportDefMapper;
        this.fileMetaMapper = fileMetaMapper;
        this.minioStorage = minioStorage;
        this.minioProperties = minioProperties;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "异步报表导出：领取 PENDING 任务，生成 CSV 并关联文件";
    }

    @Override
    public String execute() throws Exception {
        if (!minioStorage.isReady()) {
            // 整批不动：任务保持 PENDING，抛异常让调度层记 FAILED 并退避重试
            throw new IllegalStateException("文件服务(MinIO)不可用，导出任务保留 PENDING 等待下次调度");
        }
        List<SysExportTask> pending = exportTaskService.claimPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return "无待处理导出任务";
        }

        int done = 0;
        int failed = 0;
        int waiting = 0;
        for (SysExportTask task : pending) {
            exportTaskService.markRunning(task.getId());
            try {
                byte[] bytes = buildCsv().getBytes(StandardCharsets.UTF_8);
                String objectKey = "exports/" + task.getId() + ".csv";
                if (!minioStorage.putObject(minioProperties.getBucket(), objectKey, bytes, CONTENT_TYPE)) {
                    exportTaskService.markPending(task.getId());
                    waiting++;
                    continue;
                }
                Long fileId = saveFileMeta(objectKey, bytes.length, task.getId());
                exportTaskService.markDone(task.getId(), fileId);
                done++;
            } catch (Exception e) {
                exportTaskService.markFailed(task.getId());
                failed++;
            }
        }

        String summary = String.format("导出完成 %d, 业务失败 %d, 等待文件服务 %d", done, failed, waiting);
        if (failed > 0 || waiting > 0) {
            // 抛给调度器：写 FAILED 执行日志并按策略重试
            throw new IllegalStateException(summary);
        }
        return summary;
    }

    /** 导出当前租户的报表定义清单（真实查询，不编造业务数据）。 */
    private String buildCsv() {
        StringBuilder sb = new StringBuilder("报表编码,报表名称,定义JSON,更新时间\n");
        List<SysReportDef> defs = reportDefMapper.selectList(
                new LambdaQueryWrapper<SysReportDef>().orderByAsc(SysReportDef::getCode));
        for (SysReportDef d : defs) {
            sb.append(csvCell(d.getCode())).append(',')
                    .append(csvCell(d.getName())).append(',')
                    .append(csvCell(d.getDefJson())).append(',')
                    .append(csvCell(String.valueOf(d.getUpdatedAt()))).append('\n');
        }
        return sb.toString();
    }

    private Long saveFileMeta(String objectKey, int size, Long taskId) {
        SysFileMeta meta = new SysFileMeta();
        meta.setBucket(minioProperties.getBucket());
        meta.setObjectKey(objectKey);
        meta.setFileName("report-defs-" + taskId + ".csv");
        meta.setContentType(CONTENT_TYPE);
        meta.setSizeBytes((long) size);
        fileMetaMapper.insert(meta);
        return meta.getId();
    }

    /** CSV 字段转义：双引号包裹，内部引号翻倍，换行压平。 */
    private static String csvCell(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        return "\"" + v + "\"";
    }
}
