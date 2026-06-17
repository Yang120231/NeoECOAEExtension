package cn.dancingsnow.neoecoae.gui.ldlib.state;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

public record NEStorageUiState(
        BlockPos pos,
        List<NEStorageUiTypeState> typeStates,
        List<NEStorageUiMatrixState> matrixStates,
        long storedEnergy,
        long maxEnergy,
        boolean formed,
        boolean infiniteComponentInstalled,
        int infiniteComponentCount,
        int requiredInfiniteComponentCount,
        boolean fullL9MatrixStorage,
        boolean infiniteStorageUnlocked,
        int l9MatrixDriveCount,
        int requiredL9MatrixDriveCount,
        long l9MatrixStorageCapacityBytes,
        long requiredInfiniteStorageCapacityBytes) {
    public static NEStorageUiState empty(BlockPos pos) {
        return new NEStorageUiState(
                pos,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                false,
                false,
                0,
                64,
                false,
                false,
                0,
                0,
                0,
                1_000_000_000_001L);
    }

    public long totalUsedTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.usedTypes());
        }
        return sum;
    }

    public long totalTypes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.totalTypes());
        }
        return sum;
    }

    public long totalUsedBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.usedBytes());
        }
        return sum;
    }

    public long totalBytes() {
        long sum = 0;
        for (var ts : typeStates) {
            sum = saturatedAdd(sum, ts.totalBytes());
        }
        return sum;
    }

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
