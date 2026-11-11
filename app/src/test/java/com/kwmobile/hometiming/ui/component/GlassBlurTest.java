package com.kwmobile.hometiming.ui.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class GlassBlurTest {
    @Test public void uniformBackdropKeepsItsColorThroughEdges() {
        int[] pixels = new int[35];
        Arrays.fill(pixels, 0xFF486C91);
        GlassBlur.apply(pixels, new int[pixels.length], 7, 5, 3);
        for (int pixel : pixels) assertEquals(0xFF486C91, pixel);
    }

    @Test public void hardEdgeBecomesContinuousWithoutRepeatedBands() {
        int[] pixels = new int[41];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i < 20 ? 0xFF000000 : 0xFFFFFFFF;
        GlassBlur.apply(pixels, new int[pixels.length], 41, 1, 3);
        int previous = 0;
        for (int pixel : pixels) {
            int value = pixel & 255;
            assertEquals(255, pixel >>> 24);
            assertTrue(value >= previous);
            previous = value;
        }
        assertTrue((pixels[19] & 255) > 0);
        assertTrue((pixels[20] & 255) < 255);
    }

    @Test public void singlePixelIsSafeWhenKernelExceedsImage() {
        int[] pixels = {0xFFABCDEF};
        GlassBlur.apply(pixels, new int[1], 1, 1, 5);
        assertEquals(0xFFABCDEF, pixels[0]);
    }
}
