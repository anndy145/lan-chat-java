package common;

/** 圖片協定沿用每行一筆 UTF-8 資料的 TCP 連線。 */
public final class ImageWire {
    public static final String ENABLE = "@image-enable";
    public static final String READY = "@image-ready";
    public static final String BEGIN = "@image-begin:";
    public static final String CHUNK = "@image-chunk:";
    public static final String END = "@image-end:";
    public static final String FAIL = "@image-fail:";
    public static final String NOTICE = "@image-notice:";
    public static final String RESULT = "@image-result:";

    private ImageWire() {}

    public static boolean validName(String name) {
        return !name.isBlank() && name.length() <= 255 && name.indexOf('/') < 0
                && name.indexOf((char) 92) < 0 && !name.equals(".") && !name.equals("..")
                && name.chars().noneMatch(Character::isISOControl);
    }
}
