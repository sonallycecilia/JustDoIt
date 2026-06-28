package com.justdoit.backend.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Tarefa {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	private String titulo;
    private String descricao;
    private String status;
	private LocalDateTime dataCriacao;
	private LocalDateTime dataAtualizacao;

    public Tarefa() {}
    
    public Long getId() {
    	return id; 
    	}
    public void setId(Long id) { 
    	this.id = id; 
    	}
	public String getTitulo() {
		return titulo;}
	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}
    public String getDescricao() { 
    	return descricao; 
    	}
    public void setDescricao(String descricao) { 
    	this.descricao = descricao; 
    	}
    public String getStatus() {
    	return status;
    	}
    public void setStatus(String status) {
    	this.status = status;
    	}
	public LocalDateTime getDataCriacao() {return dataCriacao;}
	public void setDataCriacao(LocalDateTime dataCriacao) {this.dataCriacao = dataCriacao;}
	public LocalDateTime getDataAtualizacao() {return dataAtualizacao;}
	public void setDataAtualizacao(LocalDateTime dataAtualizacao) {this.dataAtualizacao = dataAtualizacao;}

	public String completarTarefa() {
		this.status = "Concluido";
		return this.status;
	}
}
