package com.codestats.run;

import javax.swing.*;

public class SimpleSwingApp {
    public static void main(String[] args) {
        // 创建 JFrame（窗口）
        JFrame frame = new JFrame("Hello Swing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);

        // 创建一个按钮
        JButton button = new JButton("Click Me");

        // 添加事件处理
        button.addActionListener(e -> 
            JOptionPane.showMessageDialog(frame, "Hello, World!")
        );

        // 将按钮添加到窗口中心
        frame.getContentPane().add(button);

        // 显示窗口
        frame.setVisible(true);
    }
}