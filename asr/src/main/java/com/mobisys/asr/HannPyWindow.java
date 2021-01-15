package com.mobisys.asr;

import be.tarsos.dsp.util.fft.WindowFunction;

public class HannPyWindow extends WindowFunction {
    public HannPyWindow() {
    }

    protected float value(int length, int index) {
        double result;
        double w = 0;
        double q = 2 * Math.PI / length;
        result = -Math.PI + index * q;
        for (int k = 0; k < 2; k++) {
            w += 0.5 * Math.cos(k * result);
        }
        return (float) w;
    }
}
