package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared list of equipment types. Anyone with MASTERDATA_VIEW can read it; only SUPER_ADMIN changes it
 * (checked in the controller). The 13 seeded types keep their code and can never be deleted.
 */
@Service
class EquipmentTypeService {

    record Request(
            @NotBlank @Size(max = 30) @Pattern(regexp = AreaService.CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @NotBlank @Size(max = 100) String name) {
    }

    record Response(Long id, String code, String name, boolean seeded) {
    }

    private final EquipmentTypeRepository repository;
    private final AuditService audit;
    private final JsonMapper json;

    EquipmentTypeService(EquipmentTypeRepository repository, AuditService audit, JsonMapper json) {
        this.repository = repository;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Response> list(String q) {
        Specification<EquipmentType> spec = Specs.all(Specs.<EquipmentType>search(q, "code", "name"));
        return repository.findAll(spec, Sort.by("code")).stream().map(EquipmentTypeService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Response get(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public Response create(Request r, WmsUserPrincipal who) {
        String code = r.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw MasterDataException.invalid("code", "this code is already used");
        }
        EquipmentType t = new EquipmentType();
        t.code = code;
        t.name = r.name().trim();
        t.seeded = false;
        Response after = toResponse(repository.saveAndFlush(t));
        record("CREATE", who, after.id(), null, after);
        return after;
    }

    @Transactional
    public Response update(Long id, Request r, WmsUserPrincipal who) {
        EquipmentType t = load(id);
        Response before = toResponse(t);
        String code = r.code().trim().toUpperCase();
        if (t.seeded && !t.code.equals(code)) {
            throw MasterDataException.forbidden("The code of a built-in equipment type cannot be changed.");
        }
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw MasterDataException.invalid("code", "this code is already used");
        }
        t.code = code;
        t.name = r.name().trim();
        Response after = toResponse(repository.saveAndFlush(t));
        record("UPDATE", who, id, before, after);
        return after;
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        EquipmentType t = load(id);
        if (t.seeded) {
            throw MasterDataException.forbidden("A built-in equipment type cannot be deleted.");
        }
        Response before = toResponse(t);
        try {
            repository.delete(t);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw MasterDataException.inUse();
        }
        record("DELETE", who, id, before, null);
    }

    private EquipmentType load(Long id) {
        return repository.findById(id).orElseThrow(() -> MasterDataException.notFound("equipment_type"));
    }

    private void record(String action, WmsUserPrincipal who, Object id, Object before, Object after) {
        AuditBuffer.record(audit, new AuditEvent(who.username(), action, who.siteId(), "equipment_type", String.valueOf(id),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null,
                MasterDataService.remoteAddr(), null));
    }

    private static Response toResponse(EquipmentType t) {
        return new Response(t.id, t.code, t.name, t.seeded);
    }
}
