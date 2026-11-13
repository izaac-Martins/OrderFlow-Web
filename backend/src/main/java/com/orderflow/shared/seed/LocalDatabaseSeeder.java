package com.orderflow.shared.seed;

import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.caixa.domain.Caixa;
import com.orderflow.caixa.repository.CaixaRepository;
import com.orderflow.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inicializador automático de dados base para execução local integrada (Fase 5).
 * Garante que a Loja Matriz, usuários de todos os perfis (com PINs gerenciais 4829 / 1234)
 * e terminais de caixa existam no banco SQL local.
 */
@Component
@Profile("!test")
public class LocalDatabaseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseSeeder.class);
    public static final UUID LOJA_MATRIZ_ID = UUID.fromString("019325f0-8a00-7000-8000-000000000001");

    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CaixaRepository caixaRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalDatabaseSeeder(
            LojaRepository lojaRepository,
            UsuarioRepository usuarioRepository,
            CaixaRepository caixaRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
        this.caixaRepository = caixaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (lojaRepository.existsById(LOJA_MATRIZ_ID)) {
            log.info("OrderFlow Database já inicializado para a Loja Matriz ({})", LOJA_MATRIZ_ID);
            return;
        }

        Loja matriz = lojaRepository.save(new Loja(
                LOJA_MATRIZ_ID,
                "MATRIZ-01",
                "Cafeteria OrderFlow — Unidade Matriz Jardins",
                "12.345.678/0001-90"
        ));

        String senhaHash = passwordEncoder.encode("123456");
        String pinGerenteHash = passwordEncoder.encode("4829");
        String pinAdminHash = passwordEncoder.encode("1234");

        usuarioRepository.save(new Usuario(
                matriz.getId(),
                "Roberto Almeida (Gerente)",
                "gerente@orderflow.local",
                senhaHash,
                Role.GERENTE,
                pinGerenteHash
        ));

        usuarioRepository.save(new Usuario(
                matriz.getId(),
                "Helena Costa (Admin)",
                "admin@orderflow.local",
                senhaHash,
                Role.ADMIN,
                pinAdminHash
        ));

        usuarioRepository.save(new Usuario(
                matriz.getId(),
                "Lucas Martins (Operador de Caixa)",
                "caixa@orderflow.local",
                senhaHash,
                Role.ATENDENTE,
                null
        ));

        usuarioRepository.save(new Usuario(
                matriz.getId(),
                "Marina Souza (Atendente PDV)",
                "atendente@orderflow.local",
                senhaHash,
                Role.ATENDENTE,
                null
        ));

        usuarioRepository.save(new Usuario(
                matriz.getId(),
                "Chef Barista KDS",
                "cozinha@orderflow.local",
                senhaHash,
                Role.COZINHA,
                null
        ));

        caixaRepository.save(new Caixa(matriz.getId(), "Caixa 01 — Balcão Principal", "PDV-BALCAO-01", 1, true, true));
        caixaRepository.save(new Caixa(matriz.getId(), "Caixa 02 — Ilha Express", "PDV-EXPRESS-02", 2, true, true));

        log.info("OrderFlow Seed concluído com sucesso! Loja Matriz={}, Usuários=5, Terminais=2", LOJA_MATRIZ_ID);
    }
}
