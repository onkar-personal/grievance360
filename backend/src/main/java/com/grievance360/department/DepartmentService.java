package com.grievance360.department;

import com.grievance360.audit.AuditService;
import com.grievance360.common.ApiException;
import com.grievance360.department.DepartmentDtos.*;
import com.grievance360.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departments;
    private final CategoryRepository categories;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments(boolean includeInactive) {
        var list = includeInactive ? departments.findAllByOrderByNameAsc() : departments.findByActiveTrueOrderByNameAsc();
        return list.stream().map(DepartmentDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(boolean includeInactive) {
        var list = includeInactive ? categories.findAllByOrderByNameAsc() : categories.findByActiveTrueAndDepartmentActiveTrueOrderByNameAsc();
        return list.stream().map(CategoryDto::of).toList();
    }

    @Transactional
    public DepartmentDto createDepartment(DepartmentRequest req, UserPrincipal actor) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (departments.existsByCodeIgnoreCase(code)) throw ApiException.conflict("DUPLICATE_CODE", "Department code already exists");
        if (departments.existsByNameIgnoreCase(req.name().trim())) throw ApiException.conflict("DUPLICATE_NAME", "Department name already exists");
        Department d = new Department();
        apply(d, req, code);
        departments.save(d);
        audit.record(actor, "DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), d.getCode());
        return DepartmentDto.of(d);
    }

    @Transactional
    public DepartmentDto updateDepartment(Long id, DepartmentRequest req, UserPrincipal actor) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (!d.getCode().equalsIgnoreCase(code) && departments.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("DUPLICATE_CODE", "Department code already exists");
        }
        if (!d.getName().equalsIgnoreCase(req.name().trim()) && departments.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("DUPLICATE_NAME", "Department name already exists");
        }
        apply(d, req, code);
        audit.record(actor, "DEPARTMENT_UPDATED", "DEPARTMENT", d.getId(), d.getCode() + " active=" + d.isActive());
        return DepartmentDto.of(d);
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest req, UserPrincipal actor) {
        Department d = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
        if (categories.existsByDepartmentIdAndNameIgnoreCase(d.getId(), req.name().trim())) {
            throw ApiException.conflict("DUPLICATE_NAME", "Category already exists in this department");
        }
        ComplaintCategory c = new ComplaintCategory();
        apply(c, req, d);
        categories.save(c);
        audit.record(actor, "CATEGORY_CREATED", "CATEGORY", c.getId(), c.getName());
        return CategoryDto.of(c);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest req, UserPrincipal actor) {
        ComplaintCategory c = categories.findWithDepartmentById(id).orElseThrow(() -> ApiException.notFound("Category"));
        Department d = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
        boolean renamed = !c.getName().equalsIgnoreCase(req.name().trim()) || !c.getDepartment().getId().equals(d.getId());
        if (renamed && categories.existsByDepartmentIdAndNameIgnoreCase(d.getId(), req.name().trim())) {
            throw ApiException.conflict("DUPLICATE_NAME", "Category already exists in this department");
        }
        apply(c, req, d);
        audit.record(actor, "CATEGORY_UPDATED", "CATEGORY", c.getId(), c.getName() + " sla=" + c.getSlaHours() + "h");
        return CategoryDto.of(c);
    }

    private static void apply(Department d, DepartmentRequest req, String code) {
        d.setCode(code);
        d.setName(req.name().trim());
        d.setDescription(blankToNull(req.description()));
        d.setContactEmail(blankToNull(req.contactEmail()));
        d.setActive(req.active());
    }

    private static void apply(ComplaintCategory c, CategoryRequest req, Department d) {
        c.setDepartment(d);
        c.setName(req.name().trim());
        c.setDescription(blankToNull(req.description()));
        c.setSlaHours(req.slaHours());
        c.setDefaultPriority(req.defaultPriority());
        c.setActive(req.active());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
