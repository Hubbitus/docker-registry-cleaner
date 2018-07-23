[![CI Build Status](https://travis-ci.org/Hubbitus/docker-registry-cleaner.svg?branch=master)](https://travis-ci.org/Hubbitus/docker-registry-cleaner)
[![Docker Build Status](https://img.shields.io/docker/build/hubbitus/docker-registry-cleaner.svg)](https://hub.docker.com/r/hubbitus/docker-registry-cleaner/)

Any feedback and comments welcome! Please use github issues for that.

**Docker hub automated build: https://hub.docker.com/r/hubbitus/docker-registry-cleaner/**
Instructions how to build manually see below.

# Basic usage info

## Direct commandline:
```
$ java -jar docker-registry-cleaner-all-0.1-SNAPSHOT.jar --help
Usage: docker-registry-cleaner [options]
  Options:
    -D, --debug               Debug mode - write JSON data for got tags into debug.json file
                              Default: false
    -d, --delete              Delete old optionsByTag!
                                By default we do not delete anything. Just list applications and optionsByTag. Information about tagRegexp and its build time also provided. If there
                              any --clean options present also mark optionsByTag which supposed to be deleted.
                                For sort you may also use --sort option.
                              Default: false
    -f, --format              Format of printing tagRegexp line. See SimpleTemplateEngine description and info.hubbitus.RegistryTagInfo class for available data
                              Default: tag=[<% printf("%-" + (tag.application.length() + 1 + tags.max{ it.name.length()}.name.length()) + "s", "${tag.application}:${tag.name}")%>]; time=[${tag.created}]; isForDelete=[<%printf("%-5s", tag.keptBy.isForDelete())%>]; [${tag.keptBy}]
        --help
                              Default: false
    -i, --interactive         Interactive mode. Ask for deletion each image. Details like data and why it is proposed for delete will be shown. Implies delete = true
                              Default: false
  *     --keep                As primary goal to delete some old images we just provide some opposite rules which must be kept.
                                1) We process only applications, matches --only-applications pattern if it
                              set.
                                2) Firstly match "application" against `application` regexp
                                3) Then `tag` name against `tag` regexp
                                4) If `period` present and greater than 0 check it matches build time
                              early then in `period` (which is by default number of seconds, but suffixes m, h, d, w also supported for minutes, hours, days and weeks)
                                5) If `top` present and greater than 0,
                              from above results exempt from deletion that amount of elements, according to sorting, provided in --sort
                                6) If both `period` and `top` provided only tags *match both criteria*
                              will be kept (boolean AND)!
                                F.e.:
                                        - Said by date matched 10 tags and you have top=5 - so only 5 will be kept - other deleted
                                        - In configured date period was 10 tags and
                              you set top=20 - only 10 will be kept - other deleted

                                All other will be deleted!

                                Example of options (to provide in file, please quote properly in shell
                              options):
                                --keep=GLOBAL={ top: 5, period: 1w }
                                --keep=egaisapp=[ { tag: ".+", top: 10, period: 3d }, { tag: "release_.+", top: 4 }, {tag: "auto.+", period: "4d"} ]
                                --keep=bp-app=[ { tag:
                              "^(dev|master)$", top: 2 }, { tag: "^dev-", top: 5 }, {tag: "^master-", period: "4d"} ]
                                --keep=glrapp={ top: 4 }

                              Syntax: --keepkey=value
                              Default: {}
  * -l, --login               Docker registry login
    -o, --only-applications   Regexp to match against applications name to process. Tags for other even will not be fetched
    -p, --password            Docker registry password
    -P, --password-file       Docker registry password, stored in file for security.
  * -u, --registry-url        URL to docker registry REST endpoint. Like https://docreg.taskdata.work/v2/
        --sort                Sort method on optionsByTag list. Either "name" or "time" (build time of image, default). In case of time sorting most recent should be stay, so DESC assuming
                              Default: time
```

## Docker (recommended)
If you do not want develop, and just harry up to run ti, you may use docker:

    docker run hubbitus/docker-registry-cleaner --usage

And real clean invocation:

    docker run hubbitus/docker-registry-cleaner --registry-url 'https://docreg.taskdata.work/v2/' --login egais --keep='GLOBAL={ top: 5, period: 1w }' --password='coll-password' --delete

Instead of pass all arguments each time `docker-registry-cleaner` may read all arguments from file where such options placed by one on line. Look example file in [test.args](src/test/resources/test.args). In case of docker don't forget mount it inso container (`--passwordFile` too, if used):

    docker run -v $(pwd)/run.args:/host/run.args hubbitus/docker-registry-cleaner @/run.args


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

Intended for automatic builds on docker hub. In that case `gradle` invoked from `Dockerfile`. To make target image small used [multi-stage technic](https://docs.docker.com/develop/develop-images/multistage-build/#use-multi-stage-builds) which require docker-ce version >= 17.

In that variant image may be built by:

    docker build -t hubbitus/docker-registry-cleaner:some-tag .

