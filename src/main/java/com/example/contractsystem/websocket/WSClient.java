package com.example.contractsystem.websocket;

import okhttp3.*;

public class WSClient {

    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("ws://localhost:8000/ws") // WebSocket 地址
                .build();

        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("Connected to FastAPI WebSocket!");

                // 发送 Solidity 代码到服务器
                webSocket.send("pragma solidity ^0.8.0; contract A {}");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("Server output: " + text); // 实时打印 run.py 输出
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("Closing: " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                t.printStackTrace();
            }
        });

        // 保持程序运行，否则主线程结束 WebSocket 会被关闭
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}