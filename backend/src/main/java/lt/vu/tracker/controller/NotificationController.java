package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.CountResponse;
import lt.vu.tracker.dto.response.NotificationPageResponse;
import lt.vu.tracker.dto.response.NotificationResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.NotificationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping("/notifications")
    public NotificationPageResponse list(@RequestParam(defaultValue = "false") boolean unread,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(unread, page, size, currentUser.get());
    }

    @GetMapping("/notifications/unread-count")
    public CountResponse unreadCount() {
        return new CountResponse(notificationService.unreadCount(currentUser.get()));
    }

    @PutMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable String id) {
        return notificationService.markRead(id, currentUser.get());
    }

    @PutMapping("/notifications/read-all")
    public CountResponse markAllRead() {
        return new CountResponse(notificationService.markAllRead(currentUser.get()));
    }
}