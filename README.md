# GeoSpace microservice #

Geospatial dataset microservice for Socrata SODA Server.

Provides the following services:
- Shapefile ingestion and reprojection to WGS84
- geo region coding for points in datasets

The routes are all in `GeospaceServlet.scala`.

#### BEFORE PROCEEDING ####

## Make sure you have updated SODA-FOUNTAIN and DATA-COORDINATOR to the latest from the master branch ##

To do so: perform the follow steps:

# For SODA-FOUNTAIN: #

git checkout master
git reset --hard
git pull
sbt assembly

Ensure that your startup script launches the latest version of the JAR located in:

`soda-fountain-jetty/target/scala-2.10/`

# For DATA-COORDINATOR #

git checkout master
git reset --hard
git pull
sbt assembly

Ensure that your startup script launches the latest version of the JAR located in:

`coordinator/target/scala-2.10/`

## Build & Run ##

```sh
$ cd geospace
$ ./sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:2020/](http://localhost:2020/) in your browser.

To manually restart the geospace server after changes, use `container:restart`.

### Automated recompile and reload of server

```sh
$ ./sbt
> container:start
> ~ ;copy-resources;aux-compile
```

## Test

```sh
$ ./sbt
> test
```

Test logs are written to `sbt-test.log` in the project root dir.   The
`GeospaceServletSpec` is particularly noisy.  This is what you can expect to see
in a normal run of the test:

- ZK server start up
- Curator discovery and ZK Client start up
- WireMock server start up
- Geospace server starts and registers requests during the test

## On Memory Usage

The JTS library's Geometry shapes use up 40 bytes per Coordinate.  However, Geospace uses `PackedCoordinateSequence`, which stores coordinates as `Array[Double]`s and only expands as needed, meaning permanent on-heap storage is only 16 bytes per coordinate.  Also, Geospace will partition regions and only load the partitions needed for georegioncoding the points in a request.  This means that less memory is used for most datasets, and less memory is used while reading in shapes as well.

Currently, when Geospace caches region datasets and it experiences memory
pressure, it will attempt to start uncaching regions from the biggest currently
cached one in order down to try to free memory.  If it doesn't work then it will
throw an error.

One good region to test memory usage with is nationwide zip codes: https://data.austintexas.gov/d/a3it-2a2z with ~34000 features.  Another one is: qz3q-ghft.  These are all 4x4's in production.

NOTE: If you are doing memory testing locally on spatial caching, some hints:
- Start SBT with something like `-Xmx8g -Xms8g` in `$SBT_OPTS`.  If -Xms is not equal to -Xmx, Geospace's memory detector doesn't work properly.
- Use the `local-shp` route to load a local unpacked shape file dir into memory as cache:

        curl -X POST "http://localhost:2020/v1/regions/census_orig/local-shp?forceLonLat=true" -d $HOME/data/census/orig/ -H "Content-Type: application/json"

## Optimizations

The partitioning scheme will not work well for polar regions, because the size of each partition will get skinnier and skinnier as one heads towards the poles.

Shapefile ingest can be made streaming; right now it loads all features into the heap.  This is a lot of work however;

1. Convert shapefile validation to work off of a FeatureCollection (disk based) rather than a `Traversable[Feature]`.  
2. Reprojection - validation needs reprojected features, so we'd need to write the reprojected shapefile layer to disk first
3. Might need to make the ingestion into core more streaming.

One alternative is to do the one part of validation that requires reprojection
separately.  Shape coordinate bounds checking can be done by using getBounds()
from the feature source, and throwing an error if the bounds of the entire layer
is outside expected values.  This requires reprojecting the Envelope first.
