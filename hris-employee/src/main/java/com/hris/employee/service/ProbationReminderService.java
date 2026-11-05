package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.dto.EmployeeDtos.ProbationReminderItem;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试用期到期提醒查询（EMP-F-007 深化）。
 *
 * <p>供 platform 调度任务 PROBATION_REMINDER 经内部端点拉取：返回 probation_end
 * 落在 [今天, 今天+windowDays] 的有效合同与员工摘要。提醒档位按剩余天数归档：
 * &lt;=3 天 D3、&lt;=7 天 D7、否则 D15（与需求「转正前 15/7/3 天提醒」对应）。</p>
 */
@Service
public class ProbationReminderService {

    /** 提醒档位（写入通知记录的分档标识）。 */
    public static final String LEVEL_D15 = "D15";
    public static final String LEVEL_D7 = "D7";
    public static final String LEVEL_D3 = "D3";

    private final ContractMapper contractMapper;
    private final EmployeeMapper employeeMapper;
    private final EmploymentMapper employmentMapper;

    public ProbationReminderService(ContractMapper contractMapper, EmployeeMapper employeeMapper,
                                    EmploymentMapper employmentMapper) {
        this.contractMapper = contractMapper;
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
    }

    /** 查询窗口内到期的试用期合同摘要（含姓名/工号/部门/到期日/剩余天数/档位）。 */
    public List<ProbationReminderItem> reminders(int windowDays) {
        int window = windowDays <= 0 ? 15 : windowDays;
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(window);
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, 1)
                .isNotNull(Contract::getProbationEnd)
                .ge(Contract::getProbationEnd, today)
                .le(Contract::getProbationEnd, end)
                .orderByAsc(Contract::getProbationEnd));
        if (contracts.isEmpty()) {
            return List.of();
        }
        Map<Long, Employee> empMap = batchEmployees(contracts.stream().map(Contract::getEmpId).toList());
        Map<Long, Employment> deptMap = batchCurrentEmployments(contracts.stream().map(Contract::getEmpId).toList());
        return contracts.stream().map(c -> {
            Employee e = empMap.get(c.getEmpId());
            long days = ChronoUnit.DAYS.between(today, c.getProbationEnd());
            return new ProbationReminderItem(c.getId(), c.getEmpId(),
                    e == null ? null : e.getEmpNo(),
                    e == null ? null : e.getName(),
                    deptMap.get(c.getEmpId()) == null ? null : deptMap.get(c.getEmpId()).getDeptId(),
                    c.getProbationEnd(), days, level(days));
        }).toList();
    }

    /** 剩余天数归档：<=3 → D3，<=7 → D7，其余（<=window）→ D15。 */
    public static String level(long daysRemaining) {
        if (daysRemaining <= 3) {
            return LEVEL_D3;
        }
        if (daysRemaining <= 7) {
            return LEVEL_D7;
        }
        return LEVEL_D15;
    }

    private Map<Long, Employee> batchEmployees(List<Long> empIds) {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>().in(Employee::getId, empIds))
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Employment> batchCurrentEmployments(List<Long> empIds) {
        return employmentMapper.selectList(new LambdaQueryWrapper<Employment>()
                        .in(Employment::getEmpId, empIds)
                        .eq(Employment::getStatus, 1)
                        .isNull(Employment::getEffectiveTo))
                .stream().collect(Collectors.toMap(Employment::getEmpId, Function.identity(), (a, b) -> a));
    }
}
