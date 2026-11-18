package lt.vu.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;




import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://localhost:27017/tracker_test")
class AuthAndProjectFlowE2ETest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    // One simulated browser so it keeps its own session cookie.
    private final class Api {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String userId;

        HttpResponse<String> send(String method, String path, String json) {
            HttpRequest.BodyPublisher body = json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json);
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                    .header("Content-Type", "application/json")
                    .method(method, body)
                    .build();
            try {
                return http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@test.lt";
    }

    private static String uniqueProjectKey() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private static String registerJson(String email, String password) {
        return """
                {"firstName":"Test","lastName":"User","birthYear":2000,"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new AssertionError("Field '" + name + "' not found in: " + json);
        return m.group(1);
    }

    private Api registerAndLogin(String email) {
        Api api = new Api();
        assertThat(api.send("POST", "/auth/register", registerJson(email, "password123")).statusCode())
                .isEqualTo(201);
        HttpResponse<String> login = api.send("POST", "/auth/login", loginJson(email, "password123"));
        assertThat(login.statusCode()).isEqualTo(200);
        api.userId = field(login.body(), "id");
        return api;
    }

    @Test
    void registerLoginMeLogout() {
        String email = uniqueEmail();
        Api api = new Api();

        assertThat(api.send("GET", "/users/me", null).statusCode()).isEqualTo(401);

        HttpResponse<String> registered = api.send("POST", "/auth/register", registerJson(email, "password123"));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.body()).contains(email).doesNotContain("passwordHash").doesNotContain("password123");

        assertThat(api.send("POST", "/auth/register", registerJson(email, "password123")).statusCode())
                .isEqualTo(409);
        assertThat(api.send("POST", "/auth/register", registerJson("not-an-email", "password123")).statusCode())
                .isEqualTo(400);
        assertThat(api.send("POST", "/auth/register", registerJson(uniqueEmail(), "short")).statusCode())
                .isEqualTo(400);

        assertThat(api.send("POST", "/auth/login", loginJson(email, "wrong-password")).statusCode())
                .isEqualTo(401);
        assertThat(api.send("GET", "/users/me", null).statusCode()).isEqualTo(401);

        assertThat(api.send("POST", "/auth/login", loginJson(email, "password123")).statusCode()).isEqualTo(200);
        HttpResponse<String> me = api.send("GET", "/users/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email);

        assertThat(api.send("POST", "/auth/logout", null).statusCode()).isEqualTo(204);
        assertThat(api.send("GET", "/users/me", null).statusCode()).isEqualTo(401);
    }

    //Invitation tests
    @Test
    void projectMembersInvitationsAndRoles() {
        Api alice = registerAndLogin(uniqueEmail());
        String bobEmail = uniqueEmail();
        Api bob = registerAndLogin(bobEmail);
        String key = uniqueProjectKey();

        // create
        HttpResponse<String> created = alice.send("POST", "/projects",
                "{\"key\":\"" + key + "\",\"name\":\"Demo\",\"description\":\"d\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"myRole\":\"ADMIN\"");
        String projectId = field(created.body(), "id");

        assertThat(alice.send("POST", "/projects",
                "{\"key\":\"" + key + "\",\"name\":\"Again\"}").statusCode()).isEqualTo(409);
        assertThat(alice.send("POST", "/projects",
                "{\"key\":\"lowercase\",\"name\":\"Bad\"}").statusCode()).isEqualTo(400);

        // outsiders cannot see it
        assertThat(bob.send("GET", "/projects/" + projectId, null).statusCode()).isEqualTo(404);

        // invite and accept
        assertThat(alice.send("POST", "/projects/" + projectId + "/invitations",
                "{\"email\":\"" + bobEmail + "\",\"role\":\"MEMBER\"}").statusCode()).isEqualTo(201);
        assertThat(alice.send("POST", "/projects/" + projectId + "/invitations",
                "{\"email\":\"" + bobEmail + "\",\"role\":\"MEMBER\"}").statusCode()).isEqualTo(409);

        HttpResponse<String> invitations = bob.send("GET", "/invitations", null);
        assertThat(invitations.statusCode()).isEqualTo(200);
        String invitationId = field(invitations.body(), "id");

        HttpResponse<String> accepted = bob.send("POST", "/invitations/" + invitationId + "/accept", null);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).contains("\"myRole\":\"MEMBER\"");
        assertThat(bob.send("POST", "/invitations/" + invitationId + "/accept", null).statusCode())
                .isEqualTo(409);
        assertThat(bob.send("GET", "/projects/" + projectId, null).statusCode()).isEqualTo(200);

        // a MEMBER cannot administer
        assertThat(bob.send("POST", "/projects/" + projectId + "/invitations",
                "{\"email\":\"" + bobEmail + "\",\"role\":\"VIEWER\"}").statusCode()).isEqualTo(403);

        // the only admin cannot demote themselves
        assertThat(alice.send("PUT", "/projects/" + projectId + "/members/" + alice.userId,
                "{\"role\":\"MEMBER\"}").statusCode()).isEqualTo(409);

        // promote bob, then alice may step down
        assertThat(alice.send("PUT", "/projects/" + projectId + "/members/" + bob.userId,
                "{\"role\":\"ADMIN\"}").statusCode()).isEqualTo(200);
        assertThat(alice.send("PUT", "/projects/" + projectId + "/members/" + alice.userId,
                "{\"role\":\"MEMBER\"}").statusCode()).isEqualTo(200);
        assertThat(alice.send("PUT", "/projects/" + projectId,
                "{\"name\":\"Renamed\",\"description\":\"x\"}").statusCode()).isEqualTo(403);

        // bob is the only admin now and cannot leave; alice can
        assertThat(bob.send("DELETE", "/projects/" + projectId + "/members/" + bob.userId, null).statusCode())
                .isEqualTo(409);
        assertThat(alice.send("DELETE", "/projects/" + projectId + "/members/" + alice.userId, null).statusCode())
                .isEqualTo(204);
        assertThat(alice.send("GET", "/projects/" + projectId, null).statusCode()).isEqualTo(404);
    }

        private void joinProject(Api admin, String projectId, Api invitee, String inviteeEmail, String role) {
            assertThat(admin.send("POST", "/projects/" + projectId + "/invitations",
                    "{\"email\":\"" + inviteeEmail + "\",\"role\":\"" + role + "\"}").statusCode()).isEqualTo(201);
            String invitationId = field(invitee.send("GET", "/invitations", null).body(), "id");
            assertThat(invitee.send("POST", "/invitations/" + invitationId + "/accept", null).statusCode())
                    .isEqualTo(200);
        }

        //Board tests
        @Test
        void issuesMoveAlongTheWorkflowOnTheBoard() {
            Api alice = registerAndLogin(uniqueEmail());
            String bobEmail = uniqueEmail();
            Api bob = registerAndLogin(bobEmail);
            Api outsider = registerAndLogin(uniqueEmail());

            HttpResponse<String> project = alice.send("POST", "/projects",
                    "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Board demo\"}");
            assertThat(project.statusCode()).isEqualTo(201);
            String projectId = field(project.body(), "id");
            joinProject(alice, projectId, bob, bobEmail, "VIEWER");

            HttpResponse<String> created = alice.send("POST", "/projects/" + projectId + "/issues",
                    "{\"title\":\"First\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(created.body()).contains("\"status\":\"TODO\"");
            String transition = "/issues/" + field(created.body(), "key") + "/transition";
            String boardPath = "/projects/" + projectId + "/board";

            // the board has one column per status, in workflow order, and the issue starts in To do
            HttpResponse<String> board = alice.send("GET", boardPath, null);
            assertThat(board.statusCode()).isEqualTo(200);
            assertThat(board.body()).contains("\"myRole\":\"ADMIN\"", "\"title\":\"First\"");
            int todo = board.body().indexOf("\"title\":\"To do\"");
            int inProgress = board.body().indexOf("\"title\":\"In progress\"");
            int done = board.body().indexOf("\"title\":\"Done\"");
            assertThat(todo).isGreaterThanOrEqualTo(0).isLessThan(inProgress);
            assertThat(inProgress).isLessThan(done);

            // workflow rules
            assertThat(alice.send("POST", transition, "{\"status\":\"DONE\"}").statusCode()).isEqualTo(400);
            assertThat(alice.send("POST", transition, "{\"status\":\"NOPE\"}").statusCode()).isEqualTo(400);
            HttpResponse<String> started = alice.send("POST", transition, "{\"status\":\"IN_PROGRESS\"}");
            assertThat(started.statusCode()).isEqualTo(200);
            assertThat(started.body()).contains("\"status\":\"IN_PROGRESS\"");
            assertThat(alice.send("POST", transition, "{\"status\":\"IN_PROGRESS\"}").statusCode()).isEqualTo(200);

            // roles
            assertThat(bob.send("POST", transition, "{\"status\":\"DONE\"}").statusCode()).isEqualTo(403);
            HttpResponse<String> viewerBoard = bob.send("GET", boardPath, null);
            assertThat(viewerBoard.statusCode()).isEqualTo(200);
            assertThat(viewerBoard.body()).contains("\"myRole\":\"VIEWER\"");
            assertThat(outsider.send("GET", boardPath, null).statusCode()).isEqualTo(404);
            assertThat(outsider.send("POST", transition, "{\"status\":\"DONE\"}").statusCode()).isEqualTo(404);

            // back, forward, finish, reopen
            assertThat(alice.send("POST", transition, "{\"status\":\"TODO\"}").statusCode()).isEqualTo(200);
            assertThat(alice.send("POST", transition, "{\"status\":\"IN_PROGRESS\"}").statusCode()).isEqualTo(200);
            assertThat(alice.send("POST", transition, "{\"status\":\"DONE\"}").statusCode()).isEqualTo(200);
            assertThat(alice.send("POST", transition, "{\"status\":\"TODO\"}").statusCode()).isEqualTo(400);
            assertThat(alice.send("POST", transition, "{\"status\":\"IN_PROGRESS\"}").statusCode()).isEqualTo(200);

            // the issue now sits in In progress: one column with count 1, the other two with count 0
            HttpResponse<String> after = alice.send("GET", boardPath, null);
            assertThat(after.body()).contains("\"count\":1", "\"count\":0");
        }

    //Comment
    @Test
    void commentsRespectRolesAndMentions() {
        Api alice = registerAndLogin(uniqueEmail());
        String bobEmail = uniqueEmail();
        Api bob = registerAndLogin(bobEmail);
        String carolEmail = uniqueEmail();
        Api carol = registerAndLogin(carolEmail);
        String outsiderEmail = uniqueEmail();
        Api outsider = registerAndLogin(outsiderEmail);

        HttpResponse<String> project = alice.send("POST", "/projects",
                "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Comments demo\"}");
        assertThat(project.statusCode()).isEqualTo(201);
        String projectId = field(project.body(), "id");
        joinProject(alice, projectId, bob, bobEmail, "MEMBER");
        joinProject(alice, projectId, carol, carolEmail, "VIEWER");

        HttpResponse<String> issue = alice.send("POST", "/projects/" + projectId + "/issues",
                "{\"title\":\"Discuss me\"}");
        assertThat(issue.statusCode()).isEqualTo(201);
        String issuePath = "/issues/" + field(issue.body(), "key");

        // a member comments and mentions a viewer and an outsider: only the project member is kept
        HttpResponse<String> created = bob.send("POST", issuePath + "/comments",
                "{\"text\":\"Please review @" + carolEmail + " and @" + outsiderEmail + "\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"email\":\"" + carolEmail + "\"", "\"canEdit\":true", "\"canDelete\":true");
        assertThat(created.body()).doesNotContain("\"email\":\"" + outsiderEmail + "\"");
        String commentId = field(created.body(), "id");

        // validation and roles
        assertThat(bob.send("POST", issuePath + "/comments", "{\"text\":\"   \"}").statusCode()).isEqualTo(400);
        assertThat(carol.send("POST", issuePath + "/comments", "{\"text\":\"Can I?\"}").statusCode()).isEqualTo(403);
        assertThat(outsider.send("POST", issuePath + "/comments", "{\"text\":\"Hi\"}").statusCode()).isEqualTo(404);
        assertThat(outsider.send("GET", issuePath + "/comments", null).statusCode()).isEqualTo(404);

        // a viewer can read, but sees no edit or delete rights on someone else's comment
        HttpResponse<String> asViewer = carol.send("GET", issuePath + "/comments", null);
        assertThat(asViewer.statusCode()).isEqualTo(200);
        assertThat(asViewer.body()).contains("Please review", "\"canEdit\":false", "\"canDelete\":false");

        // only the author edits, and the mentions are recomputed
        assertThat(alice.send("PUT", "/comments/" + commentId, "{\"text\":\"hijack\"}").statusCode()).isEqualTo(403);
        HttpResponse<String> edited = bob.send("PUT", "/comments/" + commentId, "{\"text\":\"Edited, no mentions\"}");
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(edited.body()).contains("Edited, no mentions", "\"mentions\":[]")
                .doesNotContain("\"editedAt\":null");

        // delete: another member is refused, an admin may
        assertThat(carol.send("DELETE", "/comments/" + commentId, null).statusCode()).isEqualTo(403);
        assertThat(alice.send("DELETE", "/comments/" + commentId, null).statusCode()).isEqualTo(204);
        assertThat(alice.send("GET", issuePath + "/comments", null).body()).isEqualTo("[]");

        // deleting the issue removes its comments, and the issue is gone
        assertThat(bob.send("POST", issuePath + "/comments", "{\"text\":\"Last words\"}").statusCode()).isEqualTo(201);
        assertThat(alice.send("DELETE", issuePath, null).statusCode()).isEqualTo(204);
        assertThat(alice.send("GET", issuePath + "/comments", null).statusCode()).isEqualTo(404);
    }
    //History
    @Test
    void issueChangesAreWrittenToTheAuditTrail() {
        Api alice = registerAndLogin(uniqueEmail());
        String bobEmail = uniqueEmail();
        Api bob = registerAndLogin(bobEmail);
        String carolEmail = uniqueEmail();
        Api carol = registerAndLogin(carolEmail);
        Api outsider = registerAndLogin(uniqueEmail());

        HttpResponse<String> project = alice.send("POST", "/projects",
                "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Audit demo\"}");
        assertThat(project.statusCode()).isEqualTo(201);
        String projectId = field(project.body(), "id");
        joinProject(alice, projectId, bob, bobEmail, "MEMBER");
        joinProject(alice, projectId, carol, carolEmail, "VIEWER");

        HttpResponse<String> created = bob.send("POST", "/projects/" + projectId + "/issues",
                "{\"title\":\"Audit me\",\"description\":\"first\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        String issuePath = "/issues/" + field(created.body(), "key");

        // three real changes: a title edit and a status move (plus the creation above)
        assertThat(bob.send("PUT", issuePath, "{\"title\":\"Audit me v2\",\"description\":\"first\"}").statusCode())
                .isEqualTo(200);
        assertThat(bob.send("POST", issuePath + "/transition", "{\"status\":\"IN_PROGRESS\"}").statusCode())
                .isEqualTo(200);

        // no-ops and failed attempts must leave no trace
        assertThat(bob.send("PUT", issuePath, "{\"title\":\"Audit me v2\",\"description\":\"first\"}").statusCode())
                .isEqualTo(200);
        assertThat(bob.send("POST", issuePath + "/transition", "{\"status\":\"IN_PROGRESS\"}").statusCode())
                .isEqualTo(200);
        assertThat(bob.send("POST", issuePath + "/transition", "{\"status\":\"NOPE\"}").statusCode())
                .isEqualTo(400);
        assertThat(carol.send("PUT", issuePath, "{\"title\":\"Viewer edit\"}").statusCode()).isEqualTo(403);

        // a viewer can read the history: exactly three entries, newest first
        HttpResponse<String> history = carol.send("GET", issuePath + "/history", null);
        assertThat(history.statusCode()).isEqualTo(200);
        String body = history.body();
        assertThat(body).contains("\"action\":\"CREATED\"", "\"action\":\"UPDATED\"", "\"action\":\"TRANSITIONED\"",
                "\"oldValue\":\"Audit me\"", "\"newValue\":\"Audit me v2\"",
                "\"oldValue\":\"TODO\"", "\"newValue\":\"IN_PROGRESS\"",
                "\"email\":\"" + bobEmail + "\"");
        assertThat(body.split("\"action\":", -1).length - 1).isEqualTo(3);
        assertThat(body.indexOf("\"action\":\"TRANSITIONED\"")).isLessThan(body.indexOf("\"action\":\"UPDATED\""));
        assertThat(body.indexOf("\"action\":\"UPDATED\"")).isLessThan(body.indexOf("\"action\":\"CREATED\""));

        // outsiders see nothing
        assertThat(outsider.send("GET", issuePath + "/history", null).statusCode()).isEqualTo(404);
        assertThat(outsider.send("GET", "/projects/" + projectId + "/activity", null).statusCode()).isEqualTo(404);

        // the project feed has the same three entries
        HttpResponse<String> feed = carol.send("GET", "/projects/" + projectId + "/activity", null);
        assertThat(feed.statusCode()).isEqualTo(200);
        assertThat(feed.body()).contains("\"totalItems\":3");

        // deleting the issue is itself recorded, and the history outlives the issue
        assertThat(alice.send("DELETE", issuePath, null).statusCode()).isEqualTo(204);
        assertThat(alice.send("GET", issuePath + "/history", null).statusCode()).isEqualTo(404);
        HttpResponse<String> after = alice.send("GET", "/projects/" + projectId + "/activity", null);
        assertThat(after.body()).contains("\"totalItems\":4", "\"action\":\"DELETED\"");
    }

    //Worklog
    @Test
    void timeLoggingRespectsRolesAndAddsUp() {
        Api alice = registerAndLogin(uniqueEmail());
        String bobEmail = uniqueEmail();
        Api bob = registerAndLogin(bobEmail);
        String carolEmail = uniqueEmail();
        Api carol = registerAndLogin(carolEmail);
        Api outsider = registerAndLogin(uniqueEmail());

        HttpResponse<String> project = alice.send("POST", "/projects",
                "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Time demo\"}");
        assertThat(project.statusCode()).isEqualTo(201);
        String projectId = field(project.body(), "id");
        joinProject(alice, projectId, bob, bobEmail, "MEMBER");
        joinProject(alice, projectId, carol, carolEmail, "VIEWER");

        HttpResponse<String> issue = alice.send("POST", "/projects/" + projectId + "/issues",
                "{\"title\":\"Track my time\"}");
        assertThat(issue.statusCode()).isEqualTo(201);
        String issuePath = "/issues/" + field(issue.body(), "key");

        // a member logs 90 minutes now, and 30 minutes from two days ago
        HttpResponse<String> first = bob.send("POST", issuePath + "/worklogs",
                "{\"minutes\":90,\"note\":\"Wrote the endpoint\"}");
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.body()).contains("\"minutes\":90", "\"timeSpent\":\"1h 30m\"", "\"canEdit\":true");
        String firstId = field(first.body(), "id");
        String twoDaysAgo = java.time.Instant.now().minusSeconds(2 * 86400L).toString();
        HttpResponse<String> second = bob.send("POST", issuePath + "/worklogs",
                "{\"minutes\":30,\"startedAt\":\"" + twoDaysAgo + "\"}");
        assertThat(second.statusCode()).isEqualTo(201);

        // validation and roles
        assertThat(bob.send("POST", issuePath + "/worklogs", "{\"minutes\":0}").statusCode()).isEqualTo(400);
        assertThat(bob.send("POST", issuePath + "/worklogs", "{\"minutes\":1441}").statusCode()).isEqualTo(400);
        assertThat(bob.send("POST", issuePath + "/worklogs", "{\"note\":\"no minutes\"}").statusCode())
                .isEqualTo(400);
        String inTwoDays = java.time.Instant.now().plusSeconds(2 * 86400L).toString();
        assertThat(bob.send("POST", issuePath + "/worklogs",
                "{\"minutes\":30,\"startedAt\":\"" + inTwoDays + "\"}").statusCode()).isEqualTo(400);
        assertThat(carol.send("POST", issuePath + "/worklogs", "{\"minutes\":30}").statusCode()).isEqualTo(403);
        assertThat(outsider.send("POST", issuePath + "/worklogs", "{\"minutes\":30}").statusCode()).isEqualTo(404);

        // a viewer can read: total 120 minutes, newest start first, and no edit or delete rights
        HttpResponse<String> list = carol.send("GET", issuePath + "/worklogs", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"totalMinutes\":120", "\"totalTime\":\"2h\"",
                "\"canEdit\":false", "\"canDelete\":false");
        assertThat(list.body().indexOf("\"minutes\":90")).isLessThan(list.body().indexOf("\"minutes\":30"));
        assertThat(outsider.send("GET", issuePath + "/worklogs", null).statusCode()).isEqualTo(404);

        // only the author edits
        assertThat(alice.send("PUT", "/worklogs/" + firstId, "{\"minutes\":10}").statusCode()).isEqualTo(403);
        HttpResponse<String> edited = bob.send("PUT", "/worklogs/" + firstId, "{\"minutes\":45}");
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(edited.body()).contains("\"minutes\":45", "\"timeSpent\":\"45m\"")
                .doesNotContain("\"updatedAt\":null");
        assertThat(bob.send("GET", issuePath + "/worklogs", null).body())
                .contains("\"totalMinutes\":75", "\"totalTime\":\"1h 15m\"");

        // delete: a viewer is refused, an admin may remove someone else's entry
        assertThat(carol.send("DELETE", "/worklogs/" + firstId, null).statusCode()).isEqualTo(403);
        assertThat(alice.send("DELETE", "/worklogs/" + firstId, null).statusCode()).isEqualTo(204);
        assertThat(alice.send("GET", issuePath + "/worklogs", null).body()).contains("\"totalMinutes\":30");
        assertThat(alice.send("PUT", "/worklogs/" + firstId, "{\"minutes\":5}").statusCode()).isEqualTo(404);

        // once the issue is deleted its worklogs are unreachable
        assertThat(alice.send("DELETE", issuePath, null).statusCode()).isEqualTo(204);
        assertThat(alice.send("GET", issuePath + "/worklogs", null).statusCode()).isEqualTo(404);
    }
    //Notifications
    private long unreadCount(Api api) {
        HttpResponse<String> response = api.send("GET", "/notifications/unread-count", null);
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"count\":(\\d+)").matcher(response.body());
        assertThat(matcher.find()).as(response.body()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private String assignJson(Api who) {
        return "{\"assigneeId\":\"" + who.userId + "\"}";
    }

    @Test
    void notificationsAreCreatedForMentionsAndAssignmentsAndCanBeRead() {
        String aliceEmail = uniqueEmail();
        Api alice = registerAndLogin(aliceEmail);
        String bobEmail = uniqueEmail();
        Api bob = registerAndLogin(bobEmail);
        String carolEmail = uniqueEmail();
        Api carol = registerAndLogin(carolEmail);
        String viewerEmail = uniqueEmail();
        Api viewer = registerAndLogin(viewerEmail);
        Api outsider = registerAndLogin(uniqueEmail());

        assertThat(new Api().send("GET", "/notifications", null).statusCode()).isEqualTo(401);

        HttpResponse<String> project = alice.send("POST", "/projects",
                "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Notify demo\"}");
        assertThat(project.statusCode()).isEqualTo(201);
        String projectId = field(project.body(), "id");
        joinProject(alice, projectId, bob, bobEmail, "MEMBER");
        joinProject(alice, projectId, carol, carolEmail, "MEMBER");
        joinProject(alice, projectId, viewer, viewerEmail, "VIEWER");

        HttpResponse<String> issue = alice.send("POST", "/projects/" + projectId + "/issues",
                "{\"title\":\"Notify me\"}");
        assertThat(issue.statusCode()).isEqualTo(201);
        String issueKey = field(issue.body(), "key");
        String issuePath = "/issues/" + issueKey;
        String assignPath = issuePath + "/assignee";

        // a mention notifies the mentioned member, but mentioning yourself notifies nobody
        HttpResponse<String> comment = bob.send("POST", issuePath + "/comments",
                "{\"text\":\"Please look @" + carolEmail + " and @" + bobEmail + "\"}");
        assertThat(comment.statusCode()).isEqualTo(201);
        String commentId = field(comment.body(), "id");
        assertThat(unreadCount(carol)).isEqualTo(1);
        assertThat(unreadCount(bob)).isZero();
        assertThat(unreadCount(alice)).isZero();
        HttpResponse<String> mentionList = carol.send("GET", "/notifications", null);
        assertThat(mentionList.statusCode()).isEqualTo(200);
        assertThat(mentionList.body()).contains("\"type\":\"MENTION\"", "\"email\":\"" + bobEmail + "\"",
                "\"issueKey\":\"" + issueKey + "\"", "\"read\":false", "mentioned you", "\"unreadCount\":1");

        // assigning notifies the assignee, and assigning the same person again does nothing
        HttpResponse<String> assigned = alice.send("PUT", assignPath, assignJson(carol));
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(assigned.body()).contains("\"assigneeId\":\"" + carol.userId + "\"");
        assertThat(unreadCount(carol)).isEqualTo(2);
        assertThat(carol.send("GET", "/notifications", null).body())
                .contains("\"type\":\"ASSIGNED\"", "assigned " + issueKey + " to you");
        assertThat(alice.send("PUT", assignPath, assignJson(carol)).statusCode()).isEqualTo(200);
        assertThat(unreadCount(carol)).isEqualTo(2);

        // assigning yourself notifies nobody, then handing it back to carol notifies her again
        assertThat(alice.send("PUT", assignPath, assignJson(alice)).statusCode()).isEqualTo(200);
        assertThat(unreadCount(alice)).isZero();
        assertThat(alice.send("PUT", assignPath, assignJson(carol)).statusCode()).isEqualTo(200);
        assertThat(unreadCount(carol)).isEqualTo(3);

        // who can be assigned, and who can assign
        assertThat(alice.send("PUT", assignPath, assignJson(viewer)).statusCode()).isEqualTo(400);
        assertThat(alice.send("PUT", assignPath, assignJson(outsider)).statusCode()).isEqualTo(400);
        assertThat(alice.send("PUT", assignPath, "{\"assigneeId\":\"  \"}").statusCode()).isEqualTo(400);
        assertThat(viewer.send("PUT", assignPath, assignJson(carol)).statusCode()).isEqualTo(403);
        assertThat(outsider.send("PUT", assignPath, assignJson(carol)).statusCode()).isEqualTo(404);

        // editing a comment only notifies people who were not mentioned before
        assertThat(bob.send("PUT", "/comments/" + commentId,
                "{\"text\":\"Look @" + carolEmail + " and @" + aliceEmail + "\"}").statusCode()).isEqualTo(200);
        assertThat(unreadCount(alice)).isEqualTo(1);
        assertThat(unreadCount(carol)).isEqualTo(3);
        assertThat(bob.send("PUT", "/comments/" + commentId,
                "{\"text\":\"Look again @" + carolEmail + " and @" + aliceEmail + "\"}").statusCode())
                .isEqualTo(200);
        assertThat(unreadCount(alice)).isEqualTo(1);

        // marking one notification read: only its owner can, and it can be repeated
        HttpResponse<String> unread = carol.send("GET", "/notifications?unread=true", null);
        assertThat(unread.body()).contains("\"totalItems\":3");
        String notificationId = field(unread.body(), "id");
        assertThat(bob.send("PUT", "/notifications/" + notificationId + "/read", null).statusCode())
                .isEqualTo(404);
        HttpResponse<String> read = carol.send("PUT", "/notifications/" + notificationId + "/read", null);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("\"read\":true");
        assertThat(unreadCount(carol)).isEqualTo(2);
        assertThat(carol.send("PUT", "/notifications/" + notificationId + "/read", null).statusCode())
                .isEqualTo(200);
        assertThat(unreadCount(carol)).isEqualTo(2);
        assertThat(carol.send("PUT", "/notifications/000000000000000000000000/read", null).statusCode())
                .isEqualTo(404);

        // marking everything read keeps the history of notifications
        HttpResponse<String> all = carol.send("PUT", "/notifications/read-all", null);
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(all.body()).contains("\"count\":2");
        assertThat(unreadCount(carol)).isZero();
        assertThat(carol.send("GET", "/notifications?unread=true", null).body()).contains("\"totalItems\":0");
        assertThat(carol.send("GET", "/notifications", null).body()).contains("\"totalItems\":3");

        // unassigning clears the assignee, notifies nobody, and can be repeated
        HttpResponse<String> cleared = alice.send("DELETE", assignPath, null);
        assertThat(cleared.statusCode()).isEqualTo(200);
        assertThat(cleared.body()).contains("\"assigneeId\":null");
        assertThat(alice.send("DELETE", assignPath, null).statusCode()).isEqualTo(200);
        assertThat(unreadCount(carol)).isZero();
        assertThat(viewer.send("DELETE", assignPath, null).statusCode()).isEqualTo(403);

        // assignment changes show up in the audit trail
        assertThat(alice.send("GET", issuePath + "/history", null).body())
                .contains("\"field\":\"assignee\"", "\"newValue\":\"" + carolEmail + "\"");
    }
    @Test
    void movesAnIssueBetweenBacklogAndSprintAndCompletesTheSprint() {
        Api admin = registerAndLogin(uniqueEmail());
        String projectId = sprintTestProject(admin);
        String sprintId = createTestSprint(admin, projectId, "Sprint one");
        String projects = "/projects/" + projectId;
        String sprint = "/sprints/" + sprintId;

        var createdIssue = admin.send("POST", projects + "/issues", "{\"title\":\"Backlog task\"}");
        assertThat(createdIssue.statusCode()).isEqualTo(201);
        String key = field(createdIssue.body(), "key");
        String issuePath = "/issues/" + key + "/sprint";

        var backlog = admin.send("GET", projects + "/backlog", null);
        assertThat(backlog.statusCode()).isEqualTo(200);
        assertThat(backlog.body()).contains(key);

        var assigned = admin.send("PUT", issuePath, "{\"sprintId\":\"" + sprintId + "\"}");
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(field(assigned.body(), "sprintId")).isEqualTo(sprintId);
        backlog = admin.send("GET", projects + "/backlog", null);
        assertThat(backlog.statusCode()).isEqualTo(200);
        assertThat(backlog.body()).doesNotContain(key);

        var sprintIssues = admin.send("GET", sprint + "/issues", null);
        assertThat(sprintIssues.statusCode()).isEqualTo(200);
        assertThat(sprintIssues.body()).contains(key);
        assertThat(admin.send("POST", sprint + "/complete", null).statusCode()).isEqualTo(409);
        assertSprintStatus(admin.send("POST", sprint + "/start", null), "ACTIVE");
        assertSprintStatus(admin.send("POST", sprint + "/complete", null), "COMPLETED");

        assertThat(admin.send("DELETE", issuePath, null).statusCode()).isEqualTo(200);
        backlog = admin.send("GET", projects + "/backlog", null);
        assertThat(backlog.statusCode()).isEqualTo(200);
        assertThat(backlog.body()).contains(key);
    }

    @Test
    void onlyOneOfTwoConcurrentStartsSucceeds() throws Exception {
        Api admin = registerAndLogin(uniqueEmail());
        String projectId = sprintTestProject(admin);
        String first = createTestSprint(admin, projectId, "First candidate");
        String second = createTestSprint(admin, projectId, "Second candidate");
        var startTogether = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            Callable<HttpResponse<String>> startFirst = () -> {
                startTogether.await();
                return admin.send("POST", "/sprints/" + first + "/start", null);
            };
            Callable<HttpResponse<String>> startSecond = () -> {
                startTogether.await();
                return admin.send("POST", "/sprints/" + second + "/start", null);
            };
            var firstCall = pool.submit(startFirst);
            var secondCall = pool.submit(startSecond);
            startTogether.countDown();

            var firstResult = firstCall.get(20, TimeUnit.SECONDS);
            var secondResult = secondCall.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(firstResult.statusCode(), secondResult.statusCode()))
                    .containsExactlyInAnyOrder(200, 409);

            String active = firstResult.statusCode() == 200 ? first : second;
            String waiting = firstResult.statusCode() == 200 ? second : first;
            assertSprintStatus(admin.send("POST", "/sprints/" + active + "/complete", null), "COMPLETED");
            assertSprintStatus(admin.send("POST", "/sprints/" + waiting + "/start", null), "ACTIVE");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cachesProjectViewsAndRefreshesThemAfterIssueChanges() {
        Api admin = registerAndLogin(uniqueEmail());
        String projectId = sprintTestProject(admin);
        String sprintId = createTestSprint(admin, projectId, "Cache test sprint");
        String projectPath = "/projects/" + projectId;

        var created = admin.send("POST", projectPath + "/issues",
                "{\"title\":\"Before cache invalidation\"}");
        assertThat(created.statusCode()).isEqualTo(201);

        String issueKey = field(created.body(), "key");
        String issuePath = "/issues/" + issueKey;
        var views = List.of("issues", "board", "backlog");

        for (String view : views) {
            var response = admin.send("GET", projectPath + "/" + view, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Before cache invalidation");
        }

        String prefix = "tracker:cache:project:" + projectId + ":";
        String oldRevision = redis.opsForValue().get(prefix + "revision");
        assertThat(oldRevision).isNotNull();

        for (String view : views) {
            assertThat(redis.opsForValue().get(prefix + view + ":" + oldRevision))
                    .contains("Before cache invalidation");
        }

        Api outsider = registerAndLogin(uniqueEmail());
        for (String view : views) {
            assertThat(outsider.send("GET", projectPath + "/" + view, null).statusCode())
                    .isEqualTo(404);
        }

        assertThat(admin.send("PUT", issuePath,
                "{\"title\":\"After cache invalidation\"}").statusCode())
                .isEqualTo(200);

        assertThat(redis.opsForValue().get(prefix + "revision"))
                .isNotEqualTo(oldRevision);

        for (String view : views) {
            var response = admin.send("GET", projectPath + "/" + view, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .contains("After cache invalidation")
                    .doesNotContain("Before cache invalidation");
        }
        
        assertThat(admin.send("PUT", issuePath + "/sprint",
                "{\"sprintId\":\"" + sprintId + "\"}").statusCode()).isEqualTo(200);

        var backlog = admin.send("GET", projectPath + "/backlog", null);
        assertThat(backlog.statusCode()).isEqualTo(200);
        assertThat(backlog.body()).doesNotContain(issueKey);

        assertThat(admin.send("DELETE", issuePath + "/sprint", null).statusCode())
                .isEqualTo(200);

        backlog = admin.send("GET", projectPath + "/backlog", null);
        assertThat(backlog.statusCode()).isEqualTo(200);
        assertThat(backlog.body()).contains(issueKey);
    }

    private String sprintTestProject(Api admin) {
        var created = admin.send("POST", "/projects",
                "{\"key\":\"" + uniqueProjectKey() + "\",\"name\":\"Sprint test project\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        return field(created.body(), "id");
    }

    private String createTestSprint(Api admin, String projectId, String name) {
        var created = admin.send("POST", "/projects/" + projectId + "/sprints",
                "{\"name\":\"" + name + "\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "status")).isEqualTo("PLANNED");
        return field(created.body(), "id");
    }

    private void assertSprintStatus(HttpResponse<String> response, String status) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "status")).isEqualTo(status);
    }
}