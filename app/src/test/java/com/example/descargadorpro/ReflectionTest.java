package com.example.descargadorpro;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionTest {
    @Test
    public void testUpdateChannel() {
        try {
            Class<?> clazz = Class.forName("com.yausername.youtubedl_android.UpdateChannel");
            System.out.println("=== UPDATECHANNEL INFO ===");
            for (Field f : clazz.getDeclaredFields()) {
                System.out.println("FIELD: " + f.getName() + " TYPE: " + f.getType().getName());
            }
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println("METHOD: " + m.getName());
            }
            for (Class<?> c : clazz.getDeclaredClasses()) {
                System.out.println("CLASS: " + c.getName());
            }
            System.out.println("==========================");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
