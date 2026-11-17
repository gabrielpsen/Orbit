package dev.orbit.api;

import dev.orbit.domain.JobStatus;
import dev.orbit.scheduler.JobService;
import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/jobs")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JobController {
    private final JobService jobs;

    public JobController(JobService jobs) { this.jobs = jobs; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobResponse create(@Valid @RequestBody CreateJobRequest request,
                              @RequestHeader(name = "Idempotency-Key", required = false) String headerKey,
                              HttpServletRequest servletRequest) {
        String tenantScope = tenantScope(servletRequest);
        if (tenantScope != null && !tenantScope.equals(request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token is not authorized for this tenant");
        }
        if (headerKey != null && !headerKey.isBlank()) {
            if (headerKey.length() > 180) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be at most 180 characters");
            request = request.withIdempotencyKey(headerKey);
        }
        return jobs.submit(request);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id, HttpServletRequest request) {
        JobResponse result = jobs.get(id);
        requireTenantAccess(request, result.tenantId());
        return result;
    }

    @GetMapping
    public List<JobResponse> list(@RequestParam(required = false) String tenantId,
                                  @RequestParam(required = false) JobStatus status,
                                  HttpServletRequest request) {
        String tenantScope = tenantScope(request);
        if (tenantScope != null && tenantId != null && !tenantScope.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token is not authorized for this tenant");
        }
        return jobs.list(tenantScope == null ? tenantId : tenantScope, status);
    }

    @DeleteMapping("/{id}")
    public JobResponse cancel(@PathVariable UUID id, HttpServletRequest request) {
        JobResponse current = jobs.get(id);
        requireTenantAccess(request, current.tenantId());
        return jobs.cancel(id);
    }

    private static String tenantScope(HttpServletRequest request) {
        return (String) request.getAttribute(ApiTokenFilter.TENANT_ATTRIBUTE);
    }

    private static void requireTenantAccess(HttpServletRequest request, String jobTenant) {
        String scope = tenantScope(request);
        if (scope != null && !scope.equals(jobTenant)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    }
}
