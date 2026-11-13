package com.orderflow.shared.security;

import java.util.List;
import java.util.Set;

/**
 * Perfis de acesso (RBAC) e mapeamento determinístico de authorities granulares
 * do Ecossistema OrderFlow.
 */
public enum Role {

    ATENDENTE(Set.of(
            "catalogo:ler",
            "pedido:criar",
            "pedido:ler",
            "pedido:confirmar",
            "pedido:cancelar_inicial",
            "pedido:entregar",
            "pagamento:registrar",
            "caixa:operar"
    )),

    COZINHA(Set.of(
            "catalogo:ler",
            "insumo:disponibilidade",
            "cozinha:operar",
            "pedido:entregar"
    )),

    GERENTE(Set.of(
            "catalogo:ler",
            "catalogo:escrever",
            "catalogo:preco",
            "insumo:disponibilidade",
            "pedido:criar",
            "pedido:ler",
            "pedido:confirmar",
            "pedido:cancelar_inicial",
            "pedido:cancelar_forcado",
            "pedido:entregar",
            "cozinha:operar",
            "pagamento:registrar",
            "pagamento:estornar",
            "caixa:operar",
            "caixa:gerenciar",
            "caixa:conferir",
            "relatorio:ler"
    )),

    ADMIN(Set.of(
            "catalogo:ler",
            "catalogo:escrever",
            "catalogo:preco",
            "insumo:disponibilidade",
            "pedido:criar",
            "pedido:ler",
            "pedido:confirmar",
            "pedido:cancelar_inicial",
            "pedido:cancelar_forcado",
            "pedido:entregar",
            "cozinha:operar",
            "pagamento:registrar",
            "pagamento:estornar",
            "caixa:operar",
            "caixa:gerenciar",
            "caixa:conferir",
            "relatorio:ler",
            "admin:usuarios",
            "admin:lojas"
    ));

    private final Set<String> defaultAuthorities;

    Role(Set<String> defaultAuthorities) {
        this.defaultAuthorities = defaultAuthorities;
    }

    public String asSpringRole() {
        return "ROLE_" + this.name();
    }

    public Set<String> getDefaultAuthorities() {
        return defaultAuthorities;
    }

    public List<String> allGrantedAuthorities() {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(asSpringRole()),
                defaultAuthorities.stream()
        ).toList();
    }
}
