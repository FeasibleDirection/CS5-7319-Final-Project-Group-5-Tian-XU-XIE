package com.projectgroup5.gamedemo.controller;

import com.projectgroup5.gamedemo.GameHeartbeatRequest;
import com.projectgroup5.gamedemo.dto.GameScoreEntry;
import com.projectgroup5.gamedemo.entity.User;
import com.projectgroup5.gamedemo.service.AuthService;
import com.projectgroup5.gamedemo.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameController {

    private final AuthService authService;
    private final GameService gameService;

    public GameController(AuthService authService,
                          GameService gameService) {
        this.authService = authService;
        this.gameService = gameService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody GameHeartbeatRequest body) {

        Optional<User> userOpt = getUserFromHeader(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or missing token");
        }
        if (body.getRoomId() <= 0) {
            return ResponseEntity.badRequest().body("roomId is required");
        }

        String username = userOpt.get().getUsername();
        gameService.heartbeat(username, body);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/room/{roomId}/scoreboard")
    public ResponseEntity<List<GameScoreEntry>> scoreboard(
            @PathVariable("roomId") long roomId) {
        List<GameScoreEntry> list = gameService.getScoreboard(roomId);
        return ResponseEntity.ok(list);
    }

    // 可选：客户端主动调用 /leave 也可以标记 finished
    @PostMapping("/room/{roomId}/leave")
    public ResponseEntity<?> leave(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @PathVariable("roomId") long roomId) {

        Optional<User> userOpt = getUserFromHeader(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or missing token");
        }
        String username = userOpt.get().getUsername();
        gameService.markPlayerFinished(username, roomId);
        return ResponseEntity.ok().build();
    }

    private Optional<User> getUserFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        return authService.getUserByToken(token);
    }
}
