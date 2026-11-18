package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.CommentRequest;
import lt.vu.tracker.dto.response.CommentResponse;
import lt.vu.tracker.dto.response.UserSummary;
import lt.vu.tracker.model.Comment;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.ProjectMember;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.CommentRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.UserRepository;
import lt.vu.tracker.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    // "@name@example.com". The lookbehind stops a plain "name@example.com" from matching
    private static final Pattern MENTION =
            Pattern.compile("(?<![\\w.%+-])@([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");


    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    public CommentResponse add(String issueKey, CommentRequest request, User current) {
        Issue issue = findIssue(issueKey);
        Project project = permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.MEMBER);
        String text = request.text().trim();

        Comment comment = new Comment();
        comment.setIssueId(issue.getId());
        comment.setIssueKey(issue.getKey());
        comment.setProjectId(project.getId());
        comment.setAuthorId(current.getId());
        comment.setText(text);
        comment.setMentions(resolveMentions(text, project, current));
        comment.setCreatedAt(Instant.now());

        Comment saved = commentRepository.save(comment);
        notifyNewMentions(saved, List.of(), current);
        return toResponse(saved, project, current, usersFor(List.of(saved)));
    }

    public List<CommentResponse> list(String issueKey, User current) {
        Issue issue = findIssue(issueKey);
        Project project = permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.VIEWER);

        List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId());
        Map<String, User> users = usersFor(comments);
        return comments.stream().map(c -> toResponse(c, project, current, users)).toList();
    }

    public CommentResponse update(String commentId, CommentRequest request, User current) {
        Comment comment = findComment(commentId);
        Project project = permissionService.requireRole(comment.getProjectId(), current.getId(), ProjectRole.MEMBER);
        if (!comment.getAuthorId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit a comment");
        }

        List<String> previousMentions = new ArrayList<>(comment.getMentions());
        String text = request.text().trim();
        comment.setText(text);
        comment.setMentions(resolveMentions(text, project, current));
        comment.setEditedAt(Instant.now());

        Comment saved = commentRepository.save(comment);
        notifyNewMentions(saved, previousMentions, current);
        return toResponse(saved, project, current, usersFor(List.of(saved)));
    }

    // The author can delete their own comment, and a project admin can delete any comment
    public void delete(String commentId, User current) {
        Comment comment = findComment(commentId);
        Project project = permissionService.requireRole(comment.getProjectId(), current.getId(), ProjectRole.VIEWER);
        boolean author = comment.getAuthorId().equals(current.getId());
        boolean admin = permissionService.roleOf(project, current.getId())
                .map(role -> role == ProjectRole.ADMIN).orElse(false);
        if (!author && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or a project admin can delete a comment");
        }
        commentRepository.delete(comment);
    }

    // helpers

    private Issue findIssue(String key) {
        String normalised = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        return issueRepository.findByKey(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    private Comment findComment(String id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    }

    // User ids of the other project members mentioned in the text. Everything else is ignored
    private List<String> resolveMentions(String text, Project project, User author) {
        Set<String> emails = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            emails.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (emails.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> memberIds = project.getMembers().stream()
                .map(ProjectMember::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findByEmailIn(emails).stream()
                .filter(u -> memberIds.contains(u.getId()))
                .filter(u -> !u.getId().equals(author.getId()))
                .map(User::getId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, User> usersFor(Collection<Comment> comments) {
        Set<String> ids = new HashSet<>();
        for (Comment c : comments) {
            ids.add(c.getAuthorId());
            ids.addAll(c.getMentions());
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private CommentResponse toResponse(Comment c, Project project, User current, Map<String, User> users) {
        ProjectRole role = permissionService.roleOf(project, current.getId()).orElse(null);
        boolean author = c.getAuthorId().equals(current.getId());
        boolean canEdit = author && role != null && role.atLeast(ProjectRole.MEMBER);
        boolean canDelete = author || role == ProjectRole.ADMIN;
        List<UserSummary> mentions = c.getMentions().stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .map(UserSummary::from)
                .toList();
        return new CommentResponse(c.getId(), c.getIssueKey(), UserSummary.from(users.get(c.getAuthorId())),
                c.getText(), mentions, c.getCreatedAt(), c.getEditedAt(), canEdit, canDelete);
    }
    // Notifies only the people who were not mentioned before, so an edit never re-notifies anyone.
    private void notifyNewMentions(Comment comment, List<String> alreadyNotified, User actor) {
        List<String> added = comment.getMentions().stream()
                .filter(id -> !alreadyNotified.contains(id))
                .toList();
        if (!added.isEmpty()) {
            notificationService.notifyMentioned(added, actor, comment);
        }
    }
}