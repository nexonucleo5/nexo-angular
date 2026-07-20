package com.nexo.repository;

import com.nexo.domain.Conversa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {
    List<Conversa> findByCaixaOrderByAtualizadaEmDesc(Conversa.Caixa caixa);
}
