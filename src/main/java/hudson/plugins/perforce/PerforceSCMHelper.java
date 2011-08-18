package hudson.plugins.perforce;

import com.tek42.perforce.PerforceException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.ByteArrayInputStream;
import java.io.InputStream;


/**
 * @author Brian Westrich
 */
public final class PerforceSCMHelper {

    private static final Logger LOGGER = Logger.getLogger(PerforceSCMHelper.class.getName());
    private static final String DEPOT_ROOT = "//";
    private static final String EXCLUSION_VIEW_PREFIX = "-";

    private PerforceSCMHelper() {
        // static methods, do not instantiate
    }

    /**
     * Generate a path for the changes command based on a workspaces views.
     *
     * @param views
     * @return
     */
    static String computePathFromViews(Collection<String> views) {

        StringBuilder path = new StringBuilder("");

        for (String view : views) {
            StringTokenizer columns = new StringTokenizer(view, " ");
            String leftColumn = columns.nextToken().trim();
            if (leftColumn.indexOf(EXCLUSION_VIEW_PREFIX + DEPOT_ROOT) != -1) {
                continue;
            }
            leftColumn = leftColumn.substring(leftColumn.indexOf(DEPOT_ROOT));
            path.append(leftColumn + " ");
        }

        return path.toString();
    }

    /**
     * Assuming there are multiple views, see whether the project path is valid.
     *
     * @param projectPath the project path specified by the user.
     *
     * @return true if valid, false if invalid
     */
    static boolean projectPathIsValidForMultiviews(String projectPath) {
        return projectPath.equals("//...") // root of depot ok
                || projectPath.indexOf('@') > -1; // labels ok {
    }

    static public class WhereMapping {
        private String depot;
        private String workspace;
        private String filesystem;

        public WhereMapping(String depot,String workspace,String filesystem){
            this.depot = depot;
            this.workspace = workspace;
            this.filesystem = filesystem;
        }

        public String getDepotPath() {
            return depot;
        }
        
        public String getFilesystemPath() {
            return filesystem;
        }

        public String getWorkspacePath() {
            return workspace;
        }
        
    }

    static public int readInt(InputStream stream) throws IOException {
        int result=0;
        for (int i=0; i<4; i++) {
            result += (int) (stream.read()&0xff) << (8*(i));
        }
        return result;
    }

    static private Map<String,String> readPythonDictionary(InputStream stream) throws IOException {
        int counter = 0;
        Map<String,String> map = new HashMap<String,String>();
        if(stream.read() == '{'){
            //good to go
            int b = stream.read();
            while(b != -1 && b != 0x30){
                //read in pairs
                String key,value;
                if(b == 's'){
                    key = readPythonString(stream);
                    b = stream.read();
                } else {
                    //keys 'should' always be strings
                    throw new IOException ("Expected 's', but got '" + Integer.toString(b) + "'.");
                }
                if(b == 's'){
                    value = readPythonString(stream);
                    b = stream.read();
                } else if(b == 'i'){
                    value = Integer.toString(readInt(stream));
                    b = stream.read();
                } else {
                    // Don't know how to handle anything but ints and strings, so bail out
                    throw new IOException ("Expected 's' or 'i', but got '" + Integer.toString(b) + "'.");
                }
                map.put(key, value);
            }
        } else {
            throw new IOException ("Not a dictionary.");
        }
        return map;
    }

    static private String readPythonString(InputStream stream) throws IOException {
        int length = (int)readInt(stream);
        byte[] buf = new byte[length];
        stream.read(buf, 0, length);
        String result = new String(buf);
        return result;
    }

    static public WhereMapping parseWhereMapping(byte[] whereOutput) throws PerforceException {
        String depot;
        String workspace;
        String filesystem;
        ByteArrayInputStream stream = new ByteArrayInputStream(whereOutput);
        Map<String,String> map;
        try{
            map = readPythonDictionary(stream);
        } catch (IOException e) {
            throw new PerforceException("Could not parse Where map.", e);
        }
        if(map == null){
            throw new PerforceException("Could not parse Where map.");
        }
        if(map.get("code").equals("error")){
            //error handling
            LOGGER.log(Level.FINE, "P4 Where Parsing Error: "+map.get("data"));
            if(map.get("data")!=null){
                if(map.get("data").contains("not in client view")){
                    //this is non-fatal, but not sure what to do with it
                } else {
                    throw new PerforceException("P4 Where Parsing Error: "+map.get("data"));
                }
            }
        }
        depot = map.get("depotFile");
        workspace = map.get("clientFile");
        filesystem = map.get("path");
        return new WhereMapping(depot,workspace,filesystem);
    }

}
