package br.com.fiap.agendafit.assembler;

import br.com.fiap.agendafit.controller.GymClassController;
import br.com.fiap.agendafit.dto.GymClassResponse;
import br.com.fiap.agendafit.security.CurrentUser;
import br.com.fiap.agendafit.service.GymClassView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@Component
@RequiredArgsConstructor
public class GymClassAssembler implements RepresentationModelAssembler<GymClassView, EntityModel<GymClassResponse>> {

    private final CurrentUser currentUser;

    @Override
    public EntityModel<GymClassResponse> toModel(GymClassView view) {
        GymClassResponse response = view.response();
        var classLink = linkTo(GymClassController.class).slash(response.id());
        EntityModel<GymClassResponse> model = EntityModel.of(response,
                classLink.withSelfRel(), linkTo(GymClassController.class).withRel("classes"));
        if (currentUser.isAuthenticated() && response.availableSpots() > 0 && !view.enrolled()) {
            model.add(classLink.slash("enrollments").withRel("enroll"));
        }
        if (currentUser.isAdmin()) {
            model.add(classLink.withRel("update"), classLink.withRel("delete"));
        }
        return model;
    }

    public PagedModel<EntityModel<GymClassResponse>> toPage(Page<GymClassView> page,
            PagedResourcesAssembler<GymClassView> pages) {
        pages.setForceFirstAndLastRels(true);
        PagedModel<EntityModel<GymClassResponse>> model = pages.toModel(page, this);
        if (currentUser.isAdmin()) {
            model.add(linkTo(GymClassController.class).withRel("create"));
        }
        return model;
    }
}
