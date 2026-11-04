<script setup lang="ts">
/**
 * 管理端-考勤报表（ATT-F-025）：出勤率 / 假期消耗 / 加班统计三张报表。
 *
 * 三张报表的筛选条件不同（日期区间 vs 期间），因此拆成独立子组件各自管理
 * 查询状态，容器只负责 Tab 切换。这样任一张报表的改动不会影响其余两张。
 */
import AttendanceRateReport from '../../../components/admin/attendance/AttendanceRateReport.vue';
import LeaveConsumeReport from '../../../components/admin/attendance/LeaveConsumeReport.vue';
import OvertimeReport from '../../../components/admin/attendance/OvertimeReport.vue';
</script>

<template>
  <div class="report">
    <section class="head">
      <h2 class="head-title">考勤报表</h2>
      <p class="head-sub">
        数据来自日考勤计算结果与已审批单据；加班费折算在薪酬模块，不在本页。
      </p>
    </section>

    <a-tabs>
      <a-tab-pane key="rate" tab="出勤率">
        <AttendanceRateReport />
      </a-tab-pane>
      <a-tab-pane key="leave" tab="假期消耗">
        <LeaveConsumeReport />
      </a-tab-pane>
      <a-tab-pane key="overtime" tab="加班统计">
        <OvertimeReport />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.report {
  padding: 4px;
}
.head {
  margin-bottom: 12px;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
</style>
