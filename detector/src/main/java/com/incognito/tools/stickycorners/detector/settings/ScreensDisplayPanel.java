package com.incognito.tools.stickycorners.detector.settings;

import com.incognito.tools.stickycorners.detector.graphics.PointDir;
import com.incognito.tools.stickycorners.detector.graphics.Corner;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by jahorton on 1/2/2019
 */
public class ScreensDisplayPanel extends JPanel implements MouseListener, MouseMotionListener {
    private final List<Rectangle> screens;
    private final List<PointDir.Integer> points;
    private final Rectangle extents = new Rectangle(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    private Point mousePos = new Point(-1, -1);
    private Map<PointDir.Double, PointDir.Integer> scaledPoints;
    private List<Rectangle.Double> scaledScreens;
    private final Map<PointDir.Double, Path2D> tris = new HashMap<>();

    private Set<Consumer<PointDir.Integer>> listeners = new HashSet<>();

    private Dimension lastDimension;

    public ScreensDisplayPanel(List<Rectangle> screens, List<PointDir.Integer> points) {
        this.screens = screens;
        this.points = points;

        addMouseListener(this);
        addMouseMotionListener(this);

        for (Rectangle r : screens) {
            if (r.x < extents.x) extents.x = r.x;
            if (r.y < extents.y) extents.y = r.y;
            if (r.x + r.width > extents.width) extents.width = r.x + r.width;
            if (r.y + r.height > extents.height) extents.height = r.y + r.height;
        }
        extents.width = extents.width - extents.x;
        extents.height = extents.height - extents.y;
        setPreferredSize(new Dimension(extents.width / 20, extents.height / 20));
        updateScaled();
        lastDimension = getSize();
    }

    private void updateScaled() {
        scaledScreens = screens.stream()
                .map(r -> scaleRect(r, getWidth() - 1, getHeight() - 1))
                .collect(Collectors.toList());

        scaledPoints = points.stream().collect(Collectors.toMap(p -> {
            Point point = p.toPoint();
            point.translate(extents.x, extents.y);
            Point.Double scaled = scalePoint(point, getWidth() - 1, getHeight() - 1);
            return new PointDir.Double(scaled.x, scaled.y, p.direction, p.corner, p.enabled);
        }, p -> p));

        tris.clear();
        for (PointDir.Double p : scaledPoints.keySet()) {
            Path2D tri = new Path2D.Double();
            tri.moveTo(p.x, p.y);
            int dir;
            int size = 10;
            switch (p.direction) {
                case UP:
                    dir = size * (p.corner == Corner.BL ? 1 : -1);
                    tri.lineTo(p.x, p.y - size);
                    tri.lineTo(p.x + dir, p.y - size);
                    break;
                case RIGHT:
                    dir = size * (p.corner == Corner.TL ? 1 : -1);
                    tri.lineTo(p.x + size, p.y);
                    tri.lineTo(p.x + size, p.y + dir);
                    break;
                case DOWN:
                    dir = size * (p.corner == Corner.TL ? 1 : -1);
                    tri.lineTo(p.x, p.y + size);
                    tri.lineTo(p.x + dir, p.y + size);
                    break;
                case LEFT:
                    dir = size * (p.corner == Corner.TR ? 1 : -1);
                    tri.lineTo(p.x - size, p.y);
                    tri.lineTo(p.x - size, p.y + dir);
                    break;
            }
            tri.closePath();

            tris.put(p, tri);
        }
    }

    private double scaleValue(double value, double min, double max, double newMin, double newMax) {
        return (((newMax - newMin) * (value - min)) / (max - min)) + newMin;
    }

    private Rectangle.Double scaleRect(Rectangle value, int width, int height) {
        Point.Double scaledTL = scalePoint(new Point(value.x, value.y), width, height);
        Point.Double scaledBR = scalePoint(new Point(value.x + value.width, value.y + value.height), width, height);
        return new Rectangle.Double(scaledTL.x, scaledTL.y, scaledBR.x - scaledTL.x, scaledBR.y - scaledTL.y);
    }

    private Point.Double scalePoint(Point value, int width, int height) {
        return new Point.Double(
                scaleValue(value.x, extents.x, extents.x + extents.width, 0, width),
                scaleValue(value.y, extents.y, extents.y + extents.height, 0, height));
    }

    @Override
    public void paint(Graphics g) {
        Dimension d = getSize();
        if (!d.equals(lastDimension)) {
            lastDimension = d;
            updateScaled();
        }

        g.setColor(getBackground());
        g.clearRect(0, 0, getWidth(), getHeight());

        Graphics2D g2 = (Graphics2D) g;

        for (Rectangle.Double r : scaledScreens) {
            g.setColor(Color.lightGray);
            g2.fill(r);
        }

        for (Map.Entry<PointDir.Double, Path2D> kvp : tris.entrySet()) {
            Color highlight = kvp.getKey().enabled ? Color.green : Color.red;
            g.setColor(kvp.getValue().contains(mousePos) ? highlight : highlight.darker());
            g2.fill(kvp.getValue());
        }

        for (Rectangle.Double r : scaledScreens){
            g.setColor(Color.black);
            g2.draw(r);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        for (Map.Entry<PointDir.Double, Path2D> kvp : tris.entrySet()){
            PointDir.Double scaled = kvp.getKey();
            if (kvp.getValue().contains(mousePos)) {
                PointDir.Integer orig = scaledPoints.get(kvp.getKey());
                orig.enabled = !orig.enabled;
                scaled.enabled = orig.enabled;
                listeners.forEach(l -> l.accept(orig));
                repaint();
                break;
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {
        mousePos = new Point(-1, -1);
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mousePos = e.getPoint();
        repaint();
    }

    public void addChangeListener(Consumer<PointDir.Integer> e){
        listeners.add(e);
    }
}
