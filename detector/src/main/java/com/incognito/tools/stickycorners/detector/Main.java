package com.incognito.tools.stickycorners.detector;

import com.incognito.tools.stickycorners.detector.settings.Form;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ItemEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.logging.Logger;

/**
 * Created by jahorton on 9/13/2018
 */
public class Main {
    private static Logger log = Logger.getLogger("Main");
    private static Detector detector;
    private static boolean debug = false;
    private static long pollInterval = 5;
    private static boolean autoRefresh = false;
    private static Thread refreshThread;
    private static String graphicsJarPath = "";
    private static TrayIcon icon;
    private static Form settingsForm;

    private static synchronized void restart(Rectangle[] screenBounds) {
        if (detector != null) {
            detector.stop();
            detector.dispose();
        }
        detector = new Detector(screenBounds, debug);
    }

    private static void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("-d") || s.equals("--debug")) {
                debug = true;
            } else if ((s.equals("-i") || s.equals("--interval")) && i != args.length - 1) {
                pollInterval = Math.max(5, Long.parseLong(args[++i]));
            } else if ((s.equals("-g") || s.equals("--graphics")) && i != args.length - 1) {
                graphicsJarPath = args[++i];
            }
        }
    }

    private static void notification(String message) {
        log.info(message);
    }

    public static void main(String[] args) throws Exception {
        parse(args);
        setupTray();
        refreshThread = makeThread();
        refreshThread.start();
    }

    private static Thread makeThread() {
        return new Thread(() -> {
            Rectangle[] screenBounds = getScreenBounds();
            if (screenBounds.length == 0) {
                log.severe("Can't start auto-refresh thread");
                close(false);
                return;
            }
            restart(screenBounds);
            while (autoRefresh) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Stopping auto-refresh thread");
                    break;
                }
                try {
                    Thread.sleep(pollInterval * 1000);
                } catch (InterruptedException e) {
                    log.info("Stopping auto-refresh thread");
                    Thread.currentThread().interrupt();
                    break;
                }
                Rectangle[] newScreenBounds = getScreenBounds();
                if (newScreenBounds.length != screenBounds.length) {
                    notification("Detected change in screen environment, restarting...");
                    restart(newScreenBounds);
                    for (int i = 0; i < screenBounds.length; i++) {
                        screenBounds[i] = null;
                    }
                    screenBounds = newScreenBounds;
                } else {
                    for (int i = 0; i < screenBounds.length; i++) {
                        if (!screenBounds[i].equals(newScreenBounds[i])) {
                            notification("Detected change in screen orientations, restarting...");
                            restart(newScreenBounds);
                            for (int j = 0; j < screenBounds.length; j++) {
                                screenBounds[j] = null;
                            }
                            screenBounds = newScreenBounds;
                            break;
                        }
                    }
                }
            }
        });
    }

    private static void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.severe(() -> "Could not set default look and feel: " + e.getMessage());
        }

        URL iconPath = Main.class.getClassLoader().getResource("icon.png");
        if (iconPath == null) {
            log.severe("Could not load icon.png!");
            return;
        }
        icon = new TrayIcon(new ImageIcon(iconPath).getImage());
        icon.setImageAutoSize(true);
        icon.setToolTip("StickyCorners Fix");
        final PopupMenu menu = new PopupMenu();
        final SystemTray tray = SystemTray.getSystemTray();

        MenuItem settings = new MenuItem("Settings");
        CheckboxMenuItem debug = new CheckboxMenuItem("Debug Mode", Main.debug);
        CheckboxMenuItem autoRefresh = new CheckboxMenuItem("Auto-Refresh", Main.autoRefresh);
        MenuItem refresh = new MenuItem("Refresh Now");
        MenuItem exit = new MenuItem("Close");

        menu.add(settings);
        menu.add(debug);
        menu.add(autoRefresh);
        menu.add(refresh);
        menu.add(exit);

        icon.setPopupMenu(menu);
        try {
            tray.add(icon);
        } catch (AWTException e) {
            log.severe(() -> "Could not add to system tray: " + e.getMessage());
            return;
        }

        settings.addActionListener(e -> {
            if (settingsForm == null) {
                settingsForm = new Form(pollInterval, detector);
                settingsForm.onClose(() -> settingsForm = null);
            } else {
                settingsForm.bringToFront();
            }
        });

        debug.addItemListener(e -> setDebug(e.getStateChange() == ItemEvent.SELECTED));

        autoRefresh.addItemListener(e -> setRefresh(e.getStateChange() == ItemEvent.SELECTED));

        refresh.addActionListener(e -> {
            refreshThread.interrupt();
            refreshThread = makeThread();
            refreshThread.start();
        });

        exit.addActionListener(e -> close(true));
    }

    private static Rectangle[] getScreenBounds() {
        Process proc;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"java", "-jar", graphicsJarPath + "graphics-1.0.jar"});
        } catch (IOException e) {
            log.severe(() -> "Could not execute process: " + e.getMessage());
            throw new RuntimeException();
        }

        BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        Scanner in = new Scanner(input);

        TriConsumer<Scanner, BufferedReader, InputStream> close = (scanner, reader, stream) -> {
            if (scanner != null) {
                scanner.close();
            }
            try {
                if (reader != null) {
                    reader.close();
                }
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                log.severe(() -> "Could not close stream: " + e.getMessage());
            }
        };

        if (!in.hasNext()) {
            BufferedReader errInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            StringJoiner lines = new StringJoiner("\n");
            try {
                String line;
                while ((line = errInput.readLine()) != null) {
                    lines.add(line);
                }
                log.severe(() -> "No output from graphics: " + lines.toString());
            } catch (Exception ex) {
                log.severe(() -> "Could not read errors from process:" + ex.getMessage());
            }
            close.apply(in, input, proc.getInputStream());
            close.apply(null, errInput, proc.getErrorStream());
            JOptionPane.showMessageDialog(null, "Cannot detect screen settings!\n" +
                    "Is the graphics jar in the same folder as the detector jar?\n" +
                    "Try setting the path to the graphics jar with the -g parameter.", "Error", JOptionPane.ERROR_MESSAGE);
            return new Rectangle[0];
        }
        int numScreens = in.nextInt();
        Rectangle[] screens = new Rectangle[numScreens];
        for (int i = 0; i < numScreens; i++) {
            screens[i] = new Rectangle(in.nextInt(), in.nextInt(), in.nextInt(), in.nextInt());
        }

        close.apply(in, input, proc.getInputStream());

        return screens;
    }

    private static void setDebug(boolean state) {
        debug = state;
        detector.setVisible(debug);
    }

    private static void setRefresh(boolean state) {
        autoRefresh = state;
        if (!autoRefresh && refreshThread != null) {
            refreshThread.interrupt();
        } else {
            refreshThread = makeThread();
            refreshThread.start();
        }
    }

    private static void close(boolean interrupt) {
        if (interrupt) {
            refreshThread.interrupt();
        }
        if (detector != null) {
            detector.stop();
        }
        SystemTray.getSystemTray().remove(icon);
        System.exit(0);
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void apply(A a, B b, C c);
    }
}
