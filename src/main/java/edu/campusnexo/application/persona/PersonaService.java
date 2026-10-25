package edu.campusnexo.application.persona;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.PersonaAdminUseCase;
import edu.campusnexo.application.port.in.RegistrarPersonaCommand;
import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.port.PersonaRepository;
import edu.campusnexo.domain.shared.DomainChecks;
import java.util.List;

public class PersonaService implements PersonaAdminUseCase {
    private final PersonaRepository repository;

    public PersonaService(PersonaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Persona registrar(RegistrarPersonaCommand command) {
        DomainChecks.requireNonNull(command.tipo(), "tipo");
        return repository.save(Persona.registrar(command.tipo(), command.codigo(), command.nombre(),
                command.adscripcion()));
    }

    @Override
    public List<Persona> listar(TipoPersona tipo) {
        return repository.findAll(tipo);
    }

    @Override
    public Persona obtener(String codigo) {
        return repository.findByCodigo(codigo).orElseThrow(() -> notFound(codigo));
    }

    @Override
    public void eliminar(String codigo) {
        if (!repository.delete(codigo)) {
            throw notFound(codigo);
        }
    }

    private static ResourceNotFoundException notFound(String codigo) {
        return new ResourceNotFoundException("persona_not_found",
                "No hay una persona registrada con el código " + codigo);
    }
}
