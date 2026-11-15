package com.kwmobile.hometiming.data.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import com.kwmobile.hometiming.domain.HouseholdNames;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Pinned COCO EfficientDet model, CPU only. All inference stays on this device. */
public final class ProductObjectRecognizer implements AutoCloseable {
    private final Context context;
    private Interpreter model;
    private List<String> labels;
    public ProductObjectRecognizer(Context context) { this.context=context.getApplicationContext(); }
    private void load() throws IOException {
        if(model!=null)return;
        byte[] data;
        try(InputStream in=context.getAssets().open("models/efficientdet_lite2.tflite"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);data=out.toByteArray();
        }
        ByteBuffer bytes=ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder()); bytes.put(data).rewind();
        Interpreter created;
        try{created=new Interpreter(bytes,new Interpreter.Options().setNumThreads(2));}
        catch(LinkageError unavailable){throw new IOException("Object runtime unavailable",unavailable);}
        try {
            created.allocateTensors();
            if(created.getInputTensor(0).dataType()!=DataType.UINT8 || !Arrays.equals(created.getInputTensor(0).shape(),new int[]{1,448,448,3})
                    || created.getOutputTensorCount()!=4) throw new IOException("Unexpected object model contract");
            List<String> loaded=new ArrayList<>();
            try(BufferedReader in=new BufferedReader(new InputStreamReader(context.getAssets().open("models/coco_labels.txt"), java.nio.charset.StandardCharsets.UTF_8))){
                String line;while((line=in.readLine())!=null)loaded.add(line.trim());
            }
            labels=loaded;model=created;
        } catch(Exception e){created.close();throw e;}
    }
    public synchronized List<String> recognize(Bitmap source,int rotation) throws IOException {
        load();
        Bitmap oriented=source, scaled=null;
        try {
            if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation);oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);}
            scaled=Bitmap.createScaledBitmap(oriented,448,448,true);
            int[] pixels=new int[448*448];scaled.getPixels(pixels,0,448,0,0,448,448);
            ByteBuffer input=ByteBuffer.allocateDirect(448*448*3).order(ByteOrder.nativeOrder());
            for(int pixel:pixels){input.put((byte)(pixel>>16));input.put((byte)(pixel>>8));input.put((byte)pixel);}input.rewind();
            int n=model.getOutputTensor(0).shape()[1];
            float[][][] boxes=new float[1][n][4];float[][] classes=new float[1][n],scores=new float[1][n];float[] count=new float[1];
            Map<Integer,Object> outputs=new HashMap<>();outputs.put(0,boxes);outputs.put(1,classes);outputs.put(2,scores);outputs.put(3,count);
            model.runForMultipleInputsOutputs(new Object[]{input},outputs);
            List<HouseholdNames.Observation> found=new ArrayList<>();
            for(int i=0;i<Math.min(n,(int)count[0]);i++){
                int label=(int)classes[0][i];float[] b=boxes[0][i];
                if(label<0||label>=labels.size())continue;
                double left=Math.max(0,b[1]),right=Math.min(1,b[3]),top=Math.max(0,b[0]),bottom=Math.min(1,b[2]);
                found.add(new HouseholdNames.Observation(labels.get(label),scores[0][i],(left+right)/2,(top+bottom)/2,Math.max(0,right-left)*Math.max(0,bottom-top)));
            }
            return HouseholdNames.visual(found);
        } finally {
            if(scaled!=null&&scaled!=source&&scaled!=oriented)scaled.recycle();
            if(oriented!=source)oriented.recycle();
        }
    }
    @Override public synchronized void close(){if(model!=null){model.close();model=null;}}
}
