# GeoSpace microservice #

Geospatial dataset microservice for Socrata SODA Server.

Provides the following services:
- Shapefile ingestion and reprojection to WGS84
- geo region coding for points in datasets

## Build & Run ##

```sh
$ cd geospace
$ ./sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

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
