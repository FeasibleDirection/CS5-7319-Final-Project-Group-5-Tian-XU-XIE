package com.projectgroup5.gamedemo.dto;

import java.util.List;

public class RoomDto {
    private long roomId;
    private int tableIndex;
    private int maxPlayers;
    private int currentPlayers;
    private String mapName;
    private String winMode;
    private String ownerName;

    private boolean started;                // 游戏是否已经开始
    private List<PlayerInfoDto> players;           // 按顺序：第一个是房主
    private List<String> readyUsernames;    // 已准备的玩家

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public int getTableIndex() {
        return tableIndex;
    }

    public void setTableIndex(int tableIndex) {
        this.tableIndex = tableIndex;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public String getWinMode() {
        return winMode;
    }

    public void setWinMode(String winMode) {
        this.winMode = winMode;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public List<PlayerInfoDto> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerInfoDto> players) {
        this.players = players;
    }

    public List<String> getReadyUsernames() {
        return readyUsernames;
    }

    public void setReadyUsernames(List<String> readyUsernames) {
        this.readyUsernames = readyUsernames;
    }
}
