/**
 * 二期管理端静态 Mock（VITE_USE_MOCK=true 时的降级数据源）。
 * 内存存储支撑分页/新建/编辑/删除，便于后端未就绪时演示页面。
 */
import type { PageResult } from '../types/api';
import type {
  Candidate,
  Cycle,
  EmployeePay,
  EmployeeProfileBrief,
  Goal,
  KbDocument,
  KnowledgeSpace,
  Offer,
  PayrollPreviewLine,
  PayrollRun,
  Requisition,
} from '../types/admin';

function delay<T>(data: T, ms = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms));
}

function now(): string {
  return new Date().toISOString().slice(0, 19).replace('T', ' ');
}

function paginate<T>(rows: T[], page: number, size: number): PageResult<T> {
  const start = (page - 1) * size;
  return { records: rows.slice(start, start + size), page, size, total: rows.length };
}

interface Store<T extends { id: number }> {
  list: (page: number, size: number) => Promise<PageResult<T>>;
  create: (data: Partial<T>) => Promise<T>;
  update: (id: number, data: Partial<T>) => Promise<T>;
  remove: (id: number) => Promise<void>;
}

function createStore<T extends { id: number }>(seed: T[]): Store<T> {
  const rows: T[] = seed.map((r) => ({ ...r }));
  let seq = rows.reduce((max, r) => Math.max(max, r.id), 0);
  return {
    list: (page, size) => delay(paginate(rows, page, size)),
    create: (data) => {
      const row = { ...data, id: ++seq, createdAt: now(), updatedAt: now() } as unknown as T;
      rows.unshift(row);
      return delay(row);
    },
    update: (id, data) => {
      const i = rows.findIndex((r) => r.id === id);
      const row = i >= 0 ? ({ ...rows[i], ...data, id, updatedAt: now() } as T) : (data as T);
      if (i >= 0) rows[i] = row;
      return delay(row);
    },
    remove: (id) => {
      const i = rows.findIndex((r) => r.id === id);
      if (i >= 0) rows.splice(i, 1);
      return delay(undefined);
    },
  };
}

const requisitionStore = createStore<Requisition>([
  { id: 1, reqNo: 'REQ-2026-0012', deptId: 101, positionId: 301, headcount: 2, reason: '研发扩编', status: 'APPROVED', published: 1, createdAt: '2026-09-02 10:12:00', updatedAt: '2026-09-05 09:30:00' },
  { id: 2, reqNo: 'REQ-2026-0013', deptId: 102, positionId: 305, headcount: 1, reason: '岗位替补', status: 'PENDING', published: 0, createdAt: '2026-09-18 14:20:00', updatedAt: '2026-09-18 14:20:00' },
]);

const candidateStore = createStore<Candidate>([
  { id: 1, name: '李思远', mobileCipher: '13800001234', mobileMask: '138****1234', email: 'li.siyuan@example.com', status: 'INTERVIEWING', createdAt: '2026-09-10 11:00:00', updatedAt: '2026-09-15 16:40:00' },
  { id: 2, name: '周若彤', mobileCipher: '13900005678', mobileMask: '139****5678', email: 'zhou.ruotong@example.com', status: 'SCREENING', createdAt: '2026-09-20 09:05:00', updatedAt: '2026-09-20 09:05:00' },
]);

const offerStore = createStore<Offer>([
  { id: 1, applicationId: 9001, offerNo: 'OFF-2026-0031', salaryBandCheck: 1, salaryAmountCipher: '25000', salaryAmountMask: '25****', status: 'PENDING', procInstId: 'pi-8801', createdAt: '2026-09-21 15:10:00', updatedAt: '2026-09-21 15:10:00' },
  { id: 2, applicationId: 9002, offerNo: 'OFF-2026-0032', salaryBandCheck: 0, salaryAmountCipher: '18000', salaryAmountMask: '18****', status: 'ACCEPTED', procInstId: 'pi-8802', createdAt: '2026-09-08 10:40:00', updatedAt: '2026-09-12 18:00:00' },
]);

const payrollRunStore = createStore<PayrollRun>([
  { id: 1, periodCode: '2026-09', payGroupId: 1, status: 'CLOSED', calcBy: 2001, reviewBy: 2002, createdAt: '2026-09-25 09:00:00', updatedAt: '2026-09-28 17:30:00' },
  { id: 2, periodCode: '2026-10', payGroupId: 1, status: 'CALCULATING', calcBy: 2001, reviewBy: 2002, createdAt: '2026-10-08 09:15:00', updatedAt: '2026-10-08 09:15:00' },
]);

const employeePayStore = createStore<EmployeePay>([
  { id: 1, empId: 10001, payGroupId: 1, payGradeId: 5, effectiveFrom: '2026-01-01', effectiveTo: '', salaryAmountCipher: '22000', salaryAmountMask: '22****', bankAccountCipher: '6222000011112222', bankAccountMask: '6222****2222', createdAt: '2026-01-01 00:00:00', updatedAt: '2026-01-01 00:00:00' },
  { id: 2, empId: 10002, payGroupId: 1, payGradeId: 6, effectiveFrom: '2026-03-01', effectiveTo: '', salaryAmountCipher: '26000', salaryAmountMask: '26****', bankAccountCipher: '6222000033334444', bankAccountMask: '6222****4444', createdAt: '2026-03-01 00:00:00', updatedAt: '2026-03-01 00:00:00' },
]);

const cycleStore = createStore<Cycle>([
  { id: 1, planId: 1, periodCode: '2026-H2', startDate: '2026-07-01', endDate: '2026-12-31', status: 'RUNNING', createdAt: '2026-07-01 09:00:00', updatedAt: '2026-07-01 09:00:00' },
  { id: 2, planId: 2, periodCode: '2026-Q3', startDate: '2026-07-01', endDate: '2026-09-30', status: 'CLOSED', createdAt: '2026-07-01 09:00:00', updatedAt: '2026-10-02 11:00:00' },
]);

const goalStore = createStore<Goal>([
  { id: 1, cycleId: 1, empId: 10001, title: '核心系统可用性提升', weight: 40, targetValue: '99.95%', resultValue: '99.93%', createdAt: '2026-07-05 10:00:00', updatedAt: '2026-09-30 18:00:00' },
  { id: 2, cycleId: 1, empId: 10001, title: '招聘周期缩短', weight: 30, targetValue: '30 天', resultValue: '32 天', createdAt: '2026-07-05 10:00:00', updatedAt: '2026-09-30 18:00:00' },
]);

const spaceStore = createStore<KnowledgeSpace>([
  { id: 1, name: '人事制度库', code: 'hr-policy', description: '公司人事制度、假勤与薪酬政策', createdAt: '2026-08-01 09:00:00', updatedAt: '2026-08-01 09:00:00' },
  { id: 2, name: '技术文档库', code: 'tech-doc', description: '研发规范、架构设计与运维手册', createdAt: '2026-08-06 09:00:00', updatedAt: '2026-08-06 09:00:00' },
]);

const documentStore = createStore<KbDocument>([
  { id: 1, spaceId: 1, title: '员工考勤管理办法', titleHash: 'h-a1b2c3', docType: 'POLICY', fileId: 7001, createdAt: '2026-08-02 10:00:00', updatedAt: '2026-08-02 10:00:00' },
  { id: 2, spaceId: 2, title: '微服务网关接入规范', titleHash: 'h-d4e5f6', docType: 'SPEC', fileId: 7002, createdAt: '2026-08-09 10:00:00', updatedAt: '2026-08-09 10:00:00' },
]);

function mockPayrollPreview(periodCode: string, empIds: number[]): Promise<PayrollPreviewLine[]> {
  const ids = empIds.length ? empIds : [10001, 10002];
  const rows: PayrollPreviewLine[] = ids.map((empId, i) => ({
    empId,
    empNo: `ZY${10000 + empId}`,
    name: i === 0 ? '王芳' : `员工${empId}`,
    deptId: 101 + i,
    attPeriodStatus: 'CLOSED',
    overtimeHours: 6.5 + i,
    leaveHours: 8 + i * 4,
    periodEmployeeCount: 1,
  }));
  return delay(rows);
}

function mockProfileBrief(empId: number): Promise<EmployeeProfileBrief> {
  return delay({
    empId,
    name: '王芳',
    empNo: 'ZY10021',
    status: 2,
    tenantId: 100001,
    deptId: 101,
    positionId: 301,
    supervisorEmpId: 10008,
  });
}

export const adminMock = {
  listRequisitions: requisitionStore.list,
  createRequisition: requisitionStore.create,
  updateRequisition: requisitionStore.update,
  removeRequisition: requisitionStore.remove,

  listCandidates: candidateStore.list,
  createCandidate: candidateStore.create,
  updateCandidate: candidateStore.update,
  removeCandidate: candidateStore.remove,

  listOffers: offerStore.list,
  createOffer: offerStore.create,
  updateOffer: offerStore.update,
  removeOffer: offerStore.remove,
  onboardOffer: (id: number): Promise<number> => delay(10000 + id),

  listPayrollRuns: payrollRunStore.list,
  createPayrollRun: payrollRunStore.create,
  updatePayrollRun: payrollRunStore.update,
  removePayrollRun: payrollRunStore.remove,

  listEmployeePays: employeePayStore.list,
  createEmployeePay: employeePayStore.create,
  updateEmployeePay: employeePayStore.update,
  removeEmployeePay: employeePayStore.remove,
  payrollPreview: mockPayrollPreview,

  listCycles: cycleStore.list,
  createCycle: cycleStore.create,
  updateCycle: cycleStore.update,
  removeCycle: cycleStore.remove,

  listGoals: goalStore.list,
  createGoal: goalStore.create,
  updateGoal: goalStore.update,
  removeGoal: goalStore.remove,
  reviewContext: mockProfileBrief,

  listSpaces: spaceStore.list,
  createSpace: spaceStore.create,
  updateSpace: spaceStore.update,
  removeSpace: spaceStore.remove,

  listDocuments: documentStore.list,
  createDocument: documentStore.create,
  updateDocument: documentStore.update,
  removeDocument: documentStore.remove,
  knowledgeScope: mockProfileBrief,
};
