"""Generates town.geojson, the shared test fixture. Tags follow the OpenMapTiles schema.

Usage: python fixtures/make_town.py fixtures/town.geojson
"""
import json, math, sys

features = []

def add(layer, geom_type, coords, **props):
    # "class" is a Python keyword, so callers pass class_.
    props = {"layer": layer, **{k.rstrip("_"): v for k, v in props.items()}}
    features.append({"type": "Feature", "properties": props,
                     "geometry": {"type": geom_type, "coordinates": coords}})

def r(v):
    return round(v, 6)

def rect(w, s, e, n):
    return [[r(w), r(s)], [r(e), r(s)], [r(e), r(n)], [r(w), r(n)], [r(w), r(s)]]

# 1. Silver River: a meandering band west of town, north to south.
left, right = [], []
steps = 32
for i in range(steps + 1):
    lat = 50.0080 - i * 0.016 / steps
    lon = 9.9935 + 0.0007 * math.sin(i / steps * 2.5 * math.pi)
    left.append([r(lon - 0.0003), r(lat)])
    right.append([r(lon + 0.0003), r(lat)])
ring = left + right[::-1] + [left[0]]
add("water", "Polygon", [ring], class_="river", name="Silver River")

# 2. Ring Road: chamfered rectangle, closed line.
w, s, e, n, k = 9.9960, 49.9965, 10.0060, 50.0035, 0.0008
ring_road = [[w + k, s], [e - k, s], [e, s + k], [e, n - k], [e - k, n], [w + k, n], [w, n - k], [w, s + k], [w + k, s]]
add("transportation", "LineString", [[r(x), r(y)] for x, y in ring_road], class_="primary", name="Ring Road")

# 3-6. North-south streets.
add("transportation", "LineString", [[9.998, 49.9965], [9.998, 50.0035]], class_="minor", name="Mill Lane")
add("transportation", "LineString", [[10.000, 49.9965], [10.000, 50.0035]], class_="secondary", name="Main Street")
add("transportation", "LineString", [[10.002, 49.9965], [10.002, 50.000]], class_="minor", name="Park Lane")
add("transportation", "LineString", [[10.004, 49.9965], [10.004, 50.0035]], class_="minor", name="Station Road")

# 7-9. East-west streets; Bridge Street continues west across the river.
add("transportation", "LineString", [[9.996, 49.998], [10.006, 49.998]], class_="minor", name="Church Street")
add("transportation", "LineString", [[9.990, 50.000], [10.006, 50.000]], class_="primary", name="Bridge Street")
add("transportation", "LineString", [[9.996, 50.002], [10.000, 50.002]], class_="minor", name="North Street")

# 10. Central Park, with a clearing (hole) that holds 11. Mill Pond.
add("landcover", "Polygon", [rect(10.0003, 50.0003, 10.0037, 50.0032), rect(10.0015, 50.0012, 10.0025, 50.0022)],
    class_="grass", subclass="park", name="Central Park")
add("water", "Polygon", [rect(10.0017, 50.0014, 10.0023, 50.0020)], class_="pond", name="Mill Pond")

# 12. Oak Wood, south-east outside the ring.
add("landcover", "Polygon", [[[10.0065, 49.9960], [10.0095, 49.9955], [10.0100, 49.9938], [10.0080, 49.9930], [10.0062, 49.9940], [10.0065, 49.9960]]],
    class_="wood", subclass="forest", name="Oak Wood")

# 13. Willow Way: curved residential street north-west outside the ring.
willow = []
for i in range(9):
    t = i / 8
    willow.append([r(9.9960 - 0.0035 * t), r(50.0030 + 0.0025 * math.sin(t * math.pi / 2))])
add("transportation", "LineString", willow, class_="minor", name="Willow Way")

# Unnamed buildings in the southern blocks.
for bw, bs in [(9.9965, 49.9970), (9.9985, 49.9970), (10.0005, 49.9970), (10.0025, 49.9970), (10.0045, 49.9970),
               (9.9985, 49.9985), (10.0005, 49.9985), (10.0025, 49.9985), (10.0045, 49.9985),
               (9.9965, 50.0005), (9.9985, 50.0005), (9.9985, 50.0025)]:
    add("building", "Polygon", [rect(bw, bs, bw + 0.0010, bs + 0.0006)], render_height="8")

# 14-20. Places and points of interest.
add("place", "Point", [10.0010, 49.9990], class_="town", name="Millbrook")
add("place", "Point", [9.9990, 50.0010], class_="suburb", name="Old Town")
add("place", "Point", [9.9915, 49.9975], class_="neighbourhood", name="Riverside")
add("poi", "Point", [10.0030, 49.9990], class_="marketplace", name="Market Square")
add("poi", "Point", [10.0010, 49.9983], class_="town_hall", name="Town Hall")
add("poi", "Point", [10.0050, 49.9968], class_="railway", subclass="station", name="Station")
add("poi", "Point", [9.9990, 49.9992], class_="library", name="Library")

named = sum(1 for f in features if "name" in f["properties"])
assert named == 20, named

with open(sys.argv[1], "w", newline="\n", encoding="utf-8") as out:
    out.write('{"type": "FeatureCollection", "features": [\n')
    out.write(",\n".join(json.dumps(f, separators=(", ", ": ")) for f in features))
    out.write("\n]}\n")
