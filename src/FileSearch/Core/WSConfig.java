package FileSearch.Core;

import java.util.Arrays;
import java.util.List;

public class WSConfig {
    public static int MaxFileSize_ProgramLang = 1024 * 1024;
    public static int MaxFileSize_OtherFile = 1024 * 100;
    public static int MAX_RESULT = 1000;
    public static int Max_Column_Per_Line = 3000;
    public static String VecProgramFileSuffix[] = {
            ".asm",
            ".bat",
            ".vb",
            ".c",".cc",".h",".hh",".cpp",".inc",".hpp",".cxx",".inl",
            ".m",".mm",
            ".cs",
            ".py",
            ".java",
            ".js",
            ".ts",
            ".as",
            ".ml",
            ".perl",".pl",
            ".css",".html",".htm",
            ".fx",
            ".php",
            ".hlsl",
            ".kt",
            ".go",
            ".sh",
            ".shader",
            ".sql",
            ".R",
            ".swfit",
            ".scala",
            ".rb"
    };
    public static String VecOtherFileSuffix[] = {
            ".json",
            ".xml",
            ".txt",
            ".mk",
            ".csv",
            ".xaml",
            ".resx",
            ".vsixmanifest",
            ".log"
    };
    public static String VecSuffixToExclude[] = {
            ".min.js",
            "-min.js"
    };

    public static final List<String> ListSearchSuffix_ProgramLang = Arrays.asList(VecProgramFileSuffix);
    public static final List<String> ListSearchSuffix_OtherFile = Arrays.asList(VecOtherFileSuffix);
    public static final List<String> ListExcludeSuffx = Arrays.asList(VecSuffixToExclude);
}