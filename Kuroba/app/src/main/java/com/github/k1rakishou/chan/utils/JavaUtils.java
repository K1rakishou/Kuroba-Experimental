package com.github.k1rakishou.chan.utils;

public class JavaUtils {
    public static boolean in(int value, int[] array) {
        for (int i : array) {
            if (value == i) {
                return true;
            }
        }

        return false;
    }
}
