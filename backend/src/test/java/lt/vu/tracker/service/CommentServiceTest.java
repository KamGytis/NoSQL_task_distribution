package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.CommentRequest;
import lt.vu.tracker.dto.response.CommentResponse;
import lt.vu.tracker.dto.response.UserSummary;
import lt.vu.tracker.model.Comment;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.CommentRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class CommentServiceTest {
    private final CommentRepository comments = mock(CommentRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CommentService service =
            new CommentService(comments, issues, users, new PermissionService(projects));

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER
    private final User dave = user("u4", "dave@test.lt");     // not a member
    private final List<User> everyone = List.of(alice, bob, carol, dave);

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER))));
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue()));
        when(comments.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findByEmailIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> emails = inv.getArgument(0);
            return everyone.stream().filter(u -> emails.contains(u.getEmail())).toList();
        });
        when(users.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<String> given = inv.getArgument(0);
            Set<String> ids = new HashSet<>();
            given.forEach(ids::add);
            return everyone.stream().filter(u -> ids.contains(u.getId())).toList();
        });
    }

    private Issue issue() {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        i.setTitle("Title");
        return i;
    }

    private Comment commentBy(String id, String authorId) {
        Comment c = new Comment();
        c.setId(id);
        c.setIssueId("i1");
        c.setIssueKey("DEMO-1");
        c.setProjectId("p1");
        c.setAuthorId(authorId);
        c.setText("original");
        c.setCreatedAt(Instant.now());
        when(comments.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private Comment saved() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(comments).save(captor.capture());
        return captor.getValue();
    }

    //  add

    @Test
    void membersCanAddAComment() {
        CommentResponse res = service.add("DEMO-1", new CommentRequest("  Looks good  "), bob);

        Comment c = saved();
        assertThat(c.getText()).isEqualTo("Looks good");
        assertThat(c.getIssueId()).isEqualTo("i1");
        assertThat(c.getIssueKey()).isEqualTo("DEMO-1");
        assertThat(c.getProjectId()).isEqualTo("p1");
        assertThat(c.getAuthorId()).isEqualTo("u2");
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getEditedAt()).isNull();
        assertThat(c.getMentions()).isEmpty();
        assertThat(res.author().email()).isEqualTo("bob@test.lt");
        assertThat(res.canEdit()).isTrue();
        assertThat(res.canDelete()).isTrue();
    }

    @Test
    void viewersCannotComment() {
        assertStatus(() -> service.add("DEMO-1", new CommentRequest("Hi"), carol), 403);
        verify(comments, never()).save(any());
    }

    @Test
    void nonMembersCannotComment() {
        assertStatus(() -> service.add("DEMO-1", new CommentRequest("Hi"), dave), 404);
    }

    @Test
    void unknownIssueIs404() {
        when(issues.findByKey("DEMO-99")).thenReturn(Optional.empty());
        assertStatus(() -> service.add("DEMO-99", new CommentRequest("Hi"), bob), 404);
    }

    @Test
    void issueKeyIsCaseInsensitive() {
        CommentResponse res = service.add("demo-1", new CommentRequest("Hi"), bob);
        assertThat(res.issueKey()).isEqualTo("DEMO-1");
    }

    //  mentions

    @Test
    void mentionsOnlyResolveToOtherProjectMembers() {
        CommentResponse res = service.add("DEMO-1", new CommentRequest(
                        "Hi @carol@test.lt and @dave@test.lt, cc @carol@test.lt @alice@test.lt @bob@test.lt @ghost@test.lt"),
                bob);

        // carol and alice are members; dave is not; bob is the author; ghost does not exist; carol is deduplicated
        assertThat(saved().getMentions()).containsExactlyInAnyOrder("u3", "u1");
        assertThat(res.mentions()).extracting(UserSummary::email)
                .containsExactlyInAnyOrder("carol@test.lt", "alice@test.lt");
    }

    @Test
    void mentionsIgnoreCase() {
        service.add("DEMO-1", new CommentRequest("ping @CAROL@Test.LT"), bob);
        assertThat(saved().getMentions()).containsExactly("u3");
    }

    @Test
    void aPlainEmailIsNotAMention() {
        service.add("DEMO-1", new CommentRequest("write to carol@test.lt about it"), bob);
        assertThat(saved().getMentions()).isEmpty();
    }

    @Test
    void noUserLookupWhenThereAreNoMentions() {
        service.add("DEMO-1", new CommentRequest("Nothing to see"), bob);
        verify(users, never()).findByEmailIn(anyCollection());
    }

    //  list
    @Test
    void listShowsWhatTheCallerMayDo() {
        Comment fromBob = commentBy("c1", "u2");
        Comment fromCarol = commentBy("c2", "u3");
        when(comments.findByIssueIdOrderByCreatedAtAsc("i1")).thenReturn(List.of(fromBob, fromCarol));

        List<CommentResponse> asViewer = service.list("DEMO-1", carol);

        assertThat(asViewer).extracting(CommentResponse::id).containsExactly("c1", "c2");
        assertThat(asViewer.get(0).author().email()).isEqualTo("bob@test.lt");
        assertThat(asViewer.get(0).canEdit()).isFalse();
        assertThat(asViewer.get(0).canDelete()).isFalse();
        assertThat(asViewer.get(1).canEdit()).isFalse();     // a viewer cannot edit even their own
        assertThat(asViewer.get(1).canDelete()).isTrue();
    }

    @Test
    void adminsCanDeleteButNotEditOthersComments() {
        Comment c = commentBy("c1", "u2");
        when(comments.findByIssueIdOrderByCreatedAtAsc("i1")).thenReturn(List.of(c));

        CommentResponse res = service.list("DEMO-1", alice).get(0);

        assertThat(res.canEdit()).isFalse();
        assertThat(res.canDelete()).isTrue();
    }

    @Test
    void nonMembersCannotListComments() {
        assertStatus(() -> service.list("DEMO-1", dave), 404);
    }

    //  update

    @Test
    void authorsCanEditAndMentionsAreRecomputed() {
        Comment c = commentBy("c1", "u2");
        c.setMentions(new ArrayList<>(List.of("u3")));

        CommentResponse res = service.update("c1", new CommentRequest("  no mentions now  "), bob);

        assertThat(c.getText()).isEqualTo("no mentions now");
        assertThat(c.getEditedAt()).isNotNull();
        assertThat(c.getMentions()).isEmpty();
        assertThat(res.mentions()).isEmpty();
        assertThat(res.canEdit()).isTrue();
    }

    @Test
    void onlyTheAuthorCanEdit() {
        Comment c = commentBy("c1", "u2");
        assertStatus(() -> service.update("c1", new CommentRequest("hijack"), alice), 403);
        assertThat(c.getText()).isEqualTo("original");
        verify(comments, never()).save(any());
    }

    @Test
    void anAuthorWhoBecameAViewerCannotEdit() {
        commentBy("c1", "u3");
        assertStatus(() -> service.update("c1", new CommentRequest("x"), carol), 403);
    }

    @Test
    void editingAnUnknownCommentIs404() {
        when(comments.findById("nope")).thenReturn(Optional.empty());
        assertStatus(() -> service.update("nope", new CommentRequest("x"), bob), 404);
    }

    @Test
    void nonMembersCannotEdit() {
        commentBy("c1", "u2");
        assertStatus(() -> service.update("c1", new CommentRequest("x"), dave), 404);
    }

    // delete

    @Test
    void authorsCanDeleteTheirOwnComment() {
        Comment c = commentBy("c1", "u2");
        service.delete("c1", bob);
        verify(comments).delete(c);
    }

    @Test
    void aViewerCanDeleteTheirOwnComment() {
        Comment c = commentBy("c1", "u3");
        service.delete("c1", carol);
        verify(comments).delete(c);
    }

    @Test
    void adminsCanDeleteAnyComment() {
        Comment c = commentBy("c1", "u2");
        service.delete("c1", alice);
        verify(comments).delete(c);
    }

    @Test
    void membersCannotDeleteSomeoneElsesComment() {
        commentBy("c1", "u1");
        assertStatus(() -> service.delete("c1", bob), 403);
        verify(comments, never()).delete(any(Comment.class));
    }

    @Test
    void nonMembersCannotDelete() {
        commentBy("c1", "u2");
        assertStatus(() -> service.delete("c1", dave), 404);
    }
}