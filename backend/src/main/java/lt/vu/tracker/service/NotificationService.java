package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.NotificationPageResponse;
import lt.vu.tracker.dto.response.NotificationResponse;
import lt.vu.tracker.dto.response.UserSummary;
import lt.vu.tracker.model.Comment;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Notification;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.NotificationType;
import lt.vu.tracker.repository.NotificationRepository;
import lt.vu.tracker.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // writing

    // One notification per recipient. The actor and duplicates are skipped
    public void notifyMentioned(Collection<String> userIds, User actor, Comment comment) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : new LinkedHashSet<>(userIds)) {
            if (userId.equals(actor.getId())) {
                continue;
            }
            Notification notification = create(userId, NotificationType.MENTION, actor,
                    comment.getProjectId(), comment.getIssueKey());
            notification.setCommentId(comment.getId());
            notificationRepository.insert(notification);
        }
    }

    // Nothing happens when you assign an issue to yourself
    public void notifyAssigned(User assignee, User actor, Issue issue) {
        if (assignee.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.insert(create(assignee.getId(), NotificationType.ASSIGNED, actor,
                issue.getProjectId(), issue.getKey()));
    }

    //  reading

    public NotificationPageResponse list(boolean unreadOnly, int page, int size, User current) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest request = PageRequest.of(safePage, safeSize, NEWEST_FIRST);

        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndRead(current.getId(), false, request)
                : notificationRepository.findByUserId(current.getId(), request);
        long unread = notificationRepository.countByUserIdAndRead(current.getId(), false);

        return new NotificationPageResponse(toResponses(result.getContent()), safePage, safeSize,
                result.getTotalElements(), result.getTotalPages(), unread);
    }

    public long unreadCount(User current) {
        return notificationRepository.countByUserIdAndRead(current.getId(), false);
    }

    public NotificationResponse markRead(String id, User current) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(current.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        return toResponses(List.of(notification)).get(0);
    }

    // Marks everything as read
    public long markAllRead(User current) {
        List<Notification> unread = notificationRepository.findAllByUserIdAndRead(current.getId(), false);
        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    //  helpers

    private Notification create(String userId, NotificationType type, User actor, String projectId, String issueKey) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setActorId(actor.getId());
        notification.setProjectId(projectId);
        notification.setIssueKey(issueKey);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        return notification;
    }

    private List<NotificationResponse> toResponses(Collection<Notification> items) {
        Set<String> actorIds = items.stream().map(Notification::getActorId).collect(Collectors.toSet());
        Map<String, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return items.stream().map(n -> {
            User actor = actors.get(n.getActorId());
            return new NotificationResponse(n.getId(), n.getType(), UserSummary.from(actor), n.getIssueKey(),
                    n.getCommentId(), n.getProjectId(), message(n, actor), n.isRead(), n.getCreatedAt());
        }).toList();
    }

    private static String message(Notification n, User actor) {
        String name = actor == null ? "Someone" : (actor.getFirstName() + " " + actor.getLastName()).trim();
        return switch (n.getType()) {
            case MENTION -> name + " mentioned you in a comment on " + n.getIssueKey();
            case ASSIGNED -> name + " assigned " + n.getIssueKey() + " to you";
        };
    }
}