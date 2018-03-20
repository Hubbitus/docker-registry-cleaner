[![CI Build Status](https://travis-ci.org/Hubbitus/docker-registry-cleaner.svg?branch=master)](https://travis-ci.org/Hubbitus/docker-registry-cleaner)

# Base usage info
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