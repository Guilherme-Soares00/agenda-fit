package br.com.fiap.agendafit.assembler;

import br.com.fiap.agendafit.controller.GymClassController;
import br.com.fiap.agendafit.controller.EnrollmentController;
import br.com.fiap.agendafit.dto.EnrollmentResponse;
import br.com.fiap.agendafit.security.CurrentUser;
import br.com.fiap.agendafit.service.EnrollmentView;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@Component
@RequiredArgsConstructor
public class EnrollmentAssembler implements RepresentationModelAssembler<EnrollmentView, EntityModel<EnrollmentResponse>> {

    private final CurrentUser currentUser;

    @Override
    public EntityModel<EnrollmentResponse> toModel(EnrollmentView view) {
        EnrollmentResponse response = view.response();
        EntityModel<EnrollmentResponse> model = EntityModel.of(response,
                linkTo(GymClassController.class).slash(response.classId()).withRel("class"));
        if (currentUser.isAdmin() || currentUser.email().filter(view.ownerEmail()::equals).isPresent()) {
            model.add(linkTo(EnrollmentController.class)
                    .slash(response.id()).withRel("cancel"));
        }
        return model;
    }
}
