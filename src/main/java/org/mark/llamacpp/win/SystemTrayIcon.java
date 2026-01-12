package org.mark.llamacpp.win;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.function.Consumer;

/**
 * 系统托盘图标工具类
 * 用于在 Windows 任务栏创建和管理系统托盘图标
 */
public class SystemTrayIcon {

    private final SystemTray systemTray;
    private TrayIcon trayIcon;
    private PopupMenu popupMenu;
    private Consumer<ActionEvent> defaultActionListener;

    /**
     * 构造函数
     * @throws AWTException 如果系统不支持托盘图标
     */
    public SystemTrayIcon() throws AWTException {
        if (!SystemTray.isSupported()) {
            throw new AWTException("系统托盘不支持");
        }
        this.systemTray = SystemTray.getSystemTray();
    }

    /**
     * 使用自定义图标创建托盘图标
     * @param icon 图标图片
     * @param tooltip 提示文本
     * @throws AWTException 如果添加托盘图标失败
     */
    public void createTrayIcon(Image icon, String tooltip) throws AWTException {
        createTrayIcon(icon, tooltip, null);
    }

    /**
     * 使用自定义图标和弹出菜单创建托盘图标
     * @param icon 图标图片
     * @param tooltip 提示文本
     * @param popupMenu 弹出菜单（如果为 null，则使用已有的 popupMenu 或创建默认菜单）
     * @throws AWTException 如果添加托盘图标失败
     */
    public void createTrayIcon(Image icon, String tooltip, PopupMenu popupMenu) throws AWTException {
        if (trayIcon != null) {
            removeTrayIcon();
        }

        if (popupMenu != null) {
            this.popupMenu = popupMenu;
        } else if (this.popupMenu == null) {
            this.popupMenu = createDefaultPopupMenu();
        }
        
        trayIcon = new TrayIcon(icon, tooltip, this.popupMenu);
        trayIcon.setImageAutoSize(true);

        // 设置双击事件监听器
        if (defaultActionListener != null) {
            trayIcon.addActionListener(e -> defaultActionListener.accept(e));
        }

        systemTray.add(trayIcon);
    }

    /**
     * 使用默认图标创建托盘图标
     * @param tooltip 提示文本
     * @throws AWTException 如果添加托盘图标失败
     */
    public void createTrayIcon(String tooltip) throws AWTException {
        Image icon = createDefaultIcon();
        createTrayIcon(icon, tooltip);
    }

    /**
     * 使用资源文件中的图标创建托盘图标
     * @param iconPath 图标路径（相对于 classpath）
     * @param tooltip 提示文本
     * @throws AWTException 如果添加托盘图标失败
     */
    public void createTrayIconFromResource(String iconPath, String tooltip) throws AWTException {
        Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource(iconPath));
        createTrayIcon(icon, tooltip);
    }

    /**
     * 获取支持中文的字体
     * @return 中文字体
     */
    private static Font getChineseFont() {
        // 尝试使用常见的中文字体
        String[] fontNames = {"Microsoft YaHei", "SimSun", "SimHei", "Arial Unicode MS"};
        for (String fontName : fontNames) {
            Font font = new Font(fontName, Font.PLAIN, 12);
            if (font.canDisplay('中')) {
                return font;
            }
        }
        // 如果都不支持，使用系统默认字体
        return new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /**
     * 创建默认的弹出菜单
     * @return 默认弹出菜单
     */
    private PopupMenu createDefaultPopupMenu() {
        PopupMenu menu = new PopupMenu();
        Font chineseFont = getChineseFont();
        
        MenuItem openItem = new MenuItem("打开");
        openItem.setFont(chineseFont);
        openItem.addActionListener(e -> onOpenAction());
        menu.add(openItem);
        
        menu.addSeparator();
        
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setFont(chineseFont);
        exitItem.addActionListener(e -> onExitAction());
        menu.add(exitItem);
        
        return menu;
    }

    /**
     * 创建默认图标（简单的 16x16 像素图标）
     * @return 默认图标
     */
    protected Image createDefaultIcon() {
        // 创建一个简单的 16x16 像素图标
        int size = 16;
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            // 创建一个简单的蓝色圆形图标
            int x = i % size;
            int y = i / size;
            int centerX = size / 2;
            int centerY = size / 2;
            double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
            if (distance <= size / 2 - 1) {
                pixels[i] = 0xFF4A90E2; // 蓝色
            } else {
                pixels[i] = 0x00000000; // 透明
            }
        }
        return Toolkit.getDefaultToolkit().createImage(new java.awt.image.MemoryImageSource(size, size, pixels, 0, size));
    }

    /**
     * 设置双击托盘图标时的默认操作
     * @param actionListener 操作监听器
     */
    public void setDefaultActionListener(Consumer<ActionEvent> actionListener) {
        this.defaultActionListener = actionListener;
        if (trayIcon != null) {
            trayIcon.addActionListener(e -> actionListener.accept(e));
        }
    }

    /**
     * 添加菜单项到弹出菜单
     * @param label 菜单项标签
     * @param actionListener 菜单项点击事件监听器
     */
    public void addMenuItem(String label, ActionListener actionListener) {
        if (popupMenu == null) {
            popupMenu = new PopupMenu();
        }
        MenuItem item = new MenuItem(label);
        item.setFont(getChineseFont());
        item.addActionListener(actionListener);
        popupMenu.add(item);
    }

    /**
     * 添加分隔符到弹出菜单
     */
    public void addMenuSeparator() {
        if (popupMenu != null) {
            popupMenu.addSeparator();
        }
    }

    /**
     * 添加子菜单到弹出菜单
     * @param label 子菜单标签
     * @return 子菜单对象
     */
    public PopupMenu addSubMenu(String label) {
        if (popupMenu == null) {
            popupMenu = new PopupMenu();
        }
        PopupMenu subMenu = new PopupMenu(label);
        MenuItem subMenuItem = new MenuItem(label);
        subMenuItem.setEnabled(false); // 作为标题显示
        popupMenu.add(subMenuItem);
        popupMenu.add(subMenu);
        return subMenu;
    }

    /**
     * 显示托盘消息
     * @param caption 消息标题
     * @param text 消息内容
     * @param messageType 消息类型
     */
    public void displayMessage(String caption, String text, TrayIcon.MessageType messageType) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, messageType);
        }
    }

    /**
     * 显示信息消息
     * @param caption 消息标题
     * @param text 消息内容
     */
    public void displayInfoMessage(String caption, String text) {
        displayMessage(caption, text, TrayIcon.MessageType.INFO);
    }

    /**
     * 显示警告消息
     * @param caption 消息标题
     * @param text 消息内容
     */
    public void displayWarningMessage(String caption, String text) {
        displayMessage(caption, text, TrayIcon.MessageType.WARNING);
    }

    /**
     * 显示错误消息
     * @param caption 消息标题
     * @param text 消息内容
     */
    public void displayErrorMessage(String caption, String text) {
        displayMessage(caption, text, TrayIcon.MessageType.ERROR);
    }

    /**
     * 显示无类型消息
     * @param caption 消息标题
     * @param text 消息内容
     */
    public void displayNoneMessage(String caption, String text) {
        displayMessage(caption, text, TrayIcon.MessageType.NONE);
    }

    /**
     * 更新托盘图标提示文本
     * @param tooltip 新的提示文本
     */
    public void updateTooltip(String tooltip) {
        if (trayIcon != null) {
            trayIcon.setToolTip(tooltip);
        }
    }

    /**
     * 更新托盘图标图片
     * @param icon 新的图标图片
     */
    public void updateIcon(Image icon) {
        if (trayIcon != null) {
            trayIcon.setImage(icon);
        }
    }

    /**
     * 移除托盘图标
     */
    public void removeTrayIcon() {
        if (trayIcon != null) {
            systemTray.remove(trayIcon);
            trayIcon = null;
        }
    }

    /**
     * 检查系统是否支持托盘图标
     * @return 如果支持返回 true，否则返回 false
     */
    public static boolean isSystemTraySupported() {
        return SystemTray.isSupported();
    }

    /**
     * 获取托盘图标对象
     * @return 托盘图标对象
     */
    public TrayIcon getTrayIcon() {
        return trayIcon;
    }

    /**
     * 获取弹出菜单
     * @return 弹出菜单对象
     */
    public PopupMenu getPopupMenu() {
        return popupMenu;
    }

    /**
     * 默认的打开操作（可被子类重写）
     */
    protected void onOpenAction() {
        // 默认实现：打开浏览器访问本地服务器
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8080"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 默认的退出操作（可被子类重写）
     */
    protected void onExitAction() {
        removeTrayIcon();
        System.exit(0);
    }

    /**
     * 托盘图标构建器类
     */
    public static class Builder {
        private Image icon;
        private String tooltip = "LlamaCpp Server";
        private PopupMenu popupMenu;
        private Consumer<ActionEvent> defaultActionListener;

        /**
         * 设置图标
         * @param icon 图标图片
         * @return 构建器实例
         */
        public Builder icon(Image icon) {
            this.icon = icon;
            return this;
        }

        /**
         * 设置图标路径
         * @param iconPath 图标路径（相对于 classpath）
         * @return 构建器实例
         */
        public Builder iconFromResource(String iconPath) {
            this.icon = Toolkit.getDefaultToolkit().getImage(
                SystemTrayIcon.class.getResource(iconPath));
            return this;
        }

        /**
         * 设置提示文本
         * @param tooltip 提示文本
         * @return 构建器实例
         */
        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /**
         * 设置弹出菜单
         * @param popupMenu 弹出菜单
         * @return 构建器实例
         */
        public Builder popupMenu(PopupMenu popupMenu) {
            this.popupMenu = popupMenu;
            return this;
        }

        /**
         * 设置双击操作监听器
         * @param actionListener 操作监听器
         * @return 构建器实例
         */
        public Builder defaultAction(Consumer<ActionEvent> actionListener) {
            this.defaultActionListener = actionListener;
            return this;
        }

        /**
         * 构建系统托盘图标
         * @return 系统托盘图标实例
         * @throws AWTException 如果创建失败
         */
        public SystemTrayIcon build() throws AWTException {
            SystemTrayIcon trayIcon = new SystemTrayIcon();
            trayIcon.setDefaultActionListener(defaultActionListener);
            
            if (icon == null) {
                icon = trayIcon.createDefaultIcon();
            }
            
            trayIcon.createTrayIcon(icon, tooltip, popupMenu);
            return trayIcon;
        }
    }
}
