[![CI Build Status](https://travis-ci.org/Hubbitus/docker-registry-cleaner.svg?branch=master)](https://travis-ci.org/Hubbitus/docker-registry-cleaner)

Any feedback and comments welcome! Please use github issues for that.

# Basic usage info

## Direct commandline:
```
$ java -jar docker-registry-cleaner-all-0.1-SNAPSHOT.jar --help
Picked up _JAVA_OPTIONS: -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java2d.pmoffscreen=false -XX:+UseCompressedOops -XX:+DoEscapeAnalysis -XX:+AggressiveOpts -XX:+EliminateLocks -XX:+UseNUMA -XX:+TieredCompilation
Usage: <main class> [options]
  Options:
    -d, --delete
      Delete old tags!
    By default we do not delete anything. Just list applications and tags. Information about tag and its build time also provided. If there any --clean options present also mark tags which
      supposed to be deleted.
    For sort you may also use --sort option.
      Default: false
    -f, --format
      Format of printing tag line. See SimpleTemplateEngine
      Default: tag=[${sprintf("%-90s", "$application:$tagName")}]; time=[${tagData.time}]; deleteBy=[${tagData.deleteBy}]
    --help

    --keep
      Dynamic parameters go here
      Syntax: --keepkey=value
      Default: {}
  * -l, --login
      Docker registry login
  * -p, --password
      Docker registry password
  * -u, --registry-url
      URL to docker registry REST endpoint. Like https://docreg.taskdata.work/v2/
    --sort
      Sort method on tags list. Either "name" or "time" (build time of image, default)
      Default: time
```

## Docker (recommended)
If you do not want develop, and just harry up to run ti, you may use docker:

    docker run docker-registry-cleaner --usage

And real clean invocation:

    docker run docker-registry-cleaner --registry-url 'https://docreg.taskdata.work/v2/' --login egais --keep='GLOBAL={ top: 5, period: 1w }' --password='coll-password' --delete

Instead of pass all arguments each time `docker-registry-cleaner` may read all arguments from file where such options placed by one on line. Look example file in [test.args](src/test/resources/test.args). In case of docker don't forget mount it inso container (`--passwordFile` too, if used):

    docker run -v $(pwd)/run.args:/host/run.args docker-registry-cleaner @/run.args


# Build

If you wish contribute and build it himself. It si written on `groovy` and backed by `gradle` build.

## Jar
Simple build executable jar (on Windows replace `./gradlew` by `gradlew.bat` command in all examples):

    ./gradlew fatJar

Then you may run it in traditional way:

    java -jar build/libs/docker-registry-cleaner-fat-0.1-SNAPSHOT.jar

## To run tests

    ./gradlew check --rerun-tasks

## Docker build

You have to choose one variant from:

### Gradle build:

    ./gradlew dockerBuildImage

In that case will be generated file `build/Dockerfile` and docker build invoked via socket `/run/docker.sock` (please be sure current user have rights on it).

This method intended for convenient development.

Push also possible, but requires providing password:

    DOCKER_REGISTRY_PASSWORD='cool-password' ./gradlew dockerPushImage

### Traditional docker build

Intended for automatic builds on docker hub. In that case `gradle` invoked from `Dockerfile`. To make target image smalle used [multi-stage technic](https://docs.docker.com/develop/develop-images/multistage-build/#use-multi-stage-builds) which require docker-ce version >= 17.

In that variant image may be built by:

    docker build -t docker-registry-cleaner:some-tag .
