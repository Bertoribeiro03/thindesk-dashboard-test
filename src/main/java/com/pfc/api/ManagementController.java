package com.pfc.api;

import com.pfc.model.Forms.*;
import com.pfc.service.ManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ManagementController {
    private final ManagementService service;
    public ManagementController(ManagementService service) { this.service=service; }
    @GetMapping("/chamados") public List<Chamado> chamados() { return service.chamados(); }
    @GetMapping("/chamados/{id}") public Chamado chamado(@PathVariable String id) { return service.chamado(id); }
    @PostMapping("/chamados") @ResponseStatus(HttpStatus.CREATED) public Chamado criar(@Valid @RequestBody Chamado item) { return service.saveChamado(null, item); }
    @PutMapping("/chamados/{id}") public Chamado atualizar(@PathVariable String id, @Valid @RequestBody Chamado item) { return service.saveChamado(id, item); }
    @GetMapping("/clientes") public List<Cliente> clientes() { return service.clientes(); }
    @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED) public Cliente criarCliente(@Valid @RequestBody Cliente item) { return service.saveCliente(null, item); }
    @PutMapping("/clientes/{id}") public Cliente atualizarCliente(@PathVariable String id, @Valid @RequestBody Cliente item) { return service.saveCliente(id, item); }
    @DeleteMapping("/clientes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCliente(@PathVariable String id) { service.deleteCliente(id); }
    @GetMapping("/ajustes-horarios") public List<Horario> horarios() { return service.horarios(); }
    @PostMapping("/ajustes-horarios") @ResponseStatus(HttpStatus.CREATED) public Horario criarHorario(@Valid @RequestBody Horario item) { return service.saveHorario(null, item); }
    @PutMapping("/ajustes-horarios/{id}") public Horario atualizarHorario(@PathVariable String id, @Valid @RequestBody Horario item) { return service.saveHorario(id, item); }
    @DeleteMapping("/ajustes-horarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteHorario(@PathVariable String id) { service.deleteHorario(id); }
}
