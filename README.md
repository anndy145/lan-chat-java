# Java 內網聊天室

使用 Java TCP Socket 實作的內網文字聊天室，支援公開聊天、私人訊息、自訂群組與線上使用者名單，提供 Swing 圖形介面和命令列操作。

程式架構與通訊格式請參考[技術文件](docs/technical.md)。

## 編譯

在專案根目錄執行：

```cmd
javac -encoding UTF-8 -d out src\server\*.java src\client\*.java
```

## 下載 Windows 免安裝版

在 GitHub 的 Actions 頁面開啟成功的 `Build Windows portable client` 執行紀錄，從 Artifacts 下載 `LanChatClient-Windows-x64`。解壓縮後執行 `LanChatClient.exe`，並保留旁邊的 `app` 和 `runtime` 資料夾。Client 不需要另外安裝 Java，可連接現有 Server 或啟動臨時 Server。此流程會在推送至 `main` 時執行，也可以手動執行。

## 使用方式

伺服器端（可以自訂port, 預設為 port 5487）：

```cmd
java -cp out server.Server <port>
```

客戶端：

```cmd
java -cp "out;resources" client.ChatWindow
```

輸入 Server 的 IP、連接埠和暱稱，按「連線」即可聊天。同一台電腦測試時，IP 使用 `127.0.0.1`；其他電腦則使用 Server 的內網 IPv4 位址。

按 `Dark mode` 或 `Light mode` 可切換介面主題。主題設定目前不會在關閉程式後保存。

若沒有 Server，可在 Client 輸入 port 後按 `Start temporary server`。畫面會顯示主機的內網 IPv4 位址，提供給其他電腦使用；主機自己再輸入暱稱並按 `Connect`。按 `Stop temporary server` 或關閉主機視窗會斷開所有使用者。臨時 Server 只保留執行期間的聊天狀態，不提供帳號或保存聊天紀錄。

在線上名單選擇使用者並按 `Create group`，輸入群組名稱即可建立群組；按住 Ctrl 可選取多人。群組成員會收到各自的聊天分頁。使用者離開聊天室或斷線時會離開群組，群組不會永久保存。

要邀請新成員時，先選取群組分頁，再從線上名單選人並按 `Add members`。新成員只會看到加入後的訊息；Server 與 Client 都需要更新至支援此功能的版本。

雙擊線上使用者可開啟或切換到他的私訊分頁。按分頁上的 `×` 可隱藏私訊或群組；雙擊線上使用者可重新開啟私訊，收到新訊息時分頁也會重新出現。隱藏群組不會退出群組，目前連線期間的對話紀錄會保留。

也可以使用命令列 Client：

```cmd
java -cp out client.Client <ip address> <port> <username>
```

命令列使用者受邀後可用 `/group <group-id> <message>` 回覆；輸入 `/quit` 可離開聊天室。Server 與 Client 請使用相同版本。私訊分頁使用臨時 ID 辨識對象；對方離線後，須從名單重新選擇才能與新連線聊天。

Server 主控台顯示使用者加入、離開或斷線的時間，以及訊息的傳送時間、發送者與收件者；不保存聊天內容。通訊目前未加密。
