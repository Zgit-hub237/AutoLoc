package fr.ece.autoloc.console;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Generic CRUD endpoints used by the web console.
 * <p>
 * Each subclass maps one entity to a flat DTO (associations exposed as ids), so the
 * LAZY associations are always read inside the transaction and never serialized directly.
 *
 * @param <E> JPA entity
 * @param <D> DTO exchanged with the console
 */
public abstract class CrudResource<E, D> {

    private final JpaRepository<E, Long> repository;
    private final String entityLabel;

    protected CrudResource(JpaRepository<E, Long> repository, String entityLabel) {
        this.repository = repository;
        this.entityLabel = entityLabel;
    }

    /** Entity -> DTO, called inside the transaction. */
    protected abstract D toDto(E entity);

    protected abstract E newEntity();

    /** Copies the DTO values onto the entity and resolves the associations. */
    protected abstract void apply(D dto, E entity);

    /** Hook to detach associations before deletion. */
    protected void beforeDelete(E entity) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<D> list() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public D get(@PathVariable Long id) {
        return toDto(find(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<D> create(@RequestBody D dto) {
        E entity = newEntity();
        apply(dto, entity);
        E saved = repository.saveAndFlush(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public D update(@PathVariable Long id, @RequestBody D dto) {
        E entity = find(id);
        apply(dto, entity);
        repository.flush();
        return toDto(entity);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        E entity = find(id);
        beforeDelete(entity);
        repository.delete(entity);
        // Flush now so a foreign key violation is reported here, with a clear message
        repository.flush();
        return ResponseEntity.noContent().build();
    }

    protected E find(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, entityLabel + " " + id + " introuvable"));
    }

    /** Loads an associated entity from its id; null id means "no association". */
    protected static <T> T reference(JpaRepository<T, Long> repo, Long id, String label) {
        if (id == null) {
            return null;
        }
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " " + id + " introuvable"));
    }

    @SuppressWarnings("unchecked")
    protected static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ « " + field + " » est obligatoire");
        }
        return value instanceof String s ? (T) s.trim() : value;
    }

    protected static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
