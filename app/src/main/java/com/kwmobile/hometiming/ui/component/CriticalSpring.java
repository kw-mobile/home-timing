package com.kwmobile.hometiming.ui.component;

/** Unit-mass critical damping: position relative to equilibrium, seconds and radians/second. */
public final class CriticalSpring {
    private CriticalSpring() {}
    public static float displacement(float initial,float velocity,float seconds,float omega) {
        return (initial+(velocity+omega*initial)*seconds)*(float)Math.exp(-omega*seconds);
    }
    public static float velocity(float initial,float velocity,float seconds,float omega) {
        return (velocity-omega*(velocity+omega*initial)*seconds)*(float)Math.exp(-omega*seconds);
    }
}
