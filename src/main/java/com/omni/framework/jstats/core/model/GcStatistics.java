package com.omni.framework.jstats.core.model;

public class GcStatistics {
    private final long s0c, s1c, s0u, s1u, ec, eu, oc, ou, mc, mu;
    private final int ygc, fgc;
    private final double ygct, fgct, gct;

    public GcStatistics(long s0c, long s1c, long s0u, long s1u, long ec, long eu,
                        long oc, long ou, long mc, long mu,
                        int ygc, int fgc, double ygct, double fgct, double gct) {
        this.s0c = s0c; this.s1c = s1c; this.s0u = s0u; this.s1u = s1u;
        this.ec = ec; this.eu = eu; this.oc = oc; this.ou = ou;
        this.mc = mc; this.mu = mu;
        this.ygc = ygc; this.fgc = fgc;
        this.ygct = ygct; this.fgct = fgct; this.gct = gct;
    }
    public long getS0c() { return s0c; }
    public long getS1c() { return s1c; }
    public long getS0u() { return s0u; }
    public long getS1u() { return s1u; }
    public long getEc() { return ec; }
    public long getEu() { return eu; }
    public long getOc() { return oc; }
    public long getOu() { return ou; }
    public long getMc() { return mc; }
    public long getMu() { return mu; }
    public int getYgc() { return ygc; }
    public int getFgc() { return fgc; }
    public double getYgct() { return ygct; }
    public double getFgct() { return fgct; }
    public double getGct() { return gct; }
}