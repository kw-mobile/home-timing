package com.kwmobile.hometiming.ui.component;

/** Three separable box passes approximate a Gaussian on the small backdrop only. */
final class GlassBlur {
    private GlassBlur() { }

    static void apply(int[] pixels, int[] scratch, int width, int height, int radius) {
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, scratch, width, height, radius, true);
            blur(scratch, pixels, width, height, radius, false);
        }
    }

    private static void blur(int[] source, int[] target, int width, int height,
            int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int origin = horizontal ? line * width : line;
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int pixel = source[origin + Math.max(0, Math.min(length - 1, k)) * step];
                a += pixel >>> 24; r += (pixel >>> 16) & 255;
                g += (pixel >>> 8) & 255; b += pixel & 255;
            }
            for (int position = 0; position < length; position++) {
                target[origin + position * step] = (a / count << 24) | (r / count << 16)
                        | (g / count << 8) | b / count;
                int previous = source[origin + Math.max(0, position - radius) * step];
                int next = source[origin + Math.min(length - 1, position + radius + 1) * step];
                a += (next >>> 24) - (previous >>> 24);
                r += ((next >>> 16) & 255) - ((previous >>> 16) & 255);
                g += ((next >>> 8) & 255) - ((previous >>> 8) & 255);
                b += (next & 255) - (previous & 255);
            }
        }
    }
}
