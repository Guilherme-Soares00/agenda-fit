package br.com.fiap.agendafit.controller;

import java.net.URI;
import java.util.List;

import br.com.fiap.agendafit.dto.GymClassRequest;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.service.GymClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// CRUD simples: JSON puro, sem paginação, sem HATEOAS e sem segurança
@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class GymClassController {

    private final GymClassService service;

    @GetMapping
    public List<GymClass> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public GymClass findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<GymClass> create(@RequestBody @Valid GymClassRequest request) {
        GymClass gymClass = service.create(request);
        return ResponseEntity.created(URI.create("/classes/" + gymClass.getId())).body(gymClass);
    }

    @PutMapping("/{id}")
    public GymClass update(@PathVariable Long id, @RequestBody @Valid GymClassRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
