package ro.bucharest.transit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

/**
 * Small travel-direction arrows repeated along a projected path. Kept separate
 * from the route polyline so that every line can be drawn before any arrow:
 * when each polyline drew its own arrows, the next direction's line painted
 * over the previous direction's arrows.
 */
final class DirectionArrows extends Overlay {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<GeoPoint> points;
    private final int spacingPx;
    private final float arrowPx;
    private final float sideOffsetPx;

    DirectionArrows(List<GeoPoint> points, int color, int spacingPx, float arrowPx, float sideOffsetPx) {
        this.points = points;
        this.spacingPx = spacingPx;
        this.arrowPx = arrowPx;
        this.sideOffsetPx = sideOffsetPx; // perpendicular shift so overlapping directions don't collide
        fill.setColor(color);
        outline.setColor(Color.WHITE);
    }

    @Override
    public void draw(Canvas c, MapView mv, boolean shadow) {
        if (shadow || points.size() < 2) return;
        Projection proj = mv.getProjection();
        int n = points.size();
        android.graphics.Point out = new android.graphics.Point();
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            proj.toPixels(points.get(i), out);
            xs[i] = out.x;
            ys[i] = out.y;
        }
        float acc = spacingPx;
        for (int i = 1; i < n; i++) {
            float dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1];
            float segLen = (float) Math.hypot(dx, dy);
            while (segLen > 0 && acc < segLen) {
                float t = acc / segLen;
                float ax = xs[i - 1] + dx * t, ay = ys[i - 1] + dy * t;
                if (sideOffsetPx != 0) { // push the arrow sideways, opposite signs per direction
                    float nx = -dy / segLen, ny = dx / segLen;
                    ax += nx * sideOffsetPx;
                    ay += ny * sideOffsetPx;
                }
                drawArrow(c, ax, ay, (float) Math.toDegrees(Math.atan2(dy, dx)));
                acc += spacingPx;
            }
            acc -= segLen;
        }
    }

    private void drawArrow(Canvas c, float x, float y, float angleDeg) {
        c.save();
        c.rotate(angleDeg, x, y);
        c.drawPath(triangle(x, y, arrowPx * 0.8f), outline);
        c.drawPath(triangle(x, y, arrowPx * 0.55f), fill);
        c.restore();
    }

    private Path triangle(float x, float y, float s) {
        Path p = new Path();
        p.moveTo(x + s, y);
        p.lineTo(x - s, y - s);
        p.lineTo(x - s, y + s);
        p.close();
        return p;
    }
}
