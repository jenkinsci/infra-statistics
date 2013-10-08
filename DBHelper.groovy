import org.sqlite.*
import java.sql.*

class DBHelper {

    def static setupDB(workingDir){

        def dbFile = new File(workingDir, "stats.db")

        boolean dbExists = dbFile.exists()

        // in memory
        // db = groovy.sql.Sql.newInstance("jdbc:sqlite::memory:","org.sqlite.JDBC")

        // persitent
        def db = groovy.sql.Sql.newInstance("jdbc:sqlite:"+dbFile.absolutePath,"org.sqlite.JDBC")

        if(!dbExists){
            // define the tables
            db.execute("create table jenkins(instanceid, month, version)")
            db.execute("create table plugin(instanceid, month, name, version)")
            db.execute("create table job(instanceid, month, type, jobnumber)")
            db.execute("create table node(instanceid, month, osname, nodenumber)")
            db.execute("create table executor(instanceid, month, numberofexecutors)")
            db.execute("create table importedfile(name)")
            db.execute("CREATE INDEX plugin_name on plugin (name)")
        }

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



