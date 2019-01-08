package com.incognito.tools.stickycorners.detector.graphics;

import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * Created by jahorton on 1/2/2019
 */
public abstract class PointDir {
    public Direction direction;
    public Corner corner;
    public boolean enabled;

    public abstract Point2D toPoint();

    public static class Integer extends PointDir {
        public int x;
        public int y;

        public Integer(int x, int y, Direction direction, Corner corner) {
            this(x, y, direction, corner, true);
        }

        public Integer(int x, int y, Direction direction, Corner corner, boolean enabled){
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.corner = corner;
            this.enabled = enabled;
        }

        @Override
        public Point toPoint(){
            return new Point(x, y);
        }
    }

    public static class Double extends PointDir {
        public double x;
        public double y;

        public Double(double x, double y, Direction direction, Corner corner) {
            this(x, y, direction, corner, true);
        }

        public Double(double x, double y, Direction direction, Corner corner, boolean enabled){
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.corner = corner;
            this.enabled = enabled;
        }

        @Override
        public Point.Double toPoint(){
            return new Point.Double(x, y);
        }
    }
}