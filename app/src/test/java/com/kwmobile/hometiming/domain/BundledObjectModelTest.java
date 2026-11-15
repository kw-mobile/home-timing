package com.kwmobile.hometiming.domain;

import org.junit.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

public class BundledObjectModelTest {
    @Test public void modelMatchesPinnedArtifactAndLabelIndices() throws Exception {
        byte[] bytes=Files.readAllBytes(Paths.get("src/main/assets/models/efficientdet_lite2.tflite"));
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
        assertEquals("bc30295c5e1436cdd675f63e4f56e4f5110fc0a2057087d9385e06bc95900116",hex.toString());
        List<String> labels=Files.readAllLines(Paths.get("src/main/assets/models/coco_labels.txt"));
        assertEquals(90,labels.size());assertEquals("toothbrush",labels.get(89));
        assertEquals(80,labels.stream().filter(x->!x.equals("n/a")).count());
    }
}
