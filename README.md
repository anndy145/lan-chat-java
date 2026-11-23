# Java 內網聊天室

使用 Java TCP Socket 建立的內網聊天室，支援公開聊天、私訊、群組聊天、圖片傳送與線上使用者名單，提供 Swing 圖形介面和命令列介面。

程式架構與通訊協定請參考[技術文件](docs/technical.md)。

## Windows 免安裝版

從 [GitHub Releases](https://github.com/anndy145/lan-chat-java/releases) 下載 `LanChatClient-Windows-x64.zip`。解壓縮整個 ZIP 後執行 `LanChatClient.exe`，並保留旁邊的 `app` 和 `runtime` 資料夾。

### 開啟臨時伺服器

1. 在主機開啟 **Connection settings**，將 **Port** 設為 `5487` 或自訂連接埠，再按 **Start temp server**。
2. 主機要加入聊天時，在 **Server IP** 填 `127.0.0.1`，輸入暱稱後按 **Connect**。
3. 同一區網的其他電腦填入主機 Lobby 顯示的 LAN IP、相同連接埠和自己的暱稱，再按 **Connect**。

按 **Stop temp server** 或關閉開房的視窗會中斷所有連線。

## 從原始碼編譯與啟動

在專案根目錄編譯：

```cmd
javac -encoding UTF-8 -d out src\common\*.java src\server\*.java src\client\*.java
```

使用獨立 Server 時，先執行以下指令。省略連接埠時預設為 `5487`。

```cmd
java -cp out server.Server 5487
```

另開終端機啟動 GUI：

```cmd
java -cp "out;resources" client.ChatWindow
```

在 **Connection settings** 輸入 Server IP、連接埠與暱稱，再按 **Connect**。也能從 GUI 開啟臨時伺服器。

## 圖形介面操作

- **文字**：在 Lobby、私訊或群組輸入訊息。按 **Enter** 送出，按 **Shift+Enter** 換行。貼上多行文字會保留原有換行。
- **私訊**：雙擊左側名單的使用者，或右鍵選擇 **Open private chat**。
- **群組**：按住 **Ctrl** 可選取多位使用者，再按分頁列的 **+** 建立群組。選取群組分頁後，可使用 **Add members**、**Group members** 和 **Leave group**。關閉分頁只會隱藏分頁，新成員只會看到加入後的訊息。
- **線上名單**：在 **Connection settings** 按 **Observe only** 可只查看名單。要加入時按 **Join chat**。在聊天中按上方的 **Leave** 會返回觀察模式，再按設定內的 **Disconnect** 可中斷連線。
- **圖片**：在私訊或群組分頁按輸入框左側的圖片圖示，選擇 PNG、JPG 或 JPEG。每張限 5 MiB、2,000 萬像素。點擊縮圖可預覽或另存，命令列只顯示收圖提示。
- **主題**：點右上角的月亮或太陽圖示切換亮色與深色模式。

## 命令列介面

連接現有 Server 並加入聊天：

```cmd
java -cp out client.Client 127.0.0.1 5487 Andy
```

只查看線上名單：

```cmd
java -cp out client.Client 127.0.0.1 5487 --observe
```

連線後輸入 `/help` 可查看指令：

| 指令                      | 用途                                                           |
| ------------------------- | -------------------------------------------------------------- |
| `/users`                  | 顯示線上使用者。                                               |
| `/join <暱稱>`            | 在觀察模式加入聊天。                                           |
| `/leave`                  | 離開聊天，繼續查看線上名單。                                   |
| `/msg <暱稱> <訊息>`      | 傳送私訊。暱稱有空白時請加引號，例如 `/msg "Andy Chen" 你好`。 |
| `/group <群組 ID> <訊息>` | 向已加入的群組傳送訊息。群組 ID 顯示在收到的群組通知中。       |
| `/group-members <群組 ID>` | 查看群組成員。 |
| `/group-leave <群組 ID>` | 退出群組。 |
| `/quit`                   | 斷線並結束 Client。                                            |

一般文字會送到 Lobby。若公開訊息要以 `/` 開頭，請輸入兩個斜線，例如 `//hello` 會送出 `/hello`。命令列使用者可受邀加入群組，但不能建立群組或邀請成員。

## 限制

- 暱稱最多 32 個 Unicode 碼位，文字訊息最多 2,000 個 Unicode 碼位。
- Server 不保存聊天內容；離開或斷線後會退出群組，重新加入時取得新的使用者 ID。
- 目前沒有帳號登入、離線訊息或傳輸加密。Server 可以讀取轉送的訊息。
- Server 與 Client 請使用相同版本。

## 發布 Windows Client

推送到 `main` 時，GitHub Actions 會建置並上傳 Artifact。推送版本 tag 時，工作流程會建立同名 Release，並附上 Windows 免安裝版 ZIP。以下以 `v0.8` 為例。

```powershell
git tag -a v0.8 -m "LAN Chat v0.8"
git push origin v0.8
```

若版本 tag 已存在，可在 GitHub 的 **Actions → Build Windows portable client → Run workflow** 選擇 `main`，並在 `release_tag` 輸入該 tag，例如 `v0.4`。留空則只建置、不發布。
