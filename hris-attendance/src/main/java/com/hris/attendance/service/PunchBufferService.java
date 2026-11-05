package com.hris.attendance.service;

import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 打卡写入异步缓冲（批12#26 / ATT-F-005「5,000 TPS 打卡洪峰吸收」MVP）。
 *
 * <p>本机未部署 RocketMQ，故以「内存有界队列 + 批量落库线程」实现缓冲：
 * <ul>
 *   <li>队列容量 10,000（{@link ArrayBlockingQueue} 有界，天然背压）；</li>
 *   <li>批量线程按「200ms 窗口 或 累计 500 条」两个条件中最先满足者 flush；</li>
 *   <li>队列满时**不静默丢弃**，抛 50001「系统繁忙」由客户端重试（另可用同步直写降级，
 *       本实现选择背压拒绝：直写会在数据库已过载时放大压力）；</li>
 *   <li>在途去重表保证同 emp+考勤日+类型 的重复提交在入库前即命中幂等（DB 唯一键仍兜底）；</li>
 *   <li>Micrometer 暴露 {@code att.punch.buffer.backlog} 与 {@code att.punch.buffer.rejected}
 *       供监控告警（积压持续 &gt; 0 说明消费慢于生产）。</li>
 * </ul>
 *
 * <p><b>真 MQ 接入点</b>：把 {@link #persist} 的下游从 {@code PunchRecordMapper}
 * 换成 RocketMQ Producer 即可——缓冲/背压/幂等语义不变，消费端落库逻辑与
 * {@link #persistOne} 一致（消费幂等依赖同一 uk(tenant_id, emp_id, punch_time) 与
 * 业务幂等键）。</p>
 */
@Service
public class PunchBufferService {

    private static final Logger log = LoggerFactory.getLogger(PunchBufferService.class);

    /** 缺省队列容量 */
    public static final int DEFAULT_CAPACITY = 10_000;
    /** 缺省单批最大条数 */
    public static final int DEFAULT_BATCH_SIZE = 500;
    /** 缺省 flush 窗口（毫秒） */
    public static final long DEFAULT_FLUSH_MILLIS = 200;

    /** 队列积压指标名（Gauge，监控告警用） */
    public static final String METRIC_BACKLOG = "att.punch.buffer.backlog";
    /** 背压拒绝计数指标名 */
    public static final String METRIC_REJECTED = "att.punch.buffer.rejected";

    private final PunchRecordMapper punchMapper;
    private final BlockingQueue<PunchRecord> queue;
    private final int batchSize;
    private final long flushMillis;
    private final boolean workerEnabled;

    /** 在途去重表：key = empId|考勤日|类型 → 队列中的记录（flush 落库后移除）。 */
    private final ConcurrentHashMap<String, PunchRecord> inflight = new ConcurrentHashMap<>();

    private final LongAdder rejected = new LongAdder();

    private volatile boolean running;
    private Thread worker;

    /**
     * Spring 装配用构造器：读默认容量/批量/窗口并启动后台线程。
     * 指标同步注册，服务启动即在 /actuator/prometheus 可见。
     */
    public PunchBufferService(PunchRecordMapper punchMapper, MeterRegistry meterRegistry) {
        this(punchMapper, meterRegistry, DEFAULT_CAPACITY, DEFAULT_BATCH_SIZE,
                DEFAULT_FLUSH_MILLIS, true);
    }

    /** 可配构造器（单测用 workerEnabled=false 关闭后台线程，手动调用 {@link #flushOnce()}）。 */
    PunchBufferService(PunchRecordMapper punchMapper, MeterRegistry meterRegistry, int capacity,
                       int batchSize, long flushMillis, boolean workerEnabled) {
        this.punchMapper = punchMapper;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.batchSize = batchSize;
        this.flushMillis = flushMillis;
        this.workerEnabled = workerEnabled;
        if (meterRegistry != null) {
            Gauge.builder(METRIC_BACKLOG, queue, q -> q.size())
                    .description("打卡缓冲队列积压条数（>0 表示消费慢于生产）")
                    .register(meterRegistry);
            Gauge.builder(METRIC_REJECTED, rejected, LongAdder::sum)
                    .description("队列满被背压拒绝的打卡次数")
                    .register(meterRegistry);
        }
    }

    @PostConstruct
    void start() {
        if (!workerEnabled) {
            return;
        }
        running = true;
        worker = new Thread(this::loop, "att-punch-buffer");
        worker.setDaemon(true);
        worker.start();
        log.info("打卡缓冲已启动：capacity={} batchSize={} flushMillis={}",
                queue.remainingCapacity() + queue.size(), batchSize, flushMillis);
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        flushOnce();
    }

    /**
     * 入队（幂等 + 背压）。
     *
     * @param record 已完成幂等判重（DB 无同键记录）的待落库打卡
     * @return 实际代表本次打卡的记录：同键在途时返回在途记录，否则返回入参本身
     * @throws BizException 队列满（50001 系统繁忙，客户端应重试）
     */
    public PunchRecord enqueue(PunchRecord record) {
        String key = keyOf(record);
        PunchRecord prev = inflight.putIfAbsent(key, record);
        if (prev != null) {
            return prev;
        }
        if (!queue.offer(record)) {
            inflight.remove(key);
            rejected.increment();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
        }
        return record;
    }

    /** 当前积压条数（健康检查/测试用）。 */
    public int backlog() {
        return queue.size();
    }

    /** 单次 flush：按「窗口 200ms 或 500 条」收批后顺序落库，供测试与关闭时排空复用。 */
    public int flushOnce() {
        List<PunchRecord> batch = collect();
        if (batch.isEmpty()) {
            return 0;
        }
        persist(batch);
        return batch.size();
    }

    /** 后台循环：每轮收批一次，异常不外抛（否则线程退出导致缓冲失效）。 */
    private void loop() {
        while (running) {
            try {
                flushOnce();
            } catch (RuntimeException e) {
                log.warn("打卡缓冲 flush 异常，下轮重试", e);
            }
        }
    }

    /** 收批：最多等 flushMillis 或凑满 batchSize，先到先返回。 */
    private List<PunchRecord> collect() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushMillis);
        List<PunchRecord> batch = new ArrayList<>(Math.min(batchSize, 256));
        while (batch.size() < batchSize) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            PunchRecord record;
            try {
                record = queue.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (record == null) {
                break;
            }
            batch.add(record);
            queue.drainTo(batch, batchSize - batch.size());
        }
        return batch;
    }

    /**
     * 批量落库：保持入队顺序，逐条 insert（MySQL/PG 通用，避免跨库 batch 语法差异）。
     * 单条失败不阻塞整批（否则一次脏数据会卡死缓冲），失败记录仅记日志并移出去重表，
     * 由客户端重试或补卡流程兜底。
     */
    private void persist(List<PunchRecord> batch) {
        for (PunchRecord record : batch) {
            try {
                persistOne(record);
            } catch (RuntimeException e) {
                log.warn("打卡落库失败 empId={} punchTime={} type={}",
                        record.getEmpId(), record.getPunchTime(), record.getType(), e);
            } finally {
                inflight.remove(keyOf(record));
            }
        }
    }

    private void persistOne(PunchRecord record) {
        punchMapper.insert(record);
    }

    private static String keyOf(PunchRecord record) {
        LocalDate day = record.getPunchTime() == null ? null : record.getPunchTime().toLocalDate();
        return record.getEmpId() + "|" + day + "|" + record.getType();
    }
}
