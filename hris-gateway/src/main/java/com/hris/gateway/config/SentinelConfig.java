package com.hris.gateway.config;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.WebFluxCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * Sentinel 网关限流配置（WebFlux 适配）。
 *
 * <p>端点维度（QPS）通过 FlowRule 加载；IP 维度通过 origin parser 预留
 * （生产做精确单 IP 限流需 ParamFlowRule 或 Redis 令牌桶，MVP 先端点级兜底）。</p>
 */
@Configuration
public class SentinelConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    private final HrisGatewayProperties props;

    public SentinelConfig(HrisGatewayProperties props) {
        this.props = props;
    }

    /**
     * Bean 初始化完成后：注册客户端 IP 解析器（供 Sentinel origin 维度统计），
     * 若配置启用限流则按规则列表加载 FlowRule（端点维度 QPS）；未启用则跳过并打 info 日志。
     */
    @Override
    public void afterPropertiesSet() {
        WebFluxCallbackManager.setRequestOriginParser(SentinelConfig::resolveClientIp);
        if (!props.getRateLimit().isEnabled()) {
            log.info("sentinel gateway rate-limit disabled");
            return;
        }
        List<FlowRule> rules = props.getRateLimit().getRules().stream()
                .map(this::toFlowRule)
                .toList();
        FlowRuleManager.loadRules(rules);
        log.info("sentinel gateway flow rules loaded: {}", rules.size());
    }

    /**
     * 把配置中的限流规则转换为 Sentinel FlowRule：资源名=请求路径， grade=QPS， limitApp=default。
     */
    private FlowRule toFlowRule(HrisGatewayProperties.Rule r) {
        FlowRule rule = new FlowRule(r.getPath());
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(r.getQps());
        rule.setLimitApp("default");
        return rule;
    }

    /**
     * 解析客户端真实 IP：优先取 X-Forwarded-For 第一个 IP（经反向代理），
     * 否则取直连 RemoteAddress；解析不到返回 "unknown"。
     */
    private static String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return request.getRemoteAddress() == null ? "unknown" : request.getRemoteAddress().getAddress().getHostAddress();
    }
}
