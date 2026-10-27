package com.grievance360.notification;

import com.grievance360.complaint.Complaint;
import com.grievance360.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;

    @Transactional
    public void notify(User user, Complaint complaint, Notification.Type type, String title, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setComplaint(complaint);
        n.setType(type);
        n.setTitle(title.length() > 160 ? title.substring(0, 160) : title);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        repo.save(n);
    }

    @Transactional
    public void notifyAll(Collection<User> users, Complaint complaint, Notification.Type type, String title, String message) {
        users.forEach(u -> notify(u, complaint, type, title, message));
    }
}
