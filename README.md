Jenkins Usage Statistics
========================

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Infra/infra-statistics/master)](https://ci.jenkins.io/job/Infra/job/infra-statistics/job/master/)

These scripts generate various data from existing census JSON files collected by the jenkins.io infrastructure.
More specifically:

- http://stats.jenkins.io/
- data used by the ['jenkins-plugin-info' confluence macro](https://github.com/jenkinsci/backend-jenkins-plugin-info-plugin)

## WHAT
----

this scipts will create the following files:

* multiple SVG graphics showing different statistcs about the usage of jenkins
* a file `<plugin-name>.stats.json`for every plugin, containing the following data (example: git-plugin - [git.stats.json](http://stats.jenkins.io/plugin-installation-trend/git.stats.json) ):

  * `name`: the name of the plugin (as used in the filename)
  * `installations`: the number of installations for a given month
  * `installationsPercentage`: the percentage of installations
  * `installationsPerVersion`: the number of plugin installations for for each plugin version
  * `installationsPercentagePerVersion`: the percentage of installations a specifig version of the plugin makes up
  
* `installations.json`: the number of jenkins installations by the jenkins core version
* `capabilities.json`: a reverse cumulation of the `installations.json` to assist plugin developers in choosing base versions for there further plugin development
  
  

## HOWTO
-----

#### Data

1. you need to download the raw data (*.json.gz) from jenkins-ci.org

   `$> groovy download.groovy [pwd]`
   
#### SVG (optional)

* generate the graphs
   ... you might have to increase the memory: `JAVA_OPTS="-Xmx4000M"`

```
$> export LANG=en
$> groovy generateStats.groovy
```


The final SVGs will be in `[workspace]/target/svg` 

#### JSON (optional)

1. collect the data from the raw json format and store it into a local SQLite database
   
```
$> export LANG=en
$> groovy collectNumbers.groovy`
```

2. generate the fine grained data set (json) for each plugin
   
    `$> groovy createJson.groovy`

The final json files will be in `[workspace]/target/stats` - these files have to be uploaded to a webserver to make them available for the confluence plugin.


All the scripts can be reexecuted in case a failure happens, e.g. the download will only download the files he needs and collecting the numbers will only happen on the raw data which is not imported yet.

