package WSAnything.Core;

import WSAnything.FSLog;
import com.intellij.openapi.project.Project;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheFileSerializer {
    private static final Integer CACHE_TAG = 1;
    private static String CACHE_NAME = "WSANYTHING_CACHE";

    public static boolean SerializeToLocalFile(Project pro, List<WSFileCache> list) {
        FSLog.log.info("SerializeToLocalFile begin");
        String path = getCacheFileName(pro);
        try {
            deleteFile(path);
            FileOutputStream fileOut = new FileOutputStream(path);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);

            out.writeObject(CACHE_TAG);
            out.writeObject(list.size());

            list.forEach((v) -> {
                try {
                    out.writeObject(v);
                } catch (Exception e) {
                    FSLog.log.info(e);
                }
            });
            out.close();
            fileOut.close();
        } catch (Exception e) {
            FSLog.log.warn(e);
        }
        FSLog.log.info("SerializeToLocalFile end");
        return true;
    }

    public static Map<String, WSFileCache> readFromLocalFile(Project pro) {
        FSLog.log.info("readFromLocalFile begin");
        String path = getCacheFileName(pro);
        Map<String, WSFileCache> ret = new HashMap<>();
        try {
            File file = new File(path);
            if (!file.exists()) {
                return ret;
            }
            FileInputStream fileIn = new FileInputStream(path);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Integer tag = (Integer) in.readObject();
            Integer count = (Integer) in.readObject();

            if (tag.intValue() == CACHE_TAG.intValue()) {
                for (int i = 0; i < count.intValue(); ++i) {
                    WSFileCache cache = (WSFileCache)in.readObject();
                    ret.put(cache.m_fileName, cache);
                }
            } else {
                FSLog.log.info(String.format("CacheFile tag(%d) != curr tag(%d),delete it", tag, CACHE_TAG));
                deleteFile(path);
            }
            in.close();
            fileIn.close();
        } catch (Exception c) {
            deleteFile(path);
            FSLog.log.warn(c);
        }
        FSLog.log.info("readFromLocalFile end");
        return ret;
    }

    public static String getCacheFileName(Project pro) {
        String projectPath = pro.getBasePath();
        File path = new File(projectPath, CACHE_NAME);
        return path.getPath();
    }

    public static void deleteFile(String fileName) {
        File path = new File(fileName);
        path.delete();
    }
}
