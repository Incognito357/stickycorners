package com.incognito.tools.stickycorners.detector;

import com.incognito.tools.stickycorners.detector.graphics.Direction;
import com.incognito.tools.stickycorners.detector.graphics.PointDir;
import com.incognito.tools.stickycorners.detector.graphics.Corner;

import javax.swing.JWindow;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jahorton on 9/13/2018
 */
public class Detector extends JWindow {
    private List<PointDir.Integer> corners = new ArrayList<>();
    private List<Rectangle> screens = new ArrayList<>();

    private Point min = new Point();

    private Robot robot;
    private Point lastMouse;

    private int debugFontSize = 10;

    Detector(Rectangle[] screenBounds, boolean debug) {
        setBackground(new Color(0, true));
        setAlwaysOnTop(true);
        setLayout(null);
        Rectangle bounds = new Rectangle();
        for (Rectangle r : screenBounds) {
            Rectangle.union(bounds, r, bounds);
            screens.add(r);
            corners.add(new PointDir.Integer(r.x, r.y, Direction.RIGHT, Corner.TL));
            corners.add(new PointDir.Integer(r.x, r.y, Direction.DOWN, Corner.TL));
            corners.add(new PointDir.Integer(r.x, r.y + r.height - 1, Direction.UP, Corner.BL));
            corners.add(new PointDir.Integer(r.x, r.y + r.height - 1, Direction.RIGHT, Corner.BL));
            corners.add(new PointDir.Integer(r.x + r.width - 1, r.y, Direction.DOWN, Corner.TR));
            corners.add(new PointDir.Integer(r.x + r.width - 1, r.y, Direction.LEFT, Corner.TR));
            corners.add(new PointDir.Integer(r.x + r.width - 1, r.y + r.height - 1, Direction.UP, Corner.BR));
            corners.add(new PointDir.Integer(r.x + r.width - 1, r.y + r.height - 1, Direction.LEFT, Corner.BR));
            min.x = Math.min(min.x, r.x);
            min.y = Math.min(min.y, r.y);
            Point max = new Point();
            max.x = Math.max(max.x, r.x + r.width);
            max.y = Math.max(max.y, r.y + r.height);
        }

        corners.removeIf(p -> {
            boolean contained = false;
            for (Rectangle2D r : screens) {
                if (!r.contains(p.x, p.y)) {
                    contained |= p.direction.isHorizontalBoundary()
                            ? r.contains(p.x - 5.0, p.y) || r.contains(p.x + 5.0, p.y)
                            : r.contains(p.x, p.y - 5.0) || r.contains(p.x, p.y + 5.0);
                }
            }
            return !contained;
        });

        for (PointDir.Integer p : corners) {
            p.x -= min.x;
            p.y -= min.y;
        }
        setSize((int) bounds.getWidth(), (int) bounds.getHeight());
        setLocation((int) bounds.getX(), (int) bounds.getY());

        try {
            robot = new Robot();
            thread.start();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        if (debug) {
            setVisible(true);
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.setFont(g.getFont().deriveFont(g.getFont().getStyle(), debugFontSize));
        for (PointDir.Integer p : corners) {
            String str = p.x + ", " + p.y;
            int w = g.getFontMetrics().stringWidth(str);
            int h = g.getFontMetrics().getHeight();
            Point t = new Point(p.x + (p.corner == Corner.TL || p.corner == Corner.BL ? 5 : -(w + 5)), p.y + (p.corner == Corner.TL || p.corner == Corner.TR ? (int) (h * 0.65) : -(int) (h * 0.15)));
            g.setColor(Color.black);
            int borderScale = 1;
            if (debugFontSize > 28) {
                borderScale = 2;
            }
            for (int y = -borderScale; y < borderScale + 1; y++) {
                for (int x = -borderScale; x < borderScale + 1; x++) {
                    if (x == 0 && y == 0) continue;
                    g.drawString(str, t.x + x, t.y + y);
                }
            }
            g.setColor(p.enabled ? Color.green : Color.red);
            g.drawString(str, t.x, t.y);

            if (p.direction.isHorizontalBoundary()) {
                g.drawLine(p.x, p.y, p.x, p.y + (p.corner == Corner.TL || p.corner == Corner.TR ? 5 : -5));
            } else {
                g.drawLine(p.x, p.y, p.x + (p.corner == Corner.TL || p.corner == Corner.BL ? 5 : -5), p.y);
            }
        }
    }

    void stop() {
        this.setVisible(false);
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        corners.clear();
        screens.clear();
        min = null;
        lastMouse = null;
        robot = null;
    }

    private Thread thread = new Thread(() -> {
        System.out.println("Detector started.");
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null) {
                continue;
            }
            Point m = pi.getLocation();
            m.x -= min.x;
            m.y -= min.y;
            if (lastMouse != null && (m.x != lastMouse.x || m.y != lastMouse.y)) {
                boolean moved = false;
                for (PointDir.Integer p : corners) {
                    if (!p.enabled){
                        continue;
                    }
                    switch (p.corner) {
                        case TL:
                            if (p.direction.isHorizontalBoundary() && m.x == p.x && m.y >= p.y && m.y <= p.y + 5 && lastMouse.x > m.x) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x, m.y + 10);
                                robot.mouseMove(m.x - 1, m.y + 10);
                                robot.mouseMove(m.x - 1, m.y);
                                m.x--;
                                moved = true;
                            } else if (p.direction.isVerticalBoundary() && m.y == p.y && m.x >= p.x && m.x <= p.x + 5 && lastMouse.y > m.y) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x + 10, m.y);
                                robot.mouseMove(m.x + 10, m.y - 1);
                                robot.mouseMove(m.x, m.y - 1);
                                m.y--;
                                moved = true;
                            }
                            break;
                        case TR:
                            if (p.direction.isHorizontalBoundary() && m.x == p.x && m.y >= p.y && m.y <= p.y + 5 && lastMouse.x < m.x) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x, m.y + 10);
                                robot.mouseMove(m.x + 1, m.y + 10);
                                robot.mouseMove(m.x + 1, m.y);
                                m.x++;
                                moved = true;
                            } else if (p.direction.isVerticalBoundary() && m.y == p.y && m.x <= p.x && m.x >= p.x - 5 && lastMouse.y > m.y) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x - 10, m.y);
                                robot.mouseMove(m.x - 10, m.y - 1);
                                robot.mouseMove(m.x, m.y - 1);
                                m.y++;
                                moved = true;
                            }
                            break;
                        case BL:
                            if (p.direction.isHorizontalBoundary() && m.x == p.x && m.y <= p.y && m.y >= p.y - 5 && lastMouse.x > m.x) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x, m.y - 10);
                                robot.mouseMove(m.x - 1, m.y - 10);
                                robot.mouseMove(m.x - 1, m.y);
                                m.x--;
                                moved = true;
                            } else if (p.direction.isVerticalBoundary() && m.y == p.y && m.x >= p.x && m.x <= p.x + 5 && lastMouse.y < m.y) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x + 10, m.y);
                                robot.mouseMove(m.x + 10, m.y + 1);
                                robot.mouseMove(m.x, m.y + 1);
                                m.y--;
                                moved = true;
                            }
                            break;
                        case BR:
                            if (p.direction.isHorizontalBoundary() && m.x == p.x && m.y <= p.y && m.y >= p.y - 5 && lastMouse.x < m.x) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x, m.y - 10);
                                robot.mouseMove(m.x + 1, m.y - 10);
                                robot.mouseMove(m.x + 1, m.y);
                                m.x++;
                                moved = true;
                            } else if (p.direction.isVerticalBoundary() && m.y == p.y && m.x < p.x && m.x > p.x - 5 && lastMouse.y < m.y) {
                                m.x += min.x;
                                m.y += min.y;
                                robot.mouseMove(m.x - 10, m.y);
                                robot.mouseMove(m.x - 10, m.y + 1);
                                robot.mouseMove(m.x, m.y + 1);
                                m.y--;
                                moved = true;
                            }
                            break;
                    }
                    if (moved) {
                        m.x -= min.x;
                        m.y -= min.y;
                        break;
                    }
                }
            }
            lastMouse = m;
        }
        System.out.println("Detector stopped.");
        if (isVisible()) {
            setVisible(false);
        }
    });

    public List<PointDir.Integer> getCorners() {
        return corners;
    }

    public List<Rectangle> getScreens() {
        return screens;
    }

    public int getDebugFontSize() {
        return debugFontSize;
    }

    public void setDebugFontSize(int debugFontSize) {
        this.debugFontSize = debugFontSize;
    }
}
