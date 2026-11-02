# Java 內網聊天室

使用 Java TCP Socket 實作的內網文字聊天室，支援多人聊天、線上使用者名單，以及 Swing 圖形介面和命令列操作。

## 編譯

安裝 JDK 後，在專案根目錄執行：

```cmd
javac -encoding UTF-8 -d out src\server\*.java src\client\*.java
```

## 使用方式

伺服器端（可以自訂port, 預設為 port 5487）：

```cmd
java -cp out server.Server <port>
```

客戶端：

```cmd
java -cp out client.ChatWindow
```

輸入 Server 的 IP、連接埠和暱稱，按「連線」即可聊天。同一台電腦測試時，IP 使用 `127.0.0.1`；其他電腦則使用 Server 的內網 IPv4 位址。

也可以使用命令列 Client：

```cmd
java -cp out client.Client 127.0.0.1 5487 Andy
```

輸入 `/quit` 可離開聊天室。Server 與 Client 請使用相同版本。
