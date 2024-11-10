@GrabConfig(systemClassLoader=true)
@Grab('org.xerial:sqlite-jdbc:3.16.1')
import org.sqlite.*
import java.sql.*
import java.util.zip.GZIPInputStream;

import groovy.xml.MarkupBuilder

class Generator {

    def db
    def statsDir

    def Generator(workingDir, db){
        this.db = db
        this.statsDir = new File(workingDir, "stats")
    }

    def generateInstallationsJson() {

        println "generating installations.json..."
        def installations = [:]
        db.eachRow("SELECT version, COUNT(*) AS number FROM jenkins WHERE month=(select MAX(month) FROM plugin) GROUP BY version;") {
            installations.put it.version, it.number
        }

        def json = new groovy.json.JsonBuilder()
        json.installations(installations)
        new File(statsDir, "installations.json") << groovy.json.JsonOutput.prettyPrint(json.toString())
        new File(statsDir, "installations.csv").withPrintWriter { w ->
            installations.each { v, n ->
                w.println("\"${v}\",\"${n}\"")
            }
        }
    }

    def generateOldestJenkinsPerPlugin() {

        // Loading map of instanceid:version for the last month
        def instanceVersion = [:]

        def start = System.currentTimeMillis()
        print "Loading instanceid <-> Jenkins version map... "
        db.eachRow("SELECT instanceid,max(version) as version from jenkins where month=(select max(month) from jenkins) group by instanceid") {
            instanceVersion[it.instanceid] = it.version
        }
        println "Done. ${instanceVersion.size()} instanceids found. Took ${(System.currentTimeMillis() - start)/1000 } seconds."

        // NOTE: might have been simpler with an inner join, but missing index on instanceid to improve inner join.
        // Should/could we add it?
        def nameVersionWithMinJenkinsVersion = [:] // [ : [:] ] actually pluginid/pluginversion/jenkinsoldestversion


        println "analyzing plugins to get plugin/pluginversion/jenkinsoldestversion info... "
        start = System.currentTimeMillis()
        // fetch all plugin names, excluding the private ones...
        db.eachRow("select name,version,instanceid" +
                "   from plugin where month = (select max(month) from plugin) " +
                "        and name NOT LIKE 'privateplugin%' " +
                "        and version NOT LIKE '%(private)' " + // add e.g. `and name like 'b%'` to reduce the dataset when testing
                "   order by name,version desc,instanceid") {

            if( ! nameVersionWithMinJenkinsVersion.containsKey(it.name) ) {
                nameVersionWithMinJenkinsVersion.put(it.name, [:])
            }
            if( ! nameVersionWithMinJenkinsVersion[it.name].containsKey(it.version)) {
                nameVersionWithMinJenkinsVersion[it.name].put(it.version, [:])
            }

            String jenkinsVersion = instanceVersion[it.instanceid]
            Integer count = nameVersionWithMinJenkinsVersion[it.name][it.version][jenkinsVersion]

            if(count == null) {
                count = 0;
            }
            count++;
            nameVersionWithMinJenkinsVersion[it.name][it.version][jenkinsVersion] = count;
        }
        println "Done. Took ${(System.currentTimeMillis() - start)/1000} seconds."

        println "Sorting Jenkins versions... "
        // comparator to sort string type versions as numeric order 
        def semverComparator = { String version1, String version2 ->
            def parseVersion = { version -> 
                version.tokenize('.').collect { it as Integer }
            }
            def v1Parts = parseVersion(version1)
            def v2Parts = parseVersion(version2)

            for (int i = 0; i < Math.min(v1Parts.size(), v2Parts.size()); i++) {
                int partComparison = v1Parts[i] <=> v2Parts[i]
                if (partComparison != 0) return partComparison
            }
            return v1Parts.size() <=> v2Parts.size()
        }

        nameVersionWithMinJenkinsVersion.each { pluginName, versionMap ->
            versionMap.each { version, jenkinsCountMap ->
                nameVersionWithMinJenkinsVersion[pluginName][version] = new TreeMap(nameVersionWithMinJenkinsVersion[pluginName][version])
            }
            // sorts string type versions as numeric order
            def sortedVersionMap = new TreeMap(semverComparator)
            sortedVersionMap.putAll(versionMap)
            nameVersionWithMinJenkinsVersion[pluginName] = sortedVersionMap

        }
        println "Done."

        def json = new groovy.json.JsonBuilder()
        json 'jenkins-version-per-plugin-version':nameVersionWithMinJenkinsVersion

        def file = new File(statsDir, "jenkins-version-per-plugin-version.json")
        file << groovy.json.JsonOutput.prettyPrint(json.toString())
        println "wrote: $file.absolutePath"
    }

    def generatePluginsJson() {

        println "fetching plugin names..."
        def names = []
        // fetch all plugin names, excluding the private ones...
        db.eachRow("SELECT name FROM plugin WHERE name NOT LIKE 'privateplugin%' GROUP BY name ;") { names << it.name }
        println "found ${names.size()} plugins"

        def total = [:];
        db.eachRow("SELECT month, COUNT(*) AS number FROM jenkins GROUP BY month ORDER BY month ASC;") {
            total[it.month] = it.number;
        }

        names.each{ name ->
            def month2number = [:]
            def month2percentage = [:]
            def file = new File(statsDir, "${name}.stats.json")
            // fetch the number of installations per plugin per month
            db.eachRow("SELECT month, COUNT(*) AS number FROM plugin WHERE name = $name GROUP BY month ORDER BY month ASC;") {
                month2number.put it.month, it.number
                month2percentage[it.month] = (it.number as float)*100/(total[it.month] as float)
            }
			
			def version2number = [:]
			def version2percentage = [:]
			// fetch the number of installations per plugin version this month
			db.eachRow("SELECT COUNT(*) AS number, version, month FROM plugin WHERE name = $name AND month = (SELECT MAX(month) FROM plugin) GROUP BY version") {
				version2number.put it.version, it.number
				version2percentage[it.version] = (it.number as float)*100/(total[it.month] as float)
			}
			
			def json = new groovy.json.JsonBuilder()
            json name:name, installations:month2number, installationsPercentage:month2percentage, installationsPerVersion:version2number, installationsPercentagePerVersion:version2percentage
            file << groovy.json.JsonOutput.prettyPrint(json.toString())
            println "wrote: $file.absolutePath"
        }

    }

    def generateLatestNumbersJson() {
        println "generating latestNumbers.json..."
        def plugins = [:]
        def latestMonth;
        db.eachRow("SELECT name, COUNT(*) AS number, month FROM plugin WHERE month=(select MAX(month) FROM plugin) AND name NOT LIKE 'privateplugin%' GROUP BY name, month;"){
            plugins.put it.name, it.number
            latestMonth = it.month // ok, this is probably not the nicest way, but the month is realy the same for all the numbers anyway
        }
        def json = new groovy.json.JsonBuilder()
        json month:latestMonth, plugins:plugins
        new File(statsDir, "latestNumbers.json") << groovy.json.JsonOutput.prettyPrint(json.toString())
        new File(statsDir, "latestNumbers.csv").withPrintWriter { w ->
            plugins.each { name, number ->
                w.println("\"${name}\",\"${number}\"")
            }
        }
    }


    // like installations.json, but cumulative descending: number indicates number of installations of given version or higher
    def generateCapabilitiesJson() {
        println "generating capabilities.json..."
        def installations = [:]
        def higherCap = 0
        db.eachRow("SELECT version, COUNT(*) AS number FROM jenkins WHERE month=(select MAX(month) FROM jenkins) AND (version LIKE '1.%' OR version LIKE '2.%') GROUP BY version ORDER BY version DESC;") {
            installations.put it.version, it.number + higherCap
            higherCap += it.number
        }

        def json = new groovy.json.JsonBuilder()
        json.installations(installations)
        new File(statsDir, "capabilities.json") << groovy.json.JsonOutput.prettyPrint(json.toString())
        new File(statsDir, "capabilities.csv").withPrintWriter { w ->
            installations.each { v, n ->
                w.println("\"${v}\",\"${n}\"")
            }
        }
    }

    // for each month, counts the number of JVM versions in use (using strict filtering to ignore weird/local JVM version names)
    def generateJvmJson() {
        final def JVM_VERSIONS = ["1.5", "1.6", "1.7", "1.8", "9", "10", "11", "12", "13"]
        def jvmVersionsRestriction = "(jvmv='" + JVM_VERSIONS.join("' OR jvmv='") + "')"
        def fileName = 'jvms.json'
        println "generating $fileName..."
        def months = []
        db.eachRow("SELECT DISTINCT month FROM jenkins ORDER BY month ;") { months << it.month }

        def jvmPerDate = [:]
        months.each { month ->
            def jvmCount = [:]
            db.eachRow("SELECT SUBSTR(jvmversion,1,3) AS jvmv,COUNT(0) AS cnt " +
                    "FROM jenkins " +
                    "WHERE month=$month AND $jvmVersionsRestriction " +
                    "GROUP BY month,jvmv " +
                    "ORDER BY jvmv;") {
                jvmCount.put(it.jvmv, it.cnt)
            }
            jvmPerDate.put(month, jvmCount)
        }

        def jvmPerDate2DotxOnly = [:]
        months.findAll { it > 1459536318000 } // Ignore data before April 2016, when Jenkins 2.0 was released
              .each    { month ->
            def jvmCount = [:]
            db.eachRow("SELECT SUBSTR(jvmversion,1,3) AS jvmv,COUNT(0) AS cnt " +
                    "FROM jenkins " +
                    "WHERE month=$month AND $jvmVersionsRestriction AND version like '2.%'" +
                    "GROUP BY month,jvmv " +
                    "ORDER BY jvmv;") {
                jvmCount.put(it.jvmv, it.cnt)
            }
            jvmPerDate2DotxOnly.put(month, jvmCount)
        }

        def json = new groovy.json.JsonBuilder()
        json jvmStatsPerMonth: jvmPerDate, jvmStatsPerMonth_2_x: jvmPerDate2DotxOnly
        new File(statsDir, fileName) << groovy.json.JsonOutput.prettyPrint(json.toString())
    }

    def run() {

        // clean the stats directory
        statsDir.deleteDir()
        statsDir.mkdirs()

        generateOldestJenkinsPerPlugin()
        generateCapabilitiesJson()
        generateInstallationsJson()
        generateLatestNumbersJson()
        generatePluginsJson()
        generateJvmJson()

    }
}


def workingDir = new File("target")
def db = DBHelper.setupDB(workingDir)
new Generator(workingDir, db).run()





