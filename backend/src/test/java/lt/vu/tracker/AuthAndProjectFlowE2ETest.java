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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://localhost:27017/tracker_test")
class AuthAndProjectFlowE2ETest {

    @Value("${local.server.port}")
    private int port;

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
}