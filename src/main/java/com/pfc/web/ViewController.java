package com.pfc.web;

import com.pfc.api.ApiError;
import com.pfc.api.Filters;
import com.pfc.config.ThindeskProperties;
import com.pfc.data.DashboardRepository;
import com.pfc.model.Forms.*;
import com.pfc.service.ManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
public class ViewController {
    private final ManagementService service;
    private final DashboardRepository dashboard;
    private final ThindeskProperties properties;
    public ViewController(ManagementService service, DashboardRepository dashboard, ThindeskProperties properties) { this.service=service; this.dashboard=dashboard; this.properties=properties; }
    @ModelAttribute public void common(Model model) {
        model.addAttribute("statusLabels", Filters.STATUSES); model.addAttribute("days", ManagementService.DAYS);
        model.addAttribute("securityEnabled", properties.securityEnabled());
    }
    @GetMapping({"/", "/dashboard"}) public String dashboard() { return "dashboard"; }
    @GetMapping("/home") public String home() { return "redirect:/"; }
    @GetMapping("/login") public String login(Model model) { model.addAttribute("securityEnabled", properties.securityEnabled()); return "login"; }
    @GetMapping("/chamados") public String chamados(@RequestParam(defaultValue="1") int page, Model model) {
        var all = service.chamados();
        if (page < 1) throw ApiError.validation("page", "Informe uma página maior ou igual a 1.");
        int pages = Math.max(1, (all.size()+19)/20);
        int current = Math.min(page, pages), offset=(current-1)*20;
        model.addAttribute("chamados", all.subList(offset, Math.min(offset+20, all.size())));
        model.addAttribute("page", current); model.addAttribute("pages", pages); model.addAttribute("total", all.size());
        return "chamados/lista";
    }
    @GetMapping({"/chamados/novo", "/chamados/novo/"}) public String novoChamado(Model model) { model.addAttribute("chamado", new Chamado()); return ticketForm(null, model); }
    @GetMapping("/chamados/atualizar/{id}") public String editarChamado(@PathVariable String id, Model model) { model.addAttribute("chamado", service.chamado(id)); return ticketForm(id, model); }
    @PostMapping({"/chamados/novo", "/chamados/atualizar/{id}"}) public String saveChamado(@PathVariable(required=false) String id, @Valid @ModelAttribute("chamado") Chamado item, BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) try { service.saveChamado(id, item); } catch (ApiError exception) { if (exception.status()!=400) throw exception; reject(errors, exception); }
        if (errors.hasErrors()) return ticketForm(id, model);
        flash.addFlashAttribute("success", "Chamado salvo com sucesso."); return "redirect:/chamados";
    }
    @PostMapping("/chamados/cancelar/{id}") public String cancelar(@PathVariable String id, RedirectAttributes flash) { service.cancelChamado(id); flash.addFlashAttribute("success", "Chamado cancelado."); return "redirect:/chamados"; }
    private String ticketForm(String id, Model model) { model.addAttribute("types", dashboard.types()); model.addAttribute("editId", id); return "chamados/form"; }
    @GetMapping("/clientes") public String clientes(Model model) { model.addAttribute("clientes", service.clientes()); return "clientes/lista"; }
    @GetMapping({"/clientes/novo", "/clientes/novo/"}) public String novoCliente(Model model) { model.addAttribute("cliente", new Cliente()); model.addAttribute("editId", null); return "clientes/form"; }
    @GetMapping("/clientes/editar/{id}") public String editarCliente(@PathVariable String id, Model model) { model.addAttribute("cliente", service.cliente(id)); model.addAttribute("editId", id); return "clientes/form"; }
    @PostMapping({"/clientes/novo", "/clientes/editar/{id}"}) public String saveCliente(@PathVariable(required=false) String id, @Valid @ModelAttribute("cliente") Cliente item, BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) { model.addAttribute("editId", id); return "clientes/form"; }
        service.saveCliente(id, item); flash.addFlashAttribute("success", "Cliente salvo com sucesso."); return "redirect:/clientes";
    }
    @PostMapping("/clientes/deletar/{id}") public String deleteCliente(@PathVariable String id, RedirectAttributes flash) { service.deleteCliente(id); flash.addFlashAttribute("success", "Cliente excluído."); return "redirect:/clientes"; }
    @GetMapping("/ajustes-horarios") public String horarios(Model model) { model.addAttribute("novoHorario", new Horario()); return scheduleForm(model); }
    @GetMapping("/ajustes-horarios/editar/{id}") public String editarHorario(@PathVariable String id, Model model) { model.addAttribute("novoHorario", service.horario(id)); return scheduleForm(model); }
    @PostMapping("/ajustes-horarios/salvar") public String saveHorario(@Valid @ModelAttribute("novoHorario") Horario item, BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) try { service.saveHorario(item.getId()==null || item.getId().isBlank() ? null : item.getId(), item); } catch (ApiError exception) { if (exception.status()!=400) throw exception; reject(errors, exception); }
        if (errors.hasErrors()) return scheduleForm(model);
        flash.addFlashAttribute("success", "Horário salvo com sucesso."); return "redirect:/ajustes-horarios";
    }
    @PostMapping("/ajustes-horarios/deletar/{id}") public String deleteHorario(@PathVariable String id, RedirectAttributes flash) { service.deleteHorario(id); flash.addFlashAttribute("success", "Horário excluído."); return "redirect:/ajustes-horarios"; }
    private String scheduleForm(Model model) { model.addAttribute("horarios", service.horarios()); return "ajustes-horarios"; }
    @GetMapping("/tecnicos") public String tecnicos(Model model) {
        model.addAttribute("catalogActive", "tecnicos");
        model.addAttribute("catalogTitle", "Técnicos"); model.addAttribute("catalogDescription", "Profissionais associados aos chamados cadastrados.");
        model.addAttribute("entries", service.chamados().stream().map(Chamado::getTecnico).filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().sorted().toList());
        return "catalogo";
    }
    @GetMapping("/tipo-chamado") public String tipos(Model model) {
        model.addAttribute("catalogActive", "tipos");
        model.addAttribute("catalogTitle", "Tipos de chamado"); model.addAttribute("catalogDescription", "Categorias disponíveis para classificação dos chamados.");
        model.addAttribute("entries", dashboard.types().stream().map(row -> row.get("name")).toList()); return "catalogo";
    }
    @GetMapping("/recursos") public String recursos() { return "redirect:/docs"; }
    @GetMapping("/docs") public String documentation() { return "api-docs"; }
    private static void reject(BindingResult errors, ApiError exception) {
        if (exception.details() instanceof List<?> details) {
            for (Object detail : details) if (detail instanceof Map<?,?> field) errors.rejectValue(field.get("param").toString(), "validation", field.get("msg").toString());
        } else errors.reject("validation", exception.getMessage());
    }
}
