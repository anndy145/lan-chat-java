# Java 內網聊天室

使用 Java TCP Socket 建立的內網文字聊天室，支援公開聊天、私訊、群組聊天與線上使用者名單，提供 Swing 圖形介面和命令列介面。

程式架構與通訊協定請參考[技術文件](docs/technical.md)。

## 編譯

```cmd
javac -encoding UTF-8 -d out src\common\*.java src\server\*.java src\client\*.java
```

## 圖形介面

先啟動 Server。連接埠可省略，預設為 `5487`：

```cmd
java -cp out server.Server
```

指定連接埠：

```cmd
java -cp out server.Server 5487
```

再啟動 Client：

```cmd
java -cp "out;resources" client.ChatWindow
```

輸入 Server IP、連接埠與暱稱後按 `Connect`。同一台電腦測試時，Server IP 可填 `127.0.0.1`；其他電腦請使用 Server 的內網 IPv4 位址。

若沒有現成的 Server，可在 Client 輸入連接埠後按 `Start temporary server`，再輸入暱稱並按 `Connect`。按 `Stop temporary server` 或關閉主機視窗會中斷所有連線。

雙擊線上使用者可開啟私訊。選取線上使用者並按 `Create group` 可建立群組；要邀請新成員時，先選取群組分頁，再選擇使用者並按 `Add members`。按住 Ctrl 可選取多人。新成員只會看到加入後的群組訊息。

按 `View online users` 可在不加入聊天的情況下查看名單。加入聊天後按 `Leave` 可離開聊天並繼續查看名單；按 `Dark mode` 或 `Light mode` 可切換主題。

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
| `/quit`                   | 斷線並結束 Client。                                            |

一般文字會送到 Lobby。若公開訊息要以 `/` 開頭，請輸入兩個斜線，例如 `//hello` 會送出 `/hello`。命令列使用者可受邀加入群組，但不能建立群組或邀請成員。

## 限制

- 暱稱最多 32 個 Unicode 碼位，文字訊息最多 2,000 個 Unicode 碼位。
- Server 不保存聊天內容；離開或斷線後會退出群組，重新加入時取得新的使用者 ID。
- 目前沒有帳號登入、離線訊息或傳輸加密。Server 可以讀取轉送的訊息。
- Server 與 Client 請使用相同版本。

## 發布 Windows Client

推送到 `main` 時，GitHub Actions 會建置並上傳 Artifact。推送版本 tag 時，工作流程會建立同名 Release，並附上 Windows 免安裝版 ZIP：

```powershell
git tag v0.
git push origin v0.
```

若版本 tag 已存在，可在 GitHub 的 **Actions → Build Windows portable client → Run workflow** 選擇 `main`，並在 `release_tag` 輸入該 tag，例如 `v0.4`。留空則只建置、不發布。下載 ZIP 後，請解壓縮並保留 EXE 旁的 `app` 和 `runtime` 資料夾。
