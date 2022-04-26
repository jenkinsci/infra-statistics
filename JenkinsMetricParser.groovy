
import org.codehaus.jackson.JsonToken

import org.codehaus.jackson.*
import org.codehaus.jackson.node.*
import org.codehaus.jackson.map.*
import java.util.zip.GZIPInputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * This parser treats a file as an input for one month and only uses the newest stats entry of each instanceId.
 * 
 * 
 * Note: Although groovy provides first class json support, we use jackson because of the amount of data we have to deal
 */
class JenkinsMetricParser {

    // 11/Oct/2011:05:14:43 -0400
    private static final FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss X", Locale.ENGLISH)

    /**
     * Returns a map of "instanceId -> InstanceMetric" - only the newest entry for each instance is returned (latest of the given month, each file contains only data for one month).
     * SNAPSHOT versions are ignored too.
     */
    public Map parse(File file) throws Exception {
        def installations = [:]
        forEachInstance(file) { InstanceMetric m -> installations[m.instanceId]=m }
        return installations
    }

    /**
     * Pass {@link InstanceMetric} for each installation to the given closure.
     */
    public void forEachInstance(File file, Closure processor) throws Exception {
        println "parsing $file"

        JsonFactory f = new org.codehaus.jackson.map.MappingJsonFactory();

        def is = new FileInputStream(file);
        if (file.name.endsWith(".gz")) is = new GZIPInputStream(is)
        JsonParser jp = f.createJsonParser(is);

        JsonToken current;

        current = jp.nextToken();
        if (current != JsonToken.START_OBJECT) {
            println("Error: root must be object!");
            return;
        }

        while (jp.nextToken() != JsonToken.END_OBJECT) {
            String instanceId = jp.getCurrentName();
            // move from field name to field value
            current = jp.nextToken();

            // Install id *should* be 64 but one of size 128 showed up recently and broke all parsing here
            // past its entry, so adding handling of 128 size...
            if(instanceId?.size() == 64 || instanceId?.size() == 128){ // installation hash is 64 chars

                def availableStatsForInstance = 0

                def latestStatsDate
                def jobs
                def plugins
                def jVersion
                InstanceJVM masterJvm
                def servletContainer;
                def nodesOnOs
                def totalExecutors

                if (current == JsonToken.START_ARRAY) {
                    while (jp.nextToken() != JsonToken.END_ARRAY) {
                        // read the record into a tree model,
                        // this moves the parsing position to the end of it
                        JsonNode jsonNode = jp.readValueAsTree();
                        // And now we have random access to everything in the object
                        def timestampStr = jsonNode.get("timestamp").getTextValue()
                        ZonedDateTime parsedDate = ZonedDateTime.parse(timestampStr, FORMATTER)

                        servletContainer = jsonNode.get("servletContainer")?.getTextValue()

                        // we only want the latest available date for each instance
                        if (!latestStatsDate || parsedDate.isAfter(latestStatsDate)) {

                            def versionStr = jsonNode.get("version").getTextValue()
                            // ignore SNAPSHOT versions
                            if(!versionStr.contains("SNAPSHOT") && !versionStr.contains("***")){
                                jVersion = versionStr ? versionStr : "N/A"

                                availableStatsForInstance++

                                latestStatsDate = parsedDate

                                jobs = [:]

                                def jobsNode = jsonNode.get("jobs");
                                jobsNode.getFieldNames().each { jobType -> jobs.put(jobType, jobsNode.get(jobType).intValue) };

                                plugins = [:]
                                jsonNode.get("plugins").each { plugins.put(it.get("name").textValue, it.get("version").textValue)} // org.codehaus.jackson.node.ArrayNode

                                nodesOnOs = [:]
                                totalExecutors = 0

                                jsonNode.get("nodes").each {

                                    if (BooleanNode.TRUE.equals(it.get("master"))) { // See https://github.com/jenkinsci/jenkins/blob/9303136c9d4e5f8fedfac2cd3cf78c10b677298d/core/src/main/java/hudson/model/UsageStatistics.java#L138
                                        masterJvm = new InstanceJVM(
                                            vendor: it.get("jvm-vendor")?.textValue,
                                            name: it.get("jvm-name")?.textValue,
                                            version: it.get("jvm-version")?.textValue
                                        )
                                    }
                                    def os = it.get("os") == null ? "N/A" : it.get("os")
                                    def currentNodesNumber = nodesOnOs.get(os)
                                    currentNodesNumber = currentNodesNumber ? currentNodesNumber + 1 : 1
                                    nodesOnOs.put(os, currentNodesNumber)
                                    def executors = it.get("executors")
                                    totalExecutors += executors.intValue
                                }
                            }
                        }
                    }
                }

                if (jVersion) { // && availableStatsForInstance >= 10 // take stats only if we have at least 10 stats snapshots
                    def metric = new InstanceMetric(
                            instanceId: instanceId,
                            jenkinsVersion: jVersion,
                            jvm: masterJvm,
                            plugins: plugins,
                            jobTypes: jobs,
                            nodesOnOs: nodesOnOs,
                            totalExecutors: totalExecutors,
                            servletContainer: servletContainer
                    )

                    processor(metric)
                }

                // jp.skipChildren();
            }
        }
    }
}
