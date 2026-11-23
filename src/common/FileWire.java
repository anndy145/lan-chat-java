package common;

/** 一般檔案沿用圖片的分塊格式，但使用獨立的前綴、能力宣告與大小上限。 */
public final class FileWire {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public static final String ENABLE = "@file-enable";
    public static final String READY = "@file-ready";
    public static final String BEGIN = "@file-begin:";
    public static final String CHUNK = "@file-chunk:";
    public static final String END = "@file-end:";
    public static final String FAIL = "@file-fail:";
    public static final String RESULT = "@file-result:";

    private FileWire() {}
}
