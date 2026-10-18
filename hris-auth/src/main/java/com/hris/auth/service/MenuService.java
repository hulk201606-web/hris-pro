package com.hris.auth.service;

import com.hris.auth.dto.MenuNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树派生（docs/08 §2：菜单/按钮/API 三处同源）。本增量无独立菜单表，
 * 菜单结构由权限点「模块:资源:动作」派生：模块=一级、资源=二级、动作=按钮。
 */
@Service
public class MenuService {

    private static final Map<String, String> MODULE_NAMES = moduleNames();

    public List<MenuNode> build(List<String> perms) {
        Map<String, Map<String, List<String>>> modules = new LinkedHashMap<>();
        for (String perm : perms) {
            String[] parts = perm.split(":");
            if (parts.length < 2) {
                continue;
            }
            String module = parts[0];
            String resource = parts[1];
            String action = parts.length >= 3 ? parts[2] : "view";
            modules.computeIfAbsent(module, k -> new LinkedHashMap<>())
                    .computeIfAbsent(resource, k -> new ArrayList<>())
                    .add(action);
        }

        List<MenuNode> tree = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> me : modules.entrySet()) {
            List<MenuNode> resources = new ArrayList<>();
            for (Map.Entry<String, List<String>> re : me.getValue().entrySet()) {
                List<MenuNode> actions = re.getValue().stream()
                        .distinct()
                        .map(a -> new MenuNode(me.getKey() + ":" + re.getKey() + ":" + a, a, "button", List.of()))
                        .toList();
                resources.add(new MenuNode(me.getKey() + ":" + re.getKey(), re.getKey(), "menu", actions));
            }
            tree.add(new MenuNode(me.getKey(), MODULE_NAMES.getOrDefault(me.getKey(), me.getKey()),
                    "module", resources));
        }
        return tree;
    }

    private static Map<String, String> moduleNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("auth", "认证与权限");
        m.put("org", "组织管理");
        m.put("emp", "员工管理");
        m.put("att", "假勤管理");
        m.put("rcu", "招聘管理");
        m.put("cmp", "薪酬管理");
        m.put("prf", "绩效培训");
        m.put("flow", "流程中心");
        m.put("rag", "知识库");
        m.put("plt", "平台管理");
        return m;
    }
}
