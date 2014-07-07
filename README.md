# GeoSpace microservice #

Geospatial dataset microservice for Socrata SODA Server.

Provides the following services:
- Shapefile ingestion and reprojection to WGS84
- geo region coding for points in datasets

The routes are all in `GeospaceServlet.scala`.

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