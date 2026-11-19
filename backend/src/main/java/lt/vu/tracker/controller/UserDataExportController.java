package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.UserDataExportService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserDataExportController {
    private final CurrentUser currentUser;
    private final UserDataExportService export;

    @GetMapping("/me/export")
    public ResponseEntity<byte[]> download() throws IOException {
        byte[] archive = export.build(currentUser.get());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(archive.length)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("user-data.zip")
                                .build()
                                .toString()
                )
                .body(archive);
    }

    @ExceptionHandler({
            IOException.class,
            DataAccessException.class,
            JacksonException.class
    })
    public ResponseEntity<Map<String, String>> handleExportFailure(
            Exception exception
    ) {
        log.warn("User data export failed", exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "message", "Data export is temporarily unavailable"
                ));
    }
}