package com.projectgroup5.gamedemo.dto;

public class LoginResponse {
    private String token;
    private UserDto user;

    public String getToken() { 
        return token; 
    }
    
    public void setToken(String token) { 
        this.token = token; 
    }

    public UserDto getUser() { 
        return user; 
    }
    
    public void setUser(UserDto user) { 
        this.user = user; 
    }

    public static class UserDto {
        private long id;
        private String username;
        private String email;

        public long getId() { 
            return id; 
        }
        
        public void setId(long id) { 
            this.id = id; 
        }

        public String getUsername() { 
            return username; 
        }
        
        public void setUsername(String username) { 
            this.username = username; 
        }

        public String getEmail() { 
            return email; 
        }
        
        public void setEmail(String email) { 
            this.email = email; 
        }
    }
}


