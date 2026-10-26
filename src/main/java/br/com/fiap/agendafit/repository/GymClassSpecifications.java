package br.com.fiap.agendafit.repository;

import java.time.LocalDateTime;
import java.util.Locale;

import br.com.fiap.agendafit.model.Enrollment;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public final class GymClassSpecifications {

    private GymClassSpecifications() {
    }

    public static Specification<GymClass> titleContains(String title) {
        return (root, query, builder) -> title == null ? null : builder.like(builder.lower(root.get("title")),
                "%" + title.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", '\\');
    }

    public static Specification<GymClass> modalityEquals(Modality modality) {
        return (root, query, builder) -> modality == null ? null : builder.equal(root.get("modality"), modality);
    }

    public static Specification<GymClass> levelEquals(Level level) {
        return (root, query, builder) -> level == null ? null : builder.equal(root.get("level"), level);
    }

    public static Specification<GymClass> startsAfter(LocalDateTime startsAfter) {
        return (root, query, builder) -> startsAfter == null ? null : builder.greaterThanOrEqualTo(root.get("startsAt"), startsAfter);
    }

    public static Specification<GymClass> startsBefore(LocalDateTime startsBefore) {
        return (root, query, builder) -> startsBefore == null ? null : builder.lessThanOrEqualTo(root.get("startsAt"), startsBefore);
    }

    public static Specification<GymClass> hasAvailability(Boolean availableOnly) {
        return (root, query, builder) -> {
            if (!Boolean.TRUE.equals(availableOnly)) {
                return null;
            }
            Subquery<Long> count = query.subquery(Long.class);
            Root<Enrollment> enrollment = count.from(Enrollment.class);
            count.select(builder.count(enrollment)).where(builder.equal(enrollment.get("gymClass"), root));
            return builder.lessThan(count, root.get("capacity").as(Long.class));
        };
    }
}
