package com.projectgroup5.gamedemo.dto;

public class LeaderboardEntryDto {
    private String username;
    private int totalScore;
    private int gamesPlayed;

    public LeaderboardEntryDto(String username, int totalScore, int gamesPlayed) {
        this.username = username;
        this.totalScore = totalScore;
        this.gamesPlayed = gamesPlayed;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }
}

