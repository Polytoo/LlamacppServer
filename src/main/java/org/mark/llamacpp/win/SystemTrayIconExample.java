package org.mark.llamacpp.win;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 系统托盘图标使用示例
 * 演示如何使用 SystemTrayIcon 类创建和管理系统托盘图标
 */
public class SystemTrayIconExample {

    public static void main(String[] args) {
        // 检查系统是否支持托盘图标
        if (!SystemTrayIcon.isSystemTraySupported()) {
            System.err.println("系统不支持托盘图标");
            return;
        }

        try {
            // 方式一：使用默认图标和默认菜单
            SystemTrayIcon tray1 = new SystemTrayIcon();
            tray1.createTrayIcon("LlamaCpp Server - 运行中");

            // 方式二：使用 Builder 模式创建
            SystemTrayIcon tray2 = new SystemTrayIcon.Builder()
                .tooltip("LlamaCpp Server")
                .defaultAction(e -> {
                    System.out.println("托盘图标被双击");
                    // 打开浏览器访问本地服务器
                    try {
                        java.awt.Desktop.getDesktop().browse(
                            new java.net.URI("http://localhost:8080"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                })
                .build();

            // 方式三：自定义弹出菜单
            SystemTrayIcon tray3 = new SystemTrayIcon();
            
            // 使用 addMenuItem 方法添加菜单项
            tray3.addMenuItem("打开控制台", e -> {
                System.out.println("打开控制台");
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI("http://localhost:8080"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            
            tray3.addMenuSeparator();
            
            tray3.addMenuItem("启动服务", e -> {
                System.out.println("启动服务");
                tray3.displayInfoMessage("服务状态", "服务已启动");
            });
            
            tray3.addMenuItem("停止服务", e -> {
                System.out.println("停止服务");
                tray3.displayWarningMessage("服务状态", "服务已停止");
            });
            
            tray3.addMenuSeparator();
            
            tray3.addMenuItem("关于", e -> {
                tray3.displayMessage("关于",
                    "LlamaCpp Server v1.0\n\n本地 LLM 推理服务器",
                    java.awt.TrayIcon.MessageType.INFO);
            });
            
            tray3.addMenuSeparator();
            
            tray3.addMenuItem("退出", e -> {
                tray3.removeTrayIcon();
                System.exit(0);
            });
            
            tray3.createTrayIcon("LlamaCpp Server");

            // 显示测试消息
            tray3.displayInfoMessage("启动成功", "LlamaCpp Server 已在后台运行");

            System.out.println("托盘图标已创建，程序将在后台运行...");

            // 防止程序退出
            Thread.sleep(Long.MAX_VALUE);

        } catch (AWTException e) {
            System.err.println("创建托盘图标失败: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("程序被中断");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 创建一个简单的托盘图标并返回
     * @return SystemTrayIcon 实例
     */
    public static SystemTrayIcon createSimpleTray() {
        try {
            SystemTrayIcon tray = new SystemTrayIcon();
            tray.createTrayIcon("LlamaCpp Server");
            tray.displayInfoMessage("启动成功", "LlamaCpp Server 已在后台运行");
            return tray;
        } catch (AWTException e) {
            System.err.println("创建托盘图标失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建带有自定义菜单的托盘图标
     * @param tooltip 提示文本
     * @param onStart 启动服务回调
     * @param onStop 停止服务回调
     * @param onExit 退出回调
     * @return SystemTrayIcon 实例
     */
    public static SystemTrayIcon createCustomTray(
            String tooltip, 
            Runnable onStart, 
            Runnable onStop, 
            Runnable onExit) {
        try {
            SystemTrayIcon tray = new SystemTrayIcon();
            
            tray.addMenuItem("打开控制台", e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI("http://localhost:8080"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            
            tray.addMenuSeparator();
            
            tray.addMenuItem("启动服务", e -> {
                if (onStart != null) {
                    onStart.run();
                }
            });
            
            tray.addMenuItem("停止服务", e -> {
                if (onStop != null) {
                    onStop.run();
                }
            });
            
            tray.addMenuSeparator();
            
            tray.addMenuItem("关于", e -> {
                tray.displayMessage("关于", 
                    "LlamaCpp Server v1.0\n\n本地 LLM 推理服务器", 
                    java.awt.TrayIcon.MessageType.INFO);
            });
            
            tray.addMenuSeparator();
            
            tray.addMenuItem("退出", e -> {
                if (onExit != null) {
                    onExit.run();
                }
                tray.removeTrayIcon();
                System.exit(0);
            });
            
            tray.createTrayIcon(tooltip);
            return tray;
            
        } catch (AWTException e) {
            System.err.println("创建托盘图标失败: " + e.getMessage());
            return null;
        }
    }
}
