# 技術文件

本文件描述 Java 內網聊天室的程式架構、通訊格式與目前限制。編譯和執行方式請參考[專案 README](../README.md)。

## 架構與檔案職責

Server 使用 TCP `ServerSocket` 接受連線。每個 Client 透過一條 TCP Socket 連線，公開訊息、私訊與群組訊息都由 Server 轉送。

| 檔案                            | 職責                                                      |
| ------------------------------- | --------------------------------------------------------- |
| `src/server/Server.java`        | 啟動 Server、管理 Client 與群組、轉送訊息及輸出事件紀錄。 |
| `src/server/ClientSession.java` | 處理單一 Client 的輸入、協定解析及連線結束。              |
| `src/client/ChatWindow.java`    | 提供 Swing 介面、連線與聊天分頁。                         |
| `src/client/Client.java`        | 提供命令列介面及群組訊息輸入。                            |

## 連線與使用者狀態

Server 預設使用 port `5487`，也可從命令列指定 `1` 到 `65535` 的 port。Server 成功建立 `ServerSocket` 後才顯示啟動訊息。

GUI 的 `Start temporary server` 使用目前的匿名聊天 Server 實作，先在背景執行緒綁定指定 port，再等待連線。主機可用 `127.0.0.1` 連線，其他電腦使用畫面列出的內網位址。按下 `Stop temporary server` 或關閉主機視窗會關閉監聽及所有連線。臨時 Server 不提供帳號，也不保存聊天室資料；若日後加入帳號功能，需要維持這個模式的界線。

GUI Client 連線後先送出 `@observe:v2`，取得線上使用者名單。使用者加入聊天時，Client 送出 `@join:<name>`，其中 `name` 是以 UTF-8 編碼後再轉成 Base64 的暱稱。Server 檢查暱稱，成功時產生新的臨時 UUID，並回傳 `@joined:<ID>`；暱稱重複時回傳 `@error:name`。線上暱稱比較區分大小寫，例如 `Andy` 和 `andy` 是不同使用者。

使用者按下 **Leave** 後，GUI 傳送 `@leave`。Server 移除其群組成員資格，但保留連線並繼續傳送線上名單。再次加入時，即使使用原本暱稱，也會取得新的臨時 ID。命令列 Client 的第一行使用 `@hello:v2:<b64(暱稱)>`，`/quit` 會關閉連線。

Server 為沒有輸入暱稱的直接連線使用 `Anonymous`。GUI 在加入前要求輸入非空白暱稱。連線中斷時，Server 移除該 Client 並更新名單。

## 執行緒與共享資料

Server 的主執行緒執行 `accept()`。每個連線由獨立的 `ClientSession` 執行緒讀取訊息。Server 的同步方法保護加入、離開、群組操作與轉送流程；每個 Client 的 `send()` 也使用同步，避免多個執行緒同時寫入同一個輸出串流。

GUI 在背景執行緒建立連線並讀取 Socket。背景執行緒使用 `SwingUtilities.invokeLater()`，將介面更新交給 Swing 事件派送執行緒。命令列 Client 則使用主執行緒接收訊息，背景執行緒讀取鍵盤。

## 通訊格式

雙方使用 UTF-8 文字，每行是一則訊息。下表的 `b64(...)` 表示文字先編成 UTF-8 位元組，再轉成 Base64。Base64 是編碼方式，**不提供加密**。Server 與 Client 應使用相同版本。

### Client 傳給 Server

| 格式                                            | 用途                                     |
| ----------------------------------------------- | ---------------------------------------- |
| `@observe:v2`                                   | 連線後第一行，建立只查看線上名單的連線。 |
| `@hello:v2:<b64(暱稱)>`                         | 命令列 Client 連線後傳送的第一行。       |
| `@join:<b64(暱稱)>`                             | 觀察者加入聊天。                         |
| `@join`                                         | 使用該連線原有的暱稱加入聊天。           |
| `@leave`                                        | 離開聊天並繼續接收線上名單。             |
| `/quit`                                         | 結束連線。                               |
| `@say:<b64(訊息)>`                              | 傳送公開訊息。                           |
| `@private:<收件人 ID>:<b64(訊息)>`              | 依臨時 ID 傳送私訊。                     |
| `@group-create:<b64(群組名稱)>:<成員 ID>,...`   | 建立群組。建立者會自動成為成員。         |
| `@group-send:<群組 ID>:<b64(訊息)>`             | 傳送群組訊息。                           |

Server 也把未符合控制訊息格式的非空白行視為公開訊息。新版 Client 使用 `@say:` 傳送公開文字，因此 `@leave` 這類文字不會誤觸發控制指令。GUI 的 `/quit` 會轉成離開聊天，命令列的 `/quit` 則結束連線。

### Server 傳給 Client

| 格式                                               | 用途                           |
| -------------------------------------------------- | ------------------------------ |
| `@joined:<ID>`                                     | 確認使用者已加入並提供臨時 ID。|
| `@error:name`                                      | 暱稱已被線上使用者使用。       |
| `@error:version`                                   | Client 協定版本不相容。        |
| `@users:<ID>=<b64(暱稱)>,...`                     | 完整的線上使用者名單。         |
| `@pm:<對象 ID>:<b64(對象)>:<b64(顯示文字)>`       | 私訊內容或私訊錯誤提示。       |
| `@group:<群組 ID>:<b64(群組名稱)>:<b64(顯示文字)>` | 群組建立通知或群組訊息。       |
| `@notice:<b64(提示文字)>`                          | 一般錯誤或狀態提示。           |
| 一般文字                                           | 公開聊天訊息或加入、離開通知。 |

## 訊息與群組

公開訊息會送給所有已加入聊天的 Client，包含發送者。私訊以臨時 ID 指定線上收件人，並只送給發送者和該收件人。若原對象離線，即使有人重用暱稱，舊 ID 也不會指向新使用者；Server 會回傳提示。GUI 依對象 ID 建立分頁，離線時保留紀錄並標示 `(offline)`。

建立群組時，Server 從目前的線上使用者尋找指定成員，並以 UUID 區分群組。群組名稱不可空白，最長為 80 個 Java 字元。Server 只接受群組成員發送的群組訊息，並只轉送給該群組成員。GUI 為每個群組建立分頁。

群組與其成員只保存在 Server 記憶體。使用者離開聊天或斷線時會離開群組；沒有成員的群組會被移除。Server 重啟後群組不會保留。Client 的聊天紀錄也沒有寫入檔案，建立新的 Server 連線會清除舊的私訊及群組分頁。

## Server 紀錄

Server 主控台顯示使用者加入、主動離開或連線中斷的時間與暱稱。訊息紀錄包含傳送時間、發送者及收件者，不包含聊天內容。群聊紀錄會列出當時的收件者；這些紀錄目前不會由程式寫入檔案。

## 限制與測試狀態

- TCP 連線沒有加密，也沒有帳號驗證。Server 會接收訊息內容以進行轉送；私訊不代表 Server 無法讀取內容。
- 暱稱僅在使用者線上時保持唯一。臨時 ID 可防止舊分頁誤送，但無法驗證重新上線的人是否為同一人。
- v2 協定與舊版不相容，需要同時更新 Server、GUI Client 和命令列 Client。
- GUI 在 Server 斷線後不會自動重新連線。Client 與 Server 均不保存離線訊息。
- 已透過本機 TCP 測試公開訊息、私訊、群組、線上名單、離開與重新加入，以及部分邊界條件。GUI 的完整按鈕操作與兩台實體電腦在最近一次修改後尚未重新驗證。

臨時 ID 的回歸測試位於 `tests/IdentityRegression.java`。在 Windows 的專案根目錄執行：

```cmd
javac -encoding UTF-8 -d out src\common\*.java src\server\*.java src\client\*.java tests\IdentityRegression.java
java -cp out IdentityRegression
```
