package android.util;

import android.graphics.Path;
import java.util.ArrayList;

/**
 * Parses SVG path data (as used by VectorDrawable) into android.graphics.Path.
 * {@link PathData} keeps the commands so two paths with the same shape can morph.
 * framework-internal: android.jar does not include this class.
 */
public class PathParser {
    public static Path createPathFromPathData(String pathData) {
        Path path = new Path();
        parse(pathData, path);
        return path;
    }

    /**
     * True when both values are {@link PathData} with the same commands and the same
     * number of parameters on each command. Other objects cannot morph.
     */
    public static boolean canMorph(Object a, Object b) {
        if (!(a instanceof PathData) || !(b instanceof PathData)) return false;
        PathData pa = (PathData) a;
        PathData pb = (PathData) b;
        if (pa.mNodes.length != pb.mNodes.length) return false;
        for (int i = 0; i < pa.mNodes.length; i++) {
            if (pa.mNodes[i].type != pb.mNodes[i].type) return false;
            if (pa.mNodes[i].params.length != pb.mNodes[i].params.length) return false;
        }
        return true;
    }

    /** Writes the interpolated parameters into {@code out}. No-op when the paths cannot morph. */
    public static void interpolatePathData(PathData out, PathData from, PathData to, float fraction) {
        if (!canMorph(out, from) || !canMorph(from, to)) return;
        for (int i = 0; i < from.mNodes.length; i++) {
            float[] start = from.mNodes[i].params;
            float[] end = to.mNodes[i].params;
            float[] dst = out.mNodes[i].params;
            for (int j = 0; j < start.length; j++) dst[j] = start[j] + (end[j] - start[j]) * fraction;
        }
    }

    /** One SVG path, as commands plus their raw parameters (relative commands stay relative). */
    public static final class PathData {
        private Node[] mNodes;

        public PathData() {
            mNodes = new Node[0];
        }

        public PathData(String pathData) {
            mNodes = parseNodes(pathData);
        }

        public PathData(PathData src) {
            mNodes = new Node[src.mNodes.length];
            for (int i = 0; i < mNodes.length; i++) mNodes[i] = new Node(src.mNodes[i]);
        }

        /** Copies parameters when the paths can morph. Replaces the nodes otherwise. */
        public void setPathData(PathData src) {
            if (src == null) return;
            if (!canMorph(this, src)) {
                mNodes = new PathData(src).mNodes;
                return;
            }
            for (int i = 0; i < mNodes.length; i++) {
                mNodes[i].type = src.mNodes[i].type;
                System.arraycopy(src.mNodes[i].params, 0, mNodes[i].params, 0, mNodes[i].params.length);
            }
        }

        /** Clears {@code path} and appends this path data. */
        public void toPath(Path path) {
            path.reset();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mNodes.length; i++) {
                Node node = mNodes[i];
                sb.append(node.type);
                for (int j = 0; j < node.params.length; j++) {
                    sb.append(' ');
                    sb.append(node.params[j]);
                }
                sb.append(' ');
            }
            parse(sb.toString(), path);
        }
    }

    private static final class Node {
        char type;
        float[] params;

        Node(char type, float[] params) {
            this.type = type;
            this.params = params;
        }

        Node(Node other) {
            type = other.type;
            params = other.params.clone();
        }
    }

    private static Node[] parseNodes(String d) {
        if (d == null || d.length() == 0) return new Node[0];
        ArrayList<Node> nodes = new ArrayList<Node>();
        int n = d.length();
        int i = 0;
        char cmd = 0;
        float[] one = new float[1];
        while (i < n) {
            while (i < n && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
            if (i >= n) break;
            char c = d.charAt(i);
            if (Character.isLetter(c) && c != 'e' && c != 'E') {
                cmd = c;
                i++;
                if (cmd == 'z' || cmd == 'Z') {
                    nodes.add(new Node(cmd, new float[0]));
                    continue;
                }
            } else if (cmd == 'M') {
                cmd = 'L';
            } else if (cmd == 'm') {
                cmd = 'l';
            } else if (cmd == 0 || cmd == 'z' || cmd == 'Z') {
                break;
            }
            int count = argCount(cmd);
            if (count < 0) {
                i++;
                continue;
            }
            float[] args = new float[count];
            int got = 0;
            for (int k = 0; k < count; k++) {
                while (i < n && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
                if (i >= n) break;
                if ((cmd == 'a' || cmd == 'A') && (k == 3 || k == 4)) {
                    args[k] = d.charAt(i) == '1' ? 1f : 0f;
                    i++;
                    got++;
                    continue;
                }
                int next = readNumber(d, i, one);
                if (next < 0) break;
                args[k] = one[0];
                i = next;
                got++;
            }
            if (got < count) break;
            nodes.add(new Node(cmd, args));
        }
        return nodes.toArray(new Node[nodes.size()]);
    }

    private static int argCount(char cmd) {
        switch (Character.toLowerCase(cmd)) {
            case 'm': case 'l': case 't': return 2;
            case 'h': case 'v': return 1;
            case 'c': return 6;
            case 's': case 'q': return 4;
            case 'a': return 7;
            default: return -1;
        }
    }

    /** Reads one SVG number at {@code i}. Returns the index after it, or -1. Writes {@code out[0]}. */
    private static int readNumber(String d, int i, float[] out) {
        int n = d.length();
        if (i >= n) return -1;
        int start = i;
        char sign = d.charAt(i);
        if (sign == '-' || sign == '+') i++;
        int digits = 0;
        boolean dot = false;
        boolean exp = false;
        while (i < n) {
            char ch = d.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits++;
                i++;
            } else if (ch == '.' && !dot && !exp) {
                dot = true;
                i++;
            } else if ((ch == 'e' || ch == 'E') && !exp && digits > 0) {
                exp = true;
                i++;
                if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
            } else {
                break;
            }
        }
        if (digits == 0 || i == start) return -1;
        out[0] = Float.parseFloat(d.substring(start, i));
        return i;
    }

    public static void parse(String d, Path path) {
        if (d == null) return;
        int n = d.length();
        int i = 0;
        char cmd = 0;
        float cx = 0, cy = 0, sx = 0, sy = 0; // current point and subpath start
        float lcx = 0, lcy = 0; // last control point
        char prev = 0;
        float[] args = new float[7];
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            if (Character.isLetter(c) && c != 'e' && c != 'E') {
                cmd = c;
                i++;
                if (cmd == 'z' || cmd == 'Z') {
                    path.close();
                    cx = sx;
                    cy = sy;
                    lcx = cx;
                    lcy = cy;
                    prev = cmd;
                    continue;
                }
            }
            int count;
            switch (Character.toLowerCase(cmd)) {
                case 'm': case 'l': case 't': count = 2; break;
                case 'h': case 'v': count = 1; break;
                case 'c': count = 6; break;
                case 's': case 'q': count = 4; break;
                case 'a': count = 7; break;
                default: i++; continue;
            }
            int got = 0;
            for (int k = 0; k < count; k++) {
                // skip separators
                while (i < n && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
                if (i >= n) break;
                if (Character.toLowerCase(cmd) == 'a' && (k == 3 || k == 4)) {
                    // arc flags may be written without separators
                    args[k] = d.charAt(i) == '1' ? 1 : 0;
                    i++;
                    got++;
                    continue;
                }
                int start = i;
                if (d.charAt(i) == '-' || d.charAt(i) == '+') i++;
                boolean dot = false, exp = false;
                while (i < n) {
                    char ch = d.charAt(i);
                    if (ch >= '0' && ch <= '9') {
                        i++;
                    } else if (ch == '.' && !dot && !exp) {
                        dot = true;
                        i++;
                    } else if ((ch == 'e' || ch == 'E') && !exp) {
                        exp = true;
                        i++;
                        if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
                    } else {
                        break;
                    }
                }
                if (i == start) break;
                args[k] = Float.parseFloat(d.substring(start, i));
                got++;
            }
            if (got < count) break;
            boolean rel = Character.isLowerCase(cmd);
            char lc = Character.toLowerCase(cmd);
            switch (lc) {
                case 'm': {
                    float x = args[0] + (rel ? cx : 0), y = args[1] + (rel ? cy : 0);
                    path.moveTo(x, y);
                    cx = sx = x;
                    cy = sy = y;
                    lcx = cx;
                    lcy = cy;
                    cmd = rel ? 'l' : 'L';
                    break;
                }
                case 'l': {
                    float x = args[0] + (rel ? cx : 0), y = args[1] + (rel ? cy : 0);
                    path.lineTo(x, y);
                    cx = x;
                    cy = y;
                    lcx = cx;
                    lcy = cy;
                    break;
                }
                case 'h': {
                    float x = args[0] + (rel ? cx : 0);
                    path.lineTo(x, cy);
                    cx = x;
                    lcx = cx;
                    lcy = cy;
                    break;
                }
                case 'v': {
                    float y = args[0] + (rel ? cy : 0);
                    path.lineTo(cx, y);
                    cy = y;
                    lcx = cx;
                    lcy = cy;
                    break;
                }
                case 'c': {
                    float ox = rel ? cx : 0, oy = rel ? cy : 0;
                    float x1 = args[0] + ox, y1 = args[1] + oy, x2 = args[2] + ox, y2 = args[3] + oy, x = args[4] + ox, y = args[5] + oy;
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lcx = x2;
                    lcy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 's': {
                    float ox = rel ? cx : 0, oy = rel ? cy : 0;
                    float x1 = cx, y1 = cy;
                    char p = Character.toLowerCase(prev);
                    if (p == 'c' || p == 's') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    float x2 = args[0] + ox, y2 = args[1] + oy, x = args[2] + ox, y = args[3] + oy;
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lcx = x2;
                    lcy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'q': {
                    float ox = rel ? cx : 0, oy = rel ? cy : 0;
                    float x1 = args[0] + ox, y1 = args[1] + oy, x = args[2] + ox, y = args[3] + oy;
                    path.quadTo(x1, y1, x, y);
                    lcx = x1;
                    lcy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 't': {
                    float ox = rel ? cx : 0, oy = rel ? cy : 0;
                    float x1 = cx, y1 = cy;
                    char p = Character.toLowerCase(prev);
                    if (p == 'q' || p == 't') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    float x = args[0] + ox, y = args[1] + oy;
                    path.quadTo(x1, y1, x, y);
                    lcx = x1;
                    lcy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'a': {
                    float x = args[5] + (rel ? cx : 0), y = args[6] + (rel ? cy : 0);
                    drawArc(path, cx, cy, x, y, args[0], args[1], args[2], args[3] != 0, args[4] != 0);
                    cx = x;
                    cy = y;
                    lcx = cx;
                    lcy = cy;
                    break;
                }
            }
            prev = cmd;
        }
    }

    /** Converts an SVG elliptical arc to cubic segments (W3C implementation notes F.6). */
    private static void drawArc(Path p, float x0, float y0, float x1, float y1, float a, float b, float theta, boolean isMoreThanHalf, boolean isPositiveArc) {
        if (a == 0 || b == 0) {
            p.lineTo(x1, y1);
            return;
        }
        a = Math.abs(a);
        b = Math.abs(b);
        double thetaD = Math.toRadians(theta);
        double cosTheta = Math.cos(thetaD);
        double sinTheta = Math.sin(thetaD);
        double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
        double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
        double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
        double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;
        double dx = x0p - x1p;
        double dy = y0p - y1p;
        double xm = (x0p + x1p) / 2;
        double ym = (y0p + y1p) / 2;
        double dsq = dx * dx + dy * dy;
        if (dsq == 0.0) return;
        double disc = 1.0 / dsq - 1.0 / 4.0;
        if (disc < 0.0) {
            float adjust = (float) (Math.sqrt(dsq) / 1.99999);
            drawArc(p, x0, y0, x1, y1, a * adjust, b * adjust, theta, isMoreThanHalf, isPositiveArc);
            return;
        }
        double s = Math.sqrt(disc);
        double sdx = s * dx;
        double sdy = s * dy;
        double cx, cy;
        if (isMoreThanHalf == isPositiveArc) {
            cx = xm - sdy;
            cy = ym + sdx;
        } else {
            cx = xm + sdy;
            cy = ym - sdx;
        }
        double eta0 = Math.atan2((y0p - cy), (x0p - cx));
        double eta1 = Math.atan2((y1p - cy), (x1p - cx));
        double sweep = (eta1 - eta0);
        if (isPositiveArc != (sweep >= 0)) {
            if (sweep > 0) sweep -= 2 * Math.PI;
            else sweep += 2 * Math.PI;
        }
        cx *= a;
        cy *= b;
        double tcx = cx;
        cx = cx * cosTheta - cy * sinTheta;
        cy = tcx * sinTheta + cy * cosTheta;
        arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
    }

    private static void arcToBezier(Path p, double cx, double cy, double a, double b, double e1x, double e1y, double theta, double start, double sweep) {
        int numSegments = (int) Math.ceil(Math.abs(sweep * 4 / Math.PI));
        double eta1 = start;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        double cosEta1 = Math.cos(eta1);
        double sinEta1 = Math.sin(eta1);
        double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
        double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);
        double anglePerSegment = sweep / numSegments;
        for (int i = 0; i < numSegments; i++) {
            double eta2 = eta1 + anglePerSegment;
            double sinEta2 = Math.sin(eta2);
            double cosEta2 = Math.cos(eta2);
            double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
            double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
            double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
            double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
            double tanDiff2 = Math.tan((eta2 - eta1) / 2);
            double alpha = Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
            double q1x = e1x + alpha * ep1x;
            double q1y = e1y + alpha * ep1y;
            double q2x = e2x - alpha * ep2x;
            double q2y = e2y - alpha * ep2y;
            p.cubicTo((float) q1x, (float) q1y, (float) q2x, (float) q2y, (float) e2x, (float) e2y);
            eta1 = eta2;
            e1x = e2x;
            e1y = e2y;
            ep1x = ep2x;
            ep1y = ep2y;
        }
    }
}
