package dev.limucc.histb.client.pattern;

import dev.limucc.histb.client.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A coordinate transform applied to pattern cells before matching, so a pattern
 * can be found rotated/mirrored. Each Orientation maps a pattern-local (x,y,z)
 * to a rotated/mirrored offset, and reports the resulting bounding size.
 *
 * We enumerate the enabled set from config (Y/X/Z rotations + mirror). To keep it
 * fast and predictable we generate the 24 unique cube rotations (optionally) and
 * their mirrors, then filter to those the user enabled, deduped.
 */
public final class Orientation {

    /** sign/axis permutation: out = (m[0]·in)+off … implemented directly below. */
    public final int xx, xy, xz, yx, yy, yz, zx, zy, zz;
    public final String label;

    private Orientation(int xx,int xy,int xz,int yx,int yy,int yz,int zx,int zy,int zz,String label){
        this.xx=xx;this.xy=xy;this.xz=xz;this.yx=yx;this.yy=yy;this.yz=yz;this.zx=zx;this.zy=zy;this.zz=zz;this.label=label;
    }

    /** The identity (no rotation/mirror). */
    public static Orientation identity() {
        return new Orientation(1,0,0, 0,1,0, 0,0,1, "id");
    }

    /** Build the enabled orientation list from config. Always includes identity. */
    public static List<Orientation> enabled(ModConfig cfg) {
        List<Orientation> out = new ArrayList<>();
        // Base rotations around Y (most common): 0/90/180/270
        out.add(identity());
        if (cfg.rotateY) {
            out.add(rotY(90));
            out.add(rotY(180));
            out.add(rotY(270));
        }
        if (cfg.rotateX) {
            out.add(rotX(90));
            out.add(rotX(180));
            out.add(rotX(270));
        }
        if (cfg.rotateZ) {
            out.add(rotZ(90));
            out.add(rotZ(180));
            out.add(rotZ(270));
        }
        if (cfg.mirror) {
            // mirror across X (negate x axis) combined with current set's identity + Y rots
            out.add(mirrorX());
            if (cfg.rotateY) {
                out.add(compose(mirrorX(), rotY(90)));
                out.add(compose(mirrorX(), rotY(180)));
                out.add(compose(mirrorX(), rotY(270)));
            }
        }
        return dedupe(out);
    }

    // ── rotation builders (right-handed, 90° steps) ───────────────────────────
    private static Orientation rotY(int deg) {
        return switch (deg) {
            case 90  -> new Orientation(0,0,1, 0,1,0, -1,0,0, "Y90");
            case 180 -> new Orientation(-1,0,0, 0,1,0, 0,0,-1, "Y180");
            case 270 -> new Orientation(0,0,-1, 0,1,0, 1,0,0, "Y270");
            default  -> identity();
        };
    }
    private static Orientation rotX(int deg) {
        return switch (deg) {
            case 90  -> new Orientation(1,0,0, 0,0,-1, 0,1,0, "X90");
            case 180 -> new Orientation(1,0,0, 0,-1,0, 0,0,-1, "X180");
            case 270 -> new Orientation(1,0,0, 0,0,1, 0,-1,0, "X270");
            default  -> identity();
        };
    }
    private static Orientation rotZ(int deg) {
        return switch (deg) {
            case 90  -> new Orientation(0,-1,0, 1,0,0, 0,0,1, "Z90");
            case 180 -> new Orientation(-1,0,0, 0,-1,0, 0,0,1, "Z180");
            case 270 -> new Orientation(0,1,0, -1,0,0, 0,0,1, "Z270");
            default  -> identity();
        };
    }
    private static Orientation mirrorX() {
        return new Orientation(-1,0,0, 0,1,0, 0,0,1, "mirX");
    }

    private static Orientation compose(Orientation a, Orientation b) {
        // result = a ∘ b  (apply b then a)
        return new Orientation(
            a.xx*b.xx + a.xy*b.yx + a.xz*b.zx,  a.xx*b.xy + a.xy*b.yy + a.xz*b.zy,  a.xx*b.xz + a.xy*b.yz + a.xz*b.zz,
            a.yx*b.xx + a.yy*b.yx + a.yz*b.zx,  a.yx*b.xy + a.yy*b.yy + a.yz*b.zy,  a.yx*b.xz + a.yy*b.yz + a.yz*b.zz,
            a.zx*b.xx + a.zy*b.yx + a.zz*b.zx,  a.zx*b.xy + a.zy*b.yy + a.zz*b.zy,  a.zx*b.xz + a.zy*b.yz + a.zz*b.zz,
            a.label + "+" + b.label);
    }

    private static List<Orientation> dedupe(List<Orientation> in) {
        List<Orientation> out = new ArrayList<>();
        for (Orientation o : in) {
            boolean dup = false;
            for (Orientation e : out) {
                if (e.xx==o.xx&&e.xy==o.xy&&e.xz==o.xz&&e.yx==o.yx&&e.yy==o.yy&&e.yz==o.yz&&e.zx==o.zx&&e.zy==o.zy&&e.zz==o.zz) { dup=true; break; }
            }
            if (!dup) out.add(o);
        }
        return out;
    }

    /** Rotate a local cell coordinate (integer offsets). */
    public int tx(int x,int y,int z){ return xx*x + xy*y + xz*z; }
    public int ty(int x,int y,int z){ return yx*x + yy*y + yz*z; }
    public int tz(int x,int y,int z){ return zx*x + zy*y + zz*z; }
}
