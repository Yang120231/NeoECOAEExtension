package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.U126;
import java.math.BigInteger;

/** Exact-value boundaries around GTO's mutable inventory quantities; long IO does not allocate snapshots. */
public final class GtoAmounts {
    public static final BigInteger MAX = BigInteger.ONE.shiftLeft(126).subtract(BigInteger.ONE);
    private GtoAmounts() {}

    public static U126 fromExact(BigInteger amount) {
        if (amount.signum() < 0 || amount.bitLength() > 126) throw new IllegalArgumentException("Outside U126");
        U126 result = new U126();
        result.fromByteArray(amount.toByteArray());
        return result;
    }

    public static BigInteger exact(U126 amount) {
        return amount == null ? BigInteger.ZERO : amount.isAboveLong()
            ? new BigInteger(amount.toByteArray()) : BigInteger.valueOf(amount.lo());
    }

    /** Saturates only after both reservations; saturating the stock first would hide usable overflow stock. */
    public static long available(U126 amount, long removed, long reserved) {
        return available(amount, 0, removed, reserved);
    }

    public static long available(U126 amount, long base, long removed, long reserved) {
        if (base < 0) throw new IllegalArgumentException("Negative base window");
        if (reserved < 0) throw new IllegalArgumentException("Negative protected amount");
        long hi = amount == null ? 0 : amount.hi(), lo = amount == null ? 0 : amount.lo();
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
