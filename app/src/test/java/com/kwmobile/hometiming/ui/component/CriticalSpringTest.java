package com.kwmobile.hometiming.ui.component;
import org.junit.Test;
import static org.junit.Assert.*;
public class CriticalSpringTest {
    @Test public void releaseConvergesWithoutOscillationFromRest() {
        float previous=1;
        for(int frame=0;frame<120;frame++) {
            float current=CriticalSpring.displacement(1,0,frame/120f,26);
            assertTrue(current>=0);assertTrue(current<=previous);previous=current;
        }
        assertEquals(0,previous,.0001f);
    }
    @Test public void retargetKeepsTheInitialPositionAndVelocity() {
        assertEquals(.02f,CriticalSpring.displacement(.02f,-.4f,0,26),.000001f);
        assertEquals(-.4f,CriticalSpring.velocity(.02f,-.4f,0,26),.000001f);
        assertEquals(0,CriticalSpring.displacement(.02f,-.4f,1,26),.000001f);
    }
}
