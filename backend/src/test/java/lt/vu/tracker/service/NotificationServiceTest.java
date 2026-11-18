package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.NotificationPageResponse;
import lt.vu.tracker.dto.response.NotificationResponse;
import lt.vu.tracker.model.Comment;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Notification;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.NotificationType;
import lt.vu.tracker.repository.NotificationRepository;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static lt.vu.tracker.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService service = new NotificationService(notifications, users);

    private final User alice = user("u1", "alice@test.lt");
    private final User bob = user("u2", "bob@test.lt");
    private final User carol = user("u3", "carol@test.lt");
    private final List<User> everyone = List.of(alice, bob, carol);

    @BeforeEach
    void setUp() {
        bob.setFirstName("Bob");
        bob.setLastName("Builder");
        when(notifications.insert(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        when(notifications.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<String> given = inv.getArgument(0);
            Set<String> ids = new HashSet<>();
            given.forEach(ids::add);
            return everyone.stream().filter(u -> ids.contains(u.getId())).toList();
        });
    }

    private Comment comment() {
        Comment c = new Comment();
        c.setId("c1");
        c.setIssueKey("DEMO-1");
        c.setProjectId("p1");
        return c;
    }

    private Issue issue() {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        return i;
    }

    private Notification notification(String id, String userId, NotificationType type, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setType(type);
        n.setActorId("u2");
        n.setProjectId("p1");
        n.setIssueKey("DEMO-1");
        n.setRead(read);
        n.setCreatedAt(Instant.now());
        return n;
    }

    private List<Notification> inserted(int times) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(times)).insert(captor.capture());
        return captor.getAllValues();
    }

    //  mentions

    @Test
    void everyMentionedUserGetsOneNotification() {
        service.notifyMentioned(List.of("u3", "u1"), bob, comment());

        List<Notification> created = inserted(2);
        assertThat(created).extracting(Notification::getUserId).containsExactly("u3", "u1");
        assertThat(created).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.MENTION);
            assertThat(n.getActorId()).isEqualTo("u2");
            assertThat(n.getIssueKey()).isEqualTo("DEMO-1");
            assertThat(n.getProjectId()).isEqualTo("p1");
            assertThat(n.getCommentId()).isEqualTo("c1");
            assertThat(n.isRead()).isFalse();
            assertThat(n.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void theAuthorAndDuplicatesAreSkipped() {
        service.notifyMentioned(List.of("u2", "u3", "u3"), bob, comment());
        assertThat(inserted(1)).extracting(Notification::getUserId).containsExactly("u3");
    }

    @Test
    void nobodyToNotifyMeansNothingIsWritten() {
        service.notifyMentioned(List.of(), bob, comment());
        service.notifyMentioned(null, bob, comment());
        verify(notifications, never()).insert(any(Notification.class));
    }

    //  assignments

    @Test
    void theNewAssigneeIsNotified() {
        service.notifyAssigned(carol, bob, issue());

        Notification n = inserted(1).get(0);
        assertThat(n.getUserId()).isEqualTo("u3");
        assertThat(n.getType()).isEqualTo(NotificationType.ASSIGNED);
        assertThat(n.getActorId()).isEqualTo("u2");
        assertThat(n.getIssueKey()).isEqualTo("DEMO-1");
        assertThat(n.getProjectId()).isEqualTo("p1");
        assertThat(n.getCommentId()).isNull();
    }

    @Test
    void assigningToYourselfNotifiesNobody() {
        service.notifyAssigned(bob, bob, issue());
        verify(notifications, never()).insert(any(Notification.class));
    }

    //  listing

    @Test
    void listShowsMessagesActorsAndTheUnreadCount() {
        Notification mention = notification("n1", "u1", NotificationType.MENTION, false);
        Notification assigned = notification("n2", "u1", NotificationType.ASSIGNED, true);
        when(notifications.findByUserId(eq("u1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(mention, assigned), inv.<Pageable>getArgument(1), 45));
        when(notifications.countByUserIdAndRead("u1", false)).thenReturn(7L);

        NotificationPageResponse res = service.list(false, 1, 20, alice);

        assertThat(res.items()).extracting(NotificationResponse::id).containsExactly("n1", "n2");
        assertThat(res.items().get(0).message()).isEqualTo("Bob Builder mentioned you in a comment on DEMO-1");
        assertThat(res.items().get(1).message()).isEqualTo("Bob Builder assigned DEMO-1 to you");
        assertThat(res.items().get(0).actor().email()).isEqualTo("bob@test.lt");
        assertThat(res.items().get(0).read()).isFalse();
        assertThat(res.items().get(1).read()).isTrue();
        assertThat(res.unreadCount()).isEqualTo(7);
        assertThat(res.page()).isEqualTo(1);
        assertThat(res.size()).isEqualTo(20);
        assertThat(res.totalItems()).isEqualTo(45);
        assertThat(res.totalPages()).isEqualTo(3);
    }

    @Test
    void anActorWhoNoLongerExistsBecomesSomeone() {
        Notification n = notification("n1", "u1", NotificationType.ASSIGNED, false);
        n.setActorId("u9");
        when(notifications.findByUserId(eq("u1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(n), inv.<Pageable>getArgument(1), 1));

        NotificationResponse item = service.list(false, 0, 20, alice).items().get(0);

        assertThat(item.actor()).isNull();
        assertThat(item.message()).isEqualTo("Someone assigned DEMO-1 to you");
    }

    @Test
    void unreadOnlyUsesTheUnreadQuery() {
        when(notifications.findByUserIdAndRead(eq("u1"), eq(false), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.<Notification>of(), inv.<Pageable>getArgument(2), 0));

        service.list(true, 0, 20, alice);

        verify(notifications).findByUserIdAndRead(eq("u1"), eq(false), any(Pageable.class));
        verify(notifications, never()).findByUserId(any(), any(Pageable.class));
    }

    @Test
    void listClampsPageAndSizeAndSortsNewestFirst() {
        when(notifications.findByUserId(eq("u1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.<Notification>of(), inv.<Pageable>getArgument(1), 0));

        service.list(false, -5, 1000, alice);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByUserId(eq("u1"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection().isDescending()).isTrue();
    }

    @Test
    void unreadCountIsScopedToTheCaller() {
        when(notifications.countByUserIdAndRead("u1", false)).thenReturn(4L);
        assertThat(service.unreadCount(alice)).isEqualTo(4);
    }

    //  marking read

    @Test
    void markingReadSavesTheChange() {
        Notification n = notification("n1", "u1", NotificationType.MENTION, false);
        when(notifications.findById("n1")).thenReturn(Optional.of(n));

        NotificationResponse res = service.markRead("n1", alice);

        assertThat(n.isRead()).isTrue();
        assertThat(res.read()).isTrue();
        verify(notifications).save(n);
    }

    @Test
    void markingAReadNotificationAgainDoesNotWriteAgain() {
        Notification n = notification("n1", "u1", NotificationType.MENTION, true);
        when(notifications.findById("n1")).thenReturn(Optional.of(n));

        assertThat(service.markRead("n1", alice).read()).isTrue();

        verify(notifications, never()).save(any(Notification.class));
    }

    @Test
    void someoneElsesNotificationLooksMissing() {
        Notification n = notification("n1", "u1", NotificationType.MENTION, false);
        when(notifications.findById("n1")).thenReturn(Optional.of(n));

        assertStatus(() -> service.markRead("n1", bob), 404);

        assertThat(n.isRead()).isFalse();
        verify(notifications, never()).save(any(Notification.class));
    }

    @Test
    void anUnknownNotificationIs404() {
        when(notifications.findById("nope")).thenReturn(Optional.empty());
        assertStatus(() -> service.markRead("nope", alice), 404);
    }

    @Test
    void markAllReadMarksEveryUnreadNotification() {
        Notification a = notification("n1", "u1", NotificationType.MENTION, false);
        Notification b = notification("n2", "u1", NotificationType.ASSIGNED, false);
        List<Notification> unread = List.of(a, b);
        when(notifications.findAllByUserIdAndRead("u1", false)).thenReturn(unread);

        assertThat(service.markAllRead(alice)).isEqualTo(2);

        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
        verify(notifications).saveAll(unread);
    }

    @Test
    void markAllReadWithNothingUnreadWritesNothing() {
        when(notifications.findAllByUserIdAndRead("u1", false)).thenReturn(List.of());

        assertThat(service.markAllRead(alice)).isZero();

        verify(notifications, never()).saveAll(anyIterable());
    }
}