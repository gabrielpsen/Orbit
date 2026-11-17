package dev.orbit.api;

import dev.orbit.scheduler.WorkerRegistryService;
import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workers")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class WorkerController {
    private final WorkerRegistryService workers;
    public WorkerController(WorkerRegistryService workers) { this.workers = workers; }

    @GetMapping
    public List<WorkerResponse> list(HttpServletRequest request) {
        requireAdmin(request);
        return workers.list().stream().map(WorkerResponse::from).toList();
    }

    private static void requireAdmin(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin token required");
        }
    }
}
