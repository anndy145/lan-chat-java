# Java 內網 TCP 聊天室

這是一個命令列文字聊天室，使用 Java TCP Socket。Server 接受多個 Client，每位使用者輸入的文字會廣播給所有連線中的 Client。

## 功能

- 建立 TCP `ServerSocket` 與 `Socket` 連線
- Client 可同時送出文字、接收其他人的文字
- 支援多位 Client、加入／離開訊息和 `/quit`
- 使用 UTF-8，可正常傳送中文


## 編譯

在此資料夾開啟 PowerShell：

```powershell
javac -encoding UTF-8 Server.java Client.java
```

## 同一台電腦測試

開兩個 PowerShell 視窗。

第一個視窗：

```powershell
java Server 5000
```

第二個視窗：

```powershell
java Client 127.0.0.1 5000 Andy
```

若要模擬第二位使用者，再開第三個視窗：

```powershell
java Client 127.0.0.1 5000 Bob
```

## 兩台電腦在同一個內網測試

1. 在電腦 A 執行 `java Server 5000`。它會列出可供 Client 使用的 IPv4 位址，例如 `192.168.1.10`。
2. 將 `Server.java` 和 `Client.java` 複製到電腦 B，並在 B 上編譯。
3. 電腦 B 執行：

   ```powershell
   java Client 192.168.1.10 5000 Bob
   ```

4. A 與 B 的 Client 都可以輸入文字後按 Enter；輸入 `/quit` 可以離開。

若 B 無法連線，請確認兩台電腦在同一個 Wi-Fi／區域網路，並在 Server 電腦的 Windows 防火牆允許 Java 或 TCP 連接埠 5000 的「私人網路」連線。

## 程式分工

```text
Server.java
  Server          接收連線、保存 Client 清單、廣播訊息
  ClientSession   處理單一 Client 的輸入、輸出、離線清理

Client.java
  Client          主執行緒讀鍵盤並送出；背景執行緒接收並顯示訊息
```

這是基本教學用的內網聊天室，訊息沒有加密或帳號驗證；不要直接暴露在公網上。
