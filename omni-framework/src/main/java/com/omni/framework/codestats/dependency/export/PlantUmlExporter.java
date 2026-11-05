package com.omni.framework.codestats.dependency.export;


import com.omni.framework.codestats.dependency.model.CallTreeNode;
import com.omni.framework.codestats.dependency.model.MethodInfo;

import java.util.*;

/**
 * 把调用树导出为 PlantUML 时序图。
 */
public class PlantUmlExporter {

    public static String toSequenceDiagram(CallTreeNode root, String title) {
        if (root == null || root.getInfo() == null) return "@startuml\nnote \"无数据\"\n@enduml";
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        if (title != null && !title.isEmpty()) sb.append("title ").append(title).append("\n");
        sb.append("autonumber\n");
        sb.append("skinparam maxMessageSize 200\n");
        sb.append("skinparam participantPadding 20\n\n");

        // 收集 participant（用简单类名，去重保序）
        LinkedHashSet<String> participants = new LinkedHashSet<>();
        collectParticipants(root, participants);
        for (String p : participants) sb.append("participant ").append(p).append("\n");
        sb.append("\n");

        // 递归渲染
        renderNode(root, null, sb);
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static void collectParticipants(CallTreeNode node, Set<String> set) {
        if (node == null || node.getInfo() == null) return;
        set.add(simpleName(node.getInfo().getClassName()));
        for (CallTreeNode c : node.getChildren()) collectParticipants(c, set);
    }

    private static void renderNode(CallTreeNode node, String parentCls, StringBuilder sb) {
        MethodInfo info = node.getInfo();
        if (info == null) return;
        String cls = simpleName(info.getClassName());
        String label = info.getMethodName() + "()";

        if (parentCls != null) {
            sb.append(parentCls).append(" -> ").append(cls).append(" : ").append(label).append("\n");
        }
        sb.append("activate ").append(cls).append("\n");

        if (node.isCycleCut()) {
            sb.append("note right of ").append(cls).append(" : (循环调用，已截断)\n");
        }
        if (node.isDepthCut()) {
            sb.append("note right of ").append(cls).append(" : (深度超限，已截断)\n");
        }

        for (CallTreeNode child : node.getChildren()) {
            renderNode(child, cls, sb);
        }

        sb.append("deactivate ").append(cls).append("\n");
        if (parentCls != null) {
            sb.append(cls).append(" --> ").append(parentCls).append("\n");
        }
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return "?";
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }
}