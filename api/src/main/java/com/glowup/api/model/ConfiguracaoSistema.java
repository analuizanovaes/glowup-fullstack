package com.glowup.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracoes_sistema")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoSistema {

    @Id
    private String chave;
    private String valor;
}