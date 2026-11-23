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

    // Older servers ignore this BEGIN payload because it is not a transfer UUID.
    public static final String ENABLE_LOBBY = BEGIN + "lobby-capability";
    public static final String READY_LOBBY = READY + ":lobby";
    public static final String LOBBY_TARGET = "lobby";
    public static final String LOBBY_TITLE = "Lobby";

    public enum Destination {
        PRIVATE("p"), GROUP("g"), LOBBY("l");

        private final String code;

        Destination(String code) { this.code = code; }

        public String code() { return code; }

        public static Destination fromCode(String code) {
            return switch (code) {
                case "p" -> PRIVATE;
                case "g" -> GROUP;
                case "l" -> LOBBY;
                default -> throw new IllegalArgumentException("Unknown image destination.");
            };
        }
    }

    private ImageWire() {}

    public static boolean validName(String name) {
        return !name.isBlank() && name.length() <= 255 && name.indexOf('/') < 0
                && name.indexOf((char) 92) < 0 && !name.equals(".") && !name.equals("..")
                && name.chars().noneMatch(Character::isISOControl);
    }
}
