#!/usr/bin/env groovy
@Grab(group='com.gmongo', module='gmongo', version='1.0')
@Grab(group='org.codehaus.jackson', module='jackson-core-asl', version='1.9.3')
@Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.3')
import com.gmongo.GMongo
import org.codehaus.jackson.*
import org.codehaus.jackson.map.*

import groovy.util.CliBuilder
import groovy.json.*
import java.util.zip.*;

def parseArgs(cliArgs) {
    def cli = new CliBuilder(usage: "parse-usage.groovy [options]",
                             header: "Options")

    cli._(longOpt:'logs', args:1, required:true, "Directory containing raw logs")

    cli._(longOpt:'output', args:1, required:true, "Directory to output processed JSON to")

    cli._(longOpt:'timestamp', args:1, required:false, "Base timestamp for logs - i.e., '201112'")

    cli._(longOpt:'incremental', args:0, required:false, "Parse incrementally based on the available files in --logs and --output")
    
    def options = cli.parse(cliArgs)

    assert new File(options.logs).isDirectory(), "--logs value ${options.logs} is not a directory"
    assert new File(options.output).isDirectory(), "--output value ${options.output} is not a directory"

    return options
}


def argResult = parseArgs(this.args)
def logDir=new File(argResult.logs)
def outputDir=new File(argResult.output);

if (argResult.incremental) {
    byMonth=[:] as TreeMap
    re = /.*log\.([0-9]{6})[0-9]+\.(.*\.)?gz/ 
    logDir.eachFileMatch(~re) { f ->
        m = (f=~re)
        if (m)  byMonth[m[0][1]] = true;
    }
    def data = byMonth.keySet() as List
    println "Found logs: ${data}"

    // do not process the current month as the data may not be complete yet
    data.pop()
    data.each { t ->
        if (new File(outputDir,"${t}.json.gz").exists()) {
            println "Skipping ${t}.json.gz as it already exists"
        } else {
            process(t,logDir,outputDir);
        }
    }
} else {
    // just process one month specified in the command line
    if (argResult.timestamp==null)
        throw new Error("Neither --incremental nor --timestamp was specified");
    process(argResult.timestamp, logDir, outputDir);
}

def process(String timestamp/*such as '201112'*/, File logDir, File outputDir) {
    def mongoConnection = new GMongo("127.0.0.1", 27017)
    def mongoDb = mongoConnection.getDB("test")
    def mColl = mongoDb.jenkins
    mColl.drop()

    def procJson = [:]

    def ant = new AntBuilder()

    def slurper = new JsonSlurper()

    def tmpDir = new File("./tmp")

    if (!tmpDir.isDirectory()) { 
        tmpDir.mkdirs()
    }

    def logRE = ".*log\\.${timestamp}.*gz"

    def linesSeen = 0
    def instCnt = [:]

    logDir.eachFileMatch(~/$logRE/) { origGzFile ->
        println "Handing original log ${origGzFile.canonicalPath}"
        new GZIPInputStream(new FileInputStream(origGzFile)).eachLine("UTF-8") { l ->
            linesSeen++;
            def j = slurper.parseText(l)
            def installId = j.install
            def ver = j.version

            def jobCnt = j.jobs.values().inject(0) { acc, val -> acc+ val }

            if (jobCnt > 0) {
                mColl << j
                if (!instCnt.containsKey(installId)) {
                    instCnt[installId] = 0
                }
                instCnt[installId] += 1
            }
            
        }
    }

    println "${mColl.count()} total reports with >0 jobs"
    def ex = mColl.findOne()
    def uniqIds = mColl.distinct("install")
    println "${uniqIds.size()} unique installs seen."

    def seenTwice = [:]

    def fMap = "function map() { emit(this.install, { insts: [this], count: 1 } ) }"
    def fRed = "function reduce(key, values) { var result = { insts : [], count: 0 }; values.forEach(function(value) { result.insts.push.apply(result.insts, value.insts); result.count += value.count }); return result }"
    mongoDb.tempgroup.drop()

    def aggrOutput = mColl.aggregate([
            $group: [
                '_id': [ install: '$install' ],
                uniqueIds: [ $addToSet: '$_id' ],
                count: [ $sum: 1 ]
            ]],
            [$match: [ count: [ $gte: 2 ]]],
            [$out: "tempgroup"])

    def grouped = null;
    if(uniqIds.size()>0)
        mColl.mapReduce(fMap, fRed, "tempgroup", [:])

    def printed = 0
    def otmp = new File(outputDir, "${timestamp}.json.tmp")
    otmp.withOutputStream() {os ->
        w = new OutputStreamWriter(new GZIPOutputStream(os),"UTF-8");
        try {
            def builder = new StreamingJsonBuilder(w)

            builder {
                mongoDb.tempgroup.find().each { g ->
                    def toSave = []
                    g.uniqueIds.each { u ->
                        def v = mColl.findOne('_id': u)
                        v.remove("_id")
                        toSave << v
                    }

                    "${g.'_id'}" toSave
                }
            }
        } finally {
            w.close();
        }
    }
    
    // when successfully completed, atomically produce the output
    otmp.renameTo(new File(outputDir, "${timestamp}.json.gz"))

    mongoConnection.close()
    mongoDb = ''
    mColl = ''
}
