package com.pfc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Forms {
    private Forms() {}
    public static class Chamado {
        private String id;
        @NotBlank(message="Informe a descrição.") @Size(max=250, message="A descrição deve ter até 250 caracteres.") private String descricao;
        @NotBlank(message="Informe o status.") private String status = "aberto";
        @NotBlank(message="Informe o tipo.") @Size(max=100) private String tipo;
        @Size(max=120) private String tecnico;
        @Size(max=120) private String usuario;
        @NotBlank(message="Informe a prioridade.") private String prioridade = "media";
        @Size(max=4000, message="As considerações devem ter até 4.000 caracteres.") private String consideracoesTecnicas;
        private String abertura;
        private String encerramento;
        public String getId() { return id; } public void setId(String value) { id=value; }
        public String getDescricao() { return descricao; } public void setDescricao(String value) { descricao=value; }
        public String getStatus() { return status; } public void setStatus(String value) { status=value; }
        public String getTipo() { return tipo; } public void setTipo(String value) { tipo=value; }
        public String getTecnico() { return tecnico; } public void setTecnico(String value) { tecnico=value; }
        public String getUsuario() { return usuario; } public void setUsuario(String value) { usuario=value; }
        public String getPrioridade() { return prioridade; } public void setPrioridade(String value) { prioridade=value; }
        public String getConsideracoesTecnicas() { return consideracoesTecnicas; } public void setConsideracoesTecnicas(String value) { consideracoesTecnicas=value; }
        public String getAbertura() { return abertura; } public void setAbertura(String value) { abertura=value; }
        public String getEncerramento() { return encerramento; } public void setEncerramento(String value) { encerramento=value; }
    }
    public static class Cliente {
        private String id;
        @NotBlank(message="Informe o nome.") @Size(max=120) private String nome;
        @NotBlank(message="Informe o telefone.") @Size(max=40) private String telefone;
        @NotBlank(message="Informe o setor.") @Size(max=120) private String setor;
        public String getId() { return id; } public void setId(String value) { id=value; }
        public String getNome() { return nome; } public void setNome(String value) { nome=value; }
        public String getTelefone() { return telefone; } public void setTelefone(String value) { telefone=value; }
        public String getSetor() { return setor; } public void setSetor(String value) { setor=value; }
    }
    public static class Horario {
        private String id;
        @NotBlank(message="Informe o setor.") @Size(max=120) private String setor;
        @NotBlank(message="Informe o dia da semana.") private String diaSemana;
        @NotBlank(message="Informe o horário inicial.") private String horarioInicio;
        @NotBlank(message="Informe o horário final.") private String horarioFim;
        public String getId() { return id; } public void setId(String value) { id=value; }
        public String getSetor() { return setor; } public void setSetor(String value) { setor=value; }
        public String getDiaSemana() { return diaSemana; } public void setDiaSemana(String value) { diaSemana=value; }
        public String getHorarioInicio() { return horarioInicio; } public void setHorarioInicio(String value) { horarioInicio=value; }
        public String getHorarioFim() { return horarioFim; } public void setHorarioFim(String value) { horarioFim=value; }
    }
}
