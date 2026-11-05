-- 调度任务注册：试用期到期提醒（EMP-F-007 深化，PROBATION_REMINDER）
-- 与 EXPORT_TASK_DISPATCH / PENDING_TRANSACTION_APPLY 手工 seed 同款（docs/19 §10.5-A2）。
-- 说明：一期租户开通流水线不自动注册调度任务，新租户需按本片段手工插入（demo 租户 tenant_id=100001）。
-- handler 对应 hris-platform 的 ProbationReminderHandler.KEY；cron 0 0 9 * * * = 每天 09:00:00 执行。
-- 幂等：以 (tenant_id, task_key) 唯一键约束，重复执行 INSERT 会冲突，可先查后插或用 ON DUPLICATE KEY。

-- MySQL（platform 库，如 plt_db）
INSERT INTO sys_scheduler_task_registry
  (id, tenant_id, task_key, cron, handler, status, created_by, updated_by, deleted, version)
SELECT 900730001, 100001, 'PROBATION_REMINDER', '0 0 9 * * *', 'PROBATION_REMINDER', 1, 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_scheduler_task_registry WHERE tenant_id = 100001 AND task_key = 'PROBATION_REMINDER'
);

-- PostgreSQL（platform 库）等价语句：
-- INSERT INTO sys_scheduler_task_registry
--   (id, tenant_id, task_key, cron, handler, status, created_by, updated_by, deleted, version)
-- SELECT 900730001, 100001, 'PROBATION_REMINDER', '0 0 9 * * *', 'PROBATION_REMINDER', 1, 0, 0, 0, 0
-- WHERE NOT EXISTS (
--   SELECT 1 FROM sys_scheduler_task_registry WHERE tenant_id = 100001 AND task_key = 'PROBATION_REMINDER'
-- );

-- 可选：通知模板（无模板时 PROBATION_REMINDER handler 落基础通知记录，不接发送通道）
-- INSERT INTO sys_notify_template (id, tenant_id, code, channel, subject, content, created_by, updated_by, deleted, version)
-- VALUES (900730002, 100001, 'PROBATION_REMINDER', 'APP',
--         '试用期到期提醒', '员工 {name}（{empNo}）试用期将于 {probationEnd} 到期，剩余 {days} 天，请跟进转正评估。', 0, 0, 0, 0);
