1. 專案架構：Server 管理連線、轉送訊息；Client 負責介面與輸入。
2. 檔案職責：說明 Server.java、`ClientSession.java`、`Client.java` 和 ChatWindow.java 各做什麼。
3. 連線流程：Client 如何連線、送出暱稱，以及 Server 如何接受連線。
4. 通訊格式：例如 @users:、`@private:`、`@group:` 分別代表什麼，欄位如何編碼。
5. 執行緒分工：Server 如何同時處理多位使用者，以及 GUI 為什麼要在背景接收訊息。
6. 資料與限制：聊天紀錄和群組不會永久保存；Server 主控台記錄事件與傳送對象，但通訊尚未加密。
7. 測試方式：如何測試群聊、私訊、群組、重複暱稱和斷線。
