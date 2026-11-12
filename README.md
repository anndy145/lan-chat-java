## 編譯

在專案根目錄執行：

```cmd
javac -encoding UTF-8 -d out src\server\*.java src\client\*.java
```

## 使用方式 (GUI)

伺服器端（可以自訂port, 預設為 port 5487）：

```cmd
java -cp out server.Server <port>
```

客戶端：

```cmd
java -cp "out;resources" client.ChatWindow
```

若沒有 Server，可在 Client 輸入 port 後按 `Start temporary server`。按 `Stop temporary server` 或關閉主機視窗會斷開所有使用者。臨時 Server 只保留執行期間的聊天狀態，不提供帳號或保存聊天紀錄。

在線上名單選擇使用者並按 `Create group`，輸入群組名稱即可建立群組；按住 Ctrl 可選取多人。群組成員會收到各自的聊天分頁。使用者離開聊天室或斷線時會離開群組，群組不會永久保存。

要邀請新成員時，先選取群組分頁，再從線上名單選人並按 `Add members`。新成員只會看到加入後的訊息；Server 與 Client 都需要更新至支援此功能的版本。

也可以使用命令列 Client：

```cmd
java -cp out client.Client <ip address> <port> <username>
```

命令列使用者受邀後可用 `/group <group-id> <message>` 回覆；輸入 `/quit` 可離開聊天室。Server 與 Client 請使用相同版本。私訊分頁使用臨時 ID 辨識對象；對方離線後，須從名單重新選擇才能與新連線聊天。

Server 主控台顯示使用者加入、離開或斷線的時間，以及訊息的傳送時間、發送者與收件者；不保存聊天內容。

命令列也可以使用觀察模式：以 `java -cp out client.Client <ip> <port> --observe` 啟動，只查看線上名單。輸入 `/join <暱稱>` 加入聊天；輸入 `/leave` 離開聊天但繼續查看線上名單；輸入 `/quit` 完全離開。重新加入後會取得新的使用者 ID，原本的群組成員身分不會保留。
命令列可用 `/msg <暱稱> <訊息>` 傳送私訊，例如 `/msg Andy 你好 世界`。暱稱含空白時使用引號，例如 `/msg "Andy Chen" 你好 世界`；引號內的雙引號和反斜線分別寫成 `\"` 和 `\\`。