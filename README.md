ElasticGeo
==========

<a href="https://travis-ci.org/ngageoint/elasticgeo">
	<img alt="Travis-CI test status" 
	     src="https://travis-ci.org/ngageoint/elasticgeo.svg?branch=master"/>
</a>
<br/>
<a href='https://coveralls.io/r/ngageoint/?branch=master'>
  <img src='https://coveralls.io/repos/ngageoint/elasticgeo/badge.png?branch=master'
       alt='Coverage Status' />
</a>

ElasticGeo provides a GeoTools data store that allows geospatial features from an Elasticsearch index to be published via OGC services using GeoServer.  

###Pull Request

If you'd like to contribute to this project, please make a pull request. We'll review the pull request and discuss the changes. All pull request contributions to this project will be released under the appropriate license conditions discussed below. 

Software source code previously released under an open source license and then modified by NGA staff is considered a "joint work" (see 17 USC 101); it is partially copyrighted, partially public domain, and as a whole is protected by the copyrights of the non-government authors and must be released according to the terms of the original open source license.

###Project relies upon:

GeoTools under [LGPL v 2.1](http://geotools.org/about.html)

GeoServer under [GPL v 2 with later option](http://geoserver.org/license/) 

ElasticGeo under [LGPL v 2.1](https://github.com/matsjg/elasticgeo)

Elasticsearch under [Apache License v 2.0](https://github.com/elastic/elasticsearch/blob/master/LICENSE.txt) 

###Documentation

https://github.com/ngageoint/elasticgeo/blob/master/gs-web-elasticsearch/doc/index.rst


First stab on elasticsearch GeoTools datastore.

Based on the tutorial for a CSV DataStore in the GeoTools guide.

Installation in Geoserver 2.1.1
-------------------------------

There is a problem with the different logging implementations in ES and GeoServer (the binary distribution)
As a workaround I upgraded to SLF4J 1.6.1 in both {GEOSERVER_HOME}/lib and in {GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib.

{GEOSERVER_HOME}/lib

Remove

-   log4j-1.2.14.jar
-   slf4j-simple-1.0.1.jar

Add

-   log4j-1.2.16.jar
-   slf4j-api-1.6.1.jar
-   slf4j-simple-1.6.1.jar

{GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib

Remove

-   log4j-1.2.14.jar
-   slf4j-api-1.5.8.jar
-   slf4j-log4j12-1.4.2.jar

Add

-   log4j-1.2.16.jar
-   slf4j-api-1.6.1.jar
-   slf4j-log4j12-1.6.1.jar

Dependencies
------------

    mvn dependency:copy-dependencies


    cp target/dependency/lucene-* {GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib/
    cp target/dependency/elasticsearch-* {GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib/
    cp target/dependency/spatial4j-* {GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib/

    mvn install && cp target/elasticgeo-0.0.1-SNAPSHOT.jar {GEOSERVER_HOME}/webapps/geoserver/WEB-INF/lib

Debugging Geoserver
-------------------

    export JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -XX:MaxPermSize=128m -Xmx1g -server" && {GEOSERVER_HOME}/bin/startup.sh

and attach your remote debugger from your IDE to port `localhost:5005`.



