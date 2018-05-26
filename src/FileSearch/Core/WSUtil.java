package FileSearch.Core;

import java.util.ArrayList;
import java.util.List;

public class WSUtil {
    public static String getFileSuffix(String fileName) {
        String suffix = "";
        int i = fileName.lastIndexOf('.');
        if (i != -1) {
            suffix = fileName.substring(i);
        }
        return suffix;
    }
}