package common;

/** 描述一種分塊傳輸（圖片或一般檔案）使用的協定前綴與大小上限。 */
public record TransferWire(String label, String begin, String chunk, String end, String fail, String result,
                           int maxBytes) {
    public static final TransferWire IMAGE = new TransferWire("Image", ImageWire.BEGIN, ImageWire.CHUNK,
            ImageWire.END, ImageWire.FAIL, ImageWire.RESULT, ImageFiles.MAX_BYTES);
    public static final TransferWire FILE = new TransferWire("File", FileWire.BEGIN, FileWire.CHUNK,
            FileWire.END, FileWire.FAIL, FileWire.RESULT, FileWire.MAX_BYTES);

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
                default -> throw new IllegalArgumentException("Unknown transfer destination.");
            };
        }
    }

    public static boolean validName(String name) {
        return !name.isBlank() && name.length() <= 255 && name.indexOf('/') < 0
                && name.indexOf((char) 92) < 0 && !name.equals(".") && !name.equals("..")
                && name.chars().noneMatch(Character::isISOControl);
    }

    public boolean isData(String line) {
        return line.startsWith(begin) || line.startsWith(chunk) || line.startsWith(end) || line.startsWith(fail);
    }
}
