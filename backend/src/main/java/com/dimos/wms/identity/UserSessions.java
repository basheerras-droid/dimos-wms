package com.dimos.wms.identity;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers the open browser sessions of each user, so that disabling a user or resetting a password signs that user
 * out at once instead of after the session timeout. (One server instance: sessions live in its memory.)
 */
@Component
class UserSessions {

    private final Map<Long, Set<HttpSession>> byUser = new ConcurrentHashMap<>();

    void register(Long userId, HttpSession session) {
        Set<HttpSession> sessions = byUser.computeIfAbsent(userId, id -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        sessions.removeIf(UserSessions::isDead);
        sessions.add(session);
    }

    /** Ends every open session of the user. Returns how many were open. */
    int invalidateAll(Long userId) {
        Set<HttpSession> sessions = byUser.remove(userId);
        if (sessions == null) {
            return 0;
        }
        int count = 0;
        for (HttpSession s : sessions) {
            if (!isDead(s)) {
                try {
                    s.invalidate();
                    count++;
                } catch (IllegalStateException alreadyGone) {
                    // the session ended in the meantime: nothing to do
                }
            }
        }
        return count;
    }

    private static boolean isDead(HttpSession session) {
        try {
            session.getLastAccessedTime();
            return false;
        } catch (IllegalStateException invalidated) {
            return true;
        }
    }
}
