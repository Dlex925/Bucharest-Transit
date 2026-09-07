package ro.bucharest.transit;

/**
 * A static transit route from the TPBI GTFS feed (built on device by Gtfs).
 * A route has one variant per (direction, terminus): branches of the same
 * direction, such as the two ends of M5, are separate variants. All four
 * variant arrays are aligned by index.
 */
final class Route {
    final String id;
    final String shortName;
    final String name;
    final String color;
    final int type;
    /** One polyline per variant, each an array of {lat, lon}. */
    final double[][][] paths;
    /** Ordered stop ids per variant. */
    final String[][] stopIds;
    /** Terminus (trip headsign) per variant. */
    final String[] headsigns;
    /** GTFS direction_id per variant; two variants can share one direction. */
    final int[] dirIds;

    Route(String id, String shortName, String name, String color, int type,
          double[][][] paths, String[][] stopIds, String[] headsigns, int[] dirIds) {
        this.id = id;
        this.shortName = shortName;
        this.name = name;
        this.color = color;
        this.type = type;
        this.paths = paths;
        this.stopIds = stopIds;
        this.headsigns = headsigns;
        this.dirIds = dirIds;
    }

    /** Number of variants; the arrays can disagree when the feed omits a shape or a sequence. */
    int variantCount() {
        return Math.max(Math.max(paths.length, stopIds.length), headsigns.length);
    }
}
