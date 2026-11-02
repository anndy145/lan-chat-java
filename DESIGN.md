# 設計說明

## 目標

示範兩台區域網路電腦之間的 TCP 文字通訊，並讓 Java 初學者容易編譯及理解程式。

## 使用方式

```powershell
javac -encoding UTF-8 Server.java Client.java
java Server 5000
java Client 192.168.1.10 5000 Andy
```

## 程式架構

`Server` 管理一個 `ServerSocket` 和執行緒安全的線上 `ClientSession` 集合。每個連線工作階段負責一個 Socket 及其輸出串流。`Client` 的主執行緒讀取鍵盤並送出文字，接收執行緒則讀取並顯示收到的訊息。文字使用 UTF-8 編碼，以換行分隔，因此可使用 `BufferedReader.readLine()` 讀取每則訊息。`send` 使用同步機制，避免同時廣播的內容在單一連線中交錯。

## 設計決定

專案使用兩個原始碼檔案與兩個公開的程式入口，方便使用 `javac Server.java Client.java` 編譯。程式採用 UTF-8 文字協定、同步傳送與連線結束時的資源清理。時間與訊息格式目前足夠簡單，因此沒有另外建立訊息類別。

## 取捨

- 每位連線使用者各佔一個執行緒，讓並行處理較容易理解。
- 訊息以明文傳輸，沒有身分驗證；此程式定位為內網教學用途。
- 目前沒有自動重新連線功能。
