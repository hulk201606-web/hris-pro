package com.hris.auth.dto;

import java.util.List;

/**
 * 菜单节点（由权限点「模块:资源:动作」派生，模块为一级、资源为二级、动作为按钮）。
 */
public record MenuNode(String code, String name, String type, List<MenuNode> children) {
}
