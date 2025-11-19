package com.projectgroup5.gamedemo.service;

import com.projectgroup5.gamedemo.CreateRoomRequest;
import com.projectgroup5.gamedemo.LobbySlotDto;
import com.projectgroup5.gamedemo.dto.PlayerInfoDto;
import com.projectgroup5.gamedemo.dto.RoomDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LobbyService {

    private static final int TABLE_COUNT = 20;

    // 房间内部模型
    private static class Room {
        long roomId;
        int tableIndex;
        int maxPlayers;
        String mapName;
        String winMode;
        String ownerName;
        boolean started;                // 是否已经开始游戏

        // 玩家列表（第一个一定是房主）
        LinkedHashSet<String> players = new LinkedHashSet<>();
        // 已经点了“准备”的玩家（房主不用准备）
        Set<String> readyPlayers = new HashSet<>();
    }

    private final Room[] tables = new Room[TABLE_COUNT];
    private final AtomicLong roomIdGenerator = new AtomicLong(1);

    // 方便查找：roomId -> Room
    private final Map<Long, Room> roomsById = new HashMap<>();
    // 每个玩家最多在一个房间：username -> roomId
    private final Map<String, Long> userToRoom = new HashMap<>();

    /**
     * 一局游戏真正结束后（GameService 调用），
     * 把房间重置回“等待中”：started=false，所有人未准备。
     */
    public void resetRoomAfterGame(long roomId) {
        Room room = roomsById.get(roomId);
        if (room == null) {
            return;
        }
        room.started = false;
        room.readyPlayers.clear();
        // players 列表保留：大家还在这个桌子，只是都需要重新准备
    }

    // 获取大厅快照
    public synchronized List<LobbySlotDto> getLobbySnapshot() {
        List<LobbySlotDto> list = new ArrayList<>();
        for (int i = 0; i < TABLE_COUNT; i++) {
            LobbySlotDto slot = new LobbySlotDto();
            slot.setIndex(i);
            Room r = tables[i];
            if (r == null) {
                slot.setOccupied(false);
                slot.setRoom(null);
            } else {
                slot.setOccupied(true);
                slot.setRoom(toDto(r));
            }
            list.add(slot);
        }
        return list;
    }

    // 创建房间（房主自动加入）
    public synchronized RoomDto createRoom(CreateRoomRequest req, String ownerName) {
        // 已经在别的房间里了，拒绝
        if (userToRoom.containsKey(ownerName)) {
            return null;
        }

        // 找空桌子
        int freeIndex = -1;
        for (int i = 0; i < TABLE_COUNT; i++) {
            if (tables[i] == null) {
                freeIndex = i;
                break;
            }
        }
        if (freeIndex == -1) {
            return null;
        }

        Room r = new Room();
        r.roomId = roomIdGenerator.getAndIncrement();
        r.tableIndex = freeIndex;
        r.maxPlayers = Math.max(1, Math.min(4, req.getMaxPlayers()));
        r.mapName = req.getMapName();
        r.winMode = req.getWinMode();
        r.ownerName = ownerName;
        r.started = false;
        r.players.add(ownerName);          // 房主加入

        tables[freeIndex] = r;
        roomsById.put(r.roomId, r);
        userToRoom.put(ownerName, r.roomId);

        return toDto(r);
    }

    // 加入房间
    public synchronized RoomDto joinRoom(long roomId, String username) {
        // 已在一个房间里，且不是当前房间 => 拒绝
        Long current = userToRoom.get(username);
        if (current != null && current != roomId) {
            return null;
        }

        Room r = roomsById.get(roomId);
        if (r == null || r.started) return null;
        if (r.players.size() >= r.maxPlayers) return null;

        r.players.add(username);
        userToRoom.put(username, roomId);
        // 加入时默认未准备
        r.readyPlayers.remove(username);

        return toDto(r);
    }

    // 离开房间
    public synchronized void leaveRoom(long roomId, String username) {
        Room r = roomsById.get(roomId);
        if (r == null) return;
        if (!r.players.remove(username)) return;

        userToRoom.remove(username);
        r.readyPlayers.remove(username);

        if (r.players.isEmpty()) {
            // 房间没人了，清空桌子
            tables[r.tableIndex] = null;
            roomsById.remove(roomId);
            return;
        }

        // 房主离开 -> 把第一个玩家设成新房主
        if (username.equals(r.ownerName)) {
            r.ownerName = r.players.iterator().next();
        }
    }

    // 切换准备状态（房主不用准备）
    public synchronized RoomDto toggleReady(long roomId, String username) {
        Room r = roomsById.get(roomId);
        if (r == null || r.started) return null;
        if (!r.players.contains(username)) return null;
        if (username.equals(r.ownerName)) return toDto(r);

        if (r.readyPlayers.contains(username)) {
            r.readyPlayers.remove(username);
        } else {
            r.readyPlayers.add(username);
        }
        return toDto(r);
    }

    // 房主点击开始
    public synchronized RoomDto startGame(long roomId, String ownerName) {
        Room r = roomsById.get(roomId);
        if (r == null) return null;
        if (!ownerName.equals(r.ownerName)) return null;
        if (r.started) return toDto(r);

        // （可选）要求所有非房主玩家都准备好再开始
        for (String p : r.players) {
            if (p.equals(r.ownerName)) continue;
            if (!r.readyPlayers.contains(p)) {
                return null; // 还有人没准备
            }
        }

        r.started = true;
        return toDto(r);
    }

    // 查询某个玩家当前房间（用于前端判断是否在房间里）
    public synchronized Long getRoomIdByUser(String username) {
        return userToRoom.get(username);
    }

    private RoomDto toDto(Room r) {
        RoomDto dto = new RoomDto();
        dto.setRoomId(r.roomId);
        dto.setTableIndex(r.tableIndex);
        dto.setMaxPlayers(r.maxPlayers);
        dto.setCurrentPlayers(r.players.size());
        dto.setMapName(r.mapName);
        dto.setWinMode(r.winMode);
        dto.setOwnerName(r.ownerName);
        dto.setStarted(r.started);
        List<PlayerInfoDto> playerDtos = new ArrayList<>();
        for (String username : r.players) {
            PlayerInfoDto p = new PlayerInfoDto();
            p.setUsername(username);
            p.setOwner(username.equals(r.ownerName));
            p.setReady(r.readyPlayers.contains(username));  // readyPlayers 里有就说明已准备
            playerDtos.add(p);
        }
        dto.setPlayers(playerDtos);
        dto.setReadyUsernames(new ArrayList<>(r.readyPlayers));

        return dto;
    }
}
