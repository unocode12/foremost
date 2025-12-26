package com.unocode.creational.singleton;

import java.io.Serial;
import java.io.Serializable;

public class Settings implements Serializable {

    private static volatile Settings instance; //static method not allowed to access instance field
    private final static Settings INSTANCE = new Settings(); //eager initialize

    private Settings() {

    }

    /*public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }*/

    /*public static synchronized Settings getInstance() {
        if (instance == null)  {
            instance = new Settings();
        }
        return instance;
    }*/

    /*public static Settings getInstance() { //double checked locking, volatile required, java 1.5 or higher version required
        if (instance == null) {
            synchronized (Settings.class) {
                if (instance == null) {
                    instance = new Settings();
                }
            }
        }
        return instance;
    }*/

    private static class SettingsHolder {
        private static final Settings INSTANCE = new Settings();
    }

    public static Settings getInstance() {
        return SettingsHolder.INSTANCE;
    }

    @Serial
    protected Object readResolve() {
        return getInstance();
    }


}
