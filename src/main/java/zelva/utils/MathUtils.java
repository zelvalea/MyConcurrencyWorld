package zelva.utils;

public final class MathUtils {
    public static final double _2PI         = 2 * Math.PI;
    private static final int MAX_PRECISION  = 2 << 15; // 32 bit
    private static final int _N             = MAX_PRECISION - 1;
    private static final int _COS           = MAX_PRECISION >> 2;
    private static final double _H          = MAX_PRECISION/_2PI;
    /** @see todo:
     * @author coderbot16   Author of the original (and very clever) implementation in Rust:
     *  <a href="https://gitlab.com/coderbot16/i73/-/tree/master/i73-trig/src">...</a>
     */
    private static final float[] SINE_TABLE = new float[MAX_PRECISION];
    static {
        int i = 0;
        for (double delta = _2PI / MAX_PRECISION; i < MAX_PRECISION; ++i) {
            SINE_TABLE[i] = (float) Math.sin(i * delta);
        }
    }

    private MathUtils() {}

    // jni is slow, please wait for update
    // https://openjdk.java.net/jeps/419

    public static float _sin(double value) {
        return SINE_TABLE[_N & ((int)(value*_H))];
    }
    public static float _cos(double value) {
        return SINE_TABLE[_N & ((int)(value*_H+_COS))];
    }
}
