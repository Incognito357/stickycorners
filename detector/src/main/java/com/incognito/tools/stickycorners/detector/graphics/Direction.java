package com.incognito.tools.stickycorners.detector.graphics;

/**
 * Created by jahorton on 1/2/2019
 */
public enum Direction {
    UP, RIGHT, DOWN, LEFT;

    public boolean isHorizontalBoundary(){
        return this == UP || this == DOWN;
    }

    public boolean isVerticalBoundary(){
        return this == RIGHT || this == LEFT;
    }
}
