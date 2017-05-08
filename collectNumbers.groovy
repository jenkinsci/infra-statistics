#!/usr/bin/env groovy
// push *.json.gz into a local SQLite database
import org.sqlite.*

@Grapes([
    @Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.13'),
    @Grab('org.xerial:sqlite-jdbc:3.16.1'),
    @GrabConfig(systemClassLoader=true)
])


class NumberCollector {

    def db
    def workingDir

    def NumberCollector(workingDir, db){
        this.db = db
        this.workingDir = workingDir
    }

    def generateStats(File file) {

        def dir = this.workingDir + "/target"
        "rm -rf $dir".execute()

        if(!DBHelper.doImport(db, file.name)){
            println "skip $file - already imported..."
            return
        }

	if (monthDate.before(java.util.Date.parse('yyyyMM', "201401"))) {
	    return;
	}

        def dateStr = file.name.substring(0, 6)
        def monthDate = java.util.Date.parse('yyyyMM', dateStr)

        if (monthDate.before(java.util.Date.parse('yyyyMM', "201401"))) {
            println "skipping $dateStr"
            return;
        }

        int records=0;

        db.withTransaction({
            JenkinsMetricParser p = new JenkinsMetricParser()
            p.forEachInstance(file) { InstanceMetric metric ->
                if ((records++)%100==0)
                    System.out.print('.');
                def instId = metric.instanceId;

                db.execute("insert into jenkins(instanceid, month, version, jvmvendor, jvmname, jvmversion) values( $instId, $monthDate, ${metric.jenkinsVersion}, ${metric.jvm?.vendor}, ${metric.jvm?.name}, ${metric.jvm?.version})")

                metric.plugins.each { pluginName, pluginVersion ->
                    db.execute("insert into plugin(instanceid, month, name, version) values( $instId, $monthDate, $pluginName, $pluginVersion)")
                }

                metric.jobTypes.each { jobtype, jobNumber ->
                    db.execute("insert into job(instanceid, month, type, jobnumber) values( $instId, $monthDate, $jobtype, $jobNumber)")
                }

                metric.nodesOnOs.each { os, nodesNumber ->
                    db.execute("insert into node(instanceid, month, osname, nodenumber) values( $instId, $monthDate, $os, $nodesNumber)")
                }

                db.execute("insert into executor(instanceid, month, numberofexecutors) values( $instId, $monthDate, $metric.totalExecutors)")
            }

            db.execute("insert into importedfile(name) values($file.name)")
        })

        println "\ncommited ${records} records for ${monthDate.format('yyyy-MM')}"
    }

    def run(String[] args) {
        if (args.length==0) {
            workingDir.eachFileMatch( ~".*json.gz" ) { file -> generateStats(file) }
        } else {
            args.each { name -> generateStats(new File(name)) }
        }
    }
}

def workingDir = new File("target")
def db = DBHelper.setupDB(workingDir)
new NumberCollector(workingDir, db).run(args)




