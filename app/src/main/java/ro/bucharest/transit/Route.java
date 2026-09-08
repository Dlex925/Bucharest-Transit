package ro.bucharest.transit;

final class Route {
    final String id;
    final String shortName;
    final String name;
    final String color;
    final int type;

    final double[][][] paths;

    final String[][] stopIds;

    final String[] headsigns;

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

    int variantCount() {
        return Math.max(Math.max(paths.length, stopIds.length), headsigns.length);
    }
}
