#!/usr/bin/env groovy
import java.io.File
import java.util.zip.GZIPInputStream
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder


@Grapes([
    @Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.13'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.2'),
    @Grab(group='org.apache.ant', module='ant', version='1.8.1'),
    @Grab(group='org.xerial', module='sqlite-jdbc', version='3.16.1'),
    @GrabExclude('xml-apis:xml-apis'),
    @GrabConfig(systemClassLoader=true)
])


class Downloader {
    def authUrl = "http://jenkins-ci.org"

    def db
    def workingDir

    def Downloader(workingDir, db){
        this.db = db
        this.workingDir = workingDir
    }

    /**
     * gets all compressed JSON files from jenkins-ci
     */
    def getFiles(pwd) {
		
		println("get files from $authUrl")
		
        def site = new HTTPBuilder( authUrl )
        // site.auth.basic 'jenkins', pwd - this did not work, therefore using header :(
	    def userAuth = "jenkins:$pwd".toString()
        def doc = site.get( path:'/census/', headers:[Authorization:"Basic ${userAuth.bytes.encodeBase64()}"])

        doc.depthFirst().collect { it }.findAll {
            it.name() == "A"
        }.each {
            def fileName = it.attributes()["href"]
            if(fileName.endsWith(".json.gz")){
                if(DBHelper.doImport(db, fileName)){
                    def fileUrl = '/census/'+fileName
                    def targetArchive = new File(workingDir, fileName)
                    if (targetArchive.exists()) {
                        println "ignore $fileName (already exists)"
                    } else {
                        println "download $fileUrl"
                        def tmp = new File(workingDir,fileName+".tmp");
                        tmp << site.get(contentType: ContentType.BINARY, path: fileUrl, headers:[Authorization:"Basic ${userAuth.bytes.encodeBase64()}"] ) // java.io.ByteArrayInputStream
                        tmp.renameTo(targetArchive);
                    }
                } else{
                    println "ignore $fileName (already imported)"
                }
            }
        }
    }

    def run(args) {
        if(args.size() != 1){
            println "no password for $authUrl given..."
        }else{
            getFiles(args[0])
        }
    }
}

def workingDir = new File("target")
workingDir.mkdirs()
def db = DBHelper.setupDB(workingDir)
new Downloader(workingDir, db).run(this.args)
