package com.omni.business.assistance.directory.ui;

import com.omni.business.assistance.directory.service.DirectoryService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 简易目录浏览器浮窗
 * 展示本地文件系统树，支持双击打开文件/目录
 */
public class DirectoryFloatingWindow extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(DirectoryFloatingWindow.class);
    private final DirectoryService directoryService;
    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JLabel pathLabel;

    public DirectoryFloatingWindow() {
        this.directoryService = new DirectoryService();
        initUI();
        setupDrag();
        loadRoot();
        setSize(350, 400);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 100));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 255, 200), 2),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        // 标题栏
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JLabel dragLabel = new JLabel("📁 目录浏览");
        dragLabel.setFont(new Font("Inter", Font.PLAIN, 14));
        dragLabel.setForeground(Color.WHITE);
        dragLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        titleBar.add(dragLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("✖");
        closeBtn.setFont(new Font("Inter", Font.PLAIN, 14));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(new Color(60, 60, 60, 200));
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.addActionListener(e -> dispose());
        titleBar.add(closeBtn, BorderLayout.EAST);
        main.add(titleBar, BorderLayout.NORTH);

        // 树
        rootNode = new DefaultMutableTreeNode("加载中...");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setBackground(new Color(0, 0, 0, 150));
        tree.setForeground(Color.WHITE);
        tree.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tree.setRowHeight(24);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if (node != null && node.getUserObject() instanceof FileNode) {
                        FileNode fn = (FileNode) node.getUserObject();
                        if (fn.isDirectory()) {
                            expandNode(node, fn.getPath());
                        } else {
                            // 文件双击可打开编辑器（此处仅提示）
                            JOptionPane.showMessageDialog(DirectoryFloatingWindow.this,
                                    "文件: " + fn.getPath(), "提示", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        main.add(scroll, BorderLayout.CENTER);

        // 底部路径
        pathLabel = new JLabel("路径: /");
        pathLabel.setFont(new Font("Inter", Font.PLAIN, 11));
        pathLabel.setForeground(Color.WHITE);
        pathLabel.setBackground(new Color(0, 0, 0, 150));
        pathLabel.setOpaque(true);
        pathLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        main.add(pathLabel, BorderLayout.SOUTH);

        setContentPane(main);
    }

    private void loadRoot() {
        try {
            Path root = directoryService.getRootPath(DirectoryService.ROOT_TYPE_LOCAL);
            FileNode rootNodeData = new FileNode(root.toString(), true);
            rootNode.setUserObject(rootNodeData);
            expandNode(rootNode, root.toString());
        } catch (Exception e) {
            log.error("加载目录树失败", e);
            rootNode.setUserObject("加载失败: " + e.getMessage());
            treeModel.reload();
        }
    }

    private void expandNode(DefaultMutableTreeNode parentNode, String path) {
        try {
            Path root = Paths.get(path);
            // 使用 DirectoryService 获取子节点
            java.util.List<Map<String, Object>> children = directoryService.listDirectory(
                    DirectoryService.ROOT_TYPE_LOCAL,
                    directoryService.getRootPath(DirectoryService.ROOT_TYPE_LOCAL).relativize(root).toString()
            );
            parentNode.removeAllChildren();
            for (Map<String, Object> child : children) {
                String name = (String) child.get("name");
                String childPath = (String) child.get("path");
                boolean isDir = "directory".equals(child.get("type"));
                FileNode fn = new FileNode(childPath, isDir);
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(fn);
                if (isDir) {
                    // 添加一个临时子节点以显示展开图标
                    node.add(new DefaultMutableTreeNode("加载中..."));
                }
                parentNode.add(node);
            }
            treeModel.reload(parentNode);
        } catch (Exception e) {
            log.error("展开节点失败: " + path, e);
        }
    }

    private void setupDrag() {
        MouseAdapter adapter = new MouseAdapter() {
            Point start = null;
            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (start != null) {
                    Point newPos = getLocation();
                    newPos.translate(e.getX() - start.x, e.getY() - start.y);
                    setLocation(newPos);
                }
            }
        };
        getContentPane().addMouseListener(adapter);
        getContentPane().addMouseMotionListener(adapter);
    }

    // 树节点数据封装
    private static class FileNode {
        private final String path;
        private final boolean isDirectory;

        FileNode(String path, boolean isDirectory) {
            this.path = path;
            this.isDirectory = isDirectory;
        }

        public String getPath() { return path; }
        public boolean isDirectory() { return isDirectory; }

        @Override
        public String toString() {
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            return name + (isDirectory ? "/" : "");
        }
    }
}