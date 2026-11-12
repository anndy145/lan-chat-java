# 技術文件

本文件描述 Java 內網聊天室的架構、通訊格式與限制。編譯和執行方式請參考[專案 README](../README.md)。

## 架構與檔案職責

Server 使用 TCP `ServerSocket` 接受連線。每個 Client 透過一條 TCP Socket 連線，公開訊息、私訊與群組訊息都由 Server 轉送。

| 檔案 | 職責 |
| --- | --- |
| `src/server/Server.java` | 管理 Server、Client 與群組，轉送訊息並輸出事件紀錄。 |
| `src/server/ClientSession.java` | 處理單一連線的輸入、協定解析與結束。 |
| `src/client/ChatWindow.java` | 提供 Swing 介面、聊天分頁及使用者操作。 |
| `src/client/ChatConnection.java` | 建立 GUI Client 的 Socket 連線、送出初始協定訊息並讀取回應。 |
| `src/client/Client.java` | 提供命令列介面、觀察模式與聊天指令。 |
| `src/common/ChatProtocol.java` | 提供 Client 和 Server 共用的協定字串、Base64 編解碼及文字長度常數。 |

## 連線與使用者狀態

Server 預設使用 port `5487`，也可從命令列指定 `1` 到 `65535` 的 port。GUI 的 `Start temporary server` 使用同一套匿名聊天 Server，並在背景執行緒綁定指定 port。主機可使用 `127.0.0.1` 連線；其他電腦使用主機的內網位址。停止臨時 Server 或關閉主機視窗會關閉監聽及所有連線。

目前的 GUI 和命令列 Client 連線後都先送出 `@observe:v2`。若指定暱稱加入，Client 接著送出 `@join:<b64(暱稱)>`。命令列以 `--observe` 啟動時只查看名單，之後可使用 `/join <暱稱>` 加入聊天。

Server 檢查暱稱是否已被使用，成功時產生新的臨時 UUID，並回傳 `@joined:<ID>`。暱稱比較區分大小寫，例如 `Andy` 和 `andy` 是不同使用者。Server 仍接受 `@hello:v2:<b64(暱稱)>` 作為直接加入的第一行；目前兩種 Client 不使用這個方式啟動。

GUI 的 **Leave** 與命令列的 `/leave` 會送出 `@leave`。Server 移除該使用者的群組成員資格，但保留連線並繼續傳送線上名單。再次加入時，即使使用相同暱稱，也會取得新的臨時 ID。命令列的 `/quit` 則會結束連線。連線中斷時，Server 移除該 Client 並更新名單。

Server 對空白暱稱使用 `Anonymous`；GUI 與命令列 Client 在一般加入流程中要求非空白暱稱。

## 執行緒與共享資料

Server 的主執行緒執行 `accept()`。每個連線由獨立的 `ClientSession` 執行緒讀取訊息。Server 的同步方法保護加入、離開、群組操作與轉送流程；每個 Client 的 `send()` 也使用同步，避免多個執行緒同時寫入同一個輸出串流。

GUI 在背景執行緒透過 `ChatConnection` 建立連線並讀取 Socket，再使用 `SwingUtilities.invokeLater()` 將介面更新交給 Swing 事件派送執行緒。命令列 Client 使用主執行緒接收訊息，背景執行緒讀取鍵盤。

## 通訊格式

雙方使用 UTF-8 文字，每行是一則訊息。下表的 `b64(...)` 表示文字先編成 UTF-8 位元組，再轉成 Base64。Base64 是編碼方式，不提供加密。Server 與 Client 應使用相同版本。

### Client 傳給 Server

| 格式 | 用途 |
| --- | --- |
| `@observe:v2` | 建立只查看線上名單的連線。 |
| `@hello:v2:<b64(暱稱)>` | 相容的直接加入方式，必須作為連線第一行。 |
| `@join:<b64(暱稱)>` | 觀察者以指定暱稱加入聊天。 |
| `@join` | 使用該連線原有的暱稱加入聊天。 |
| `@leave` | 離開聊天並繼續接收線上名單。 |
| `/quit` | 結束連線。 |
| `@say:<b64(訊息)>` | 傳送公開訊息。 |
| `@private:<收件人 ID>:<b64(訊息)>` | 依臨時 ID 傳送私訊。 |
| `@group-create:<b64(群組名稱)>:<成員 ID>,...` | 建立群組。建立者會自動成為成員。 |
| `@group-add:<群組 ID>:<成員 ID>,...` | 由群組成員邀請線上使用者。 |
| `@group-send:<群組 ID>:<b64(訊息)>` | 傳送群組訊息。 |

Server 也將未符合控制訊息格式的非空白行視為公開訊息。目前 Client 使用 `@say:` 傳送公開文字。命令列 Client 會攔截未知指令；公開訊息要以斜線開頭時，使用者可輸入兩個斜線。

### Server 傳給 Client

| 格式 | 用途 |
| --- | --- |
| `@joined:<ID>` | 確認已加入並提供臨時 ID。 |
| `@error:name` | 暱稱已被線上使用者使用。 |
| `@error:name-length` | 暱稱超過長度限制。 |
| `@error:message-length` | 訊息超過長度限制。 |
| `@error:version` | Client 協定版本不相容。 |
| `@users:<ID>=<b64(暱稱)>,...` | 完整的線上使用者名單。 |
| `@pm:<對象 ID>:<b64(對象)>:<b64(顯示文字)>` | 私訊內容或私訊錯誤提示。 |
| `@group:<群組 ID>:<b64(群組名稱)>:<b64(顯示文字)>` | 群組建立、成員加入通知或群組訊息。 |
| `@group-error:<b64(提示文字)>` | 邀請群組成員失敗的提示。 |
| `@notice:<b64(提示文字)>` | 一般錯誤或狀態提示。 |
| 一般文字 | 公開聊天訊息或加入、離開通知。 |

## 訊息與群組

公開訊息會送給所有已加入聊天的 Client，包含發送者。私訊以臨時 ID 指定線上收件人，並只送給發送者和該收件人。若原對象離線，即使有人重用暱稱，舊 ID 也不會指向新使用者；Server 會回傳提示。GUI 依對象 ID 建立分頁，離線時保留紀錄並標示 `(offline)`。

命令列 `/msg <暱稱> <訊息>` 每次會從目前線上名單尋找暱稱對應的 ID。如果原對象離線且另一人使用了同一暱稱，使用者輸入該暱稱時可能找到新連線的人。

Server 以 UUID 區分群組，只接受成員發送群組訊息，並只轉送給該群組的成員。群組名稱不可空白，最長為 80 個 Java 字元。群組成員可以邀請其他線上使用者；新成員收到加入通知及之後的訊息，不會收到加入前的紀錄。

群組與其成員只保存在 Server 記憶體。使用者離開聊天或斷線時會離開群組；沒有成員的群組會被移除。Server 重啟後群組不會保留。Client 的聊天紀錄沒有寫入檔案，建立新的 Server 連線會清除舊的私訊及群組分頁。

## Server 紀錄

Server 主控台顯示使用者加入、主動離開或連線中斷的時間與暱稱。訊息紀錄包含傳送時間、發送者及收件者，不包含聊天內容。群組訊息紀錄會列出當時的收件者。程式不會將這些紀錄寫入檔案。

## 限制與測試狀態

- 暱稱最多 32 個 Unicode 碼位，文字訊息最多 2,000 個 Unicode 碼位。由多個碼位組成的表情符號可能算作多個字元。
- Server 尚未限制 Socket 單行接收資料的大小，也沒有設定首次連線握手的等待期限。文字長度檢查不等同於接收資料量限制。
- TCP 連線沒有加密，也沒有帳號驗證。Server 可以讀取轉送中的訊息。
- 暱稱僅在使用者線上時保持唯一。臨時 ID 無法驗證重新上線的人是否為同一人。
- v2 協定與舊版不相容，需要同時更新 Server、GUI Client 和命令列 Client。
- GUI 在 Server 斷線後不會自動重新連線。Client 與 Server 均不保存離線訊息。
- 本機 TCP 測試涵蓋暱稱重用、私訊隔離、群組與命令列指令；本機 GUI 已測試連線及訊息收發。完整 GUI 操作與兩台實體電腦跨網路連線尚未完整驗證。

## 執行測試

`IdentityRegression` 驗證臨時 ID、私訊及群組流程。`ClientCommandRegression` 啟動命令列 Client，驗證指令、私訊與公開訊息隔離。在 Windows 專案根目錄執行：

```cmd
javac -encoding UTF-8 -d out src\common\*.java src\server\*.java src\client\*.java tests\IdentityRegression.java tests\ClientCommandRegression.java
java -cp out IdentityRegression
java -cp out ClientCommandRegression
```
