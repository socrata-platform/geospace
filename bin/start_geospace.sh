#!/bin/bash
# Start geospace http server
BASEDIR=$(dirname $0)/..
CONFIG=$BASEDIR/../docs/onramp/services/soda2.conf
JARFILE=$BASEDIR/geospace-http/target/scala-2.10/geospace-microservice-assembly-*.jar
if [ ! -e $JARFILE ]; then
  pushd $BASEDIR && sbt assembly && popd
fi
java -Xms1g -Xmx1g -Dconfig=$CONFIG -jar $JARFILE

