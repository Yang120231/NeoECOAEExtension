package cn.dancingsnow.neoecoae.compat.gto;

/** Exact planner provenance takes precedence over structurally equivalent cached diagnostics. */
public interface ECOGtoPlanOrigin {
    boolean neoecoae$isNativeGtoPlan();
    void neoecoae$markNativeGtoPlan();
}
