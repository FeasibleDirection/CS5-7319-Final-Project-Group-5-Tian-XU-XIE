package com.projectgroup5.gamedemo.dto;

public class CreateRoomRequest {
    private int maxPlayers;     // 1-4
    private String mapName;     // "Nebula-01" / "Asteroid Field" / "Eclipse Station"
    private String winMode;     // "SCORE_50" / "SCORE_100" / "TIME_1M" / "TIME_5M"

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
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
}


