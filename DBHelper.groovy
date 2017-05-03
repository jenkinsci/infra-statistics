import org.sqlite.*
import java.sql.*

class DBHelper {

    def static setupDB(workingDir){

        def dbFile = new File(workingDir, "stats.db")

        boolean dbExists = dbFile.exists()

        // in memory
        // db = groovy.sql.Sql.newInstance("jdbc:sqlite::memory:","org.sqlite.JDBC")

        // persistent
        def db = groovy.sql.Sql.newInstance("jdbc:sqlite:"+dbFile.absolutePath,"org.sqlite.JDBC")

        if(!dbExists){
            // define the tables
            db.execute("create table jenkins(instanceid, month, version, jvmvendor, jvmname, jvmversion)")
            db.execute("create table plugin(instanceid, month, name, version)")
            db.execute("create table job(instanceid, month, type, jobnumber)")
            db.execute("create table node(instanceid, month, osname, nodenumber)")
            db.execute("create table executor(instanceid, month, numberofexecutors)")
            db.execute("create table importedfile(name)")
            db.execute("CREATE INDEX plugin_name on plugin (name)")
            db.execute("CREATE INDEX jenkins_version on jenkins (version)")
            db.execute("CREATE INDEX plugin_month on plugin (month)")
            db.execute("CREATE INDEX plugin_namemonth on plugin (name,month)")
        }

        // Disable journaling to prevent "Some kind of disk I/O error occurred"
        // See http://stackoverflow.com/questions/3979322/exception-message-some-kind-of-disk-i-o-error-occurred
        db.execute("PRAGMA journal_mode=OFF")

        return db;
    }


    /**
     * is the file with the given name already imported?
     */
    static boolean doImport(db, fileName){
        if(db){
            def filePrefix = fileName.substring(0, fileName.indexOf("."))+"%"
            def rows = db.rows("select name from importedfile where name like $filePrefix;")
            return rows.size() == 0
        }
        true
    }

}



