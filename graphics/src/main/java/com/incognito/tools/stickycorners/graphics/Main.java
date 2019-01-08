package com.incognito.tools.stickycorners.graphics;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Created by jahorton on 9/13/2018
 */
public class Main {
    public static void main(String[] args){
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        System.out.println(devices.length);
        for (GraphicsDevice g : devices){
            Rectangle r = g.getDefaultConfiguration().getBounds();
            System.out.println(r.x + " " + r.y + " " + r.width + " " + r.height);
        }
    }
}
