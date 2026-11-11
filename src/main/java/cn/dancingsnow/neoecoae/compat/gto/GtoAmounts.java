package cn.dancingsnow.neoecoae.compat.gto;

import java.math.BigInteger;

/** Exact-value boundaries around GTO's mutable inventory quantities; long IO does not allocate snapshots. */
public final class GtoAmounts {
    public static final BigInteger MAX = BigInteger.ONE.shiftLeft(126).subtract(BigInteger.ONE);
    private GtoAmounts() {}

    /** GTO 0.5.6 uses BigInteger amounts; subtract reservations before exposing a long window. */
    public static long available(BigInteger amount, long removed, long reserved) {
        return available(amount, 0, removed, reserved);
    }

    public static long available(BigInteger amount, long base, long removed, long reserved) {
        if (base < 0) throw new IllegalArgumentException("Negative base window");
        if (reserved < 0) throw new IllegalArgumentException("Negative protected amount");
        BigInteger value = (amount == null ? BigInteger.ZERO : amount)
            .add(BigInteger.valueOf(base)).subtract(BigInteger.valueOf(removed))
            .subtract(BigInteger.valueOf(reserved));
        return value.signum() <= 0 ? 0 : appeng.api.stacks.AEKeyBigMap.saturateToLong(value);
    }

    public static long available(long amount, long base, long removed, long reserved) {
        if (amount < 0) throw new IllegalArgumentException("Negative stock");
        return available(0, amount, base, removed, reserved);
    }

    private static long available(long hi, long lo, long base, long removed, long reserved) {
        if (base < 0) throw new IllegalArgumentException("Negative base window");
        if (reserved < 0) throw new IllegalArgumentException("Negative protected amount");
        long combined = lo + base;
        if (combined < 0) hi++;
        lo = combined & Long.MAX_VALUE;
        if (removed >= 0) {
            long next = lo - removed;
            if (next < 0) {
                if (hi == 0) return 0;
                hi--;
            }
            lo = next & Long.MAX_VALUE;
        } else if (removed == Long.MIN_VALUE) {
            hi++;
        } else {
            long next = lo - removed;
            if (next < 0) hi++;
            lo = next & Long.MAX_VALUE;
        }
        long next = lo - reserved;
        if (next < 0) {
            if (hi == 0) return 0;
            hi--;
        }
        return hi == 0 ? next & Long.MAX_VALUE : Long.MAX_VALUE;
    }
}
