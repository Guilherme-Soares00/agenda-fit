package br.com.fiap.agendafit.controller;

import java.net.URI;
import java.time.LocalDateTime;

import br.com.fiap.agendafit.assembler.GymClassAssembler;
import br.com.fiap.agendafit.assembler.EnrollmentAssembler;
import br.com.fiap.agendafit.dto.EnrollmentResponse;
import br.com.fiap.agendafit.service.EnrollmentService;
import br.com.fiap.agendafit.dto.GymClassRequest;
import br.com.fiap.agendafit.dto.GymClassResponse;

import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import br.com.fiap.agendafit.service.GymClassService;
import br.com.fiap.agendafit.service.GymClassView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.fiap.agendafit.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Classes", description = "Gym class catalog and administration")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "Class not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "Conflicting request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class GymClassController {

    private final GymClassService service;
    private final EnrollmentService enrollmentService;
    private final EnrollmentAssembler enrollmentAssembler;
    private final GymClassAssembler assembler;
    private final PagedResourcesAssembler<GymClassView> pages;

    @Operation(summary = "List gym classes")
    @GetMapping
    public PagedModel<EntityModel<GymClassResponse>> findAll(@PageableDefault(size = 10, sort = "startsAt") Pageable pageable) {
        return assembler.toPage(service.findAll(pageable), pages);
    }

    @Operation(summary = "Find classes using query by example")
    @GetMapping("/example")
    public PagedModel<EntityModel<GymClassResponse>> findByExample(
            @Parameter(description = "Exact modality") @RequestParam(required = false) Modality modality,
            @Parameter(description = "Exact difficulty level") @RequestParam(required = false) Level level,
            @Parameter(description = "Exact instructor name, ignoring case") @RequestParam(required = false) String instructor,
            @PageableDefault(size = 10, sort = "startsAt") Pageable pageable) {
        return assembler.toPage(service.findByExample(modality, level, instructor, pageable), pages);
    }

    @Operation(summary = "Search classes using combined filters")
    @GetMapping("/search")
    public PagedModel<EntityModel<GymClassResponse>> search(
            @Parameter(description = "Title contains this text, ignoring case") @RequestParam(required = false) String title,
            @Parameter(description = "Exact modality") @RequestParam(required = false) Modality modality,
            @Parameter(description = "Exact difficulty level") @RequestParam(required = false) Level level,
            @Parameter(description = "Inclusive earliest start, in local ISO date-time") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAfter,
            @Parameter(description = "Inclusive latest start, in local ISO date-time") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsBefore,
            @Parameter(description = "Return only classes with available spots when true") @RequestParam(required = false) Boolean availableOnly,
            @PageableDefault(size = 10, sort = "startsAt") Pageable pageable) {
        return assembler.toPage(service.search(title, modality, level, startsAfter, startsBefore, availableOnly, pageable), pages);
    }

    @Operation(summary = "Get a gym class")
    @GetMapping("/{id}")
    public EntityModel<GymClassResponse> findById(@PathVariable Long id) {
        return assembler.toModel(service.findById(id));
    }

    @Operation(summary = "Create a gym class")
    @ApiResponse(responseCode = "201", description = "Class created")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<EntityModel<GymClassResponse>> create(@RequestBody @Valid GymClassRequest request) {
        EntityModel<GymClassResponse> resource = assembler.toModel(service.create(request));
        return ResponseEntity.created(URI.create(resource.getRequiredLink("self").getHref())).body(resource);
    }

    @Operation(summary = "Update a gym class")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public EntityModel<GymClassResponse> update(@PathVariable Long id, @RequestBody @Valid GymClassRequest request) {
        return assembler.toModel(service.update(id, request));
    }

    @Operation(summary = "Enroll in a gym class")
    @ApiResponse(responseCode = "201", description = "Enrollment created")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/enrollments")
    public ResponseEntity<EntityModel<EnrollmentResponse>> enroll(@PathVariable Long id) {
        return ResponseEntity.status(201).body(enrollmentAssembler.toModel(enrollmentService.enroll(id)));
    }

    @Operation(summary = "Delete a gym class and its enrollments")
    @ApiResponse(responseCode = "204", description = "Class deleted")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
