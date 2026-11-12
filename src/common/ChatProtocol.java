package common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Client 和 Server 共用的文字協定與資料長度限制。 */
public final class ChatProtocol {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final String OBSERVE = "@observe:v2";
    public static final String JOIN = "@join:";
    public static final String LEAVE = "@leave";
    public static final String SAY = "@say:";
    public static final String PRIVATE = "@private:";
    public static final String GROUP_SEND = "@group-send:";
    public static final String GROUP_CREATE = "@group-create:";
    public static final String GROUP_ADD = "@group-add:";
    public static final String JOINED = "@joined:";
    public static final String USERS = "@users:";
    public static final String PRIVATE_MESSAGE = "@pm:";
    public static final String GROUP_MESSAGE = "@group:";
    public static final String NOTICE = "@notice:";
    public static final String GROUP_ERROR = "@group-error:";
    public static final String ERROR_NAME_LENGTH = "@error:name-length";
    public static final String ERROR_MESSAGE_LENGTH = "@error:message-length";
    public static final String ERROR_NAME = "@error:name";
    public static final String ERROR_VERSION = "@error:version";

    private ChatProtocol() {}

    public static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}