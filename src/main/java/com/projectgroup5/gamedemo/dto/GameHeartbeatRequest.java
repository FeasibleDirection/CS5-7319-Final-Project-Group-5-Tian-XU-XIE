package com.projectgroup5.gamedemo.dto;

public class GameHeartbeatRequest {

    private long roomId;
    private int hp;
    private int score;
    private long elapsedMillis;
    private boolean finished;

    // 预留给架构 A 的"输入事件"，目前前端可以先传空字符串
    private String input; // e.g. "L,R,F" 代表本 tick 的按键组合

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getInput() { 
        return input; 
    }
    
    public void setInput(String input) { 
        this.input = input; 
    }
}


