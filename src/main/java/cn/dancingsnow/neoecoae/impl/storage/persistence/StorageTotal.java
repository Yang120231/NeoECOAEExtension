package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.U126;
import cn.dancingsnow.neoecoae.compat.gto.GtoAmounts;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import java.math.BigInteger;

/** Mutable native total. Beyond U126, small transfers accumulate in a primitive delta until an exact read. */
final class StorageTotal {
    private final U126 value = new U126();
    private BigInteger bigBase;
    private long delta;

    void add(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative storage addition");
        if (bigBase != null) { accumulate(amount); return; }
        long accepted = value.add(amount);
        if (accepted < amount) bigBase = GtoAmounts.MAX.add(BigInteger.valueOf(amount - accepted));
    }

    void subtract(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative storage subtraction");
        if (bigBase != null) { accumulate(-amount); return; }
        if (value.toLong() < amount) throw new IllegalArgumentException("Negative storage total");
        value.subtract(amount);
    }

    void add(BigInteger amount) {
        if (amount.signum() < 0) subtract(HugeAmount.of(amount.negate()));
        else add(HugeAmount.of(amount));
    }

    void subtract(BigInteger amount) {
        if (amount.signum() < 0) add(HugeAmount.of(amount.negate()));
        else subtract(HugeAmount.of(amount));
    }

    void add(HugeAmount amount) {
        if (!amount.isBig()) add(amount.toLongSaturated());
        else set(snapshot().add(amount));
    }

    void subtract(HugeAmount amount) {
        if (!amount.isBig()) { subtract(amount.toLongSaturated()); return; }
        HugeAmount total = snapshot();
        if (total.compareTo(amount) < 0) throw new IllegalArgumentException("Negative storage total");
        set(total.subtract(amount));
    }

    private void accumulate(long change) {
        long next = delta + change;
        if (((delta ^ next) & (change ^ next)) >= 0L) {
            delta = next;
            return;
        }
        materialize();
        if (bigBase != null) delta = change;
        else if (change >= 0) add(change);
        else subtract(-change);
    }

    private void materialize() {
        if (bigBase != null && delta != 0) set(HugeAmount.of(bigBase.add(BigInteger.valueOf(delta))));
    }

    private void set(HugeAmount amount) {
        BigInteger exact = amount.toBigInteger();
        delta = 0;
        if (exact.bitLength() > 126) {
            bigBase = exact;
            value.saturate();
        } else {
            bigBase = null;
            value.fromByteArray(exact.toByteArray());
        }
    }

    long visible() { return bigBase != null ? Long.MAX_VALUE : value.toLong(); }

    void clear() {
        value.setValue(0);
        bigBase = null;
        delta = 0;
    }

    HugeAmount snapshot() {
        materialize();
        if (bigBase != null) return HugeAmount.of(bigBase);
        return value.isAboveLong() ? HugeAmount.of(cn.dancingsnow.neoecoae.crafting.amount.U126.fromGto(value))
            : HugeAmount.of(value.lo());
    }
}
