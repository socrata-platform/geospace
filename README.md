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

## Edit configuration file ##

Replace the following values in the `geospace/src/main/resources/application.conf` file to reflect the values needed for your environment.

For example in your development environment you would replace the keys to look like this:

```
com.socrata.core-server = {
  service-name = "core"
  geo-domain   = "localhost"
  auth-token   = "Basic cmFuZHkuYW50bGVyQHNvY3JhdGEuY29tOiVhcmdiYXJpbyE="
  app-token    = "xLG2bj7oT13FqAmpjyeH0Io1K"
}
```
Where app-token is your app token for your localhost domain.
Where auth-token is the output of using "bto2()" in your browser to create a BASE64 encoded representation of the following string: `email.address@socrata.com:password`
Where geo-domain is the name of your local development domain (usually localhost).



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
