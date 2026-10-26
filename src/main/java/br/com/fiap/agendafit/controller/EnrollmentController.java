package br.com.fiap.agendafit.controller;

import br.com.fiap.agendafit.assembler.EnrollmentAssembler;
import br.com.fiap.agendafit.dto.EnrollmentResponse;
import br.com.fiap.agendafit.dto.ErrorResponse;
import br.com.fiap.agendafit.service.EnrollmentService;
import br.com.fiap.agendafit.service.EnrollmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollments", description = "Personal enrollments and cancellation")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "Enrollment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "Conflicting request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService service;
    private final EnrollmentAssembler assembler;
    private final PagedResourcesAssembler<EnrollmentView> pages;

    @Operation(summary = "List the authenticated user's enrollments")
    @GetMapping("/me")
    public PagedModel<EntityModel<EnrollmentResponse>> findMine(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        pages.setForceFirstAndLastRels(true);
        return pages.toModel(service.findMine(pageable), assembler);
    }

    @Operation(summary = "Cancel an enrollment as its owner or an administrator")
    @ApiResponse(responseCode = "204", description = "Enrollment cancelled")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
