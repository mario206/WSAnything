package FileSearch;

public class WSUtil {
    public static String getFileSuffix(String fileName) {
        String suffix = "";
        int i = fileName.lastIndexOf('.');
        if(i != -1) {
            suffix = fileName.substring(i);
        }
        return suffix;
    }
}
